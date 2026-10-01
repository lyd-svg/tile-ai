package com.tileai.auth;

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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tileai.auth.entity.User;
import com.tileai.auth.mapper.UserMapper;

import jakarta.servlet.http.Cookie;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * T-1.1 验收:C 端注册 / 登录 / 改密 / 登出 / 刷新(PRD §5.1;契约 openapi-c.yaml 的 auth 分组)。
 *
 * <p><b>需要 MySQL + Redis</b>(Redis 用于 refresh 黑名单,登出/刷新用例依赖它)。
 * 用 {@code -Ddb.verify=true -Dredis.verify=true} 显式启用。
 *
 * <p>覆盖 T-1.1 的三条判据:
 * <ol>
 *   <li>公开 / 需登录接口的鉴权矩阵(契约校验表 T-4,契约侧已由 ci.sh 保证);</li>
 *   <li><b>登录失败一律 20005,不区分「用户名不存在」与「密码错误」</b>;</li>
 *   <li>错误码覆盖 20003 / 20004 / 20005。</li>
 * </ol>
 */
@EnabledIfSystemProperty(named = "redis.verify", matches = "true",
        disabledReason = "需要 Redis 与 MySQL;用 -Ddb.verify=true -Dredis.verify=true 显式启用")
@SpringBootTest(properties = {"spring.flyway.enabled=false",
        "tile.auth.secret=0123456789abcdef0123456789abcdef"})
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthIntegrationTest {

    private static final String BASE = "/api/v1/auth";
    /** 受保护的 C 端探针路径(见 SecurityProbeController),用来验证登录态真的能用。 */
    private static final String PROTECTED_C = "/api/v1/plans/probe";

    private static final String USERNAME = "t11_alice";
    private static final String PASSWORD = "secret-123";
    private static final String USER_DISABLED = "t11_disabled";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserMapper userMapper;

    @BeforeAll
    void cleanupBefore() {
        deleteTestUsers();
    }

    @AfterAll
    void cleanupAfter() {
        deleteTestUsers();
    }

    /** 每个用例从"该用户名不存在"开始 —— JUnit 不保证执行顺序(本项目已踩过四次)。 */
    @org.junit.jupiter.api.BeforeEach
    void resetUsers() {
        deleteTestUsers();
    }

    // ---------------------------------------------------------------
    // 注册
    // ---------------------------------------------------------------

    @Test
    @DisplayName("注册成功:下发两个 Cookie,响应体只有 UserInfo,令牌不进响应体")
    void registerIssuesCookiesWithoutLeakingToken() throws Exception {
        MvcResult result = register(USERNAME, PASSWORD);

        assertEquals(200, result.getResponse().getStatus());
        String body = result.getResponse().getContentAsString();

        assertTrue(body.contains("\"code\":0"), body);
        assertTrue(body.contains("\"username\":\"" + USERNAME + "\""), body);
        assertFalse(body.contains("passwordHash"), "响应体绝不能带密码哈希: " + body);

        String access = cookieOf(result, "access_token");
        String refresh = cookieOf(result, "refresh_token");
        assertNotNull(access, "未下发 access_token: " + setCookiesOf(result));
        assertNotNull(refresh, "未下发 refresh_token: " + setCookiesOf(result));

        // 令牌不进响应体(前端零存储)
        assertFalse(body.contains(access), "access 令牌出现在响应体里了: " + body);
    }

    @Test
    @DisplayName("refresh Cookie 的 Path 必须收窄至 /api/v1/auth")
    void refreshCookiePathIsNarrowed() throws Exception {
        MvcResult result = register(USERNAME, PASSWORD);

        String refreshHeader = setCookiesOf(result).stream()
                .filter(header -> header.startsWith("refresh_token="))
                .findFirst().orElseThrow();
        assertTrue(refreshHeader.contains("Path=/api/v1/auth"), refreshHeader);
        assertTrue(refreshHeader.contains("HttpOnly"), refreshHeader);
    }

    @Test
    @DisplayName("用户名重复 → 20003")
    void duplicateUsernameIsRejected() throws Exception {
        register(USERNAME, PASSWORD);

        MvcResult again = register(USERNAME, PASSWORD);
        assertEquals(200, again.getResponse().getStatus(), "业务失败仍是 HTTP 200");
        assertEquals(20003, codeOf(again));
    }

    @Test
    @DisplayName("密码长度不合规 → 20004(不是 10001)")
    void passwordFormatIsRejected() throws Exception {
        assertEquals(20004, codeOf(register(USERNAME, "12345")), "短于 6 位");
        assertEquals(20004, codeOf(register(USERNAME, "x".repeat(21))), "长于 20 位");

        assertEquals(null, findUser(USERNAME), "密码不合规时不得创建账号");
    }

    @Test
    @DisplayName("缺参数 → 10001(这是参数校验,与业务规则的 20004 不同)")
    void missingFieldIsParamError() throws Exception {
        MvcResult result = mvc.perform(post(BASE + "/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"secret-123\"}"))
                .andReturn();

        assertEquals(10001, codeOf(result));
        assertTrue(result.getResponse().getContentAsString().contains("\"field\":\"username\""),
                result.getResponse().getContentAsString());
    }

    // ---------------------------------------------------------------
    // 登录
    // ---------------------------------------------------------------

    @Test
    @DisplayName("登录成功:下发 Cookie,且该 Cookie 能访问受保护接口")
    void loginIssuesUsableSession() throws Exception {
        register(USERNAME, PASSWORD);

        MvcResult login = login(USERNAME, PASSWORD);
        assertEquals(200, login.getResponse().getStatus());
        assertTrue(login.getResponse().getContentAsString().contains("\"username\":\"" + USERNAME + "\""),
                login.getResponse().getContentAsString());

        String access = cookieOf(login, "access_token");
        assertNotNull(access);

        // 完整链路:拿登录下发的 Cookie 去调受保护接口
        MvcResult protectedResult = mvc.perform(get(PROTECTED_C)
                .cookie(new Cookie("access_token", access))).andReturn();
        assertEquals(200, protectedResult.getResponse().getStatus(),
                "登录下发的 Cookie 应能访问受保护接口: "
                        + protectedResult.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("密码错误 → 20005")
    void wrongPasswordIsRejected() throws Exception {
        register(USERNAME, PASSWORD);

        MvcResult result = login(USERNAME, "wrong-password");
        assertEquals(200, result.getResponse().getStatus());
        assertEquals(20005, codeOf(result));
        assertTrue(setCookiesOf(result).isEmpty(), "登录失败绝不能下发任何 Cookie");
    }

    @Test
    @DisplayName("判据:用户名不存在 → 与密码错误**完全相同的响应体**(除 traceId)")
    void unknownUsernameIsIndistinguishable() throws Exception {
        register(USERNAME, PASSWORD);

        MvcResult wrongPassword = login(USERNAME, "wrong-password");
        MvcResult unknownUser = login("t11_no_such_user", PASSWORD);

        assertEquals(codeOf(wrongPassword), codeOf(unknownUser));
        assertEquals(stripTraceId(wrongPassword), stripTraceId(unknownUser),
                "两种失败的响应体必须一致 —— 否则可被用来批量枚举哪些用户名已注册");
    }

    @Test
    @DisplayName("账号已停用 → 同样是 20005,不暴露「账号存在但被停用」")
    void disabledUserIsIndistinguishable() throws Exception {
        register(USER_DISABLED, PASSWORD);
        User user = findUser(USER_DISABLED);
        user.setStatus(User.STATUS_DISABLED);
        userMapper.updateById(user);

        assertEquals(20005, codeOf(login(USER_DISABLED, PASSWORD)));
    }

    // ---------------------------------------------------------------
    // 取当前账号(会话恢复)
    // ---------------------------------------------------------------

    @Test
    @DisplayName("GET /auth/me:返回当前账号,响应体不含密码哈希")
    void meReturnsCurrentAccount() throws Exception {
        register(USERNAME, PASSWORD);
        String access = cookieOf(login(USERNAME, PASSWORD), "access_token");

        MvcResult result = mvc.perform(get(BASE + "/me")
                .cookie(new Cookie("access_token", access))).andReturn();

        assertEquals(200, result.getResponse().getStatus());
        String body = result.getResponse().getContentAsString();
        assertEquals(0, codeOf(result), body);
        assertTrue(body.contains("\"username\":\"" + USERNAME + "\""), body);
        assertFalse(body.contains("passwordHash"), "响应体绝不能带密码哈希: " + body);
    }

    @Test
    @DisplayName("GET /auth/me:未登录 → 401(该接口必须在受保护清单里)")
    void meRequiresLogin() throws Exception {
        // 这一条同时守着 SecurityConfig.PROTECTED_C_PATHS —— 漏登记该路径的话,
        // 请求会落到 anyRequest().permitAll(),变成匿名可调,而这里是 401
        assertEquals(401, mvc.perform(get(BASE + "/me")).andReturn().getResponse().getStatus());
    }

    @Test
    @DisplayName("GET /auth/me:令牌签发后账号被停用 → 10002(会话失效,不是 30001)")
    void meRejectsAccountDisabledAfterTokenIssued() throws Exception {
        register(USERNAME, PASSWORD);
        String access = cookieOf(login(USERNAME, PASSWORD), "access_token");
        User user = findUser(USERNAME);

        user.setStatus(User.STATUS_DISABLED);
        userMapper.updateById(user);
        try {
            MvcResult result = mvc.perform(get(BASE + "/me")
                    .cookie(new Cookie("access_token", access))).andReturn();

            // 令牌签名仍有效(2 小时内),但账号已停用 —— 必须按"无此人"处理,不能放行
            assertEquals(10002, codeOf(result), result.getResponse().getContentAsString());
        } finally {
            user.setStatus(User.STATUS_ENABLED);
            userMapper.updateById(user);
        }
    }

    // ---------------------------------------------------------------
    // 修改密码 / 登出 / 刷新
    // ---------------------------------------------------------------

    @Test
    @DisplayName("改密:原密码错 → 20005;成功后新密码可登录、旧密码不可")
    void changePasswordFlow() throws Exception {
        register(USERNAME, PASSWORD);
        String access = cookieOf(login(USERNAME, PASSWORD), "access_token");

        // 原密码错
        MvcResult wrong = mvc.perform(post(BASE + "/password")
                        .cookie(new Cookie("access_token", access))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(changePasswordJson("not-my-password", "new-secret-1")))
                .andReturn();
        assertEquals(20005, codeOf(wrong));

        // 新密码格式不合规
        MvcResult badFormat = mvc.perform(post(BASE + "/password")
                        .cookie(new Cookie("access_token", access))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(changePasswordJson(PASSWORD, "123")))
                .andReturn();
        assertEquals(20004, codeOf(badFormat));

        // 正常修改
        MvcResult ok = mvc.perform(post(BASE + "/password")
                        .cookie(new Cookie("access_token", access))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(changePasswordJson(PASSWORD, "new-secret-1")))
                .andReturn();
        assertEquals(0, codeOf(ok), ok.getResponse().getContentAsString());

        assertEquals(0, codeOf(login(USERNAME, "new-secret-1")), "新密码应能登录");
        assertEquals(20005, codeOf(login(USERNAME, PASSWORD)), "旧密码应失效");
    }

    @Test
    @DisplayName("未登录调改密接口 → 401(该接口在受保护清单里)")
    void changePasswordRequiresLogin() throws Exception {
        MvcResult result = mvc.perform(post(BASE + "/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(changePasswordJson("a", "b")))
                .andReturn();

        assertEquals(401, result.getResponse().getStatus());
    }

    @Test
    @DisplayName("登出后:refresh 令牌立即失效,且两个 Cookie 被清")
    void logoutInvalidatesRefreshToken() throws Exception {
        register(USERNAME, PASSWORD);
        MvcResult login = login(USERNAME, PASSWORD);
        String access = cookieOf(login, "access_token");
        String refresh = cookieOf(login, "refresh_token");

        // 登出前 refresh 可用
        assertEquals(200, refreshWith(refresh).getResponse().getStatus());

        MvcResult logout = mvc.perform(post(BASE + "/logout")
                        .cookie(new Cookie("access_token", access), new Cookie("refresh_token", refresh)))
                .andReturn();
        assertEquals(200, logout.getResponse().getStatus());

        List<String> cleared = setCookiesOf(logout);
        assertTrue(cleared.stream().anyMatch(h -> h.startsWith("access_token=") && h.contains("Max-Age=0")),
                "登出应清 access Cookie: " + cleared);
        assertTrue(cleared.stream().anyMatch(h -> h.startsWith("refresh_token=") && h.contains("Max-Age=0")),
                "登出应清 refresh Cookie: " + cleared);

        // 登出后 refresh 必须失效 —— 否则「登出」等于没做
        assertEquals(401, refreshWith(refresh).getResponse().getStatus(),
                "登出后 refresh 令牌仍可用 —— 登出等于没做");
    }

    @Test
    @DisplayName("刷新:无 refresh Cookie → 401;有效 → 下发新的 access Cookie")
    void refreshFlow() throws Exception {
        assertEquals(401, mvc.perform(post(BASE + "/refresh")).andReturn().getResponse().getStatus(),
                "没有 refresh 令牌属「未登录」,应为 401");

        register(USERNAME, PASSWORD);
        String refresh = cookieOf(login(USERNAME, PASSWORD), "refresh_token");

        MvcResult refreshed = refreshWith(refresh);
        assertEquals(200, refreshed.getResponse().getStatus());
        String newAccess = cookieOf(refreshed, "access_token");
        assertNotNull(newAccess, "刷新应下发新的 access_token: " + setCookiesOf(refreshed));

        // 新 access 能用
        assertEquals(200, mvc.perform(get(PROTECTED_C)
                .cookie(new Cookie("access_token", newAccess))).andReturn().getResponse().getStatus());
    }

    @Test
    @DisplayName("登出不因 refresh 令牌缺失或无效而失败(但该接口本身仍需登录态)")
    void logoutToleratesBadRefreshToken() throws Exception {
        register(USERNAME, PASSWORD);
        String access = cookieOf(login(USERNAME, PASSWORD), "access_token");

        // 没有 refresh 令牌 —— 可能是已登出过一次,或 Cookie 被手动清过;照样清 Cookie 并返回成功
        assertEquals(200, mvc.perform(post(BASE + "/logout")
                        .cookie(new Cookie("access_token", access)))
                .andReturn().getResponse().getStatus());

        // refresh 令牌是个无效字符串 —— 解析失败也不该让登出报错
        assertEquals(200, mvc.perform(post(BASE + "/logout")
                        .cookie(new Cookie("access_token", access),
                                new Cookie("refresh_token", "完全无效的字符串")))
                .andReturn().getResponse().getStatus());
    }

    @Test
    @DisplayName("未登录调登出 → 401(契约把它标为 cookieAuth,不是公开接口)")
    void logoutRequiresLogin() throws Exception {
        assertEquals(401, mvc.perform(post(BASE + "/logout")).andReturn().getResponse().getStatus());
    }

    @Test
    @DisplayName("登录会记录 last_login_at(§8.1 的字段)")
    void loginRecordsTimestamp() throws Exception {
        register(USERNAME, PASSWORD);
        assertEquals(null, findUser(USERNAME).getLastLoginAt(), "刚注册时还没登录过");

        login(USERNAME, PASSWORD);
        assertNotNull(findUser(USERNAME).getLastLoginAt(), "登录后应记录最近登录时间");
    }

    // ---------------------------------------------------------------

    private MvcResult register(String username, String password) throws Exception {
        return mvc.perform(post(BASE + "/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(credentialsJson(username, password))).andReturn();
    }

    private MvcResult login(String username, String password) throws Exception {
        return mvc.perform(post(BASE + "/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(credentialsJson(username, password))).andReturn();
    }

    private MvcResult refreshWith(String refreshToken) throws Exception {
        return mvc.perform(post(BASE + "/refresh")
                .cookie(new Cookie("refresh_token", refreshToken))).andReturn();
    }

    private String credentialsJson(String username, String password) throws Exception {
        var node = objectMapper.createObjectNode();
        node.put("username", username);
        node.put("password", password);
        return objectMapper.writeValueAsString(node);
    }

    private String changePasswordJson(String oldPassword, String newPassword) throws Exception {
        var node = objectMapper.createObjectNode();
        node.put("oldPassword", oldPassword);
        node.put("newPassword", newPassword);
        return objectMapper.writeValueAsString(node);
    }

    private static List<String> setCookiesOf(MvcResult result) {
        return result.getResponse().getHeaders("Set-Cookie");
    }

    /** 从 Set-Cookie 头里取出某个 Cookie 的值。 */
    private static String cookieOf(MvcResult result, String name) {
        return setCookiesOf(result).stream()
                .filter(header -> header.startsWith(name + "="))
                .map(header -> header.substring(name.length() + 1, header.indexOf(';')))
                .findFirst().orElse(null);
    }

    /** 去掉 traceId 后比较响应体 —— 它是每次请求都不同的。 */
    private static String stripTraceId(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString()
                .replaceAll("\"traceId\":\"[^\"]+\"", "\"traceId\":\"<x>\"");
    }

    private User findUser(String username) {
        return userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername, username));
    }

    private void deleteTestUsers() {
        userMapper.delete(Wrappers.<User>lambdaQuery()
                .in(User::getUsername, List.of(USERNAME, USER_DISABLED, "t11_no_such_user")));
    }

    private static int codeOf(MvcResult result) throws Exception {
        return new ObjectMapper().readTree(result.getResponse().getContentAsString())
                .get("code").asInt();
    }
}
