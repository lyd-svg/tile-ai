package com.tileai.quote.dto;

import java.math.BigDecimal;
import java.util.List;

import com.tileai.layout.model.Opening;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 报价预览请求。
 *
 * <p>契约:{@code openapi-c.yaml#/components/schemas/QuoteCalcRequest},
 * {@code required: [space, parts]},{@code parts} 的 {@code minItems: 1}。
 */
public record QuoteCalcRequest(

        @NotBlank(message = "空间不能为空")
        @Size(max = 50, message = "空间 key 不能超过 50 字符")
        String space,

        @NotEmpty(message = "至少需要一个部位")
        @Size(max = 20, message = "部位数量过多")
        List<@Valid PartSelection> parts,

        @Size(max = 20, message = "开口分组过多")
        List<@Valid PartOpenings> openings) {

    /**
     * 部位 → 瓷砖的选定(契约 {@code PartSelection})。
     *
     * <p><b>{@code area} 在这里是必填的。</b>契约把 {@code PartSelection.area} 标为可选
     * (「可由服务端依户型计算,前端可传可不传」),那是针对 <b>{@code /generate}</b> 的 ——
     * 那里请求里带着户型,服务端算得出来。而 <b>{@code /quote/calc} 请求里没有户型</b>,
     * 服务端无从推导面积。把缺失的面积当作 0 会静默报出一个偏低的价,那比报错更糟。
     */
    public record PartSelection(

            @NotBlank(message = "部位不能为空")
            @Pattern(regexp = "floor|wall|local", message = "部位只能是 floor / wall / local")
            String part,

            @NotBlank(message = "瓷砖编号不能为空")
            @Size(max = 64, message = "瓷砖编号不能超过 64 字符")
            String sku,

            @NotNull(message = "面积不能为空(/quote/calc 请求里没有户型,服务端无法推导)")
            @DecimalMin(value = "0.00", message = "面积不能为负")
            BigDecimal area) {
    }

    /** 某个部位的开口(契约 {@code QuoteCalcRequest.openings[]})。 */
    public record PartOpenings(

            @NotBlank(message = "部位不能为空")
            @Pattern(regexp = "floor|wall|local", message = "部位只能是 floor / wall / local")
            String part,

            @Size(max = 20, message = "单个部位的开口过多")
            List<@Valid Opening> openings) {
    }
}
