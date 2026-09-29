package com.tileai.common.security;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T-0.4:鉴权 Cookie 的属性必须与契约一致。
 *
 * <p>这些属性<b>不是样式偏好,而是安全边界</b>,任何一条错了都有具体后果:
 * <ul>
 *   <li>{@code HttpOnly} 丢了 → XSS 可直接读走令牌;</li>
 *   <li>refresh 的 {@code Path} 放宽到 {@code /} → 刷新令牌会在每次业务请求里裸奔,
 *       暴露面从"只走 /auth"扩大到"全站";</li>
 *   <li>{@code Secure} 在 HTTPS 下没开 → Cookie 走明文信道;</li>
 *   <li>{@code Max-Age} 与契约不符 → 令牌寿命与预期不一致。</li>
 * </ul>
 */
class AuthCookiesTest {

    private static AuthCookies cookies(boolean secure) {
        JwtProperties p = new JwtProperties();
        p.setSecure(secure);
        return new AuthCookies(p);
    }

    @Test
    @DisplayName("access Cookie:HttpOnly、Path=/、Max-Age=2 小时(契约值)")
    void accessCookieAttributes() {
        String header = cookies(false).access("TOKEN").toString();

        assertTrue(header.startsWith("access_token=TOKEN"), header);
        assertTrue(header.contains("HttpOnly"), header);
        assertTrue(header.contains("Path=/"), header);
        assertTrue(header.contains("Max-Age=7200"), "access 有效期应为 2 小时: " + header);
    }

    @Test
    @DisplayName("refresh Cookie:HttpOnly、Path 收窄至 /api/v1/auth、Max-Age=14 天(契约值)")
    void refreshCookieNarrowsPath() {
        String header = cookies(false).refresh("RTOKEN").toString();

        assertTrue(header.startsWith("refresh_token=RTOKEN"), header);
        assertTrue(header.contains("HttpOnly"), header);
        assertTrue(header.contains("Path=/api/v1/auth"),
                "refresh_token 的 Path 必须收窄 —— 这是契约的明确要求: " + header);
        assertTrue(header.contains("Max-Age=1209600"), "refresh 有效期应为 14 天: " + header);
    }

    @Test
    @DisplayName("两个 Cookie 的 Path 必须不同 —— 否则收窄就白做了")
    void accessAndRefreshPathsDiffer() {
        ResponseCookie access = cookies(false).access("T");
        ResponseCookie refresh = cookies(false).refresh("T");

        assertEquals("/", access.getPath());
        assertEquals("/api/v1/auth", refresh.getPath());
    }

    @Test
    @DisplayName("Secure 由配置控制:本地 http 关、HTTPS 开")
    void secureFollowsConfig() {
        assertTrue(cookies(true).access("T").isSecure());
        assertTrue(!cookies(false).access("T").isSecure());
    }

    @Test
    @DisplayName("SameSite 默认 Lax(架构未规定,取值见 JwtProperties 说明)")
    void sameSiteDefaultsToLax() {
        assertEquals("Lax", cookies(false).access("T").getSameSite());
    }

    @Test
    @DisplayName("清除 Cookie 时 Max-Age=0,且 Path 与下发时一致(否则删不掉)")
    void clearCookiesMatchIssuedPaths() {
        AuthCookies c = cookies(false);

        ResponseCookie clearedAccess = c.clearAccess();
        assertEquals(0, clearedAccess.getMaxAge().getSeconds());
        assertEquals(c.access("T").getPath(), clearedAccess.getPath(),
                "清除 access 的 Path 必须与下发时一致");

        ResponseCookie clearedRefresh = c.clearRefresh();
        assertEquals(0, clearedRefresh.getMaxAge().getSeconds());
        assertEquals(c.refresh("T").getPath(), clearedRefresh.getPath(),
                "清除 refresh 的 Path 必须与下发时一致 —— 这是最容易写错的一处");
    }

    @Test
    @DisplayName("改动配置后 Cookie 属性随之变化(不是把契约值写死)")
    void attributesFollowConfiguration() {
        JwtProperties p = new JwtProperties();
        p.setAccessTtl(Duration.ofMinutes(30));
        p.setRefreshCookiePath("/custom/auth");
        AuthCookies c = new AuthCookies(p);

        assertTrue(c.access("T").toString().contains("Max-Age=1800"), c.access("T").toString());
        assertEquals("/custom/auth", c.refresh("T").getPath());
    }
}
