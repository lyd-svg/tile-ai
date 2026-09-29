package com.tileai.common.security;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 构造鉴权 Cookie(架构 §10.1 · D1 定稿)。
 *
 * <p>这里是"令牌如何落到浏览器"的<b>唯一</b>出口,几条硬约束集中在此,
 * 不要在别处手搓 Set-Cookie:
 * <ul>
 *   <li><b>HttpOnly</b> —— 前端 JS 读不到,杜绝 XSS 直接窃取令牌;</li>
 *   <li><b>refresh 的 Path 收窄</b>到各自的 auth 前缀 —— 刷新令牌只在刷新/登出
 *       时随请求发出,不会出现在每次业务请求里,暴露面小一个数量级;</li>
 *   <li><b>SameSite</b> —— 见 {@link JwtProperties#getSameSite()} 的说明(上游未定义);</li>
 *   <li><b>Secure</b> —— HTTPS 下必须开,否则 Cookie 会在明文信道上发送。</li>
 * </ul>
 *
 * <p>令牌经 {@code Set-Cookie} 下发,<b>绝不进响应体</b>(前端零存储)。
 *
 * <p><b>C 端与 B 端各一套 Cookie</b>(架构 §10.1,缺口 G-9):名字与路径都不同,
 * 所以每个方法都要知道<b>是给哪个主体的</b>。别绕过 subject 参数写死一个名字 ——
 * 那正是两端互相覆盖的成因。
 */
@Component
public class AuthCookies {

    private final JwtProperties props;

    public AuthCookies(JwtProperties props) {
        this.props = props;
    }

    public ResponseCookie access(String token, SubjectType subject) {
        return base(accessName(subject), token, accessPath(subject))
                .maxAge(props.getAccessTtl())
                .build();
    }

    public ResponseCookie refresh(String token, SubjectType subject) {
        return base(refreshName(subject), token, refreshPath(subject))
                .maxAge(props.getRefreshTtl())
                .build();
    }

    /**
     * 清除 access Cookie。
     * <p>Path 必须与下发时一致,否则浏览器视为不同的 Cookie,删不掉旧的那条。
     */
    public ResponseCookie clearAccess(SubjectType subject) {
        return base(accessName(subject), "", accessPath(subject))
                .maxAge(0)
                .build();
    }

    /** 清除 refresh Cookie。同样必须与下发时的 Path 一致。 */
    public ResponseCookie clearRefresh(SubjectType subject) {
        return base(refreshName(subject), "", refreshPath(subject))
                .maxAge(0)
                .build();
    }

    /**
     * 从请求里取 refresh 令牌,没有则返回 {@code null}。
     *
     * <p>不用 {@code @CookieValue(name = "${...}")} 读:注解属性是否解析配置占位符
     * 取决于所用处理器的实现,不可靠。这里显式按配置的 Cookie 名遍历,
     * 与 {@link JwtAuthenticationFilter} 读 access 令牌的方式保持一致。
     *
     * <p>放在本类是因为<b>只有这里知道 Cookie 的名字</b>。C 端与 B 端两个控制器都要读它,
     * 各写一份就有两份实现需要同步 —— 本项目在 {@code securitySchemes} 重复定义上
     * 已经吃过一次这个亏(F-4)。
     */
    public String refreshTokenOf(HttpServletRequest request, SubjectType subject) {
        return cookieValue(request, refreshName(subject));
    }

    /** access 令牌的 Cookie 名。供过滤器按请求路径选用。 */
    public String accessNameOf(SubjectType subject) {
        return accessName(subject);
    }

    // ------------------------------------------------------------------

    private String accessName(SubjectType subject) {
        return subject == SubjectType.ADMIN
                ? props.getAdminAccessCookieName()
                : props.getAccessCookieName();
    }

    private String accessPath(SubjectType subject) {
        return subject == SubjectType.ADMIN
                ? props.getAdminAccessCookiePath()
                : props.getAccessCookiePath();
    }

    private String refreshName(SubjectType subject) {
        return subject == SubjectType.ADMIN
                ? props.getAdminRefreshCookieName()
                : props.getRefreshCookieName();
    }

    private String refreshPath(SubjectType subject) {
        return subject == SubjectType.ADMIN
                ? props.getAdminRefreshCookiePath()
                : props.getRefreshCookiePath();
    }

    private static String cookieValue(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private ResponseCookie.ResponseCookieBuilder base(String name, String value, String path) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(props.isSecure())
                .path(path)
                .sameSite(props.getSameSite());
    }
}
