package com.tileai.index;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Predicate;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tileai.admin.entity.AdminUser;
import com.tileai.admin.mapper.AdminUserMapper;
import com.tileai.common.mq.RabbitTopologyConfig;
import com.tileai.common.outbox.OutboxMapper;
import com.tileai.common.outbox.OutboxMessage;
import com.tileai.common.security.JwtService;
import com.tileai.common.security.SubjectType;
import com.tileai.product.entity.Product;
import com.tileai.product.entity.ProductImage;
import com.tileai.product.mapper.ProductImageMapper;
import com.tileai.product.mapper.ProductMapper;

import jakarta.servlet.http.Cookie;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * T-0.13 验收:索引同步的<b>端到端链路</b>(架构 §6.2、§6.4、§9.2、§15.2)。
 *
 * <p><b>需要 MySQL + RabbitMQ</b>({@code -Drabbit.verify=true};该开关隐含需要 MySQL)。
 *
 * <p>验证链路:
 * <pre>
 *   业务变更 → t_outbox(同业务事务) → 投递器扫描 → 发布到 RabbitMQ queue.index.sync
 * </pre>
 *
 * <p><b>不只断言"outbox 行变成 sent"</b> —— 那只能证明投递器认为自己发成功了。
 * 这里真的从队列里把消息取出来解析,确认它就是契约要求的 {@code IndexSyncPayload},
 * 且 payload 携带了检索过滤字段(§9.2)。少了这一步,"发出去了但消息体是错的"
 * 完全不会被发现。
 *
 * <p><b>取消息用"按条件筛选"而不是"取第一条"</b>:投递器是定时任务,上一个用例
 * 的消息可能还没发出来;"先清空队列再取第一条"会把它当成本次的结果。
 * 按条件筛选从根上消除这个竞态。
 */
@EnabledIfSystemProperty(named = "rabbit.verify", matches = "true",
        disabledReason = "需要 RabbitMQ 与 MySQL;用 -Drabbit.verify=true -Ddb.verify=true 显式启用")
@SpringBootTest(properties = {"spring.flyway.enabled=false",
        "tile.auth.secret=0123456789abcdef0123456789abcdef"})
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IndexSyncIntegrationTest {

    private static final String PRODUCTS = "/api/v1/admin/products";
    private static final String SKU = "T13-IDX-SKU";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private AdminUserMapper adminUserMapper;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private ProductImageMapper productImageMapper;

    @Autowired
    private OutboxMapper outboxMapper;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    private long adminId;
    private String token;

    @BeforeAll
    void seed() {
        AdminUser admin = new AdminUser();
        admin.setUsername("t13_admin");
        admin.setPasswordHash("$2a$10$placeholder-not-used-because-no-login-here");
        admin.setRole("operator");
        admin.setStatus(AdminUser.STATUS_ENABLED);
        adminUserMapper.insert(admin);
        adminId = admin.getId();
        token = jwtService.issueAccess(adminId, SubjectType.ADMIN);
    }

    /**
     * 每个用例都从"该 SKU 不存在"开始。
     *
     * <p>不加这一步会踩坑:多个用例共用同一个探针 SKU,而 JUnit <b>不保证执行顺序</b> ——
     * 先跑的把它建出来,后跑的再建就撞 30002「产品编号已存在」,报错还指向"创建失败"。
     * 这是本项目第四次踩同一类问题,规律很明确:
     * <b>共用可变前置数据就必须显式重置,不能依赖执行顺序。</b>
     */
    @BeforeEach
    void resetProbeProduct() {
        deleteProbeProduct();
    }

    @AfterAll
    void cleanup() {
        deleteProbeProduct();
        adminUserMapper.deleteById(adminId);
    }

    // ---------------------------------------------------------------
    // 判据:上下架 / 改价后 payload 同步更新
    // ---------------------------------------------------------------

    @Test
    @DisplayName("新建 SKU → 产生 upsert 事件,消息体符合 IndexSyncPayload 契约")
    void productCreateEmitsUpsertEvent() throws Exception {
        deleteProbeProduct();
        createProduct("268.00");

        JsonNode event = awaitProductEvent(sku -> "upsert".equals(item(sku).get("action").asText())
                && sku.hasNonNull("eventId"));

        assertEquals("product", event.get("collection").asText());
        assertTrue(event.hasNonNull("traceId"), "契约要求 traceId");

        JsonNode item = item(event);
        assertEquals(SKU, item.get("id").asText(), "product 集合的业务主键是 sku_code");

        JsonNode payload = item.get("payload");
        assertEquals(SKU, payload.get("sku_code").asText());
        assertEquals("off_sale", payload.get("status").asText());
        assertTrue(payload.hasNonNull("updated_at"),
                "§15.2 要求 payload 保留 updated_at 用于排查漂移");
    }

    @Test
    @DisplayName("判据:SKU 上架后,payload 中的 status 同步变为 on_sale")
    void statusChangeUpdatesPayload() throws Exception {
        long productId = createProduct("100.00");

        assertEquals(0, codeOf(asAdmin(patch(PRODUCTS + "/" + productId + "/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"on_sale\"}")).andReturn()));

        awaitProductEvent(event -> "on_sale".equals(
                item(event).get("payload").get("status").asText()));
        // 断言靠 await 里的条件谓词完成:队列里真的出现了 status=on_sale 的事件。
        // 下架产品要在检索时被排除,靠的正是这个字段 —— 不同步等于下架了还能搜到。
    }

    @Test
    @DisplayName("判据:改价后 payload 中的 price 同步更新")
    void priceChangeUpdatesPayload() throws Exception {
        long productId = createProduct("100.00");

        assertEquals(0, codeOf(asAdmin(put(PRODUCTS + "/" + productId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(productJson("188.88"))).andReturn()));

        JsonNode event = awaitProductEvent(node ->
                new BigDecimal("188.88").compareTo(
                        item(node).get("payload").get("price").decimalValue()) == 0);
        assertEquals(0, new BigDecimal("188.88")
                        .compareTo(item(event).get("payload").get("price").decimalValue()),
                "改价后索引里的价格必须跟上,否则按价格区间过滤会漏");
    }

    @Test
    @DisplayName("删除 SKU → 产生 delete 事件(把点从索引移除)")
    void productDeleteEmitsDeleteEvent() throws Exception {
        long productId = createProduct("100.00");

        assertEquals(0, codeOf(asAdmin(delete(PRODUCTS + "/" + productId)).andReturn()));

        JsonNode event = awaitProductEvent(node ->
                "delete".equals(item(node).get("action").asText()));
        assertEquals("product", event.get("collection").asText());
        assertEquals(SKU, item(event).get("id").asText());
    }

    // ---------------------------------------------------------------
    // Outbox 本身的行为
    // ---------------------------------------------------------------

    @Test
    @DisplayName("事件经 t_outbox 投递,投递成功后状态变为 sent")
    void outboxRowIsMarkedSent() throws Exception {
        deleteProbeProduct();
        createProduct("100.00");

        // 投递器是定时任务;这里轮询等它跑完,而不是在测试里手动触发 ——
        // 那样验的才是真实的投递路径。
        OutboxMessage sent = awaitOutboxSentAfter(0);
        assertEquals(OutboxMessage.AGGREGATE_INDEX_SYNC, sent.getAggregateType());
        assertEquals(RabbitTopologyConfig.QUEUE_INDEX_SYNC, sent.getQueue());
        assertNotNull(sent.getSentAt(), "投递成功后应记录发送时间");
    }

    @Test
    @DisplayName("消息体在落库时就确定,投递时原样发出(不重新构造)")
    void payloadIsPersistedNotRebuiltAtDispatch() throws Exception {
        deleteProbeProduct();
        long baseline = outboxMapper.selectCount(null);
        createProduct("100.00");

        OutboxMessage stored = awaitOutboxSentAfter(baseline);
        JsonNode storedPayload = objectMapper.readTree(stored.getPayload());
        String eventId = storedPayload.get("eventId").asText();

        // 从队列里捞出同一个事件,逐字段比对
        JsonNode received = awaitProductEvent(node -> eventId.equals(node.get("eventId").asText()));
        assertEquals(storedPayload, received,
                "入库的 payload 与投递出去的消息必须逐字段一致 —— "
                        + "若投递时重新构造,库里的记录就不再是「发生过什么」的凭据");
    }

    // ---------------------------------------------------------------
    // 工具
    // ---------------------------------------------------------------

    /**
     * 从队列里捞出一个<b>本测试探针 SKU</b> 的、满足条件的事件。
     *
     * <p>不满足条件的消息被丢弃(dev 环境的队列,无所谓);超时则失败。
     *
     * <p><b>必须锁定 {@link #SKU}</b>:队列里还留着其它测试类发出的事件
     * (T-0.6/T-0.7 的用例也会触发索引同步,而 Python 消费者尚未落地、
     * 没人消费这些消息)。只按 "collection=product 且 action=upsert" 筛选的话,
     * 会把别的测试的遗留消息当成本次结果 —— 那是纯粹的假阳。
     */
    private JsonNode awaitProductEvent(Predicate<JsonNode> predicate) throws Exception {
        long deadline = System.currentTimeMillis() + 30_000;
        JsonNode lastSeen = null;
        while (System.currentTimeMillis() < deadline) {
            Object received = rabbitTemplate.receiveAndConvert(RabbitTopologyConfig.QUEUE_INDEX_SYNC);
            if (received == null) {
                Thread.sleep(100);
                continue;
            }
            JsonNode event = objectMapper.readTree(String.valueOf(received));
            if (!"product".equals(event.path("collection").asText())) {
                continue;
            }
            if (!SKU.equals(item(event).path("id").asText())) {
                continue;
            }
            lastSeen = event;
            if (predicate.test(event)) {
                return event;
            }
        }
        throw new AssertionError("30 秒内未收到满足条件的索引同步事件,最后看到: " + lastSeen);
    }

    /** 等 id 大于 baseline 的 outbox 记录被投递成功。 */
    private OutboxMessage awaitOutboxSentAfter(long baseline) throws Exception {
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            List<OutboxMessage> sent = outboxMapper.selectList(Wrappers.<OutboxMessage>lambdaQuery()
                    .eq(OutboxMessage::getStatus, OutboxMessage.STATUS_SENT)
                    .eq(OutboxMessage::getQueue, RabbitTopologyConfig.QUEUE_INDEX_SYNC)
                    .gt(OutboxMessage::getId, baseline)
                    .orderByDesc(OutboxMessage::getId)
                    .last("LIMIT 1"));
            if (!sent.isEmpty()) {
                return sent.get(0);
            }
            Thread.sleep(100);
        }
        throw new AssertionError("30 秒内 outbox 记录未变成 sent");
    }

    private static JsonNode item(JsonNode event) {
        return event.get("items").get(0);
    }

    private long createProduct(String price) throws Exception {
        MvcResult result = asAdmin(post(PRODUCTS).contentType(MediaType.APPLICATION_JSON)
                .content(productJson(price))).andReturn();
        assertEquals(0, codeOf(result), "创建应成功: " + result.getResponse().getContentAsString());
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("id").asLong();
    }

    private String productJson(String price) throws Exception {
        var node = objectMapper.createObjectNode();
        node.put("sku", SKU);
        node.put("name", "索引同步探针砖");
        node.put("material", "釉面砖");
        node.put("pricePerSqm", new BigDecimal(price));
        node.putArray("applicableSpaces").add("bathroom");
        node.putArray("styleTags").add("modern");
        return objectMapper.writeValueAsString(node);
    }

    private void deleteProbeProduct() {
        Product product = productMapper.selectOne(Wrappers.<Product>lambdaQuery()
                .eq(Product::getSkuCode, SKU));
        if (product != null) {
            productImageMapper.delete(Wrappers.<ProductImage>lambdaQuery()
                    .eq(ProductImage::getProductId, product.getId()));
            productMapper.deleteById(product.getId());
        }
    }

    private org.springframework.test.web.servlet.ResultActions asAdmin(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder rb)
            throws Exception {
        // 后台接口读 admin_access_token —— 两端各一套 Cookie(架构 §10.1)
        return mvc.perform(rb.cookie(new Cookie("admin_access_token", token)));
    }

    private static int codeOf(MvcResult result) throws Exception {
        return new ObjectMapper().readTree(result.getResponse().getContentAsString())
                .get("code").asInt();
    }
}
