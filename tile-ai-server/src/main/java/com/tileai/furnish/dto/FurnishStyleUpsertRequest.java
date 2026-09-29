package com.tileai.furnish.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 新增 / 更新陈设配置请求体。
 *
 * <p>契约:{@code openapi-admin.yaml#/components/schemas/FurnishStyleUpsert},
 * {@code required: [space, style, prompt]}。
 *
 * <p>这是 <b>upsert</b> 语义 —— 以 {@code (space, style)} 为唯一键,
 * 重复提交是更新而不是新增(契约:矩阵以 space+style 唯一)。
 */
public record FurnishStyleUpsertRequest(

        @NotBlank(message = "空间不能为空")
        @Size(max = 50, message = "空间 key 不能超过 50 字符")
        String space,

        @NotBlank(message = "风格不能为空")
        @Size(max = 50, message = "风格 key 不能超过 50 字符")
        String style,

        @NotBlank(message = "陈设提示词不能为空")
        @Size(max = 2000, message = "陈设提示词不能超过 2000 字符")
        String prompt,

        @Size(max = 500, message = "封面图 URL 不能超过 500 字符")
        String coverImage,

        @Pattern(regexp = "on_sale|off_sale", message = "状态只能是 on_sale 或 off_sale")
        String status) {
}
