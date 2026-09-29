package com.tileai.combo.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 推荐搭配的对外视图。
 *
 * <p>契约:{@code openapi-common.yaml#/components/schemas/Combo},
 * {@code required: [id, name, space, style, status]}。
 */
public record ComboView(
        Long id,
        String name,
        String space,
        String style,
        BigDecimal refPriceMin,
        BigDecimal refPriceMax,
        String coverImage,
        String status,
        Integer sort,
        List<Item> items) {

    /**
     * 搭配中的一项。契约 {@code ComboItem}。
     *
     * @param sku  对外一律用编号;库里的 {@code product_id} 不暴露
     * @param name 展示名,读取时从产品表带出(库里不存)
     */
    public record Item(String sku, String name, String part, String note, Integer sort) {
    }
}
