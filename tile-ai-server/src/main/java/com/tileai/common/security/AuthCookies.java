package com.tileai.common.security;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * 构造鉴权 Cookie(架构 §10.1 · D1 定稿)。
 *
 * <p>这里是"令牌如何落到浏览器"的<b>唯一</b>出口,几条硬约束集中在此,
 * 不要在别处手搓 Set-Cookie:
 * <ul>
 *   <li><b>HttpOnly</b> —— 前端 JS 读不到,杜绝 XSS 直接窃取令牌;</li>
 *   <li><b>refresh 的 Path 收窄</b>到 {@code /api/v1/auth} —— 刷新令牌只在刷新/登出
 *       时随请求发出,不会出现在每次业务请求里,暴露面小一个数量级;</li>
 *   <li><b>SameSite</b> —— 见 {@link JwtProperties#getSameSite()} 的说明(上游未定义);</li>
 *   <li><b>Secure</b> —— HTTPS 下必须开,否则 Cookie 会在明文信道上发送。</li>
 * </ul>
 *
 * <p>令牌经 {@code Set-Cookie} 下发,<b>绝不进响应体</b>(前端零存储)。
 */
@Component
public class AuthCookies {

    private final JwtProperties props;

    public AuthCookies(JwtProperties props) {
        this.props = props;
    }

    public ResponseCookie access(String token) {
        return base(props.getAccessCookieName(), token, props.getAccessCookiePath())
                .maxAge(props.getAccessTtl())
                .build();
    }

    public ResponseCookie refresh(String token) {
        return base(props.getRefreshCookieName(), token, props.getRefreshCookiePath())
                .maxAge(props.getRefreshTtl())
                .build();
    }

    /**
     * 清除 access_token。
     * <p>Path 必须与下发时一致,否则浏览器视为不同的 Cookie,删不掉旧的那条。
     */
    public ResponseCookie clearAccess() {
        return base(props.getAccessCookieName(), "", props.getAccessCookiePath())
                .maxAge(0)
                .build();
    }

    /** 清除 refresh_token。同样必须与下发时的 Path 一致。 */
    public ResponseCookie clearRefresh() {
        return base(props.getRefreshCookieName(), "", props.getRefreshCookiePath())
                .maxAge(0)
                .build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String name, String value, String path) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(props.isSecure())
                .path(path)
                .sameSite(props.getSameSite());
    }
}
