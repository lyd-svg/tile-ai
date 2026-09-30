package com.tileai.plan.entity;

import java.math.BigDecimal;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 方案部位—瓷砖映射 + 报价分项(架构 §8.4 {@code t_plan_part})。
 *
 * <p>{@code sku_code} / {@code product_name} / {@code price_per_sqm} 是<b>快照</b>,
 * 不随产品库改动而变 —— 否则历史方案的金额会跟着今天的价格自己变。
 *
 * <p><b>T-1.11 只写前三列</b>({@code part} / {@code sku_code} / {@code gross_area})。
 * 其余列属报价(T-1.15):{@code product_name} 与 {@code price_per_sqm} 要在报价时
 * 连价一起取,{@code material_fee} 是乘法结果。刻意<b>不</b>在这条路径上去查产品
 * 快照 —— 那会给"回调成功"引入一个新的失败点:产品刚好下架就会抛 30001,
 * 把一次已经成功的生成回滚成失败,用户看到的是"图出来了但保存失败"。
 */
@TableName("t_plan_part")
public class PlanPart {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long planId;

    /** {@code floor} / {@code wall} / {@code local}。 */
    private String part;

    private Long productId;

    private String skuCode;

    /** 产品名快照(T-1.15 填)。 */
    private String productName;

    /** 单价快照,元/㎡(T-1.15 填)。 */
    private BigDecimal pricePerSqm;

    /** 毛面积,同部位同砖合并后,㎡。 */
    private BigDecimal grossArea;

    /** 有效面积(已扣开口),㎡。扣减是报价环节的事(§8.8),本期留空。 */
    private BigDecimal area;

    /** 损耗率,小数(0.0600 = 6%)。 */
    private BigDecimal lossRate;

    /** 材料费,元。 */
    private BigDecimal materialFee;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getPlanId() {
        return planId;
    }

    public void setPlanId(Long planId) {
        this.planId = planId;
    }

    public String getPart() {
        return part;
    }

    public void setPart(String part) {
        this.part = part;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public String getSkuCode() {
        return skuCode;
    }

    public void setSkuCode(String skuCode) {
        this.skuCode = skuCode;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public BigDecimal getPricePerSqm() {
        return pricePerSqm;
    }

    public void setPricePerSqm(BigDecimal pricePerSqm) {
        this.pricePerSqm = pricePerSqm;
    }

    public BigDecimal getGrossArea() {
        return grossArea;
    }

    public void setGrossArea(BigDecimal grossArea) {
        this.grossArea = grossArea;
    }

    public BigDecimal getArea() {
        return area;
    }

    public void setArea(BigDecimal area) {
        this.area = area;
    }

    public BigDecimal getLossRate() {
        return lossRate;
    }

    public void setLossRate(BigDecimal lossRate) {
        this.lossRate = lossRate;
    }

    public BigDecimal getMaterialFee() {
        return materialFee;
    }

    public void setMaterialFee(BigDecimal materialFee) {
        this.materialFee = materialFee;
    }
}
