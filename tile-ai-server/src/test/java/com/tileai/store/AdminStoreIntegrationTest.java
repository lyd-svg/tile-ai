package com.tileai.store;

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
import com.tileai.store.entity.Store;
import com.tileai.store.mapper.StoreMapper;

import jakarta.servlet.http.Cookie;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * T-0.12 验收:后台门店信息维护(架构 §8.5;PRD §7.6 —— 单店)。
 *
 * <p><b>需要 MySQL</b>。用 {@code -Ddb.verify=true} 显式启用。
 *
 * <p>判据:「<b>PUT 为整体更新而非插入</b>;GET 返回字段与契约 Store schema 一致」。
 *
 * <p>对第一条的理解:<b>无论 PUT 多少次,库里始终恰好一行</b> —— 而不是
 * "库里没有就不让配"(表里没有种子数据,那样首次配置无从进行)。
 */
@EnabledIfSystemProperty(named = "db.verify", matches = "true",
        disabledReason = "需要 MySQL;用 -Ddb.verify=true 显式启用(T-0.12 验证)")
@SpringBootTest(properties = {"spring.flyway.enabled=false",
        "tile.auth.secret=0123456789abcdef0123456789abcdef"})
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AdminStoreIntegrationTest {

    private static final String BASE = "/api/v1/admin/store";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private AdminUserMapper adminUserMapper;

    @Autowired
    private StoreMapper storeMapper;

    private long adminId;
    private String token;

    @BeforeAll
    void seed() {
        AdminUser admin = new AdminUser();
        admin.setUsername("t12_admin");
        admin.setPasswordHash("$2a$10$placeholder-not-used-because-no-login-here");
        admin.setRole("operator");
        admin.setStatus(AdminUser.STATUS_ENABLED);
        adminUserMapper.insert(admin);
        adminId = admin.getId();
        token = jwtService.issueAccess(adminId, SubjectType.ADMIN);

        // 单店单条:测试期间清空,由用例自己重新建立
        storeMapper.delete(Wrappers.<Store>lambdaQuery());
    }

    @AfterAll
    void cleanup() {
        storeMapper.delete(Wrappers.<Store>lambdaQuery());
        adminUserMapper.deleteById(adminId);
    }

    // ---------------------------------------------------------------

    @Test
    @DisplayName("尚未配置时 GET → 30001(提示需先配置)")
    void getBeforeConfiguredIsNotFound() throws Exception {
        storeMapper.delete(Wrappers.<Store>lambdaQuery());

        MvcResult result = asAdmin(get(BASE)).andReturn();
        assertEquals(200, result.getResponse().getStatus(), "业务失败仍是 HTTP 200");
        assertEquals(30001, codeOf(result));
    }

    @Test
    @DisplayName("PUT 后 GET 返回契约 Store 的字段")
    void updateThenGetReturnsContractFields() throws Exception {
        assertEquals(0, codeOf(update("旗舰店", "北京市朝阳区某路 1 号", "010-00000000",
                "https://map.example.com/store")));

        JsonNode data = dataOf(asAdmin(get(BASE)).andReturn());
        assertEquals("旗舰店", data.get("name").asText());
        assertEquals("北京市朝阳区某路 1 号", data.get("address").asText());
        assertEquals("010-00000000", data.get("phone").asText());
        assertEquals("https://map.example.com/store", data.get("mapUrl").asText());

        for (String field : new String[]{"id", "name", "address", "phone"}) {
            assertTrue(data.has(field), "契约 Store 应含字段 " + field + ": " + data);
        }
    }

    @Test
    @DisplayName("判据:连续 PUT 不产生第二行,库中始终恰好一条")
    void repeatedPutNeverCreatesSecondRow() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertEquals(0, codeOf(update("第 " + i + " 版店名", "地址 " + i, "010-0000000" + i,
                    null)));
        }

        assertEquals(1, storeMapper.selectCount(Wrappers.<Store>lambdaQuery()),
                "单店单条:多次 PUT 必须始终只有一行,否则前端不知道该显示哪家店");

        // 覆盖而非叠加:最后一次 PUT 的值生效
        JsonNode data = dataOf(asAdmin(get(BASE)).andReturn());
        assertEquals("第 2 版店名", data.get("name").asText());
    }

    @Test
    @DisplayName("未填 mapUrl 时该字段整个省略,而不是输出 null")
    void absentMapUrlIsOmitted() throws Exception {
        update("无地图店", "某地址", "010-11111111", null);

        String body = asAdmin(get(BASE)).andReturn().getResponse().getContentAsString();
        assertTrue(!body.contains("\"mapUrl\""),
                "未填的可选字段应整个省略 —— 显式 null 会被 schema 判为类型不符"
                        + "(T-0.9 已踩过这个坑): " + body);
    }

    @Test
    @DisplayName("缺必填字段 → 10001")
    void missingRequiredIsRejected() throws Exception {
        MvcResult result = asAdmin(put(BASE).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"只有名字\"}")).andReturn();

        assertEquals(10001, codeOf(result));
        assertTrue(result.getResponse().getContentAsString().contains("\"field\":\"address\""),
                result.getResponse().getContentAsString());
    }

    // ---------------------------------------------------------------

    private MvcResult update(String name, String address, String phone, String mapUrl)
            throws Exception {
        var node = objectMapper.createObjectNode();
        node.put("name", name);
        node.put("address", address);
        node.put("phone", phone);
        if (mapUrl != null) {
            node.put("mapUrl", mapUrl);
        }
        return asAdmin(put(BASE).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(node))).andReturn();
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

    private JsonNode dataOf(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(body).get("data");
        assertNotNull(data, "响应缺 data: " + body);
        return data;
    }
}
