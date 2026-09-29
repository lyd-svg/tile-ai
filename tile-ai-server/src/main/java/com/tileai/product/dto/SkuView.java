package com.tileai.product.dto;

import java.math.BigDecimal;
import java.util.List;

import com.tileai.product.entity.Product;
import com.tileai.product.entity.ProductImage;

/**
 * 产品库 SKU 的对外视图。
 *
 * <p>契约:{@code openapi-common.yaml#/components/schemas/Sku},
 * {@code required: [sku, name, pricePerSqm, status]}。
 *
 * <p>与实体 {@link Product} 分开,是因为<b>对外字段名与库内字段名不同</b>
 * (对外 {@code sku},库内 {@code sku_code}),而且库内将来可能增加内部字段 ——
 * 用视图类做白名单,新增内部字段时默认不会被带出去。
 * 这与 T-0.5 里 {@code AdminUserView} 防 {@code passwordHash} 外泄是同一个道理。
 */
public record SkuView(
        Long id,
        String sku,
        String name,
        String spec,
        String material,
        String surface,
        String colorPattern,
        BigDecimal pricePerSqm,
        List<String> applicableSpaces,
        List<String> styleTags,
        String status,
        String coverImage,
        List<ImageItem> images) {

    /** 多角度图,对应契约 {@code Sku.images[]}。 */
    public record ImageItem(String url, String angle, Integer sort) {

        static ImageItem of(ProductImage image) {
            return new ImageItem(image.getUrl(), image.getAngle(), image.getSort());
        }
    }

    public static SkuView of(Product product, List<ProductImage> images) {
        return new SkuView(
                product.getId(),
                product.getSkuCode(),
                product.getName(),
                product.getSpec(),
                product.getMaterial(),
                product.getSurface(),
                product.getColorPattern(),
                product.getPricePerSqm(),
                product.getApplicableSpaces(),
                product.getStyleTags(),
                product.getStatus(),
                product.getCoverImage(),
                images == null ? List.of() : images.stream().map(ImageItem::of).toList());
    }
}
