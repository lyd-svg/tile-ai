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
import com.tileai.common.security.SubjectType;

import jakarta.servlet.http.Cookie;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
    private static final String LOGOUT = "/api/v1/admin/auth/logout";
    private static final String ME = "/api/v1/admin/auth/me";
    private static final String ADMIN_REFRESH = "/api/v1/admin/auth/refresh";
    private static final String USERNAME = "t05_login_probe";
    private static final String PASSWORD = "correct-horse-battery";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AdminUserMapper adminUserMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private com.tileai.common.security.JwtService jwtService;

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
        String access = setCookies.stream().filter(c -> c.startsWith("admin_access_token="))
                .findFirst().orElse(null);
        String refresh = setCookies.stream().filter(c -> c.startsWith("admin_refresh_token="))
                .findFirst().orElse(null);

        assertTrue(access != null, "未下发 admin_access_token: " + setCookies);
        assertTrue(refresh != null, "未下发 admin_refresh_token: " + setCookies);
        assertTrue(access.contains("HttpOnly"), access);
        // 后台用**自己那套**名字与路径:C 端是 access_token / refresh_token,
        // 两端同名同路径会互相覆盖(架构 §10.1、缺口 G-9)。
        assertTrue(access.contains("Path=/api/v1/admin"),
                "后台 access 的 Path 应只覆盖后台接口: " + access);
        assertTrue(refresh.contains("Path=/api/v1/admin/auth"),
                "后台 refresh 的 Path 必须收窄到 auth 分组,且**覆盖登录端点自身**: " + refresh);
        assertTrue("/api/v1/admin/auth/login".startsWith("/api/v1/admin/auth"),
                "自检:该 Path 确实覆盖登录端点 —— 否则浏览器会拒存这个 Cookie");

        // §10.1 的硬要求:令牌不进响应体(前端零存储)
        String accessToken = access.split(";")[0].substring("admin_access_token=".length());
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

    // ------------------------------------------------------------------
    // v1.9 新增:GET /auth/me 与 POST /auth/logout(架构 §10.3)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("GET /me:带会话返回当前账号 —— 前端刷新页面后据此恢复会话")
    void meReturnsCurrentAccount() throws Exception {
        MvcResult result = mvc.perform(get(ME).cookie(loginCookies())).andReturn();

        assertEquals(200, result.getResponse().getStatus());
        String body = result.getResponse().getContentAsString();
        assertTrue(body.contains("\"code\":0"), body);
        assertTrue(body.contains("\"username\":\"" + USERNAME + "\""), body);
        assertTrue(body.contains("\"role\":\"operator\""), body);
        assertFalse(body.contains("passwordHash"), "响应体绝不能带密码哈希: " + body);
    }

    @Test
    @DisplayName("GET /me:未登录 → 真实 401(契约 T-4)")
    void meRequiresLogin() throws Exception {
        assertEquals(401, mvc.perform(get(ME)).andReturn().getResponse().getStatus(),
                "没有后台会话时必须是真实 401,而不是 200 + 业务码");
    }

    @Test
    @DisplayName("GET /me:会话有效期内账号被停用 → 10002(会话失效,不是 30001)")
    void meRejectsAccountDisabledAfterTokenIssued() throws Exception {
        // 令牌签发后账号可能被停用,而令牌在最长 2 小时内仍然有效。
        // 权限判定读的必须是"当下的事实"(AdminAccountService 的类注释),
        // 所以这条必须挡住,且要报"需要重新登录"而不是"资源不存在"。
        Cookie[] cookies = loginCookies();
        AdminUser admin = adminUserMapper.selectById(adminId);
        String original = admin.getStatus();
        try {
            admin.setStatus(AdminUser.STATUS_DISABLED);
            adminUserMapper.updateById(admin);

            MvcResult result = mvc.perform(get(ME).cookie(cookies)).andReturn();
            assertTrue(result.getResponse().getContentAsString().contains("\"code\":10002"),
                    result.getResponse().getContentAsString());
        } finally {
            admin.setStatus(original);
            adminUserMapper.updateById(admin);
        }
    }

    @Test
    @DisplayName("POST /logout:清掉两条 Cookie(Max-Age=0)")
    void logoutClearsBothCookies() throws Exception {
        MvcResult result = mvc.perform(post(LOGOUT).cookie(loginCookies())).andReturn();

        assertEquals(200, result.getResponse().getStatus());
        List<String> setCookies = result.getResponse().getHeaders("Set-Cookie");
        assertEquals(2, setCookies.size(), "应下发两条清除用的 Set-Cookie: " + setCookies);
        assertTrue(setCookies.stream().allMatch(c -> c.contains("Max-Age=0")), setCookies.toString());
        assertTrue(setCookies.stream().anyMatch(c -> c.startsWith("admin_access_token=")),
                setCookies.toString());
        assertTrue(setCookies.stream().anyMatch(c -> c.startsWith("admin_refresh_token=")),
                setCookies.toString());
    }

    @Test
    @DisplayName("POST /logout:未登录 → 真实 401(与 C 端同口径)")
    void logoutRequiresLogin() throws Exception {
        assertEquals(401, mvc.perform(post(LOGOUT)).andReturn().getResponse().getStatus());
    }

    @Test
    @DisplayName("后台登出**吊销得了** refresh 令牌(缺口 B 已修复)")
    void adminLogoutRevokesRefreshToken() throws Exception {
        // 曾经这是一条"钉住缺口"的测试:后台 refresh 的 Path 是 /api/v1/auth,
        // 覆盖不到 /api/v1/admin/auth/logout,浏览器根本不会把它发过来,
        // 于是登出只能清 Cookie、吊销不了令牌。
        //
        // 两端会话分离后(架构 §10.1 v2.0),后台 refresh 的 Path 改为
        // /api/v1/admin/auth —— 覆盖本组全部端点,登出收得到了。
        // 这条测试随之从"记录缺口"变成"验证修复"。
        MvcResult loginResult = login(USERNAME, PASSWORD);
        String refreshToken = cookieValueOf(loginResult, "admin_refresh_token");

        // 用**同一次**登录的 Cookie 登出,否则拉黑的是另一枚令牌,结论就假了
        MvcResult logout = mvc.perform(post(LOGOUT).cookie(cookiesOf(loginResult))).andReturn();
        assertEquals(200, logout.getResponse().getStatus());

        MvcResult refreshed = mvc.perform(post(ADMIN_REFRESH)
                        .cookie(new Cookie("admin_refresh_token", refreshToken)))
                .andReturn();
        assertTrue(refreshed.getResponse().getContentAsString().contains("\"code\":10002"),
                "登出后该 refresh 令牌必须已被拉黑: " + refreshed.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("POST /auth/refresh:凭后台 refresh Cookie 换新 access")
    void refreshIssuesNewAccessToken() throws Exception {
        MvcResult loginResult = login(USERNAME, PASSWORD);
        String refreshToken = cookieValueOf(loginResult, "admin_refresh_token");

        MvcResult refreshed = mvc.perform(post(ADMIN_REFRESH)
                        .cookie(new Cookie("admin_refresh_token", refreshToken)))
                .andReturn();

        assertEquals(200, refreshed.getResponse().getStatus());
        String body = refreshed.getResponse().getContentAsString();
        assertTrue(body.contains("\"code\":0"), body);

        List<String> setCookies = refreshed.getResponse().getHeaders("Set-Cookie");
        assertEquals(1, setCookies.size(), "刷新只该下发一条(新 access): " + setCookies);
        assertTrue(setCookies.get(0).startsWith("admin_access_token="), setCookies.toString());
        assertFalse(setCookies.get(0).contains("admin_refresh_token"),
                "契约:只刷访问令牌,不轮换 refresh");

        // 换到的 access 要真的能用
        MvcResult me = mvc.perform(get(ME).cookie(toCookie(setCookies.get(0)))).andReturn();
        assertTrue(me.getResponse().getContentAsString().contains("\"code\":0"),
                me.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("刷新端点未带 Cookie → 10002(不是 500,也不是放行)")
    void refreshWithoutCookieIsUnauthenticated() throws Exception {
        MvcResult result = mvc.perform(post(ADMIN_REFRESH)).andReturn();
        assertTrue(result.getResponse().getContentAsString().contains("\"code\":10002"),
                result.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("C 端刷新令牌塞进后台 Cookie 也换不到后台会话(主体类型校验)")
    void userRefreshTokenCannotRefreshAdminSession() throws Exception {
        // 把 **USER 主体**的刷新令牌放进后台的 Cookie 名里。签名有效、类型也是 REFRESH,
        // 只有主体类型不符 —— AuthSessionService 的第四道校验专门挡这个。
        //
        // 直接用 JwtService 签一个,不落库、不需清理。
        String userRefresh = jwtService.issueRefresh(999_999L, SubjectType.USER);

        MvcResult result = mvc.perform(post(ADMIN_REFRESH)
                        .cookie(new Cookie("admin_refresh_token", userRefresh)))
                .andReturn();

        assertTrue(result.getResponse().getContentAsString().contains("\"code\":10002"),
                "主体类型不符必须拒绝 —— 否则 C 端令牌能换到后台会话: "
                        + result.getResponse().getContentAsString());
    }

    // ------------------------------------------------------------------

    private MvcResult login(String username, String password) throws Exception {
        return mvc.perform(post(LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andReturn();
    }

    /** 登录一次,把下发的两张 Cookie 解出来供后续请求使用。 */
    private Cookie[] loginCookies() throws Exception {
        return cookiesOf(login(USERNAME, PASSWORD));
    }

    /**
     * 把某次登录响应里的 Set-Cookie 解成 Cookie 数组。
     *
     * <p><b>必须用同一次登录的响应</b>:每次登录都会签发新的 jti,
     * 拿另一次登录的 Cookie 去登出,拉黑的就是另一枚令牌 —— 测出来的结论是错的。
     */
    private Cookie[] cookiesOf(MvcResult result) {
        List<String> setCookies = result.getResponse().getHeaders("Set-Cookie");
        Cookie[] cookies = new Cookie[setCookies.size()];
        for (int i = 0; i < setCookies.size(); i++) {
            cookies[i] = toCookie(setCookies.get(i));
        }
        return cookies;
    }

    /** 只取后台 access Cookie。 */
    private Cookie accessCookieOf(MvcResult result) {
        return result.getResponse().getHeaders("Set-Cookie").stream()
                .filter(header -> header.startsWith("admin_access_token="))
                .findFirst()
                .map(this::toCookie)
                .orElseThrow(() -> new AssertionError("登录未下发 admin_access_token"));
    }

    private String cookieValueOf(MvcResult result, String name) {
        return result.getResponse().getHeaders("Set-Cookie").stream()
                .filter(header -> header.startsWith(name + "="))
                .findFirst()
                .map(header -> header.split(";", 2)[0].substring(name.length() + 1))
                .orElseThrow(() -> new AssertionError("登录未下发 " + name));
    }

    private Cookie toCookie(String setCookieHeader) {
        String[] nameValue = setCookieHeader.split(";", 2)[0].split("=", 2);
        return new Cookie(nameValue[0], nameValue[1]);
    }
}
