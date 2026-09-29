package com.tileai.furnish.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 陈设风格配置(架构 §8.5 {@code t_furnish_style};
 * PRD §5.4:陈设随所选风格自动变化)。
 *
 * <p>本质是一张 <b>空间 × 风格</b> 的矩阵,{@code (space, style)} 唯一。
 */
@TableName("t_furnish_style")
public class FurnishStyle {

    public static final String STATUS_ON_SALE = "on_sale";
    public static final String STATUS_OFF_SALE = "off_sale";

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 空间字典 key。 */
    private String space;

    /** 风格字典 key。 */
    private String style;

    /** 陈设提示词,供 Python 组装出图提示词(§7.3)。 */
    private String prompt;

    private String coverImage;

    /**
     * 状态。契约 {@code FurnishStyleUpsert.status} 引用 {@code ProductStatus},
     * 即 {@code on_sale} / {@code off_sale} —— 这里存的是"该组合是否生效"。
     */
    private String status;

    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
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

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
