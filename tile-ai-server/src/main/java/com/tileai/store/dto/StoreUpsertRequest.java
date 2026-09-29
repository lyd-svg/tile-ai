package com.tileai.store.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 更新门店请求体。
 *
 * <p>契约:{@code openapi-admin.yaml#/components/schemas/StoreUpsert},
 * {@code required: [name, address, phone]}。
 */
public record StoreUpsertRequest(

        @NotBlank(message = "门店名称不能为空")
        @Size(max = 200, message = "门店名称不能超过 200 字符")
        String name,

        @NotBlank(message = "门店地址不能为空")
        @Size(max = 500, message = "门店地址不能超过 500 字符")
        String address,

        @NotBlank(message = "联系电话不能为空")
        @Size(max = 50, message = "联系电话不能超过 50 字符")
        String phone,

        @Size(max = 500, message = "地图链接不能超过 500 字符")
        String mapUrl) {
}
