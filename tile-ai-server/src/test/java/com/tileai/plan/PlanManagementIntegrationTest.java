package com.tileai.plan;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tileai.common.outbox.OutboxMapper;
import com.tileai.common.outbox.OutboxMessage;
import com.tileai.common.security.JwtService;
import com.tileai.common.security.SubjectType;
import com.tileai.generate.entity.GenerationTask;
import com.tileai.generate.mapper.GenerationTaskMapper;
import com.tileai.plan.entity.Plan;
import com.tileai.plan.entity.PlanImage;
import com.tileai.plan.entity.PlanPart;
import com.tileai.plan.entity.PlanQuote;
import com.tileai.plan.mapper.PlanImageMapper;
import com.tileai.plan.mapper.PlanMapper;
import com.tileai.plan.mapper.PlanPartMapper;
import com.tileai.plan.mapper.PlanQuoteMapper;
import com.tileai.product.entity.Product;
import com.tileai.product.mapper.ProductMapper;
import com.tileai.quote.entity.QuoteParam;
import com.tileai.quote.mapper.QuoteParamMapper;

import jakarta.servlet.http.Cookie;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * T-1.12 验收:C 端「我的方案」五条接口
 * (契约 {@code openapi-c.yaml} 的 plan 分组;PRD §5.6、§11.4)。
 *
 * <p><b>需要 MySQL</b>({@code -Ddb.verify=true} 显式启用)。<b>不需要 Redis、
 * 不需要 RabbitMQ</b> —— 登录态由 {@link JwtService} 直接签一个 access 令牌充当
 * (登录本身要 Redis,而本测试验的不是登录);出图回调走 HTTP,不经消息队列。
 *
 * <p>本类同时是 2026-10-01 四个决策的判据,每一条都对应下面至少一个用例:
 * <ol>
 *   <li><b>封面取现成图地址</b> —— {@code coverImage} 来自 {@code is_cover = 1} 那行;</li>
 *   <li><b>报价快照在生成时落库</b> —— 算得出就落全,算不出就<b>整单不落</b>;</li>
 *   <li><b>另存为默认名 = 原名 + {@code (副本)}</b>,且原方案一字不动;</li>
 *   <li><b>{@code source} 真正落库</b>(推翻 T-1.11 的旧判断,见 {@code GenerateCallbackService}).</li>
 * </ol>
 *
 * <h2>为什么报价快照要自己 seed 产品与报价参数</h2>
 *
 * <p>{@code GenerateIntegrationTest} 用的那两个 SKU({@code T11-FLOOR} /
 * {@code T11-WALL})在 {@code t_product} 里<b>根本不存在</b>,报价参数也没有配 ——
 * 所以那条链路走的是"算不出"分支,<b>证明不了报价快照落了</b>。
 * 本类自己 seed,才拿得到"算得出"那条路径。
 *
 * <h2>只对自己造的数据下结论</h2>
 *
 * <p>用户 id 用 {@code 8_812_00xx} 这一段固定大号,SKU 与产品名一律带 {@code T12-}
 * 前缀,清理只按这两个维度删 —— 不对全表条数做任何断言(库里还有别的测试类、
 * 别的用例的数据)。
 */
@EnabledIfSystemProperty(named = "db.verify", matches = "true",
        disabledReason = "需要 MySQL;用 -Ddb.verify=true 显式启用(T-1.12 验证)")
@SpringBootTest(properties = {
        "spring.flyway.enabled=false",
        "tile.auth.secret=0123456789abcdef0123456789abcdef",
        // 32 字节(0x00…0x1f)的 Base64。测试自己给密钥,不依赖开发机上的 LAYOUT_ENC_KEY ——
        // 否则"忘了导出那个环境变量"会让整个类静默失败。
        "tile.crypto.layout-key=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=",
        "tile.agent.service-token=t12-test-token",
        // 关掉两个定时器:它们会把任务挪走、把发件箱的行投出去。
        // 本类验的是方案管理,不是编排的重试与投递。
        "tile.generate.compensation-enabled=false",
        "tile.outbox.enabled=false"})
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PlanManagementIntegrationTest {

    private static final String PLANS = "/api/v1/plans";
    private static final String GENERATE = "/api/v1/generate";
    private static final String CALLBACK = "/internal/generate/callback";
    private static final String SERVICE_TOKEN_HEADER = "X-Service-Token";
    private static final String SERVICE_TOKEN = "t12-test-token";

    /** 本测试类的两个 C 端用户。固定大号,避免撞上库里真实的用户。 */
    private static final long USER = 8_812_0001L;
    private static final long OTHER_USER = 8_812_0002L;

    /** 产品 / 参数的识别前缀,清理时按它删。 */
    private static final String SKU_PREFIX = "T12-";
    private static final String SKU_FLOOR = SKU_PREFIX + "FLOOR";
    private static final String SKU_WALL = SKU_PREFIX + "WALL";

    /** 两个 SKU 同价,方便把期望金额手算出来钉死(见 {@link #EXPECTED_TOTAL})。 */
    private static final String PRICE = "100.00";
    private static final String AUX_UNIT_PRICE = "20.00";
    private static final String LOSS_RATE = "0.06";

    private static final String DEFAULT_PARTS =
            "[{\"part\":\"floor\",\"sku\":\"" + SKU_FLOOR + "\"},"
                    + "{\"part\":\"wall\",\"sku\":\"" + SKU_WALL + "\"}]";

    /**
     * §8.8 的公式在 {@code 2m × 1.8m × 2.4m} 这个户型上的结果,手算钉死:
     * <pre>
     *   地面 毛面积 3.6   × 1.06 × 100 =  381.60
     *   墙面 毛面积 18.24 × 1.06 × 100 = 1933.44
     *   材料合计                         2315.04
     *   辅料 (3.6 + 18.24) × 20.00   =   436.80
     *   总价                             2751.84
     * </pre>
     *
     * <p>期望值是<b>手算</b>的,不是调一次 {@code QuoteService} 再比 ——
     * 拿被测代码算期望值,断言就变成了"它等于它自己"。
     */
    private static final BigDecimal EXPECTED_MATERIAL_TOTAL = new BigDecimal("2315.04");
    private static final BigDecimal EXPECTED_AUX_TOTAL = new BigDecimal("436.80");
    private static final BigDecimal EXPECTED_TOTAL = new BigDecimal("2751.84");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlanMapper planMapper;

    @Autowired
    private PlanPartMapper planPartMapper;

    @Autowired
    private PlanImageMapper planImageMapper;

    @Autowired
    private PlanQuoteMapper planQuoteMapper;

    @Autowired
    private GenerationTaskMapper taskMapper;

    @Autowired
    private OutboxMapper outboxMapper;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private QuoteParamMapper quoteParamMapper;

    /** 进本类之前 {@code t_quote_param} 里原本有什么 —— 跑完原样还回去。 */
    private Map<String, BigDecimal> originalParams = Map.of();

    @BeforeAll
    void captureOriginalParams() {
        originalParams = paramsSnapshot();
    }

    @AfterAll
    void restoreOriginalParamsAndCleanUp() {
        // 报价参数是一张全局配置表,本类会临时改它(seed / 删掉),所以不能像别处那样
        // 一删了之 —— 那会改掉开发机上的配置。进出各拍一次快照,原样还原。
        quoteParamMapper.delete(Wrappers.<QuoteParam>lambdaQuery()
                .in(QuoteParam::getParamKey, managedParamKeys()));
        originalParams.forEach(this::setParam);
        cleanup();
    }

    @BeforeEach
    void resetState() {
        cleanup();
        seedProduct(SKU_FLOOR, "T12-地面灰石纹");
        seedProduct(SKU_WALL, "T12-墙面米白");
        seedParams();
    }

    // ===============================================================
    // 列表
    // ===============================================================

    @Test
    @DisplayName("列表:只看得到自己的方案;别人的一条都不出现")
    void listShowsOnlyMine() throws Exception {
        long mine = insertPlan(USER, "T12-我的方案");
        long theirs = insertPlan(OTHER_USER, "T12-别人的方案");

        JsonNode data = dataOf(call(get(PLANS), USER));

        List<Long> ids = idsOf(data);
        assertTrue(ids.contains(mine), "自己的方案没出现在列表里: " + ids);
        assertFalse(ids.contains(theirs), "别人的方案出现了 —— 查询条件漏了 user_id: " + ids);

        // 契约 T-3:分页四字段齐备
        assertTrue(data.has("list") && data.has("total") && data.has("page") && data.has("size"),
                data.toString());
        assertEquals(ids.size(), data.get("total").asInt(),
                "total 应与本页条数一致(这两个用户下只有我这一条)");
    }

    @Test
    @DisplayName("列表:按「最后修改时间」倒序;同秒内靠 id 倒序兜底,不重不漏")
    void listIsOrderedByUpdatedAtDescWithIdTiebreak() throws Exception {
        // 连插三条必然同秒(MySQL 的 DATETIME 默认只到秒),所以这一条真正验的是
        // `order by updated_at desc, id desc` 的**第二段** —— 少了它,翻页时
        // 同一条会重复出现、另一条整条漏掉。
        long first = insertPlan(USER, "T12-第一条");
        long second = insertPlan(USER, "T12-第二条");
        long third = insertPlan(USER, "T12-第三条");

        List<Long> ids = idsOf(dataOf(call(get(PLANS), USER)));

        assertEquals(List.of(third, second, first), ids,
                "最近改过的排最前;时间戳相同时按 id 倒序 —— 否则翻页会重复或漏条");
    }

    @Test
    @DisplayName("列表:翻过头不是错误(空列表 + 正确的 total);越界的参数值才是 10001")
    void listPaginationBounds() throws Exception {
        insertPlan(USER, "T12-只有一条");

        JsonNode beyond = dataOf(call(get(PLANS).param("page", "5").param("size", "20"), USER));
        assertEquals(0, beyond.get("list").size(), "翻过头应当返回空列表");
        assertEquals(1, beyond.get("total").asInt(),
                "total 仍是真实总数 —— 前端靠它决定还有没有下一页");

        // 参数本身的越界必须拦下来:放过去会拼出一个未定义的 LIMIT
        assertEquals(10001, codeOf(call(get(PLANS).param("page", "0"), USER)));
        assertEquals(10001, codeOf(call(get(PLANS).param("size", "0"), USER)));
        assertEquals(10001, codeOf(call(get(PLANS).param("size", "101"), USER)));
    }

    @Test
    @DisplayName("七条接口都要登录:不带 Cookie 一律 401(不是 30001)")
    void allEndpointsRequireLogin() throws Exception {
        long id = insertPlan(USER, "T12-需要登录");

        assertEquals(401, mvc.perform(get(PLANS)).andReturn().getResponse().getStatus());
        assertEquals(401, mvc.perform(get(PLANS + "/" + id)).andReturn().getResponse().getStatus());
        assertEquals(401, mvc.perform(patch(PLANS + "/" + id)
                        .contentType(MediaType.APPLICATION_JSON).content(renameBody("x")))
                .andReturn().getResponse().getStatus());
        assertEquals(401, mvc.perform(delete(PLANS + "/" + id)).andReturn().getResponse().getStatus());
        assertEquals(401, mvc.perform(post(PLANS + "/" + id + "/copy")).andReturn()
                .getResponse().getStatus());
        // T-1.13:分享的两条写接口。**必须留在这里** —— 它们靠 SecurityConfig 里
        // `/api/v1/plans/**` 这个前缀受保护,而公开页靠的是"没被加进那份清单"。
        // 有人改动清单时,这四条会一起红。
        assertEquals(401, mvc.perform(post(PLANS + "/" + id + "/share")).andReturn()
                .getResponse().getStatus());
        assertEquals(401, mvc.perform(delete(PLANS + "/" + id + "/share")).andReturn()
                .getResponse().getStatus());
    }

    // ===============================================================
    // 详情(含契约校验表 T-8)
    // ===============================================================

    @Test
    @DisplayName("详情:户型是**可读明文**,响应里不得出现 layout_enc / layoutEnc(T-8)")
    void detailReturnsPlaintextLayout() throws Exception {
        long planId = generateAndSucceed(null, null, DEFAULT_PARTS);

        MvcResult result = call(get(PLANS + "/" + planId), USER);
        String body = result.getResponse().getContentAsString();
        JsonNode data = dataOf(result);

        // T-8 的三条:密文的两种写法都不许出现,而明文必须在
        assertFalse(body.contains("layout_enc"), "响应里出现了密文列名: " + body);
        assertFalse(body.contains("layoutEnc"), "响应里出现了密文列名: " + body);
        assertTrue(body.contains("\"layout\""), "响应里没有明文户型: " + body);

        JsonNode space = data.get("layout").get("spaces").get(0);
        assertEquals("bathroom", space.get("type").asText());
        assertEquals("卫生间", space.get("name").asText());
        // 面积是服务端派生后固化进快照的,不是解密后临时再推一遍
        assertEquals(0, new BigDecimal("3.6").compareTo(space.get("areas").get("floor").decimalValue()));
        assertEquals(0, new BigDecimal("18.24").compareTo(space.get("areas").get("wall").decimalValue()));

        // 封面取的是现成图地址(决策①):is_cover = 1 那行的 url
        assertTrue(data.get("coverImage").asText().startsWith("http://java.test/"),
                "封面应取现成的图地址: " + data);
    }

    /**
     * T-8 的负向对照。
     *
     * <p>没有这一条,上面那两条 {@code assertFalse} 有可能只是因为"整个响应里
     * 什么都没有"而恒真。这里证明同一个检查用在<b>实体</b>上会红 ——
     * 于是"谁把实体当 VO 返回"这件事一定会被上面的断言抓住。
     */
    @Test
    @DisplayName("负向对照:同一个「不得出现密文」的检查,用在实体上必须红")
    void entityWouldFailTheSameCheck() throws Exception {
        long planId = generateAndSucceed(null, null, DEFAULT_PARTS);
        Plan entity = planMapper.selectById(planId);
        assertNotNull(entity.getLayoutEnc(), "库里存的必须是密文列,否则上面那条断言是空跑");

        String entityJson = objectMapper.writeValueAsString(entity);

        assertTrue(entityJson.contains("layoutEnc"),
                "实体序列化后确实带着 layoutEnc —— 这才说明上面那条检查不是恒真的: " + entityJson);
        assertFalse(entityJson.contains("\"layout\""),
                "实体里不该有明文 layout 字段: " + entityJson);
    }

    @Test
    @DisplayName("归属:不存在 / 别人的 / 已删除 —— 四条路径一律 30001,措辞相同")
    void missingForeignOrDeletedPlanIs30001() throws Exception {
        long theirs = insertPlan(OTHER_USER, "T12-别人的方案");
        long deleted = insertPlan(USER, "T12-待删除");
        assertEquals(0, codeOf(call(delete(PLANS + "/" + deleted), USER)));

        for (long bad : new long[]{999_999_999L, theirs, deleted}) {
            assertEquals(30001, codeOf(call(get(PLANS + "/" + bad), USER)),
                    "详情应当 30001: planId=" + bad);
            assertEquals(30001, codeOf(call(patch(PLANS + "/" + bad)
                            .contentType(MediaType.APPLICATION_JSON).content(renameBody("T12-改个名")),
                    USER)), "改名应当 30001: planId=" + bad);
            assertEquals(30001, codeOf(call(delete(PLANS + "/" + bad), USER)),
                    "删除应当 30001: planId=" + bad);
            assertEquals(30001, codeOf(call(post(PLANS + "/" + bad + "/copy"), USER)),
                    "另存为应当 30001: planId=" + bad);
        }

        // 三种原因必须给出**同一句**话 —— 区分开来等于送出一个可以枚举别人方案 id 的探针
        String missing = messageOf(call(get(PLANS + "/999999999"), USER));
        String foreign = messageOf(call(get(PLANS + "/" + theirs), USER));
        String gone = messageOf(call(get(PLANS + "/" + deleted), USER));
        assertEquals(missing, foreign, "「不存在」与「不是你的」的措辞必须一致");
        assertEquals(missing, gone, "「不存在」与「已删除」的措辞必须一致");
    }

    // ===============================================================
    // 报价快照(决策②)
    // ===============================================================

    @Test
    @DisplayName("报价快照(算得出):总价、明细、参数快照三处一致,且与手算值相同")
    void quoteSnapshotIsPersistedWhenComputable() throws Exception {
        long planId = generateAndSucceed(null, 1, DEFAULT_PARTS);

        Plan plan = planMapper.selectById(planId);
        List<PlanQuote> quotes = quotesOf(planId);
        assertEquals(1, quotes.size(), "一个方案至多一行报价汇总(V4 没有唯一键,靠先删后插维持)");

        PlanQuote quote = quotes.get(0);
        // ① 两处总额同值:列表卡片读 plan.total_amount,详情读 quote.total_amount
        assertEquals(0, EXPECTED_TOTAL.compareTo(plan.getTotalAmount()),
                "t_plan.total_amount 与手算值不符: " + plan.getTotalAmount());
        assertEquals(0, EXPECTED_TOTAL.compareTo(quote.getTotalAmount()),
                "t_plan_quote.total_amount 与手算值不符: " + quote.getTotalAmount());
        assertEquals(0, plan.getTotalAmount().compareTo(quote.getTotalAmount()),
                "同一个价的两种存法对不上 —— 迟早有一处会显示成另一个数");
        assertEquals(0, EXPECTED_MATERIAL_TOTAL.compareTo(quote.getMaterialTotal()),
                "材料合计与手算值不符: " + quote.getMaterialTotal());
        assertEquals(0, EXPECTED_AUX_TOTAL.compareTo(quote.getAuxTotal()),
                "辅料费与手算值不符: " + quote.getAuxTotal());

        // ② 分项逐行落库,且**逐行加起来**等于汇总(不这么做,对账时永远差几分钱)
        List<PlanPart> parts = partsOf(planId);
        assertEquals(2, parts.size());
        BigDecimal materialSum = BigDecimal.ZERO;
        for (PlanPart part : parts) {
            assertNotNull(part.getMaterialFee(), "算得出价时费用列不得为空: " + part.getPart());
            assertNotNull(part.getArea(), "有效面积不得为空: " + part.getPart());
            assertEquals(0, new BigDecimal(PRICE).compareTo(part.getPricePerSqm()));
            assertEquals(0, new BigDecimal(LOSS_RATE).compareTo(part.getLossRate()));
            materialSum = materialSum.add(part.getMaterialFee());
        }
        assertEquals(0, quote.getMaterialTotal().compareTo(materialSum),
                "Σ material_fee 与 material_total 对不上 —— 明细和合计是两个数");

        // ③ 参数快照记的是**当时**用的参数,不是"现在查表查到的"
        JsonNode rules = objectMapper.readTree(quote.getRulesSnapshot());
        assertEquals(0, new BigDecimal(AUX_UNIT_PRICE)
                .compareTo(rules.get("auxUnitPrice").decimalValue()),
                "rules_snapshot 与算钱时用的参数不一致: " + quote.getRulesSnapshot());
        assertEquals(0, new BigDecimal(LOSS_RATE).compareTo(rules.get("lossRate").decimalValue()));

        // ④ 从接口看出去也是这个数(详情里既有卡片总额,也有完整报价单)
        //
        // 注意两个键名**故意不同**:卡片读的是 PlanSummary.totalAmount,
        // 报价单读的是 QuoteResult.total(契约里就是这两个名字,别顺手统一,
        // 那会改掉响应形状)。
        JsonNode data = dataOf(call(get(PLANS + "/" + planId), USER));
        assertEquals(0, EXPECTED_TOTAL.compareTo(data.get("totalAmount").decimalValue()),
                "卡片总额字段 PlanSummary.totalAmount");
        assertEquals(0, EXPECTED_TOTAL.compareTo(data.get("quote").get("total").decimalValue()),
                "报价单总额字段 QuoteResult.total");
        assertEquals(2, data.get("quote").get("items").size());
        assertEquals(0, EXPECTED_MATERIAL_TOTAL
                .compareTo(data.get("quote").get("materialTotal").decimalValue()));
        assertFalse(data.get("quoteExpired").asBoolean(), "刚算出来的价不可能是过期的");
    }

    @Test
    @DisplayName("报价快照(算不出·SKU 查不到):方案照存,报价整单不落,总额留空")
    void quoteSnapshotIsSkippedWhenProductsAreMissing() throws Exception {
        long planId = generateAndSucceed(null, null,
                "[{\"part\":\"floor\",\"sku\":\"T12-根本没有这款砖\"}]");

        assertNoQuoteSaved(planId, "SKU 在 t_product 里查不到");
    }

    @Test
    @DisplayName("报价快照(算不出·面积未定):local 部位推不出面积 —— 同样整单不落")
    void quoteSnapshotIsSkippedWhenAreaIsUndecided() throws Exception {
        // local(腰线 / 花砖)按实际铺贴,面积公式对它没有默认值,派生出来是 null。
        // 把那个 null 当 0 会静默报出一个偏低价 —— 这正是这条红线要挡的。
        long planId = generateAndSucceed(null, null,
                "[{\"part\":\"floor\",\"sku\":\"" + SKU_FLOOR + "\"},"
                        + "{\"part\":\"local\",\"sku\":\"" + SKU_WALL + "\"}]");

        assertNoQuoteSaved(planId, "某个部位的面积推不出来");
    }

    @Test
    @DisplayName("报价快照(算不出·参数没配):报价引擎报错被吞掉,方案仍然保存成功")
    void quoteSnapshotIsSkippedWhenParamsAreMissing() throws Exception {
        // 这一条同时是 **rollback-only 陷阱的对照**:若用带 @Transactional 的
        // QuoteService.calculate 再 try/catch,共享事务会被标成 rollback-only,
        // 这里会在提交时抛 UnexpectedRollbackException ——「方案保存成功」直接红。
        // 实现走的是不标事务的 tryCalculate(见 QuoteService 的说明)。
        quoteParamMapper.delete(Wrappers.<QuoteParam>lambdaQuery()
                .in(QuoteParam::getParamKey, List.of("aux_unit_price", "loss_rate")));
        try {
            long planId = generateAndSucceed(null, null, DEFAULT_PARTS);

            assertNoQuoteSaved(planId, "报价参数没配");
        } finally {
            // 还原,别把"参数没配"这个状态留给后面的用例
            quoteParamMapper.delete(Wrappers.<QuoteParam>lambdaQuery()
                    .in(QuoteParam::getParamKey, managedParamKeys()));
            originalParams.forEach(this::setParam);
        }
    }

    /** 「算不出」的三条路径共用的验收:方案在、报价全空、且**空 ≠ 0**。 */
    private void assertNoQuoteSaved(long planId, String cause) {
        Plan plan = planMapper.selectById(planId);
        assertNotNull(plan, cause + " —— 方案本身必须照常保存下来");
        assertNull(plan.getTotalAmount(),
                cause + " —— 总额应当是 null(「没算出来」),不是 0(「算出来是 0 元」): "
                        + plan.getTotalAmount());

        assertTrue(quotesOf(planId).isEmpty(), cause + " —— 算不出就不该有报价汇总行");
        for (PlanPart part : partsOf(planId)) {
            assertNull(part.getMaterialFee(), cause + " —— 费用列必须整片留空,不写 0");
            assertNull(part.getPricePerSqm());
            assertNull(part.getLossRate());
        }
    }

    // ===============================================================
    // source(决策④ —— 推翻 T-1.11 的旧判断)
    // ===============================================================

    @Test
    @DisplayName("source:不带就是 user、带 ai 就存 ai,详情原样返回,另存为逐行拷过去")
    void sourceIsPersistedAndReturned() throws Exception {
        long planId = generateAndSucceed(null, null,
                "[{\"part\":\"floor\",\"sku\":\"" + SKU_FLOOR + "\",\"source\":\"ai\"},"
                        + "{\"part\":\"wall\",\"sku\":\"" + SKU_WALL + "\"}]");

        assertEquals(Map.of("floor", "ai", "wall", "user"), sourcesOf(planId),
                "省略 source 应归一成 user,显式给 ai 应原样落库");

        JsonNode data = dataOf(call(get(PLANS + "/" + planId), USER));
        assertEquals("ai", data.get("parts").get(0).get("source").asText(),
                "详情接口必须说出「这块砖是 AI 补的」,否则用户分不清哪块是自己选的");

        long copyId = copyOf(planId);
        assertEquals(sourcesOf(planId), sourcesOf(copyId), "副本的 source 应逐行相同");
    }

    // ===============================================================
    // 重命名
    // ===============================================================

    @Test
    @DisplayName("重命名:正常改名生效;空名字与超长一律 10001,且**不截断**")
    void renameUpdatesNameAndRejectsBadInput() throws Exception {
        long planId = insertPlan(USER, "T12-原名");

        assertEquals(0, codeOf(call(patch(PLANS + "/" + planId)
                .contentType(MediaType.APPLICATION_JSON).content(renameBody("T12-新名字")), USER)));
        assertEquals("T12-新名字", planMapper.selectById(planId).getName());

        // 全空白:契约 PATCH 的 name 是必填字符串
        assertEquals(10001, codeOf(call(patch(PLANS + "/" + planId)
                .contentType(MediaType.APPLICATION_JSON).content(renameBody("   ")), USER)));

        // 超长:**报错**,不是悄悄截短 —— 用户以为改成功了、库里却是另一个名字,比报错更糟
        String tooLong = "长".repeat(PlanNames.NAME_MAX + 1);
        assertEquals(10001, codeOf(call(patch(PLANS + "/" + planId)
                .contentType(MediaType.APPLICATION_JSON).content(renameBody(tooLong)), USER)));
        assertEquals("T12-新名字", planMapper.selectById(planId).getName(),
                "被拒的改名不该动到库里的名字");

        // 恰好 200 字:允许(边界值别写成 >=,否则上限就被砍掉一个字)
        String exactly = "长".repeat(PlanNames.NAME_MAX);
        assertEquals(0, codeOf(call(patch(PLANS + "/" + planId)
                .contentType(MediaType.APPLICATION_JSON).content(renameBody(exactly)), USER)));
        assertEquals(exactly, planMapper.selectById(planId).getName(),
                "200 字应当原样存下,一个字都不能少");
    }

    /**
     * 计划里点名的风险 2:改名成<b>同一个名字</b>时,没有任何一列的值发生变化,
     * V4 的 {@code ON UPDATE CURRENT_TIMESTAMP} 不会触发 —— 而列表按
     * {@code updated_at desc} 排序,时间戳不前进,用户刚改过名的方案会沉在最底下。
     */
    @Test
    @DisplayName("重命名:改成同一个名字也要抬 updated_at —— 否则刚改过的方案沉在列表底部")
    void renameAdvancesUpdatedAtEvenWhenNameIsUnchanged() throws Exception {
        long planId = insertPlan(USER, "T12-名字不变");
        String sameName = planMapper.selectById(planId).getName();

        // 往回拨 60 秒:不拨的话"前进"这件事根本看不出来(本来就差不多是现在)。
        //
        // 用 SQL 自己的 NOW() 减,而不是把 Java 的 LocalDateTime 传下去 ——
        // t_plan.updated_at 是 DATETIME(**没有小数秒**),Java 侧带纳秒的值写进去会被
        // 截断,再读回来就跟传下去的那个对象不相等了。差值在库内算完,少一层往返换算。
        // 拨完**读回实际存下的值**当基准 —— 基准必须来自库,否则后面比的还是 Java 侧的理想值。
        jdbcTemplate.update(
                "UPDATE t_plan SET updated_at = DATE_SUB(NOW(), INTERVAL 60 SECOND) WHERE id = ?",
                planId);
        LocalDateTime backdated = planMapper.selectById(planId).getUpdatedAt();
        assertTrue(backdated.isBefore(LocalDateTime.now().minusSeconds(30)),
                "回拨没生效(仍停在 " + backdated + "),后面的断言就没意义了");

        assertEquals(0, codeOf(call(patch(PLANS + "/" + planId)
                .contentType(MediaType.APPLICATION_JSON).content(renameBody(sameName)), USER)));

        LocalDateTime after = planMapper.selectById(planId).getUpdatedAt();
        assertTrue(after.isAfter(backdated),
                "updated_at 没前进(值没变时 ON UPDATE 不触发)—— 方案会排在列表最后: "
                        + backdated + " → " + after);
    }

    // ===============================================================
    // 删除
    // ===============================================================

    @Test
    @DisplayName("删除:只标记不删行;子表与 share_token 都留着;再删一次仍是 30001")
    void deleteIsLogicalAndKeepsChildren() throws Exception {
        long planId = generateAndSucceed(null, null, DEFAULT_PARTS);
        String token = "t12-share-" + System.nanoTime();
        jdbcTemplate.update("UPDATE t_plan SET share_token = ? WHERE id = ?", token, planId);
        int parts = partsOf(planId).size();
        int images = imagesOf(planId).size();

        assertEquals(0, codeOf(call(delete(PLANS + "/" + planId), USER)));

        Plan after = planMapper.selectById(planId);
        assertNotNull(after, "删除是标记,不是删行 —— 行必须还在");
        assertTrue(Boolean.TRUE.equals(after.getIsDeleted()), "is_deleted 没置起来");
        assertEquals(token, after.getShareToken(),
                "share_token 不该被删 —— 分享出去的链接要能解释成「这个方案已被删除」");
        assertEquals(parts, partsOf(planId).size(), "子表不该被顺手删掉,误删还有恢复的余地");
        assertEquals(images, imagesOf(planId).size());

        // 列表里消失、详情 30001、再删一次也是 30001(用户看到的事实就是这个方案没了)
        assertFalse(idsOf(dataOf(call(get(PLANS), USER))).contains(planId));
        assertEquals(30001, codeOf(call(get(PLANS + "/" + planId), USER)));
        assertEquals(30001, codeOf(call(delete(PLANS + "/" + planId), USER)));
    }

    // ===============================================================
    // 另存为(决策③)
    // ===============================================================

    @Test
    @DisplayName("另存为:新方案 id、名字带「(副本)」、share_token 置空、子表逐行相同、原方案一字不动")
    void copyProducesIndependentPlan() throws Exception {
        long sourceId = generateAndSucceed(null, null, DEFAULT_PARTS);
        // 先给原方案塞一个分享令牌 —— 不然"副本的 share_token 是 null"这句是空断言
        String token = "t12-share-" + System.nanoTime();
        jdbcTemplate.update("UPDATE t_plan SET share_token = ? WHERE id = ?", token, sourceId);

        String sourceFingerprint = fingerprint(planMapper.selectById(sourceId));
        String sourceName = planMapper.selectById(sourceId).getName();
        List<String> sourceParts = descriptionsOfParts(sourceId);
        List<String> sourceImages = descriptionsOfImages(sourceId);
        String sourceQuote = descriptionOfQuote(sourceId);

        long copyId = copyOf(sourceId);

        assertNotEquals(sourceId, copyId, "另存为必须产生一个新方案");
        Plan copy = planMapper.selectById(copyId);
        assertEquals(sourceName + PlanNames.COPY_SUFFIX, copy.getName());
        assertNull(copy.getShareToken(),
                "副本不能继承分享令牌:唯一键会撞,而且一个链接指向两个方案本身就是错的");
        assertEquals(USER, copy.getUserId());
        assertFalse(Boolean.TRUE.equals(copy.getIsDeleted()));

        // 子表深拷:否则副本会「有总额没明细」
        assertEquals(sourceParts, descriptionsOfParts(copyId), "部位行应逐行相同(含费用列与 source)");
        assertEquals(sourceImages, descriptionsOfImages(copyId), "效果图应逐行相同(含 is_cover)");
        assertEquals(sourceQuote, descriptionOfQuote(copyId), "报价快照应原样带过来");

        // 原方案逐列未变 —— 用户在列表里看到的时间与排序位置不该因为"我另存了一份"而变化
        assertEquals(sourceFingerprint, fingerprint(planMapper.selectById(sourceId)),
                "原方案被改动了 —— 另存为本该是只读操作");

        // 详情:副本能读,且没有 shareToken 这个字段(不是输出 null)
        JsonNode detail = dataOf(call(get(PLANS + "/" + copyId), USER));
        assertFalse(detail.has("shareToken"), "没有令牌就不该出现这个字段: " + detail);
        assertTrue(detail.has("layout"), "副本的户型密文是直接拷字节的,应当照样解得开");
    }

    @Test
    @DisplayName("另存为:200 字原名 → 副本名不超上限,且后缀完整保留")
    void copyNameIsCappedAt200() throws Exception {
        long planId = insertPlan(USER, "长".repeat(PlanNames.NAME_MAX));

        long copyId = copyOf(planId);
        String copyName = planMapper.selectById(copyId).getName();

        assertEquals(PlanNames.NAME_MAX, copyName.codePointCount(0, copyName.length()),
                "200 字原名应当正好用满上限: " + copyName.length());
        assertTrue(copyName.endsWith(PlanNames.COPY_SUFFIX),
                "先拼后缀再截会把后缀截掉,那就白拼了: " + copyName);
    }

    // ===============================================================
    // 工具
    // ===============================================================

    /** 直接签一个 access 令牌当 Cookie —— 登录要 Redis,而本测试验的不是登录。 */
    private Cookie cookieFor(long userId) {
        return new Cookie("access_token", jwtService.issueAccess(userId, SubjectType.USER));
    }

    private MvcResult call(MockHttpServletRequestBuilder request, long userId) throws Exception {
        return mvc.perform(request.cookie(cookieFor(userId))).andReturn();
    }

    /** 受理一次生成 + 发一条 success 回调,返回落库的方案 id。 */
    private long generateAndSucceed(Long planId, Integer angles, String partsJson) throws Exception {
        String taskId = dataOf(call(post(GENERATE).contentType(MediaType.APPLICATION_JSON)
                .content(generateBody(planId, angles, partsJson)), USER)).get("taskId").asText();

        MvcResult callback = mvc.perform(post(CALLBACK).contentType(MediaType.APPLICATION_JSON)
                        .content(successBody(taskId)).header(SERVICE_TOKEN_HEADER, SERVICE_TOKEN))
                .andReturn();
        assertEquals(0, codeOf(callback),
                "出图回调应成功: " + callback.getResponse().getContentAsString());

        GenerationTask task = taskMapper.selectOne(Wrappers.<GenerationTask>lambdaQuery()
                .eq(GenerationTask::getTaskId, taskId));
        Long saved = task.getPlanId();
        assertNotNull(saved, "回调成功却没落方案 —— 后面的断言全都无从谈起");
        return saved;
    }

    /**
     * 受理请求体。户型是 {@code 2m × 1.8m × 2.4m} 的卫生间
     * (派生后 地面 3.6 / 墙面 18.24,与 {@link #EXPECTED_TOTAL} 的手算一致)。
     */
    private String generateBody(Long planId, Integer angles, String partsJson) {
        StringBuilder sb = new StringBuilder("{\"space\":\"bathroom\",\"style\":\"modern\"");
        if (angles != null) {
            sb.append(",\"angles\":").append(angles);
        }
        if (planId != null) {
            sb.append(",\"planId\":").append(planId);
        }
        sb.append(",\"layout\":{\"spaces\":[{\"type\":\"bathroom\",\"name\":\"卫生间\",")
                .append("\"length\":2,\"width\":1.8,\"height\":2.4,\"wallCount\":4}]}")
                .append(",\"parts\":").append(partsJson)
                .append('}');
        return sb.toString();
    }

    /** success 回调体。地址指向 Java —— Python 只在内网,前端够不到它的地址(§3.3)。 */
    private String successBody(String taskId) {
        return """
                {"taskId":"%s","status":"success","progress":100,"imageUrls":[
                  {"url":"http://java.test/static/placeholder.svg?task=%s","view":"default",
                   "isCover":true}]}""".formatted(taskId, taskId);
    }

    private String renameBody(String name) throws Exception {
        return objectMapper.writeValueAsString(objectMapper.createObjectNode().put("name", name));
    }

    // ------------------------------------------------------------------

    private long copyOf(long planId) throws Exception {
        return dataOf(call(post(PLANS + "/" + planId + "/copy"), USER)).get("planId").asLong();
    }

    /** 直接造一行方案 —— 只关心归属与排序的用例不必走一遍生成链路。 */
    private long insertPlan(long userId, String name) {
        Plan plan = new Plan();
        plan.setUserId(userId);
        plan.setName(name);
        plan.setQuoteExpired(false);
        plan.setIsDeleted(false);
        planMapper.insert(plan);
        return plan.getId();
    }

    /** 方案的<b>逐列</b>指纹 —— 用来证明另存为没动过原方案。 */
    private static String fingerprint(Plan plan) {
        return String.join("|",
                String.valueOf(plan.getName()),
                String.valueOf(plan.getSpace()),
                String.valueOf(plan.getStyle()),
                Arrays.toString(plan.getLayoutEnc()),
                String.valueOf(plan.getTotalAmount()),
                String.valueOf(plan.getQuoteExpired()),
                String.valueOf(plan.getShareToken()),
                String.valueOf(plan.getIsDeleted()),
                String.valueOf(plan.getCreatedAt()),
                String.valueOf(plan.getUpdatedAt()));
    }

    private List<PlanPart> partsOf(long planId) {
        return planPartMapper.selectList(Wrappers.<PlanPart>lambdaQuery()
                .eq(PlanPart::getPlanId, planId).orderByAsc(PlanPart::getId));
    }

    private List<PlanImage> imagesOf(long planId) {
        return planImageMapper.selectList(Wrappers.<PlanImage>lambdaQuery()
                .eq(PlanImage::getPlanId, planId).orderByAsc(PlanImage::getId));
    }

    private List<PlanQuote> quotesOf(long planId) {
        return planQuoteMapper.selectList(Wrappers.<PlanQuote>lambdaQuery()
                .eq(PlanQuote::getPlanId, planId));
    }

    private Map<String, String> sourcesOf(long planId) {
        Map<String, String> byPart = new LinkedHashMap<>();
        for (PlanPart part : partsOf(planId)) {
            byPart.put(part.getPart(), part.getSource());
        }
        return byPart;
    }

    private List<String> descriptionsOfParts(long planId) {
        List<String> rows = new ArrayList<>();
        for (PlanPart part : partsOf(planId)) {
            rows.add(String.join("|", part.getPart(), String.valueOf(part.getSkuCode()),
                    String.valueOf(part.getProductName()), String.valueOf(part.getPricePerSqm()),
                    String.valueOf(part.getGrossArea()), String.valueOf(part.getArea()),
                    String.valueOf(part.getLossRate()), String.valueOf(part.getMaterialFee()),
                    String.valueOf(part.getSource())));
        }
        return rows;
    }

    private List<String> descriptionsOfImages(long planId) {
        List<String> rows = new ArrayList<>();
        for (PlanImage image : imagesOf(planId)) {
            rows.add(String.join("|", image.getUrl(), image.getView(),
                    String.valueOf(image.getIsCover())));
        }
        return rows;
    }

    /** 报价汇总的逐列指纹;没有报价时返回一个固定的「无」标记。 */
    private String descriptionOfQuote(long planId) {
        List<PlanQuote> rows = quotesOf(planId);
        if (rows.isEmpty()) {
            return "(无报价)";
        }
        PlanQuote quote = rows.get(0);
        return String.join("|", String.valueOf(quote.getMaterialTotal()),
                String.valueOf(quote.getAuxTotal()), String.valueOf(quote.getAuxUnitPrice()),
                String.valueOf(quote.getAuxArea()), String.valueOf(quote.getLossRate()),
                String.valueOf(quote.getTotalAmount()), String.valueOf(quote.getRulesSnapshot()));
    }

    // ------------------------------------------------------------------

    /** 产品的最小非空集(与 {@code QuoteEngineRegressionTest} 同一个口径)。 */
    private void seedProduct(String sku, String name) {
        Product product = new Product();
        product.setSkuCode(sku);
        product.setName(name);
        product.setPricePerSqm(new BigDecimal(PRICE));
        // 报价引擎不要求上架(只有 C 端浏览要求),此处刻意用下架状态:
        // 顺带钉住"产品下架不影响已经生成过的方案的报价"这条口径
        product.setStatus(Product.STATUS_OFF_SALE);
        productMapper.insert(product);
    }

    /**
     * 报价参数。{@code aux_unit_price}(元/㎡)与 {@code loss_rate}(小数)是
     * {@code QuoteService.loadParams} 唯一必需的两个键,缺任一即报价失败。
     */
    private void seedParams() {
        setParam("aux_unit_price", AUX_UNIT_PRICE);
        setParam("loss_rate", LOSS_RATE);
        setParam("loss_rate_min", "0.05");
        setParam("loss_rate_max", "0.08");
    }

    /** 本类会写入或删除的全部参数键 —— 还原时按它清,免得漏掉一个残留在库里。 */
    private static List<String> managedParamKeys() {
        List<String> keys = new ArrayList<>(List.of(
                "aux_unit_price", "loss_rate", "loss_rate_min", "loss_rate_max"));
        return keys;
    }

    private void setParam(String key, String value) {
        setParam(key, new BigDecimal(value));
    }

    private void setParam(String key, BigDecimal value) {
        quoteParamMapper.delete(Wrappers.<QuoteParam>lambdaQuery().eq(QuoteParam::getParamKey, key));
        QuoteParam param = new QuoteParam();
        param.setParamKey(key);
        param.setParamValue(value);
        quoteParamMapper.insert(param);
    }

    private Map<String, BigDecimal> paramsSnapshot() {
        Map<String, BigDecimal> snapshot = new LinkedHashMap<>();
        for (QuoteParam param : quoteParamMapper.selectList(Wrappers.<QuoteParam>lambdaQuery())) {
            snapshot.put(param.getParamKey(), param.getParamValue());
        }
        return snapshot;
    }

    // ------------------------------------------------------------------

    private JsonNode dataOf(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString();
        JsonNode root = objectMapper.readTree(body);
        assertEquals(0, root.path("code").asInt(), "应当成功: " + body);
        return root.get("data");
    }

    private static int codeOf(MvcResult result) throws Exception {
        return new ObjectMapper().readTree(result.getResponse().getContentAsString())
                .path("code").asInt();
    }

    private static String messageOf(MvcResult result) throws Exception {
        return new ObjectMapper().readTree(result.getResponse().getContentAsString())
                .path("message").asText();
    }

    private static List<Long> idsOf(JsonNode pageData) {
        List<Long> ids = new ArrayList<>();
        for (JsonNode row : pageData.get("list")) {
            ids.add(row.get("id").asLong());
        }
        return ids;
    }

    // ------------------------------------------------------------------

    /**
     * 清理本测试造的数据。
     *
     * <p>只按<b>自己的用户号段</b>与<b>自己的 SKU 前缀</b>删 —— 绝不清空整张表,
     * 也不对全表条数下结论(库里还有别的测试类、别的用例的数据)。
     */
    private void cleanup() {
        for (long userId : new long[]{USER, OTHER_USER}) {
            for (GenerationTask task : taskMapper.selectList(Wrappers.<GenerationTask>lambdaQuery()
                    .eq(GenerationTask::getUserId, userId))) {
                outboxMapper.delete(Wrappers.<OutboxMessage>lambdaQuery()
                        .eq(OutboxMessage::getAggregateId, task.getTaskId()));
                taskMapper.deleteById(task.getId());
            }
            for (Plan plan : planMapper.selectList(Wrappers.<Plan>lambdaQuery()
                    .eq(Plan::getUserId, userId))) {
                long planId = plan.getId();
                planPartMapper.delete(Wrappers.<PlanPart>lambdaQuery()
                        .eq(PlanPart::getPlanId, planId));
                planImageMapper.delete(Wrappers.<PlanImage>lambdaQuery()
                        .eq(PlanImage::getPlanId, planId));
                planQuoteMapper.delete(Wrappers.<PlanQuote>lambdaQuery()
                        .eq(PlanQuote::getPlanId, planId));
                planMapper.deleteById(planId);
            }
        }
        productMapper.delete(Wrappers.<Product>lambdaQuery()
                .likeRight(Product::getSkuCode, SKU_PREFIX));
    }
}
