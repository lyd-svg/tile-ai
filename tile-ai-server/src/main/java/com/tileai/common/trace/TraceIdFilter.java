package com.tileai.common.trace;

import java.io.IOException;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 为每个请求注入 traceId(架构 §13.7)。
 *
 * <p>行为:
 * <ol>
 *   <li>若上游带了 {@code X-Trace-Id},<b>原样沿用</b> —— 这样 Java → Python 的内部调用
 *       能串在同一条链路上(§10.5「便于跨进程定位」)。上游为跨进程边界,不可全信,
 *       故对长度做上限约束,避免被塞入超长值污染日志。</li>
 *   <li>否则生成一个新的。</li>
 *   <li>写入 MDC(供日志 pattern 使用)与 {@link TraceContext}(供响应体使用)。</li>
 *   <li>回写响应头,便于前端与网关按 traceId 报障。</li>
 *   <li><b>finally 中务必清理</b> —— 线程池会复用线程,漏清会把上一个请求的 traceId
 *       泄漏给下一个请求。</li>
 * </ol>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    /** 上游传入的 traceId 长度上限;超出则丢弃改用新生成的。 */
    private static final int MAX_INBOUND_LENGTH = 64;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String inbound = request.getHeader(TraceContext.HEADER);
        String traceId = (StringUtils.hasText(inbound) && inbound.length() <= MAX_INBOUND_LENGTH)
                ? inbound
                : TraceContext.generate();

        TraceContext.set(traceId);
        response.setHeader(TraceContext.HEADER, traceId);
        try {
            chain.doFilter(request, response);
        } finally {
            TraceContext.clear();
        }
    }
}
