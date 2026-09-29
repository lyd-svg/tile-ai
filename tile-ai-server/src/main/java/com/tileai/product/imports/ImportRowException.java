package com.tileai.product.imports;

import com.tileai.common.error.ErrorCode;

/**
 * 单行导入失败 —— <b>批处理内部的局部失败,不是接口级错误</b>。
 *
 * <p>与 {@code BusinessException} 的区别很关键:{@code BusinessException} 会被
 * {@code GlobalExceptionHandler} 转成响应体返回给调用方;而本异常<b>只在批处理循环里
 * 被捕获</b>,转成一条 {@link ImportRowError} 记入错误报告,整个请求依然是成功的
 * —— 因为按 §8.7,"错误行不写入"是<b>正常结果</b>,不是接口失败。
 *
 * <p>继承 {@code RuntimeException} 但<b>不继承</b> {@code BusinessException},
 * 正是为了避免它意外冒泡成接口响应。
 */
public class ImportRowException extends RuntimeException {

    /** 错误码仅用于日志归类,不会出现在响应体里。 */
    private final ErrorCode errorCode = ErrorCode.PARAM_INVALID;

    public ImportRowException(String message) {
        super(message);
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    /** 单行错误是预期内的控制流,不需要堆栈。 */
    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }
}
