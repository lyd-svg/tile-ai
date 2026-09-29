package com.tileai.quote.dto;

import java.math.BigDecimal;

import com.tileai.quote.entity.QuoteParam;

/**
 * 报价参数的对外视图。
 *
 * <p>契约:{@code openapi-common.yaml#/components/schemas/QuoteParam},
 * {@code required: [id, paramKey, paramValue]}。
 *
 * <p>{@code @JsonInclude(NON_NULL)}:契约把 {@code remark} 标为 {@code nullable: true},
 * 但更稳妥的做法是未填时整个省略 —— 与 T-0.9 那次 schema 校验抓出的问题同源。
 */
@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
public record QuoteParamView(Long id, String paramKey, BigDecimal paramValue, String remark) {

    public static QuoteParamView of(QuoteParam entity) {
        return new QuoteParamView(entity.getId(), entity.getParamKey(), entity.getParamValue(),
                entity.getRemark());
    }
}
