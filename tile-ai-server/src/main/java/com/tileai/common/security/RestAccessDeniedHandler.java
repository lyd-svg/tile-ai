package com.tileai.common.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tileai.common.api.ApiResponse;
import com.tileai.common.error.ErrorCode;
import com.tileai.common.error.GlobalExceptionHandler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 已认证但无权限 → 真实 HTTP 403,响应体业务码 10004。
 *
 * <p>与 {@link RestAuthenticationEntryPoint} 同理:发生在过滤链里,绕不过
 * {@link GlobalExceptionHandler},必须自己写响应体,否则会退化成 Spring 默认的
 * 错误页,与契约 {@code Forbidden}(403 + ApiResponse,业务码 10004)不符。
 *
 * <p>与 401 的区别必须守住:<b>没登录</b>是 401/10002,
 * <b>登录了但角色不够</b>是 403/10004。前端据此决定"跳登录页"还是"提示无权限"。
 *
 * <p>不标 {@code @Component},由 {@link SecurityConfig} 统一用 {@code @Bean} 注册。
 */
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public RestAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        ErrorCode code = ErrorCode.FORBIDDEN;
        response.setStatus(code.httpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), ApiResponse.fail(code));
    }
}
