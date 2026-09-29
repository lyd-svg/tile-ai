package com.tileai.common.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import jakarta.servlet.http.Cookie;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * T-0.4:安全过滤链的行为契约。
 *
 * <p>用完整上下文({@code @SpringBootTest})而非切片测试,是因为要验的正是
 * <b>真实的过滤器装配</b> —— {@code SecurityConfig} 的路径规则、JwtAuthenticationFilter
 * 的令牌来源、401/403 的出口。切片测试会把其中最关键的装配部分换成替身。
 *
 * <p>关掉 Flyway;数据源与 Redis 都是懒连接,所以本测试<b>不需要 MySQL / Redis</b>。
 */
@SpringBootTest(properties = {
        "spring.flyway.enabled=false",
        "tile.auth.secret=0123456789abcdef0123456789abcdef"
})
@AutoConfigureMockMvc
class SecurityFilterContractTest {

    private static final String PROTECTED_C = "/api/v1/plans/probe";
    private static final String ADMIN = "/api/v1/admin/probe";
    private static final String PUBLIC_C = "/api/v1/products/probe";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtService jwtService;

    // ---------------------------------------------------------------
    // 未登录
    // ---------------------------------------------------------------

    @Test
    @DisplayName("未带 Cookie 访问受保护接口 → 真实 401,响应体仍是统一格式")
    void anonymousIsRejectedWith401() throws Exception {
        MvcResult result = mvc.perform(get(PROTECTED_C)).andReturn();

        assertEquals(401, result.getResponse().getStatus());
        String body = result.getResponse().getContentAsString();
        assertTrue(body.contains("\"code\":10002"), body);
        assertTrue(body.contains("\"traceId\":\""), "401 也必须带 traceId: " + body);
    }

    @Test
    @DisplayName("浏览类接口免登录(PRD §5.1)")
    void publicEndpointNeedsNoLogin() throws Exception {
        assertEquals(200, mvc.perform(get(PUBLIC_C)).andReturn().getResponse().getStatus());
    }

    // ---------------------------------------------------------------
    // 令牌来源:只用 Cookie
    // ---------------------------------------------------------------

    @Test
    @DisplayName("有效 access Cookie → 放行")
    void validAccessCookieIsAccepted() throws Exception {
        String token = jwtService.issueAccess(1L, SubjectType.USER);
        MvcResult result = mvc.perform(get(PROTECTED_C).cookie(new Cookie("access_token", token)))
                .andReturn();
        assertEquals(200, result.getResponse().getStatus(),
                result.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("把 access 令牌放进 Authorization 头 → 拒绝(契约:不使用 Authorization 头)")
    void accessTokenInAuthorizationHeaderIsRejected() throws Exception {
        String token = jwtService.issueAccess(1L, SubjectType.USER);
        MvcResult result = mvc.perform(get(PROTECTED_C).header("Authorization", "Bearer " + token))
                .andReturn();

        assertEquals(401, result.getResponse().getStatus(),
                "契约 §10.1:不使用 Authorization 头;access 令牌走这里必须被拒: "
                        + result.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("refresh 令牌当 access 用 → 拒绝(类型隔离,否则 2 小时寿命形同虚设)")
    void refreshTokenCannotBeUsedAsAccess() throws Exception {
        String refresh = jwtService.issueRefresh(1L, SubjectType.USER);
        MvcResult result = mvc.perform(get(PROTECTED_C).cookie(new Cookie("access_token", refresh)))
                .andReturn();

        assertEquals(401, result.getResponse().getStatus(),
                "14 天寿命的 refresh 令牌绝不能当 access 用: "
                        + result.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("篡改过的令牌 → 401")
    void tamperedTokenIsRejected() throws Exception {
        String token = jwtService.issueAccess(1L, SubjectType.USER);
        String tampered = token.substring(0, token.length() - 3) + "xyz";

        MvcResult result = mvc.perform(get(PROTECTED_C).cookie(new Cookie("access_token", tampered)))
                .andReturn();
        assertEquals(401, result.getResponse().getStatus());
    }

    // ---------------------------------------------------------------
    // 主体隔离:C 端 / B 端
    // ---------------------------------------------------------------

    @Test
    @DisplayName("业主令牌访问后台接口 → 403")
    void userTokenCannotReachAdmin() throws Exception {
        String token = jwtService.issueAccess(1L, SubjectType.USER);
        // 业主令牌放进**后台**的 Cookie 名里 —— 这样过滤器才读得到它,
        // 再由 hasAuthority("ROLE_ADMIN") 挡下 → 403。
        // (若放进 C 端的 access_token,后台路径不读那条 Cookie,只会得到 401,
        //  那样测的是"Cookie 没被读",不是"ID 空间隔离"。)
        MvcResult result = mvc.perform(get(ADMIN)
                        .cookie(new Cookie("admin_access_token", token)))
                .andReturn();

        assertEquals(403, result.getResponse().getStatus(),
                "两套 ID 空间必须隔离,否则管理员 id 会撞上业主 id: "
                        + result.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("后台令牌访问后台接口 → 放行")
    void adminTokenReachesAdmin() throws Exception {
        String token = jwtService.issueAccess(1L, SubjectType.ADMIN);
        MvcResult result = mvc.perform(get(ADMIN)
                        .cookie(new Cookie("admin_access_token", token)))
                .andReturn();

        assertEquals(200, result.getResponse().getStatus(),
                result.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("403 响应体同样是统一格式(业务码 10004),不是 Spring 默认错误页")
    void forbiddenResponseUsesEnvelope() throws Exception {
        String token = jwtService.issueAccess(1L, SubjectType.USER);
        MvcResult result = mvc.perform(get(ADMIN)
                        .cookie(new Cookie("admin_access_token", token)))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertTrue(body.contains("\"code\":10004"), body);
        assertTrue(body.contains("\"traceId\":\""), body);
    }
}
