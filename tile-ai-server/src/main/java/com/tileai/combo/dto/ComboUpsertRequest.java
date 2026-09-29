package com.tileai.combo.dto;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 新增 / 编辑搭配请求体。
 *
 * <p>契约:{@code openapi-admin.yaml#/components/schemas/ComboUpsert},
 * {@code required: [name, space, style, items]},{@code items} 的 {@code minItems: 1}。
 */
public record ComboUpsertRequest(

        @NotBlank(message = "搭配名称不能为空")
        @Size(max = 200, message = "搭配名称不能超过 200 字符")
        String name,

        @NotBlank(message = "适用空间不能为空")
        @Size(max = 50, message = "空间 key 不能超过 50 字符")
        String space,

        @NotBlank(message = "风格不能为空")
        @Size(max = 50, message = "风格 key 不能超过 50 字符")
        String style,

        @DecimalMin(value = "0.00", message = "参考价下限不能为负")
        BigDecimal refPriceMin,

        @DecimalMin(value = "0.00", message = "参考价上限不能为负")
        BigDecimal refPriceMax,

        @Size(max = 500, message = "展示图 URL 不能超过 500 字符")
        String coverImage,

        @Pattern(regexp = "on_sale|off_sale", message = "状态只能是 on_sale 或 off_sale")
        String status,

        Integer sort,

        @NotEmpty(message = "搭配至少要包含一款瓷砖")
        @Size(max = 50, message = "单个搭配最多 50 项")
        List<@Valid Item> items) {

    /**
     * 搭配中的一项。契约 {@code ComboItem},{@code required: [sku, part]}。
     *
     * <p>{@code part} 用 {@code @Pattern} 而非枚举,理由同 {@code ProductUpsertRequest.status}:
     * 非法值能返回带 {@code field} 的精确明细,而不是笼统的"请求体格式错误"。
     * 取值恒为三值(契约 T-9),与 {@code com.tileai.common.model.Part} 一致 ——
     * 由 {@code ComboContractTest} 断言两者不漂移。
     */
    public record Item(

            @NotBlank(message = "瓷砖编号不能为空")
            @Size(max = 64, message = "瓷砖编号不能超过 64 字符")
            String sku,

            /** 冗余展示名,可选 —— 库里不存,读取时由产品表带出。 */
            @Size(max = 200, message = "展示名不能超过 200 字符")
            String name,

            @NotBlank(message = "部位不能为空")
            @Pattern(regexp = "floor|wall|local", message = "部位只能是 floor / wall / local")
            String part,

            @Size(max = 255, message = "备注不能超过 255 字符")
            String note,

            Integer sort) {
    }
}
