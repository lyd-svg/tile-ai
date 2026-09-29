package com.tileai.layout.model;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.validation.constraints.DecimalMin;

/**
 * 空间各部位面积(㎡)。契约 {@code openapi-common.yaml#/components/schemas/LayoutAreas}。
 *
 * <p>契约未声明 {@code required} —— 三个字段都可省略。因此这里只做"若给了就不能为负"的校验,
 * 不强制非空。
 *
 * <p>可省略的字段用 {@code NON_NULL} 省略而非输出 {@code null}:契约把它们声明为
 * {@code number} 且未标注 {@code nullable},显式 null 会被 schema 判为类型不符。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LayoutAreas(
        @DecimalMin(value = "0.00", message = "地面面积不能为负") BigDecimal floor,
        @DecimalMin(value = "0.00", message = "墙面面积不能为负") BigDecimal wall,
        @DecimalMin(value = "0.00", message = "局部面积不能为负") BigDecimal local) {
}
