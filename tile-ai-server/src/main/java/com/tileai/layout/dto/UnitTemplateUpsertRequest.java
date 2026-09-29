package com.tileai.layout.dto;

import com.tileai.layout.model.LayoutModel;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 新增 / 编辑户型模板请求体。
 *
 * <p>契约:{@code openapi-admin.yaml#/components/schemas/UnitTemplateUpsert},
 * {@code required: [name, layout]}。
 */
public record UnitTemplateUpsertRequest(

        @NotBlank(message = "模板名称不能为空")
        @Size(max = 200, message = "模板名称不能超过 200 字符")
        String name,

        Integer roomCount,

        @Size(max = 50, message = "面积区间不能超过 50 字符")
        String areaRange,

        @Size(max = 50, message = "户型结构不能超过 50 字符")
        String structure,

        /** 结构化户型;**内部字段另有校验**,由 {@link LayoutModel} 及其嵌套类型承载。 */
        @NotNull(message = "户型结构不能为空")
        @Valid
        LayoutModel layout,

        @Pattern(regexp = "on_sale|off_sale", message = "状态只能是 on_sale 或 off_sale")
        String status) {
}
