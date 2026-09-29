package com.tileai.quote.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 新增 / 更新报价参数请求体。
 *
 * <p>契约:{@code openapi-admin.yaml#/components/schemas/QuoteParamUpsert},
 * {@code required: [paramKey, paramValue]}。
 *
 * <p>{@code paramKey} 用 {@code @Pattern} 而非枚举(理由同其他模块:非法值能返回
 * 带 field 的精确明细)。<b>取值的量纲校验在服务层</b>({@code QuoteParamKey.validate}) ——
 * 它取决于键本身,不是静态约束能表达的。
 */
public record QuoteParamUpsertRequest(

        @NotBlank(message = "参数键不能为空")
        @Pattern(regexp = "aux_unit_price|loss_rate|loss_rate_min|loss_rate_max",
                message = "参数键只能是 aux_unit_price / loss_rate / loss_rate_min / loss_rate_max")
        String paramKey,

        @NotNull(message = "参数值不能为空")
        BigDecimal paramValue,

        @Size(max = 255, message = "备注不能超过 255 字符")
        String remark) {
}
