package com.tileai.combo.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 推荐搭配(架构 §8.2 {@code t_recommend_combo};
 * PRD §5.2.2 路径 B —— <b>由运营在后台人工维护,不做自动组合</b>)。
 */
@TableName("t_recommend_combo")
public class RecommendCombo {

    /** 上下架状态,复用契约 {@code ProductStatus} 的取值。 */
    public static final String STATUS_ON_SALE = "on_sale";
    public static final String STATUS_OFF_SALE = "off_sale";

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String name;

    /** 空间字典 key。 */
    private String space;

    /** 风格字典 key。 */
    private String style;

    /** 参考价下限,元。 */
    private BigDecimal refPriceMin;

    /** 参考价上限,元。 */
    private BigDecimal refPriceMax;

    private String coverImage;

    private String status;

    private Integer sort;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSpace() {
        return space;
    }

    public void setSpace(String space) {
        this.space = space;
    }

    public String getStyle() {
        return style;
    }

    public void setStyle(String style) {
        this.style = style;
    }

    public BigDecimal getRefPriceMin() {
        return refPriceMin;
    }

    public void setRefPriceMin(BigDecimal refPriceMin) {
        this.refPriceMin = refPriceMin;
    }

    public BigDecimal getRefPriceMax() {
        return refPriceMax;
    }

    public void setRefPriceMax(BigDecimal refPriceMax) {
        this.refPriceMax = refPriceMax;
    }

    public String getCoverImage() {
        return coverImage;
    }

    public void setCoverImage(String coverImage) {
        this.coverImage = coverImage;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getSort() {
        return sort;
    }

    public void setSort(Integer sort) {
        this.sort = sort;
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
