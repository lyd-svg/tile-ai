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
    @DisplayName("C 端 access Cookie:HttpOnly、Path=/、Max-Age=2 小时(契约值)")
    void accessCookieAttributes() {
        String header = cookies(false).access("TOKEN", SubjectType.USER).toString();

        assertTrue(header.startsWith("access_token=TOKEN"), header);
        assertTrue(header.contains("HttpOnly"), header);
        assertTrue(header.contains("Path=/"), header);
        assertTrue(header.contains("Max-Age=7200"), "access 有效期应为 2 小时: " + header);
    }

    @Test
    @DisplayName("C 端 refresh Cookie:HttpOnly、Path 收窄至 /api/v1/auth、Max-Age=14 天")
    void refreshCookieNarrowsPath() {
        String header = cookies(false).refresh("RTOKEN", SubjectType.USER).toString();

        assertTrue(header.startsWith("refresh_token=RTOKEN"), header);
        assertTrue(header.contains("HttpOnly"), header);
        assertTrue(header.contains("Path=/api/v1/auth"),
                "refresh_token 的 Path 必须收窄 —— 这是契约的明确要求: " + header);
        assertTrue(header.contains("Max-Age=1209600"), "refresh 有效期应为 14 天: " + header);
    }

    @Test
    @DisplayName("两个 Cookie 的 Path 必须不同 —— 否则收窄就白做了")
    void accessAndRefreshPathsDiffer() {
        AuthCookies c = cookies(false);

        assertEquals("/", c.access("T", SubjectType.USER).getPath());
        assertEquals("/api/v1/auth", c.refresh("T", SubjectType.USER).getPath());
    }

    // ------------------------------------------------------------------
    // 后台会话:名字与路径都必须与 C 端分开(架构 §10.1,缺口 G-9)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("后台 Cookie 用完全不同的名字 —— 同名会互相覆盖")
    void adminCookiesUseDistinctNames() {
        AuthCookies c = cookies(false);

        assertTrue(c.access("T", SubjectType.ADMIN).toString().startsWith("admin_access_token=T"),
                c.access("T", SubjectType.ADMIN).toString());
        assertTrue(c.refresh("R", SubjectType.ADMIN).toString().startsWith("admin_refresh_token=R"),
                c.refresh("R", SubjectType.ADMIN).toString());
    }

    @Test
    @DisplayName("后台 refresh 的 Path 必须覆盖登录端点自身 —— 否则浏览器拒存")
    void adminRefreshPathCoversLoginEndpoint() {
        String path = cookies(false).refresh("R", SubjectType.ADMIN).getPath();

        assertEquals("/api/v1/admin/auth", path);
        // 这一条是本次修复的核心:浏览器只在路径匹配时才存储 Cookie,
        // 覆盖不到登录路径 => 下发的 refresh 被直接丢弃 => 后台永远无法刷新。
        assertTrue("/api/v1/admin/auth/login".startsWith(path),
                "后台 refresh 的 Path 必须覆盖 /api/v1/admin/auth/login,否则 Cookie 存不下来: " + path);
    }

    @Test
    @DisplayName("两端四张 Cookie 的名字与路径两两不同 —— 这是「可并存」的充要条件")
    void userAndAdminCookiesNeverCollide() {
        AuthCookies c = cookies(false);

        String[][] combos = {
                {c.access("T", SubjectType.USER).getName(), c.access("T", SubjectType.USER).getPath()},
                {c.refresh("T", SubjectType.USER).getName(), c.refresh("T", SubjectType.USER).getPath()},
                {c.access("T", SubjectType.ADMIN).getName(), c.access("T", SubjectType.ADMIN).getPath()},
                {c.refresh("T", SubjectType.ADMIN).getName(), c.refresh("T", SubjectType.ADMIN).getPath()},
        };

        for (int i = 0; i < combos.length; i++) {
            for (int j = i + 1; j < combos.length; j++) {
                boolean same = combos[i][0].equals(combos[j][0]) && combos[i][1].equals(combos[j][1]);
                assertTrue(!same, "Cookie " + combos[i][0] + "(" + combos[i][1] + ") 与 "
                        + combos[j][0] + "(" + combos[j][1] + ") 会互相覆盖");
            }
        }
    }

    @Test
    @DisplayName("Secure 由配置控制:本地 http 关、HTTPS 开")
    void secureFollowsConfig() {
        assertTrue(cookies(true).access("T", SubjectType.USER).isSecure());
        assertTrue(!cookies(false).access("T", SubjectType.USER).isSecure());
    }

    @Test
    @DisplayName("SameSite 默认 Lax(架构未规定,取值见 JwtProperties 说明)")
    void sameSiteDefaultsToLax() {
        assertEquals("Lax", cookies(false).access("T", SubjectType.USER).getSameSite());
    }

    @Test
    @DisplayName("清除 Cookie 时 Max-Age=0,且 Path 与下发时一致(否则删不掉)")
    void clearCookiesMatchIssuedPaths() {
        AuthCookies c = cookies(false);

        for (SubjectType subject : SubjectType.values()) {
            ResponseCookie clearedAccess = c.clearAccess(subject);
            assertEquals(0, clearedAccess.getMaxAge().getSeconds());
            assertEquals(c.access("T", subject).getPath(), clearedAccess.getPath(),
                    "清除 access 的 Path 必须与下发时一致(" + subject + ")");

            ResponseCookie clearedRefresh = c.clearRefresh(subject);
            assertEquals(0, clearedRefresh.getMaxAge().getSeconds());
            assertEquals(c.refresh("T", subject).getPath(), clearedRefresh.getPath(),
                    "清除 refresh 的 Path 必须与下发时一致 —— 这是最容易写错的一处(" + subject + ")");
        }
    }

    @Test
    @DisplayName("改动配置后 Cookie 属性随之变化(不是把契约值写死)")
    void attributesFollowConfiguration() {
        JwtProperties p = new JwtProperties();
        p.setAccessTtl(Duration.ofMinutes(30));
        p.setRefreshCookiePath("/custom/auth");
        AuthCookies c = new AuthCookies(p);

        assertTrue(c.access("T", SubjectType.USER).toString().contains("Max-Age=1800"),
                c.access("T", SubjectType.USER).toString());
        assertEquals("/custom/auth", c.refresh("T", SubjectType.USER).getPath());
    }
}
