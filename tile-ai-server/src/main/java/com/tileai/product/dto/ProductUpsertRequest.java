package com.tileai.product.dto;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 新增 / 编辑 SKU 请求体。
 *
 * <p>契约:{@code openapi-admin.yaml#/components/schemas/ProductUpsert},
 * {@code required: [sku, name, pricePerSqm]}。
 *
 * <p>各字段的 {@code @Size} 上限来自架构 §8.2 的列宽(见迁移 {@code V2__product_and_combo.sql})。
 * 加上它们是为了把"数据太长"变成 <b>10001 参数校验失败</b>而不是
 * 数据库抛异常后的 90001 —— 同样是拒绝,前者能给前端可读的字段级原因。
 *
 * <p>{@code status} 契约里类型化为 {@code ProductStatus} 枚举。这里用字符串 +
 * {@code @Pattern} 而不是 Java 枚举:枚举遇到非法值时 Jackson 直接解析失败,
 * 客户端只能拿到笼统的"请求体格式错误";{@code @Pattern} 能返回
 * {@code field="status"} 的精确明细,拦截效果相同而反馈更有用。
 */
public record ProductUpsertRequest(

        @NotBlank(message = "产品编号不能为空")
        @Size(max = 64, message = "产品编号不能超过 64 字符")
        String sku,

        @NotBlank(message = "产品名称不能为空")
        @Size(max = 200, message = "产品名称不能超过 200 字符")
        String name,

        @Size(max = 100, message = "规格不能超过 100 字符")
        String spec,

        @Size(max = 50, message = "材质不能超过 50 字符")
        String material,

        @Size(max = 50, message = "光泽度不能超过 50 字符")
        String surface,

        @Size(max = 200, message = "花色纹理不能超过 200 字符")
        String colorPattern,

        @NotNull(message = "单价不能为空")
        // ⚠️ 上限必须有(2026-10-07):此前**只有"不能为负"**,填个天文数字会让
        // 报价溢出 `t_plan_quote.total_amount` 的 DECIMAL(12,2)。见架构 §8.3。
        @DecimalMin(value = "0.00", message = "单价不能为负")
        @DecimalMax(value = "9999", message = "单价不能超过 9999 元/㎡")
        BigDecimal pricePerSqm,

        @Size(max = 50, message = "适用空间过多")
        List<@Size(max = 50, message = "空间 key 过长") String> applicableSpaces,

        @Size(max = 50, message = "风格标签过多")
        List<@Size(max = 50, message = "风格 key 过长") String> styleTags,

        @Pattern(regexp = "on_sale|off_sale", message = "状态只能是 on_sale 或 off_sale")
        String status,

        @Size(max = 500, message = "封面图 URL 不能超过 500 字符")
        String coverImage,

        @Size(max = 20, message = "多角度图最多 20 张")
        List<@Valid ImageItem> images) {

    /** 多角度图元素,对应契约 {@code ProductUpsert.images[]} 与 §8.2 {@code t_product_image}。 */
    public record ImageItem(
            @NotBlank(message = "图片 URL 不能为空")
            @Size(max = 500, message = "图片 URL 不能超过 500 字符")
            String url,

            @Size(max = 50, message = "角度不能超过 50 字符")
            String angle,

            Integer sort) {
    }
}
