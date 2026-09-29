package com.tileai.common.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tileai.common.api.ApiResponse;
import com.tileai.common.error.ErrorCode;
import com.tileai.common.error.GlobalExceptionHandler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 未认证 → 真实 HTTP 401,响应体仍是契约的统一格式。
 *
 * <p><b>为什么需要它</b>:Spring Security 的异常发生在过滤链里,
 * <b>早于</b> {@link GlobalExceptionHandler}(那是给 controller 层用的),
 * 所以必须在这里自己写响应。否则未登录会返回 Spring 默认的 HTML 错误页,
 * 与契约 {@code openapi-common.yaml#/components/responses/Unauthorized}
 * (401 + ApiResponse)不符。
 *
 * <p>状态码 401 是契约 T-13 明确允许的两个真实状态码之一。
 *
 * <p>与 {@link JwtAuthenticationFilter} 同理,不标 {@code @Component},
 * 由 {@link SecurityConfig} 统一用 {@code @Bean} 注册。
 */
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        // 由 JwtAuthenticationFilter 区分的 10002 / 10003;没标记时按"未登录"处理。
        Object attr = request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTR);
        ErrorCode code = (attr instanceof ErrorCode ec) ? ec : ErrorCode.UNAUTHENTICATED;

        response.setStatus(code.httpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        // traceId 由 TraceIdFilter 在更外层写入,此处仍可取到。
        objectMapper.writeValue(response.getWriter(), ApiResponse.fail(code));
    }
}
