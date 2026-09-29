package com.tileai.quote.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 报价参数(架构 §8.5 {@code t_quote_param};PRD §5.5、§5.7)。
 *
 * <p>这是报价规则的<b>唯一可配置输入</b>:辅料单价与损耗率上下限。
 * 报价公式本身写死在 {@code quote} 模块里,不参数化(架构 §4.1:确定性逻辑只写一份)。
 */
@TableName("t_quote_param")
public class QuoteParam {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 参数键,取值见 {@link QuoteParamKey}。 */
    private String paramKey;

    /**
     * 参数值。
     *
     * <p>列类型 {@code DECIMAL(10,4)} 是为了同时容纳两种量纲:
     * 辅料单价(如 {@code 20.0000} 元/㎡)与损耗率(如 {@code 0.0600})。
     */
    private BigDecimal paramValue;

    private String remark;

    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getParamKey() {
        return paramKey;
    }

    public void setParamKey(String paramKey) {
        this.paramKey = paramKey;
    }

    public BigDecimal getParamValue() {
        return paramValue;
    }

    public void setParamValue(BigDecimal paramValue) {
        this.paramValue = paramValue;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
