package com.tileai.plan.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 方案(架构 §8.4 {@code t_plan};PRD §5.6)。
 *
 * <p><b>{@code layoutEnc} 是密文</b>(AES-256-GCM,{@code AesGcmCipher})。
 * 加解密只在这一层发生,<b>对外 API 一律传明文</b>,对外 schema 里不得出现
 * {@code layout_enc}(契约 T-8;CLAUDE.md 约定)。
 *
 * <p><b>两个布尔列显式标注列名</b>,不靠下划线转换:MyBatis-Plus 对
 * {@code isXxx} 形状的字段存在"映射到 {@code xxx} 还是 {@code is_xxx}"的歧义,
 * 而这种错不会报错 —— 只会读写到不存在的列或悄悄读错列。显式写死最省事。
 */
@TableName("t_plan")
public class Plan {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 可重命名(T-1.12)。覆盖保存时<b>不</b>改动它 —— 用户改过的名字不该被重新生成冲掉。 */
    private String name;

    /** 生成空间,空间字典 key。 */
    private String space;

    /** 风格字典 key。 */
    private String style;

    /** 户型快照,{@code AES-256-GCM} 密文。 */
    private byte[] layoutEnc;

    /** 生成时总价。本期不报价(T-1.15),留空。 */
    private BigDecimal totalAmount;

    /** 分享令牌,≥128bit;废除后置空(T-1.13)。 */
    private String shareToken;

    /** 产品调价后置 1(T-1.15 的"报价已过期"标记)。 */
    @TableField("quote_expired")
    private Boolean quoteExpired;

    /** 逻辑删除。删除是标记而不是删行 —— 分享出去的链接要能解释"已删除"。 */
    @TableField("is_deleted")
    private Boolean isDeleted;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
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

    public byte[] getLayoutEnc() {
        return layoutEnc;
    }

    public void setLayoutEnc(byte[] layoutEnc) {
        this.layoutEnc = layoutEnc;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public String getShareToken() {
        return shareToken;
    }

    public void setShareToken(String shareToken) {
        this.shareToken = shareToken;
    }

    public Boolean getQuoteExpired() {
        return quoteExpired;
    }

    public void setQuoteExpired(Boolean quoteExpired) {
        this.quoteExpired = quoteExpired;
    }

    public Boolean getIsDeleted() {
        return isDeleted;
    }

    public void setIsDeleted(Boolean isDeleted) {
        this.isDeleted = isDeleted;
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
