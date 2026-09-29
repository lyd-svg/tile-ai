package com.tileai.common.security;

import java.io.IOException;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import com.tileai.common.error.BusinessException;
import com.tileai.common.error.ErrorCode;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 从请求中识别登录态(架构 §10.1 · D1 定稿)。
 *
 * <p><b>令牌来源(这条最容易被后来者改错)</b>:
 * <ul>
 *   <li>access 令牌<b>只从 Cookie</b> {@code access_token} 读。
 *       <b>不读 {@code Authorization} 头</b> —— 契约明确"不使用 Authorization 头";</li>
 *   <li>v0.7 起**没有任何例外** —— 原注册临时令牌 {@code tempToken} 随短信验证码方案一并取消
 *       (架构 §15.6),这条 Bearer 通道已删除;</li>
 *   <li>因此把令牌放进 {@code Authorization} 头<b>不会被接受</b> ——
 *       这是有意为之,并有测试锁定该行为。</li>
 * </ul>
 *
 * <p>校验失败时<b>不在这里写响应</b>,只把错误码放进请求属性,交给
 * {@link RestAuthenticationEntryPoint} 统一出口。否则"未登录"会出现两条
 * 不同的响应路径,状态码与响应体迟早会不一致。
 *
 * <p><b>故意不标 {@code @Component}</b>:标了的话 Spring Boot 会把它自动注册成一个
 * servlet 过滤器,而 {@link SecurityConfig} 又用 {@code addFilterBefore} 把它加进
 * 安全链 —— 同一个过滤器会跑两遍。改由 {@code SecurityConfig} 用 {@code @Bean} 注册。
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** 请求属性名:认证失败时的业务错误码,供 entry point 区分 10002 / 10003。 */
    static final String AUTH_ERROR_ATTR = "tile.auth.errorCode";

    private final JwtService jwtService;
    private final JwtProperties props;

    public JwtAuthenticationFilter(JwtService jwtService, JwtProperties props) {
        this.jwtService = jwtService;
        this.props = props;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        try {
            String access = cookieValue(request, props.getAccessCookieName());
            if (StringUtils.hasText(access)) {
                authenticateAccess(access);
            }
        } catch (BusinessException e) {
            SecurityContextHolder.clearContext();
            request.setAttribute(AUTH_ERROR_ATTR, e.errorCode());
        }
        chain.doFilter(request, response);
    }

    private void authenticateAccess(String token) {
        JwtService.ParsedToken parsed = jwtService.parse(token);
        // 必须是 ACCESS —— refresh 令牌有 14 天寿命,若能当 access 用就绕过了 2 小时的隔离。
        if (parsed.tokenType() != TokenType.ACCESS) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED, "令牌类型不正确");
        }
        var authentication = new UsernamePasswordAuthenticationToken(
                parsed.toPrincipal(), null,
                List.of(new SimpleGrantedAuthority(
                        parsed.subjectType() == SubjectType.ADMIN ? "ROLE_ADMIN" : "ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private static String cookieValue(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie c : cookies) {
            if (name.equals(c.getName())) {
                return c.getValue();
            }
        }
        return null;
    }
}
