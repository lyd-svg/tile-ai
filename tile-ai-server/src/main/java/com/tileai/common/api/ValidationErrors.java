package com.tileai.common.api;

import java.util.List;

/**
 * 参数校验失败(10001)的 {@code data} 载体。
 *
 * <p>架构 §10.5 与契约 {@code ErrorItem} 的描述都写明:明细放在 <b>{@code data.errors[]}</b>。
 * 也就是说 {@code data} 是 <b>对象</b>而非数组 —— 这个包装类就是那层对象。
 * (契约的 {@code ApiResponse.data} 声明为 {@code type: object},直接放数组也不合法。)
 *
 * <p>契约未为此结构单独定义 schema,故类名不取自契约;字段名 {@code errors} 取自
 * §10.5 的原文。
 */
public record ValidationErrors(List<ErrorItem> errors) {

    public static ValidationErrors of(List<ErrorItem> errors) {
        return new ValidationErrors(errors);
    }
}
