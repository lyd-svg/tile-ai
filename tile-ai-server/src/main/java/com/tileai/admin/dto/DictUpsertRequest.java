package com.tileai.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 新增 / 编辑字典项请求体。
 *
 * <p>契约:{@code openapi-admin.yaml#/components/schemas/DictUpsert},
 * {@code required: [type, key, label]}。
 */
public record DictUpsertRequest(

        @NotBlank(message = "字典类型不能为空")
        @Pattern(regexp = "space|style", message = "字典类型只能是 space 或 style")
        String type,

        @NotBlank(message = "存储键不能为空")
        @Size(max = 50, message = "存储键不能超过 50 字符")
        String key,

        @NotBlank(message = "展示名不能为空")
        @Size(max = 100, message = "展示名不能超过 100 字符")
        String label,

        Integer sort,

        @Pattern(regexp = "enabled|disabled", message = "状态只能是 enabled 或 disabled")
        String status) {
}
