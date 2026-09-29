package com.tileai.admin;

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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.tileai.admin.entity.AdminUser;
import com.tileai.admin.mapper.AdminUserMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * T-0.5:后台登录接口(契约 {@code POST /api/v1/admin/auth/login})。
 *
 * <p><b>需要 MySQL</b>(要读 {@code t_admin_user})。用 {@code -Ddb.verify=true} 显式启用。
 *
 * <p>重点验三件事:
 * <ol>
 *   <li>成功时下发 HttpOnly Cookie,且<b>令牌不出现在响应体里</b>(§10.1);</li>
 *   <li>失败时统一 20005,<b>不区分</b>"账号不存在 / 密码错 / 已停用" —— 区分开就等于
 *       提供账号枚举;</li>
 *   <li>业务失败仍是 HTTP <b>200</b>,靠响应体 code 区分(T-13)。</li>
 * </ol>
 */
@EnabledIfSystemProperty(named = "db.verify", matches = "true",
        disabledReason = "需要 MySQL;用 -Ddb.verify=true 显式启用(T-0.5 验证)")
@SpringBootTest(properties = {"spring.flyway.enabled=false",
        "tile.auth.secret=0123456789abcdef0123456789abcdef"})
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AdminAuthIntegrationTest {

    private static final String LOGIN = "/api/v1/admin/auth/login";
    private static final String USERNAME = "t05_login_probe";
    private static final String PASSWORD = "correct-horse-battery";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AdminUserMapper adminUserMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private long adminId;

    @BeforeAll
    void seed() {
        AdminUser admin = new AdminUser();
        admin.setUsername(USERNAME);
        admin.setPasswordHash(passwordEncoder.encode(PASSWORD));
        admin.setRole("operator");
        admin.setStatus(AdminUser.STATUS_ENABLED);
        adminUserMapper.insert(admin);
        adminId = admin.getId();
    }

    @AfterAll
    void cleanup() {
        adminUserMapper.deleteById(adminId);
    }

    @Test
    @DisplayName("登录成功:下发两个 HttpOnly Cookie,响应体只含 AdminUserView")
    void successfulLoginIssuesCookiesWithoutLeakingToken() throws Exception {
        MvcResult result = login(USERNAME, PASSWORD);

        assertEquals(200, result.getResponse().getStatus());
        String body = result.getResponse().getContentAsString();

        assertTrue(body.contains("\"username\":\"" + USERNAME + "\""), body);
        assertTrue(body.contains("\"role\":\"operator\""), body);
        assertFalse(body.contains("passwordHash"), "响应体绝不能带密码哈希: " + body);

        List<String> setCookies = result.getResponse().getHeaders("Set-Cookie");
        String access = setCookies.stream().filter(c -> c.startsWith("access_token="))
                .findFirst().orElse(null);
        String refresh = setCookies.stream().filter(c -> c.startsWith("refresh_token="))
                .findFirst().orElse(null);

        assertTrue(access != null, "未下发 access_token: " + setCookies);
        assertTrue(refresh != null, "未下发 refresh_token: " + setCookies);
        assertTrue(access.contains("HttpOnly"), access);
        assertTrue(refresh.contains("Path=/api/v1/auth"),
                "refresh_token 的 Path 必须收窄: " + refresh);

        // §10.1 的硬要求:令牌不进响应体(前端零存储)
        String accessToken = access.split(";")[0].substring("access_token=".length());
        assertFalse(body.contains(accessToken), "令牌出现在响应体里了: " + body);
    }

    @Test
    @DisplayName("密码错误 → HTTP 200 + 业务码 20005")
    void wrongPasswordIsBusinessError() throws Exception {
        MvcResult result = login(USERNAME, "definitely-wrong");

        assertEquals(200, result.getResponse().getStatus(),
                "业务失败必须返回 HTTP 200,靠 code 区分(T-13)");
        assertTrue(result.getResponse().getContentAsString().contains("\"code\":20005"),
                result.getResponse().getContentAsString());
        assertTrue(result.getResponse().getHeaders("Set-Cookie").isEmpty(),
                "登录失败绝不能下发任何 Cookie");
    }

    @Test
    @DisplayName("账号不存在 → 与密码错误完全相同的 20005(不给账号枚举留口子)")
    void unknownUserIsIndistinguishable() throws Exception {
        MvcResult unknown = login("t05_no_such_admin_at_all", PASSWORD);
        MvcResult wrongPassword = login(USERNAME, "definitely-wrong");

        assertEquals(200, unknown.getResponse().getStatus());
        assertEquals(wrongPassword.getResponse().getContentAsString().replaceAll("\"traceId\":\"[^\"]+\"", ""),
                unknown.getResponse().getContentAsString().replaceAll("\"traceId\":\"[^\"]+\"", ""),
                "两种失败的响应体(除 traceId 外)必须完全一致,否则可被用来枚举账号");
    }

    @Test
    @DisplayName("账号已停用 → 同样是 20005,不暴露\"账号存在但被停用\"")
    void disabledAdminIsIndistinguishable() throws Exception {
        AdminUser admin = adminUserMapper.selectById(adminId);
        String originalStatus = admin.getStatus();
        try {
            admin.setStatus(AdminUser.STATUS_DISABLED);
            adminUserMapper.updateById(admin);

            MvcResult result = login(USERNAME, PASSWORD);
            assertEquals(200, result.getResponse().getStatus());
            assertTrue(result.getResponse().getContentAsString().contains("\"code\":20005"),
                    result.getResponse().getContentAsString());
        } finally {
            admin.setStatus(originalStatus);
            adminUserMapper.updateById(admin);
        }
    }

    @Test
    @DisplayName("缺少字段 → 10001(参数校验),单据明细在 data.errors[]")
    void missingFieldsIsValidationError() throws Exception {
        MvcResult result = mvc.perform(post(LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"password\":\"\"}"))
                .andReturn();

        assertEquals(200, result.getResponse().getStatus());
        String body = result.getResponse().getContentAsString();
        assertTrue(body.contains("\"code\":10001"), body);
        assertTrue(body.contains("\"data\":{\"errors\":["), body);
    }

    @Test
    @DisplayName("未登录也能调登录接口(契约 security: [])")
    void loginIsPublic() throws Exception {
        assertEquals(200, login(USERNAME, PASSWORD).getResponse().getStatus());
    }

    private MvcResult login(String username, String password) throws Exception {
        return mvc.perform(post(LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andReturn();
    }
}
