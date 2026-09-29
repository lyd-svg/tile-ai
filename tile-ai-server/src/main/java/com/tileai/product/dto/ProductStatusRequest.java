package com.tileai.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 上下架请求体。
 *
 * <p>契约:{@code PATCH /admin/products/{id}/status},
 * {@code required: [status]},类型为 {@code ProductStatus}。
 * 与 {@link ProductUpsertRequest#status()} 同样用字符串 + {@code @Pattern}。
 */
public record ProductStatusRequest(

        @NotBlank(message = "状态不能为空")
        @Pattern(regexp = "on_sale|off_sale", message = "状态只能是 on_sale 或 off_sale")
        String status) {
}
