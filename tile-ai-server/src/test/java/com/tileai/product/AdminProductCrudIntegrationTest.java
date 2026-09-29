package com.tileai.product;

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
import com.tileai.admin.entity.AdminUser;
import com.tileai.admin.mapper.AdminUserMapper;
import com.tileai.common.security.JwtService;
import com.tileai.common.security.SubjectType;
import com.tileai.product.entity.Product;
import com.tileai.product.entity.ProductImage;
import com.tileai.product.mapper.ProductImageMapper;
import com.tileai.product.mapper.ProductMapper;

import jakarta.servlet.http.Cookie;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * T-0.6 验收:后台产品库 CRUD 的真实行为。
 *
 * <p><b>需要 MySQL</b>。用 {@code -Ddb.verify=true} 显式启用。
 *
 * <p>覆盖 T-0.6 的三条判据:
 * <ol>
 *   <li>列表分页含 list / total / page / size,且<b>确实分页</b>(校验表 T-3);</li>
 *   <li>重复上下架返回 <b>30002</b>;</li>
 *   <li>新增 / 编辑 / 详情 / 删除 的完整流转。</li>
 * </ol>
 *
 * <p>分页那条尤其重要:{@code MybatisPlusConfig} 若漏了分页插件,
 * {@code selectPage} <b>不报错</b>,只是把全表当一页返回 —— 那种错误只有
 * "造多条数据、看返回条数"才能发现。
 */
@EnabledIfSystemProperty(named = "db.verify", matches = "true",
        disabledReason = "需要 MySQL;用 -Ddb.verify=true 显式启用(T-0.6 验证)")
@SpringBootTest(properties = {"spring.flyway.enabled=false",
        "tile.auth.secret=0123456789abcdef0123456789abcdef"})
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AdminProductCrudIntegrationTest {

    private static final String BASE = "/api/v1/admin/products";
    /** 本测试所有 SKU 编号都带此前缀,便于清理时不误伤真实数据。 */
    private static final String SKU_PREFIX = "T06-";

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

    private long adminId;
    private String token;

    @BeforeAll
    void seedAdmin() {
        AdminUser admin = new AdminUser();
        admin.setUsername("t06_admin");
        admin.setPasswordHash("$2a$10$placeholder-not-used-because-no-login-here");
        admin.setRole("operator");
        admin.setStatus(AdminUser.STATUS_ENABLED);
        adminUserMapper.insert(admin);
        adminId = admin.getId();
        token = jwtService.issueAccess(adminId, SubjectType.ADMIN);
    }

    @AfterAll
    void cleanup() {
        List<Long> ids = productMapper.selectList(Wrappers.<Product>lambdaQuery()
                        .likeRight(Product::getSkuCode, SKU_PREFIX))
                .stream().map(Product::getId).toList();
        if (!ids.isEmpty()) {
            productImageMapper.delete(Wrappers.<ProductImage>lambdaQuery()
                    .in(ProductImage::getProductId, ids));
            productMapper.deleteByIds(ids);
        }
        adminUserMapper.deleteById(adminId);
    }

    // ---------------------------------------------------------------
    // 增
    // ---------------------------------------------------------------

    @Test
    @DisplayName("新增 SKU → 返回 { id },且详情能读回同一份数据")
    void createThenReadBack() throws Exception {
        long id = createSku("T06-CREATE", "雅灰石纹", "268.00",
                List.of("bathroom"), List.of("modern"));

        MvcResult detail = asAdmin(get(BASE + "/" + id)).andReturn();
        assertEquals(200, detail.getResponse().getStatus());

        JsonNode data = dataOf(detail);
        assertEquals("T06-CREATE", data.get("sku").asText());
        assertEquals("雅灰石纹", data.get("name").asText());
        assertDecimal("268.00", data.get("pricePerSqm"));
        assertEquals("off_sale", data.get("status").asText(), "未传 status 时应落在下架态");
        assertEquals(List.of("bathroom"),
                objectMapper.convertValue(data.get("applicableSpaces"), List.class));
    }

    @Test
    @DisplayName("编号重复 → 30002(状态不允许该操作)")
    void duplicateSkuIsRejected() throws Exception {
        createSku("T06-DUP", "第一款", "100.00", null, null);

        MvcResult second = asAdmin(post(BASE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(skuJson("T06-DUP", "第二款", "200.00"))).andReturn();

        assertEquals(200, second.getResponse().getStatus(), "业务失败仍是 HTTP 200");
        assertEquals(30002, codeOf(second));
    }

    @Test
    @DisplayName("缺必填字段 → 10001,明细落在 data.errors[]")
    void missingRequiredFieldIsValidationError() throws Exception {
        MvcResult result = asAdmin(post(BASE)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\":\"T06-NONAME\",\"pricePerSqm\":1.00}")).andReturn();

        assertEquals(200, result.getResponse().getStatus());
        assertEquals(10001, codeOf(result));
        String body = result.getResponse().getContentAsString();
        assertTrue(body.contains("\"data\":{\"errors\":["), body);
        assertTrue(body.contains("\"field\":\"name\""), body);
    }

    @Test
    @DisplayName("非法 status → 10001(枚举值被拦住,不是落库后才报错)")
    void invalidStatusIsValidationError() throws Exception {
        MvcResult result = asAdmin(post(BASE)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\":\"T06-BADSTATUS\",\"name\":\"x\",\"pricePerSqm\":1.00,"
                        + "\"status\":\"sold_out\"}")).andReturn();

        assertEquals(10001, codeOf(result));
        assertTrue(result.getResponse().getContentAsString().contains("\"field\":\"status\""),
                result.getResponse().getContentAsString());
    }

    // ---------------------------------------------------------------
    // 多角度图
    // ---------------------------------------------------------------

    @Test
    @DisplayName("多角度图可写入并读回,且按 sort 稳定排序")
    void imagesArePersistedAndOrdered() throws Exception {
        long id = createSku("T06-IMG", "带图款", "300.00", null, null,
                List.of(image("https://oss/a.jpg", "angle2", 2),
                        image("https://oss/b.jpg", "default", 1)));

        JsonNode images = dataOf(asAdmin(get(BASE + "/" + id)).andReturn()).get("images");
        assertEquals(2, images.size());
        assertEquals("https://oss/b.jpg", images.get(0).get("url").asText(),
                "应按 sort 升序返回");
        assertEquals("default", images.get(0).get("angle").asText());
    }

    @Test
    @DisplayName("编辑时整体替换图片(旧图不残留)")
    void updateReplacesImages() throws Exception {
        long id = createSku("T06-IMG2", "换图款", "300.00", null, null,
                List.of(image("https://oss/old1.jpg", "default", 0),
                        image("https://oss/old2.jpg", "angle1", 1)));

        asAdmin(put(BASE + "/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(skuJsonWithImages("T06-IMG2", "换图款", "300.00",
                        List.of(image("https://oss/new.jpg", "default", 0))))).andReturn();

        JsonNode images = dataOf(asAdmin(get(BASE + "/" + id)).andReturn()).get("images");
        assertEquals(1, images.size(), "旧图必须被替换掉,不能残留");
        assertEquals("https://oss/new.jpg", images.get(0).get("url").asText());
    }

    // ---------------------------------------------------------------
    // 列表与分页(T-3)
    // ---------------------------------------------------------------

    @Test
    @DisplayName("列表返回 PageData 的四个字段(T-3)")
    void listHasPageDataShape() throws Exception {
        JsonNode data = dataOf(asAdmin(get(BASE)).andReturn());
        assertTrue(data.has("list"), "缺少 list: " + data);
        assertTrue(data.has("total"), "缺少 total: " + data);
        assertTrue(data.has("page"), "缺少 page: " + data);
        assertTrue(data.has("size"), "缺少 size: " + data);
        assertTrue(data.get("list").isArray());
    }

    @Test
    @DisplayName("分页真的生效:size=2 时只返回 2 条,但 total 是全部条数")
    void paginationActuallyLimits() throws Exception {
        for (int i = 0; i < 5; i++) {
            createSku("T06-PAGE-" + i, "分页款" + i, "100.00", null, null);
        }

        JsonNode page1 = dataOf(asAdmin(get(BASE)
                .param("keyword", "T06-PAGE-")
                .param("page", "1").param("size", "2")).andReturn());

        assertEquals(2, page1.get("list").size(),
                "每页条数没生效 —— 多半是 MybatisPlusConfig 漏了分页插件");
        assertEquals(5, page1.get("total").asInt());
        assertEquals(1, page1.get("page").asInt());
        assertEquals(2, page1.get("size").asInt());

        JsonNode page2 = dataOf(asAdmin(get(BASE)
                .param("keyword", "T06-PAGE-")
                .param("page", "2").param("size", "2")).andReturn());
        assertEquals(2, page2.get("list").size());
        assertFalse(page1.get("list").get(0).get("id").asLong()
                        == page2.get("list").get(0).get("id").asLong(),
                "第 1 页与第 2 页不应返回同一条");
    }

    @Test
    @DisplayName("keyword 同时匹配名称与编号")
    void keywordMatchesNameAndSku() throws Exception {
        createSku("T06-KW-UNIQUE", "关键字特搜款", "100.00", null, null);

        JsonNode bySku = dataOf(asAdmin(get(BASE).param("keyword", "T06-KW-UNIQUE")).andReturn());
        assertEquals(1, bySku.get("list").size());

        JsonNode byName = dataOf(asAdmin(get(BASE).param("keyword", "关键字特搜款")).andReturn());
        assertEquals(1, byName.get("list").size());
    }

    @Test
    @DisplayName("status 过滤只返回对应状态")
    void statusFilterWorks() throws Exception {
        long id = createSku("T06-FILTER", "过滤款", "100.00", null, null);
        asAdmin(patch(BASE + "/" + id + "/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"on_sale\"}")).andReturn();

        JsonNode onSale = dataOf(asAdmin(get(BASE)
                .param("keyword", "T06-FILTER").param("status", "on_sale")).andReturn());
        assertEquals(1, onSale.get("list").size());

        JsonNode offSale = dataOf(asAdmin(get(BASE)
                .param("keyword", "T06-FILTER").param("status", "off_sale")).andReturn());
        assertEquals(0, offSale.get("list").size());
    }

    @Test
    @DisplayName("size 超过契约上限 100 → 10001")
    void sizeOverLimitIsRejected() throws Exception {
        MvcResult result = asAdmin(get(BASE).param("size", "101")).andReturn();
        assertEquals(10001, codeOf(result));
    }

    // ---------------------------------------------------------------
    // 上下架(30002)
    // ---------------------------------------------------------------

    @Test
    @DisplayName("上下架成功,重复操作同一状态返回 30002")
    void repeatedStatusChangeIsRejected() throws Exception {
        long id = createSku("T06-STATUS", "上下架款", "100.00", null, null);

        MvcResult first = asAdmin(patch(BASE + "/" + id + "/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"on_sale\"}")).andReturn();
        assertEquals(200, first.getResponse().getStatus());
        assertEquals(0, codeOf(first));

        MvcResult again = asAdmin(patch(BASE + "/" + id + "/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"on_sale\"}")).andReturn();
        assertEquals(200, again.getResponse().getStatus(), "业务失败仍是 HTTP 200");
        assertEquals(30002, codeOf(again), "契约:对同款砖重复上下架 → 30002");

        // 换个方向仍然允许
        assertEquals(0, codeOf(asAdmin(patch(BASE + "/" + id + "/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"off_sale\"}")).andReturn()));
    }

    // ---------------------------------------------------------------
    // 删除
    // ---------------------------------------------------------------

    @Test
    @DisplayName("上架中的 SKU 不可删 → 30002;下架后删除成功,详情转为 30001")
    void deleteRequiresOffSaleFirst() throws Exception {
        long id = createSku("T06-DEL", "待删款", "100.00", null, null);
        asAdmin(patch(BASE + "/" + id + "/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"on_sale\"}")).andReturn();

        assertEquals(30002, codeOf(asAdmin(delete(BASE + "/" + id)).andReturn()),
                "上架中不允许删除,须先下架");

        asAdmin(patch(BASE + "/" + id + "/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"off_sale\"}")).andReturn();

        assertEquals(0, codeOf(asAdmin(delete(BASE + "/" + id)).andReturn()));
        assertEquals(30001, codeOf(asAdmin(get(BASE + "/" + id)).andReturn()),
                "删除后详情应返回 30001");
    }

    @Test
    @DisplayName("删除会一并清掉多角度图,不留悬挂行")
    void deleteCascadesImages() throws Exception {
        long id = createSku("T06-DELIMG", "带图待删", "100.00", null, null,
                List.of(image("https://oss/x.jpg", "default", 0)));

        asAdmin(delete(BASE + "/" + id)).andReturn();

        assertEquals(0, productImageMapper.selectCount(Wrappers.<ProductImage>lambdaQuery()
                .eq(ProductImage::getProductId, id)), "图片行必须随 SKU 一起删除");
    }

    @Test
    @DisplayName("删除不存在的 SKU → 30001")
    void deleteMissingIsNotFound() throws Exception {
        assertEquals(30001, codeOf(asAdmin(delete(BASE + "/99999999")).andReturn()));
    }

    // ---------------------------------------------------------------
    // 编辑
    // ---------------------------------------------------------------

    @Test
    @DisplayName("编辑不存在的 SKU → 30001;改成已被占用的编号 → 30002")
    void updateGuards() throws Exception {
        assertEquals(30001, codeOf(asAdmin(put(BASE + "/99999999")
                .contentType(MediaType.APPLICATION_JSON)
                .content(skuJson("T06-ANY", "x", "1.00"))).andReturn()));

        createSku("T06-UPD-A", "A", "100.00", null, null);
        long b = createSku("T06-UPD-B", "B", "100.00", null, null);

        assertEquals(30002, codeOf(asAdmin(put(BASE + "/" + b)
                .contentType(MediaType.APPLICATION_JSON)
                .content(skuJson("T06-UPD-A", "B", "100.00"))).andReturn()),
                "改成别的 SKU 已占用的编号必须被拒");
    }

    @Test
    @DisplayName("编辑修改字段后详情反映新值")
    void updateAppliesChanges() throws Exception {
        long id = createSku("T06-UPD", "原名", "100.00", null, null);

        assertEquals(0, codeOf(asAdmin(put(BASE + "/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(skuJson("T06-UPD", "改名后", "188.88"))).andReturn()));

        JsonNode data = dataOf(asAdmin(get(BASE + "/" + id)).andReturn());
        assertEquals("改名后", data.get("name").asText());
        assertDecimal("188.88", data.get("pricePerSqm"));
    }

    /**
     * 按<b>数值</b>而非字符串比较小数。
     *
     * <p>写测试时踩过:{@code ObjectNode.put(String, BigDecimal)} 会经 Jackson 的
     * {@code JsonNodeFactory},而它默认对 BigDecimal 做 {@code stripTrailingZeros} ——
     * {@code 268.00} 会变成 {@code 268.0}。契约对金额只声明 {@code type: number},
     * 并未规定小数位数,所以断言数值相等才是正确的口径;比字符串会把
     * "格式差异"误判成"数据错误"。
     */
    private static void assertDecimal(String expected, JsonNode actual) {
        assertNotNull(actual, "缺少该数值字段");
        assertEquals(0, new java.math.BigDecimal(expected).compareTo(actual.decimalValue()),
                "期望数值 " + expected + ",实际 " + actual.asText());
    }

    // ---------------------------------------------------------------

    private long createSku(String sku, String name, String price,
                           List<String> spaces, List<String> styles) throws Exception {
        return createSku(sku, name, price, spaces, styles, null);
    }

    private long createSku(String sku, String name, String price,
                           List<String> spaces, List<String> styles,
                           List<ImageNode> images) throws Exception {
        var request = post(BASE).contentType(MediaType.APPLICATION_JSON);
        request.content(skuJsonWithImages(sku, name, price, images, spaces, styles));

        MvcResult result = asAdmin(request).andReturn();
        assertEquals(0, codeOf(result), "创建应成功: " + result.getResponse().getContentAsString());
        JsonNode data = dataOf(result);
        assertNotNull(data.get("id"), "创建响应应返回 { id }");
        return data.get("id").asLong();
    }

    private record ImageNode(String url, String angle, Integer sort) {
    }

    private static ImageNode image(String url, String angle, Integer sort) {
        return new ImageNode(url, angle, sort);
    }

    private String skuJson(String sku, String name, String price) throws Exception {
        return skuJsonWithImages(sku, name, price, null, null, null);
    }

    private String skuJsonWithImages(String sku, String name, String price,
                                     List<ImageNode> images) throws Exception {
        return skuJsonWithImages(sku, name, price, images, null, null);
    }

    private String skuJsonWithImages(String sku, String name, String price,
                                     List<ImageNode> images,
                                     List<String> spaces, List<String> styles) throws Exception {
        var node = objectMapper.createObjectNode();
        node.put("sku", sku);
        node.put("name", name);
        node.put("pricePerSqm", new java.math.BigDecimal(price));
        if (spaces != null) {
            node.set("applicableSpaces", objectMapper.valueToTree(spaces));
        }
        if (styles != null) {
            node.set("styleTags", objectMapper.valueToTree(styles));
        }
        if (images != null) {
            node.set("images", objectMapper.valueToTree(images));
        }
        return objectMapper.writeValueAsString(node);
    }

    private org.springframework.test.web.servlet.ResultActions asAdmin(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder rb)
            throws Exception {
        return mvc.perform(rb.cookie(new Cookie("access_token", token)));
    }

    private static int codeOf(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString();
        return new ObjectMapper().readTree(body).get("code").asInt();
    }

    private JsonNode dataOf(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(body).get("data");
        assertNotNull(data, "响应缺 data: " + body);
        return data;
    }
}
