package com.tileai.common.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.tileai.common.error.ErrorCode;
import com.tileai.common.trace.TraceContext;

/**
 * 统一响应体。
 *
 * <p>契约:{@code openapi-common.yaml#/components/schemas/ApiResponse},
 * {@code required: [code, message, traceId]},{@code data} 可空。
 *
 * <p><b>状态码不在这里设置</b> —— HTTP 状态由 {@link ErrorCode#httpStatus()} 决定,
 * 由异常处理器统一落到响应上。业务代码只负责选对 {@link ErrorCode}。
 *
 * <p>{@code data} 恒定序列化(无数据时为 {@code null}),与架构 §10.1 的示例形态一致。
 */
public record ApiResponse<T>(int code, String message, T data, String traceId) {

    public static <T> ApiResponse<T> ok(T data) {
        return of(ErrorCode.SUCCESS, ErrorCode.SUCCESS.defaultMessage(), data);
    }

    public static ApiResponse<Void> ok() {
        return ok(null);
    }

    public static <T> ApiResponse<T> fail(ErrorCode errorCode) {
        return of(errorCode, errorCode.defaultMessage(), null);
    }

    public static <T> ApiResponse<T> fail(ErrorCode errorCode, String message) {
        return of(errorCode, message, null);
    }

    /** 失败但需要携带数据 —— 校验失败(10001)用它装 {@code errors[]}。 */
    public static <T> ApiResponse<T> fail(ErrorCode errorCode, String message, T data) {
        return of(errorCode, message, data);
    }

    private static <T> ApiResponse<T> of(ErrorCode errorCode, String message, T data) {
        return new ApiResponse<>(errorCode.code(), message, data, TraceContext.getOrCreate());
    }

    /**
     * 便捷判断,<b>不是响应字段</b>。
     *
     * <p>必须加 {@link JsonIgnore}:否则 Jackson 会把 record 上的 {@code isSuccess()}
     * 也当成属性序列化出去,响应体就多出一个契约里没有的 {@code "success"} 字段。
     */
    @JsonIgnore
    public boolean isSuccess() {
        return code == ErrorCode.SUCCESS.code();
    }
}
