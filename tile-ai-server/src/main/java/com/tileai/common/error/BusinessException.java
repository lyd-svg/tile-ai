package com.tileai.common.error;

/**
 * 业务异常 —— 携带一个 {@link ErrorCode},由 {@link GlobalExceptionHandler} 统一转成
 * {@code ApiResponse}。
 *
 * <p>业务代码抛这个异常即可,<b>不要</b>自己构造响应或设置 HTTP 状态 ——
 * 状态码口径集中在 {@link ErrorCode#httpStatus()} 一处,避免散落。
 *
 * <p>{@code message} 用于覆盖默认文案(如"SKU 不存在: SKU-123"),
 * 但<b>不得</b>把堆栈、SQL、内部标识等细节暴露给前端(§10.5 对 90001 的要求同样适用)。
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.defaultMessage());
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    /**
     * 业务异常无需堆栈 —— 它是预期内的控制流,不是故障。
     * 关掉 fillInStackTrace 可显著降低高频业务失败(如 30001、30002)的开销。
     */
    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }
}
