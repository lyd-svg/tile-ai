package com.tileai.quote;

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
import com.tileai.quote.entity.QuoteParam;
import com.tileai.quote.mapper.QuoteParamMapper;

import jakarta.servlet.http.Cookie;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * T-0.11 验收(一):报价参数维护(架构 §8.5;PRD §5.5、§5.7)。
 *
 * <p><b>需要 MySQL</b>。用 {@code -Ddb.verify=true} 显式启用。
 *
 * <p>判据:「量纲必须落实为<b>元/㎡ + 小数</b>(D-19);损耗率以小数存储,
 * {@code 0.06} 表示 6%(D-15)」。
 *
 * <p>本接口权限仅 super_admin(架构 §4.4),故测试用 super_admin 会话;
 * 越权拒绝由 {@code AdminRbacIntegrationTest} 覆盖。
 */
@EnabledIfSystemProperty(named = "db.verify", matches = "true",
        disabledReason = "需要 MySQL;用 -Ddb.verify=true 显式启用(T-0.11 验证)")
@SpringBootTest(properties = {"spring.flyway.enabled=false",
        "tile.auth.secret=0123456789abcdef0123456789abcdef"})
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AdminQuoteParamIntegrationTest {

    private static final String BASE = "/api/v1/admin/quote-params";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private AdminUserMapper adminUserMapper;

    @Autowired
    private QuoteParamMapper quoteParamMapper;

    private long adminId;
    private String token;

    @BeforeAll
    void seed() {
        AdminUser admin = new AdminUser();
        admin.setUsername("t11_quote_admin");
        admin.setPasswordHash("$2a$10$placeholder-not-used-because-no-login-here");
        // 报价参数仅 super_admin 可读写(架构 §4.4)
        admin.setRole("super_admin");
        admin.setStatus(AdminUser.STATUS_ENABLED);
        adminUserMapper.insert(admin);
        adminId = admin.getId();
        token = jwtService.issueAccess(adminId, SubjectType.ADMIN);
    }

    @AfterAll
    void cleanup() {
        quoteParamMapper.delete(Wrappers.<QuoteParam>lambdaQuery()
                .in(QuoteParam::getParamKey,
                        List.of("aux_unit_price", "loss_rate_min", "loss_rate_max")));
        adminUserMapper.deleteById(adminId);
    }

    // ---------------------------------------------------------------
    // D-19 / D-15 量纲
    // ---------------------------------------------------------------

    @Test
    @DisplayName("辅料单价以 元/㎡ 存储,原值读回")
    void auxUnitPriceKeepsYuanPerSqmScale() throws Exception {
        assertEquals(0, codeOf(upsert("aux_unit_price", "20.00")));

        JsonNode param = find("aux_unit_price");
        assertNotNull(param, "参数应已写入");
        assertDecimal("20.00", param.get("paramValue"), "辅料单价应原样读回,单位为元/㎡");
    }

    @Test
    @DisplayName("D-15:损耗率以小数存储 —— 0.06 读回仍是 0.06,不是 6")
    void lossRateStoredAsFraction() throws Exception {
        assertEquals(0, codeOf(upsert("loss_rate_min", "0.05")));
        assertEquals(0, codeOf(upsert("loss_rate_max", "0.08")));

        assertDecimal("0.05", find("loss_rate_min").get("paramValue"),
                "损耗率必须以小数存储(0.06 表示 6%)");
        assertDecimal("0.08", find("loss_rate_max").get("paramValue"));
    }

    @Test
    @DisplayName("D-15 反例:损耗率填 6(百分数写法)被拒 → 10001")
    void percentStyleLossRateIsRejected() throws Exception {
        MvcResult result = asAdmin(put(BASE).contentType(MediaType.APPLICATION_JSON)
                .content(json("loss_rate_min", "6"))).andReturn();

        assertEquals(10001, codeOf(result),
                "填 6 会让所有报价金额偏 100 倍,必须在写入时就拦住");
        assertTrue(result.getResponse().getContentAsString().contains("0.06"),
                "错误提示应写明正确写法: " + result.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("负值与 0 被拒")
    void nonPositiveIsRejected() throws Exception {
        assertEquals(10001, codeOf(upsert("aux_unit_price", "0")));
        assertEquals(10001, codeOf(upsert("aux_unit_price", "-1")));
        assertEquals(10001, codeOf(upsert("loss_rate_max", "-0.05")));
    }

    @Test
    @DisplayName("未知参数键 → 10001")
    void unknownKeyIsRejected() throws Exception {
        assertEquals(10001, codeOf(upsert("no_such_param", "1")));
    }

    // ---------------------------------------------------------------
    // upsert 语义
    // ---------------------------------------------------------------

    @Test
    @DisplayName("同键重复提交是更新而非新增")
    void upsertUpdatesInsteadOfDuplicating() throws Exception {
        upsert("aux_unit_price", "20.00");
        upsert("aux_unit_price", "25.50");

        assertEquals(1, quoteParamMapper.selectCount(Wrappers.<QuoteParam>lambdaQuery()
                .eq(QuoteParam::getParamKey, "aux_unit_price")),
                "参数表是键值对结构,同键多条会让人不知道哪条生效");
        assertDecimal("25.50", find("aux_unit_price").get("paramValue"));
    }

    @Test
    @DisplayName("列表按契约返回 { list },不分页")
    void listIsNotPaginated() throws Exception {
        upsert("aux_unit_price", "20.00");

        JsonNode data = dataOf(asAdmin(get(BASE)).andReturn());
        assertTrue(data.has("list"));
        assertTrue(!data.has("total") && !data.has("page") && !data.has("size"),
                "契约对 /quote-params 声明的 data 只有 list: " + data);
    }

    @Test
    @DisplayName("缺 paramValue → 10001")
    void missingValueIsRejected() throws Exception {
        MvcResult result = asAdmin(put(BASE).contentType(MediaType.APPLICATION_JSON)
                .content("{\"paramKey\":\"aux_unit_price\"}")).andReturn();
        assertEquals(10001, codeOf(result));
        assertTrue(result.getResponse().getContentAsString().contains("\"field\":\"paramValue\""),
                result.getResponse().getContentAsString());
    }

    // ---------------------------------------------------------------

    private MvcResult upsert(String key, String value) throws Exception {
        return asAdmin(put(BASE).contentType(MediaType.APPLICATION_JSON)
                .content(json(key, value))).andReturn();
    }

    private String json(String key, String value) throws Exception {
        var node = objectMapper.createObjectNode();
        node.put("paramKey", key);
        node.put("paramValue", new java.math.BigDecimal(value));
        return objectMapper.writeValueAsString(node);
    }

    private JsonNode find(String key) throws Exception {
        JsonNode list = dataOf(asAdmin(get(BASE)).andReturn()).get("list");
        for (JsonNode node : list) {
            if (key.equals(node.get("paramKey").asText())) {
                return node;
            }
        }
        return null;
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

    private static void assertDecimal(String expected, JsonNode actual) {
        assertDecimal(expected, actual, null);
    }

    private static void assertDecimal(String expected, JsonNode actual, String message) {
        assertNotNull(actual, (message == null ? "" : message + " —— ") + "字段缺失");
        assertEquals(0, new java.math.BigDecimal(expected).compareTo(actual.decimalValue()),
                (message == null ? "" : message + ";")
                        + "期望 " + expected + ",实际 " + actual.asText());
    }
}
