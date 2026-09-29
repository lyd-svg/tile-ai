package com.tileai.layout.model;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * 门窗开口。契约 {@code openapi-common.yaml#/components/schemas/Opening},
 * {@code required: [type, w, h]}(架构 §8.3 字段表)。
 *
 * <p>尺寸用 {@link BigDecimal} 而非 double:面积由这些数乘出来,而面积最终决定报价金额,
 * 二进制浮点的误差会在乘法里被放大。
 */
public record Opening(
        @NotNull(message = "开口类型不能为空")
        @Pattern(regexp = "door|window", message = "开口类型只能是 door 或 window")
        String type,

        @NotNull(message = "开口宽不能为空")
        @DecimalMin(value = "0.01", message = "开口宽必须大于 0")
        BigDecimal w,

        @NotNull(message = "开口高不能为空")
        @DecimalMin(value = "0.01", message = "开口高必须大于 0")
        BigDecimal h) {
}
