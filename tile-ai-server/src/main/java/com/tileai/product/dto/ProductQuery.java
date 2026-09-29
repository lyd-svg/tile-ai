package com.tileai.product.dto;

import java.math.BigDecimal;

/**
 * C 端产品库的筛选条件(契约 {@code openapi-c.yaml} {@code GET /products} 的查询参数)。
 *
 * <p>八个筛选参数按 D-8 裁决命名:{@code keyword / material / colorPattern / spec /
 * style / space / priceMin / priceMax}。该裁决记录在 {@code openapi-verification.md} §7。
 *
 * <p>单独建一个记录而不是把 10 个参数平铺在方法签名上:参数一多,
 * 调用点很容易把 {@code spec} 与 {@code colorPattern} 这类同类型字符串传反,
 * 而编译器不会报错。
 */
public record ProductQuery(
        String keyword,
        String material,
        String colorPattern,
        String spec,
        String style,
        String space,
        BigDecimal priceMin,
        BigDecimal priceMax) {
}
