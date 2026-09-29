package com.tileai.combo;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tileai.admin.entity.AdminUser;
import com.tileai.admin.mapper.AdminUserMapper;
import com.tileai.combo.entity.RecommendCombo;
import com.tileai.combo.entity.RecommendComboItem;
import com.tileai.combo.mapper.RecommendComboItemMapper;
import com.tileai.combo.mapper.RecommendComboMapper;
import com.tileai.common.model.Part;
import com.tileai.common.security.JwtService;
import com.tileai.common.security.SubjectType;
import com.tileai.product.entity.Product;
import com.tileai.product.entity.ProductImage;
import com.tileai.product.mapper.ProductImageMapper;
import com.tileai.product.mapper.ProductMapper;

import jakarta.servlet.http.Cookie;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * T-0.8 验收:后台推荐搭配维护的真实行为(架构 §8.2;PRD §5.2.2 路径 B)。
 *
 * <p><b>需要 MySQL</b>。用 {@code -Ddb.verify=true} 显式启用。
 *
 * <p>覆盖 T-0.8 的两条判据:
 * <ol>
 *   <li>搭配详情返回的 items 含 sku + part,且 part 恒为三值(契约 T-9);</li>
 *   <li><b>删除被引用的 SKU 时的行为</b> —— 本轮定为返回 <b>30002</b> 拒绝删除
 *       (T-0.6 时因搭配模块未建而推迟,现补上)。</li>
 * </ol>
 */
@EnabledIfSystemProperty(named = "db.verify", matches = "true",
        disabledReason = "需要 MySQL;用 -Ddb.verify=true 显式启用(T-0.8 验证)")
@SpringBootTest(properties = {"spring.flyway.enabled=false",
        "tile.auth.secret=0123456789abcdef0123456789abcdef"})
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AdminComboIntegrationTest {

    private static final String COMBOS = "/api/v1/admin/combos";
    private static final String PRODUCTS = "/api/v1/admin/products";
    private static final String SKU_A = "T08-SKU-A";
    private static final String SKU_B = "T08-SKU-B";
    private static final String SKU_ORPHAN = "T08-SKU-ORPHAN";

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
    private RecommendComboMapper comboMapper;

    @Autowired
    private RecommendComboItemMapper comboItemMapper;

    private long adminId;
    private String token;
    private final List<Long> createdComboIds = new ArrayList<>();

    @BeforeAll
    void seed() {
        AdminUser admin = new AdminUser();
        admin.setUsername("t08_admin");
        admin.setPasswordHash("$2a$10$placeholder-not-used-because-no-login-here");
        admin.setRole("operator");
        admin.setStatus(AdminUser.STATUS_ENABLED);
        adminUserMapper.insert(admin);
        adminId = admin.getId();
        token = jwtService.issueAccess(adminId, SubjectType.ADMIN);

        insertProduct(SKU_A, "搭配用砖A");
        insertProduct(SKU_B, "搭配用砖B");
        insertProduct(SKU_ORPHAN, "无人引用的砖");
    }

    @AfterAll
    void cleanup() {
        for (Long comboId : createdComboIds) {
            comboItemMapper.delete(Wrappers.<RecommendComboItem>lambdaQuery()
                    .eq(RecommendComboItem::getComboId, comboId));
            comboMapper.deleteById(comboId);
        }
        List<Long> productIds = productMapper.selectList(Wrappers.<Product>lambdaQuery()
                        .likeRight(Product::getSkuCode, "T08-"))
                .stream().map(Product::getId).toList();
        if (!productIds.isEmpty()) {
            productImageMapper.delete(Wrappers.<ProductImage>lambdaQuery()
                    .in(ProductImage::getProductId, productIds));
            productMapper.deleteByIds(productIds);
        }
        adminUserMapper.deleteById(adminId);
    }

    // ---------------------------------------------------------------
    // 判据一:items 含 sku + part,part 恒为三值(T-9)
    // ---------------------------------------------------------------

    @Test
    @DisplayName("创建搭配后详情返回 items,含 sku 与 part,并带出展示名")
    void createThenReadBackWithItems() throws Exception {
        long id = createCombo("卫生间双砖搭配", "bathroom", "modern",
                item(SKU_A, "floor"), item(SKU_B, "wall"));

        JsonNode data = dataOf(asAdmin(get(COMBOS + "/" + id)).andReturn());
        assertEquals("卫生间双砖搭配", data.get("name").asText());
        assertEquals("bathroom", data.get("space").asText());

        JsonNode items = data.get("items");
        assertEquals(2, items.size());

        assertEquals(SKU_A, items.get(0).get("sku").asText());
        assertEquals("floor", items.get(0).get("part").asText());
        assertEquals("搭配用砖A", items.get(0).get("name").asText(),
                "展示名应从产品表带出 —— t_recommend_combo_item 里没有 name 列");

        assertEquals(SKU_B, items.get(1).get("sku").asText());
        assertEquals("wall", items.get(1).get("part").asText());
    }

    @Test
    @DisplayName("T-9:三种合法部位都能存能读")
    void allThreePartsAreAccepted() throws Exception {
        long id = createCombo("三部位搭配", "living_room", "nordic",
                item(SKU_A, Part.FLOOR.code()),
                item(SKU_B, Part.WALL.code()),
                item(SKU_A, Part.LOCAL.code()));

        JsonNode items = dataOf(asAdmin(get(COMBOS + "/" + id)).andReturn()).get("items");
        List<String> parts = new ArrayList<>();
        items.forEach(i -> parts.add(i.get("part").asText()));

        assertTrue(parts.containsAll(List.of("floor", "wall", "local")), parts.toString());
    }

    @Test
    @DisplayName("T-9:非法部位 → 10001,明细指向 part 字段")
    void invalidPartIsRejected() throws Exception {
        MvcResult result = asAdmin(post(COMBOS)
                .contentType(MediaType.APPLICATION_JSON)
                .content(comboJson("坏部位", "bathroom", "modern",
                        item("T08-SKU-A", "ceiling")))).andReturn();

        assertEquals(10001, codeOf(result));
        assertTrue(result.getResponse().getContentAsString().contains("\"field\":\"items[0].part\""),
                "错误明细应指向具体那一项的部位字段: "
                        + result.getResponse().getContentAsString());
    }

    // ---------------------------------------------------------------
    // 校验
    // ---------------------------------------------------------------

    @Test
    @DisplayName("items 为空 → 10001(契约 minItems: 1)")
    void emptyItemsIsRejected() throws Exception {
        String json = objectMapper.writeValueAsString(objectMapper.createObjectNode()
                .put("name", "空搭配")
                .put("space", "bathroom")
                .put("style", "modern")
                .set("items", objectMapper.createArrayNode()));

        assertEquals(10001, codeOf(asAdmin(post(COMBOS)
                .contentType(MediaType.APPLICATION_JSON).content(json)).andReturn()));
    }

    @Test
    @DisplayName("引用不存在的 SKU → 10001(否则详情里会出现查不到的砖)")
    void unknownSkuIsRejected() throws Exception {
        MvcResult result = asAdmin(post(COMBOS)
                .contentType(MediaType.APPLICATION_JSON)
                .content(comboJson("引用不存在", "bathroom", "modern",
                        item("NO-SUCH-SKU", "floor")))).andReturn();

        assertEquals(10001, codeOf(result));
        assertTrue(result.getResponse().getContentAsString().contains("NO-SUCH-SKU"),
                result.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("缺必填字段 → 10001")
    void missingRequiredIsRejected() throws Exception {
        MvcResult result = asAdmin(post(COMBOS)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"space\":\"bathroom\",\"style\":\"modern\"}")).andReturn();

        assertEquals(10001, codeOf(result));
        assertTrue(result.getResponse().getContentAsString().contains("\"field\":\"name\""),
                result.getResponse().getContentAsString());
    }

    // ---------------------------------------------------------------
    // 编辑 / 列表 / 删除
    // ---------------------------------------------------------------

    @Test
    @DisplayName("编辑整体替换 items,旧项不残留")
    void updateReplacesItems() throws Exception {
        long id = createCombo("待改搭配", "bathroom", "modern",
                item(SKU_A, "floor"), item(SKU_B, "wall"));

        assertEquals(0, codeOf(asAdmin(put(COMBOS + "/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(comboJson("改后搭配", "kitchen", "cream",
                        item(SKU_B, "floor")))).andReturn()));

        JsonNode data = dataOf(asAdmin(get(COMBOS + "/" + id)).andReturn());
        assertEquals("改后搭配", data.get("name").asText());
        assertEquals("kitchen", data.get("space").asText());
        assertEquals(1, data.get("items").size(), "旧项必须被替换掉");
        assertEquals(SKU_B, data.get("items").get(0).get("sku").asText());

        assertEquals(0, comboItemMapper.selectCount(Wrappers.<RecommendComboItem>lambdaQuery()
                .eq(RecommendComboItem::getComboId, id)
                .eq(RecommendComboItem::getProductId, productIdOf(SKU_A))),
                "换掉的旧项不该留在表里");
    }

    @Test
    @DisplayName("列表按 space / style 过滤,且含分页字段")
    void listFiltersAndPaginates() throws Exception {
        createCombo("过滤-卫生间", "bathroom", "modern", item(SKU_A, "floor"));
        createCombo("过滤-厨房", "kitchen", "cream", item(SKU_A, "floor"));

        JsonNode bySpace = dataOf(asAdmin(get(COMBOS).param("space", "kitchen")).andReturn());
        assertTrue(bySpace.get("list").size() >= 1);
        bySpace.get("list").forEach(combo ->
                assertEquals("kitchen", combo.get("space").asText()));

        assertTrue(bySpace.has("total") && bySpace.has("page") && bySpace.has("size"),
                "分页字段齐备(T-3): " + bySpace);
    }

    @Test
    @DisplayName("删除搭配:详情转 30001,关联项一并清除")
    void deleteCascadesItems() throws Exception {
        long id = createCombo("待删搭配", "bathroom", "modern",
                item(SKU_A, "floor"), item(SKU_B, "wall"));

        assertEquals(0, codeOf(asAdmin(delete(COMBOS + "/" + id)).andReturn()));
        createdComboIds.remove(id);

        assertEquals(30001, codeOf(asAdmin(get(COMBOS + "/" + id)).andReturn()));
        assertEquals(0, comboItemMapper.selectCount(Wrappers.<RecommendComboItem>lambdaQuery()
                .eq(RecommendComboItem::getComboId, id)), "关联项必须随搭配一起删除");
    }

    @Test
    @DisplayName("不存在的搭配 → 30001(详情/编辑/删除一致)")
    void missingComboIsNotFound() throws Exception {
        assertEquals(30001, codeOf(asAdmin(get(COMBOS + "/99999999")).andReturn()));
        assertEquals(30001, codeOf(asAdmin(delete(COMBOS + "/99999999")).andReturn()));
        assertEquals(30001, codeOf(asAdmin(put(COMBOS + "/99999999")
                .contentType(MediaType.APPLICATION_JSON)
                .content(comboJson("x", "bathroom", "modern", item(SKU_A, "floor")))).andReturn()));
    }

    // ---------------------------------------------------------------
    // 判据二:被搭配引用的 SKU 不可删除
    // ---------------------------------------------------------------

    @Test
    @DisplayName("被搭配引用的 SKU 不能删除 → 30002;从搭配移除后可以删")
    void referencedSkuCannotBeDeleted() throws Exception {
        // 用本用例专属的产品,不用共享的 SKU_A —— JUnit 不保证方法执行顺序,
        // 若这里把 SKU_A 删掉,先跑别的用例就会因前置数据缺失而失败。
        String sku = "T08-SKU-REF";
        insertProduct(sku, "被引用后释放的砖");
        long productId = productIdOf(sku);

        long comboId = createCombo("引用检查", "bathroom", "modern", item(sku, "floor"));

        MvcResult blocked = asAdmin(delete(PRODUCTS + "/" + productId)).andReturn();
        assertEquals(200, blocked.getResponse().getStatus(), "业务失败仍是 HTTP 200");
        assertEquals(30002, codeOf(blocked),
                "被搭配引用的 SKU 硬删会留下悬挂引用,必须挡住");
        assertTrue(blocked.getResponse().getContentAsString().contains("搭配"),
                blocked.getResponse().getContentAsString());
        assertNotNull(productMapper.selectById(productId), "被拒绝的删除不得真的删掉数据");

        // 把该 SKU 从搭配里移除后,应可删除
        assertEquals(0, codeOf(asAdmin(put(COMBOS + "/" + comboId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(comboJson("引用检查", "bathroom", "modern",
                        item(SKU_A, "floor")))).andReturn()));

        assertEquals(0, codeOf(asAdmin(delete(PRODUCTS + "/" + productId)).andReturn()),
                "解除引用后应能删除");
        assertNull(productMapper.selectById(productId));
    }

    @Test
    @DisplayName("未被引用的 SKU 照常可删(引用检查不能误伤)")
    void unreferencedSkuIsStillDeletable() throws Exception {
        String sku = "T08-SKU-FREE";
        insertProduct(sku, "无人引用的砖");
        long productId = productIdOf(sku);

        assertEquals(0, codeOf(asAdmin(delete(PRODUCTS + "/" + productId)).andReturn()));
        assertNull(productMapper.selectById(productId));
    }

    // ---------------------------------------------------------------
    // 工具
    // ---------------------------------------------------------------

    private long createCombo(String name, String space, String style, ObjectNode... items)
            throws Exception {
        MvcResult result = asAdmin(post(COMBOS)
                .contentType(MediaType.APPLICATION_JSON)
                .content(comboJson(name, space, style, items))).andReturn();
        assertEquals(0, codeOf(result), "创建应成功: " + result.getResponse().getContentAsString());
        long id = dataOf(result).get("id").asLong();
        createdComboIds.add(id);
        return id;
    }

    private ObjectNode item(String sku, String part) {
        return objectMapper.createObjectNode().put("sku", sku).put("part", part);
    }

    private String comboJson(String name, String space, String style, ObjectNode... items)
            throws Exception {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("name", name);
        node.put("space", space);
        node.put("style", style);
        ArrayNode array = node.putArray("items");
        for (ObjectNode item : items) {
            array.add(item);
        }
        return objectMapper.writeValueAsString(node);
    }

    private void insertProduct(String skuCode, String name) {
        Product product = new Product();
        product.setSkuCode(skuCode);
        product.setName(name);
        product.setPricePerSqm(new BigDecimal("100.00"));
        product.setStatus(Product.STATUS_OFF_SALE);
        productMapper.insert(product);
    }

    private long productIdOf(String skuCode) {
        Product product = productMapper.selectOne(Wrappers.<Product>lambdaQuery()
                .eq(Product::getSkuCode, skuCode));
        assertNotNull(product, "测试前置数据缺失: " + skuCode);
        return product.getId();
    }

    private org.springframework.test.web.servlet.ResultActions asAdmin(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder rb)
            throws Exception {
        return mvc.perform(rb.cookie(new Cookie("access_token", token)));
    }

    private static int codeOf(MvcResult result) throws Exception {
        return new ObjectMapper().readTree(result.getResponse().getContentAsString())
                .get("code").asInt();
    }

    private JsonNode dataOf(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(body).get("data");
        assertNotNull(data, "响应缺 data: " + body);
        return data;
    }
}
