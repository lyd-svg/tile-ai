package com.tileai.quote.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 报价结果(单空间)。
 *
 * <p>契约:{@code openapi-common.yaml#/components/schemas/QuoteResult},
 * {@code required: [materialTotal, auxTotal, total, items]}。
 */
public record QuoteResultView(
        BigDecimal materialTotal,
        BigDecimal auxTotal,
        BigDecimal total,
        List<Item> items,
        RulesSnapshot rulesSnapshot) {

    /**
     * 报价分项(按部位 + SKU)。
     *
     * <p>契约 {@code QuoteItem},{@code required: [part, sku, grossArea, effectiveArea,
     * lossRate, pricePerSqm, materialFee]} —— 七项全必填,少一个前端就画不出明细。
     */
    public record Item(
            String part,
            String sku,
            BigDecimal grossArea,
            BigDecimal effectiveArea,
            BigDecimal lossRate,
            BigDecimal pricePerSqm,
            BigDecimal materialFee) {
    }

    /**
     * 生成时的参数快照(契约 {@code QuoteResult.rulesSnapshot})。
     *
     * <p>§8.8:用于追溯与「按当前价格重算」(§15.7)。有了它,调价之后仍能说清
     * "这张报价单当时是按什么参数算的"。
     */
    public record RulesSnapshot(BigDecimal auxUnitPrice, BigDecimal lossRate) {
    }
}
