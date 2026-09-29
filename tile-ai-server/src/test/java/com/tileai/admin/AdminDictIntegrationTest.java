package com.tileai.admin;

import java.math.BigDecimal;
import java.util.List;

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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tileai.admin.entity.AdminUser;
import com.tileai.admin.entity.DictEntry;
import com.tileai.admin.mapper.AdminUserMapper;
import com.tileai.admin.mapper.DictMapper;
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
 * T-0.11 验收(二):空间 / 风格字典维护(架构 §8.5;D-14 定稿)。
 *
 * <p><b>需要 MySQL</b>。用 {@code -Ddb.verify=true} 显式启用。
 *
 * <p>判据:「删除字典项时若被产品/搭配/方案引用,返回 <b>30002</b> 而非静默删除」。
 * 静默删除会留下一堆指向不存在空间的悬空引用,前台只能显示空白 —— 属最难排查的那类问题。
 */
@EnabledIfSystemProperty(named = "db.verify", matches = "true",
        disabledReason = "需要 MySQL;用 -Ddb.verify=true 显式启用(T-0.11 验证)")
@SpringBootTest(properties = {"spring.flyway.enabled=false",
        "tile.auth.secret=0123456789abcdef0123456789abcdef"})
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AdminDictIntegrationTest {

    private static final String BASE = "/api/v1/admin/dicts";
    private static final String KEY = "t11_probe_space";
    private static final String SKU = "T11-DICT-REF";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private AdminUserMapper adminUserMapper;

    @Autowired
    private DictMapper dictMapper;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private ProductImageMapper productImageMapper;

    private long adminId;
    private String token;

    @BeforeAll
    void seed() {
        AdminUser admin = new AdminUser();
        admin.setUsername("t11_dict_admin");
        admin.setPasswordHash("$2a$10$placeholder-not-used-because-no-login-here");
        admin.setRole("operator");
        admin.setStatus(AdminUser.STATUS_ENABLED);
        adminUserMapper.insert(admin);
        adminId = admin.getId();
        token = jwtService.issueAccess(adminId, SubjectType.ADMIN);
    }

    /**
     * 每个用例都从"键不存在、产品不存在"开始。
     *
     * <p>不加这一步会踩坑:多个用例共用同一个探针键,而 JUnit <b>不保证执行顺序</b> ——
     * 先跑的用例把它建出来,后跑的再建就撞上 30002,报错信息还会指向"创建失败",
     * 看着像实现有 bug。
     */
    @BeforeEach
    void resetProbeState() {
        dictMapper.delete(Wrappers.<DictEntry>lambdaQuery().eq(DictEntry::getDictKey, KEY));
        deleteProductBySku();
    }

    @AfterAll
    void cleanup() {
        deleteProductBySku();
        dictMapper.delete(Wrappers.<DictEntry>lambdaQuery().eq(DictEntry::getDictKey, KEY));
        adminUserMapper.deleteById(adminId);
    }

    // ---------------------------------------------------------------
    // 增删改查
    // ---------------------------------------------------------------

    @Test
    @DisplayName("新增返回 id;列表按契约返回 { list } 且含禁用项")
    void createAndList() throws Exception {
        long id = create(KEY, "探针空间");
        assertTrue(id > 0);

        // 停用后,管理端列表仍应能看到它 —— 否则运营无从恢复
        assertEquals(0, codeOf(asAdmin(put(BASE + "/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("space", KEY, "探针空间", "disabled"))).andReturn()));

        JsonNode list = dataOf(asAdmin(get(BASE).param("type", "space")).andReturn()).get("list");
        boolean found = false;
        for (JsonNode node : list) {
            if (KEY.equals(node.get("key").asText())) {
                found = true;
                assertEquals("disabled", node.get("status").asText());
            }
        }
        assertTrue(found, "管理端列表必须包含 status=disabled 的项(契约明确要求)");

        assertEquals(0, codeOf(asAdmin(put(BASE + "/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("space", KEY, "探针空间", "enabled"))).andReturn()));
    }

    @Test
    @DisplayName("同类型下键重复 → 30002")
    void duplicateKeyIsRejected() throws Exception {
        create(KEY, "探针空间");

        MvcResult result = asAdmin(post(BASE).contentType(MediaType.APPLICATION_JSON)
                .content(json("space", KEY, "另一个名字", null))).andReturn();

        assertEquals(30002, codeOf(result));
    }

    @Test
    @DisplayName("按 type 过滤;未知 id → 30001")
    void filteringAndMissing() throws Exception {
        JsonNode styles = dataOf(asAdmin(get(BASE).param("type", "style")).andReturn()).get("list");
        styles.forEach(node -> assertEquals("style", node.get("type").asText()));

        assertEquals(30001, codeOf(asAdmin(put(BASE + "/99999999")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("space", "whatever", "x", null))).andReturn()));
        assertEquals(30001, codeOf(asAdmin(delete(BASE + "/99999999")).andReturn()));
    }

    @Test
    @DisplayName("非法 type / 缺 label → 10001")
    void validationErrors() throws Exception {
        assertEquals(10001, codeOf(asAdmin(post(BASE).contentType(MediaType.APPLICATION_JSON)
                .content(json("material", "x", "x", null))).andReturn()),
                "契约声明 type 的 enum 只有 space / style");
        assertEquals(10001, codeOf(asAdmin(post(BASE).contentType(MediaType.APPLICATION_JSON)
                .content(json("space", "t11_missing_label", null, null))).andReturn()));
    }

    // ---------------------------------------------------------------
    // 判据:被引用不可删
    // ---------------------------------------------------------------

    @Test
    @DisplayName("被产品引用时删除 → 30002,且数据仍在")
    void referencedByProductCannotBeDeleted() throws Exception {
        long id = create(KEY, "探针空间");
        insertProductReferencingKey();

        MvcResult result = asAdmin(delete(BASE + "/" + id)).andReturn();
        assertEquals(200, result.getResponse().getStatus(), "业务失败仍是 HTTP 200");
        assertEquals(30002, codeOf(result),
                "被引用的字典项不能静默删除 —— 否则产品会留下指向不存在空间的悬空引用");
        assertTrue(result.getResponse().getContentAsString().contains("产品"),
                "错误信息应说明被什么引用了: " + result.getResponse().getContentAsString());
        assertNotNull(dictMapper.selectById(id), "被拒绝的删除不得真的删掉数据");
    }

    @Test
    @DisplayName("解除引用后可以删除")
    void deletableAfterReferenceRemoved() throws Exception {
        long id = create(KEY, "探针空间");
        insertProductReferencingKey();

        assertEquals(30002, codeOf(asAdmin(delete(BASE + "/" + id)).andReturn()));

        deleteProductBySku();
        assertEquals(0, codeOf(asAdmin(delete(BASE + "/" + id)).andReturn()),
                "解除引用后应能删除");
        assertNull(dictMapper.selectById(id));
    }

    @Test
    @DisplayName("无任何引用时可以直接删除")
    void unreferencedIsDeletable() throws Exception {
        long id = create("t11_unreferenced", "无人引用");
        assertEquals(0, codeOf(asAdmin(delete(BASE + "/" + id)).andReturn()));
    }

    // ---------------------------------------------------------------

    private long create(String key, String label) throws Exception {
        MvcResult result = asAdmin(post(BASE).contentType(MediaType.APPLICATION_JSON)
                .content(json("space", key, label, null))).andReturn();
        assertEquals(0, codeOf(result), "创建应成功: " + result.getResponse().getContentAsString());
        return dataOf(result).get("id").asLong();
    }

    private String json(String type, String key, String label, String status) throws Exception {
        var node = objectMapper.createObjectNode();
        node.put("type", type);
        node.put("key", key);
        if (label != null) {
            node.put("label", label);
        }
        if (status != null) {
            node.put("status", status);
        }
        return objectMapper.writeValueAsString(node);
    }

    /** 建一个 applicableSpaces 含 {@link #KEY} 的产品,制造引用。 */
    private void insertProductReferencingKey() {
        Product product = new Product();
        product.setSkuCode(SKU);
        product.setName("引用了字典的产品");
        product.setPricePerSqm(new BigDecimal("100.00"));
        product.setApplicableSpaces(List.of(KEY));
        product.setStyleTags(List.of("modern"));
        product.setStatus(Product.STATUS_OFF_SALE);
        productMapper.insert(product);
    }

    private void deleteProductBySku() {
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
