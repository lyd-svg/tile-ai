package com.tileai.product.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;

/**
 * 产品库 SKU(架构 §8.2 {@code t_product})。
 *
 * <p>{@code autoResultMap = true} 是 {@link JacksonTypeHandler} 生效的前提 ——
 * 少了它,查询结果里的 JSON 列不会走类型处理器,{@code applicableSpaces}
 * 会是一个字符串而不是 List,症状是序列化出来变成带转义的字符串。
 *
 * <p>金额用 {@link BigDecimal} 而非 double:报价是钱,二进制浮点会积累误差
 * (架构 §8.4 的报价示例精确到分)。
 */
@TableName(value = "t_product", autoResultMap = true)
public class Product {

    /** 上下架状态,与契约 {@code ProductStatus} 的 enum 一致。 */
    public static final String STATUS_ON_SALE = "on_sale";
    public static final String STATUS_OFF_SALE = "off_sale";

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 业务主键,唯一。批量导入以它做 upsert(§8.7)。 */
    private String skuCode;

    private String name;

    private String spec;

    private String material;

    private String surface;

    private String colorPattern;

    /** 单价,元/㎡。 */
    private BigDecimal pricePerSqm;

    /** JSON 数组,元素为空间字典 key。 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> applicableSpaces;

    /** JSON 数组,元素为风格字典 key。 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> styleTags;

    private String status;

    private String coverImage;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSkuCode() {
        return skuCode;
    }

    public void setSkuCode(String skuCode) {
        this.skuCode = skuCode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSpec() {
        return spec;
    }

    public void setSpec(String spec) {
        this.spec = spec;
    }

    public String getMaterial() {
        return material;
    }

    public void setMaterial(String material) {
        this.material = material;
    }

    public String getSurface() {
        return surface;
    }

    public void setSurface(String surface) {
        this.surface = surface;
    }

    public String getColorPattern() {
        return colorPattern;
    }

    public void setColorPattern(String colorPattern) {
        this.colorPattern = colorPattern;
    }

    public BigDecimal getPricePerSqm() {
        return pricePerSqm;
    }

    public void setPricePerSqm(BigDecimal pricePerSqm) {
        this.pricePerSqm = pricePerSqm;
    }

    public List<String> getApplicableSpaces() {
        return applicableSpaces;
    }

    public void setApplicableSpaces(List<String> applicableSpaces) {
        this.applicableSpaces = applicableSpaces;
    }

    public List<String> getStyleTags() {
        return styleTags;
    }

    public void setStyleTags(List<String> styleTags) {
        this.styleTags = styleTags;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCoverImage() {
        return coverImage;
    }

    public void setCoverImage(String coverImage) {
        this.coverImage = coverImage;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
