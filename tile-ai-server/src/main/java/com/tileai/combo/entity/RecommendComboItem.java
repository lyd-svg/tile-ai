package com.tileai.combo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 搭配所含 SKU 及部位(架构 §8.2 {@code t_recommend_combo_item})。
 *
 * <p>表里存的是 {@code product_id} 而契约对外用 {@code sku} —— 转换在服务层完成。
 * 另外本表<b>没有 name 列</b>,契约里那个"冗余展示名"是读取时从产品表带出的。
 */
@TableName("t_recommend_combo_item")
public class RecommendComboItem {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long comboId;

    private Long productId;

    /** 铺贴部位,恒为 floor / wall / local(契约 T-9)。 */
    private String part;

    private String note;

    private Integer sort;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getComboId() {
        return comboId;
    }

    public void setComboId(Long comboId) {
        this.comboId = comboId;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public String getPart() {
        return part;
    }

    public void setPart(String part) {
        this.part = part;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public Integer getSort() {
        return sort;
    }

    public void setSort(Integer sort) {
        this.sort = sort;
    }
}
