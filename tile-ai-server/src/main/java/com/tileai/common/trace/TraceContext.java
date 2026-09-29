package com.tileai.common.trace;

import java.util.UUID;

import org.slf4j.MDC;

/**
 * 链路追踪 ID 的持有者(架构 §13.7 可观测、§10.5 跨进程定位)。
 *
 * <p>ID 由 {@link TraceIdFilter} 在请求入口写入;所有 {@code ApiResponse}
 * 都从这里取 {@code traceId},保证同一次请求的日志与响应体是同一个 ID。
 *
 * <p><b>局限</b>:底层是 {@link ThreadLocal},因此只在同步 MVC 请求线程内可靠。
 * 一旦引入异步派发或 {@code @Async},需改为显式传递(或换用 Micrometer Tracing)——
 * 届时 T-2.3 可观测会统一处理。
 */
public final class TraceContext {

    /** 响应 MDC 与日志的键名,与 logback pattern 中的 %X{traceId} 对应。 */
    public static final String MDC_KEY = "traceId";

    /** 允许上游透传的请求头 —— 跨进程(Java ↔ Python)串联同一条链路。 */
    public static final String HEADER = "X-Trace-Id";

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private TraceContext() {
    }

    /** 当前请求的 traceId;不在请求线程内时返回 null。 */
    public static String get() {
        return CURRENT.get();
    }

    /** 当前 traceId;缺失时即时生成一个,保证响应体里 {@code traceId} 永不为空。 */
    public static String getOrCreate() {
        String existing = CURRENT.get();
        if (existing != null) {
            return existing;
        }
        return generate();
    }

    public static void set(String traceId) {
        CURRENT.set(traceId);
        MDC.put(MDC_KEY, traceId);
    }

    public static void clear() {
        CURRENT.remove();
        MDC.remove(MDC_KEY);
    }

    public static String generate() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
