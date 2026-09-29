package com.tileai.product.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 产品多角度图(架构 §8.2 {@code t_product_image})。
 *
 * <p>只存 URL,不存二进制(§8 命名约定:图片/文件一律存对象存储)。
 */
@TableName("t_product_image")
public class ProductImage {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long productId;

    private String url;

    /** 拍摄角度,如 {@code default} / {@code angle1}。 */
    private String angle;

    private Integer sort;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getAngle() {
        return angle;
    }

    public void setAngle(String angle) {
        this.angle = angle;
    }

    public Integer getSort() {
        return sort;
    }

    public void setSort(Integer sort) {
        this.sort = sort;
    }
}
