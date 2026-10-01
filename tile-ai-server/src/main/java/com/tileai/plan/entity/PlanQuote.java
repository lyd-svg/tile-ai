package com.tileai.plan.entity;

import java.math.BigDecimal;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 方案报价汇总(架构 §8.4 {@code t_plan_quote})。
 *
 * <p>与 {@code t_plan_part} 的分工:分项(按部位)在那边,汇总(材料合计 + 辅料 + 总价)
 * 在这边,两处一一对应 PRD §5.5 的公式。
 *
 * <p><b>一个方案至多一行</b>。库里<b>没有</b> {@code plan_id} 唯一键(V4 只建了普通索引
 * {@code idx_plan_quote_plan}),所以这条约束靠写入方维持 —— 一律<b>先删后插</b>,
 * 见 {@code PlanQuoteSnapshotService}。别指望数据库兜底,也别为了这个去加 V11:
 * 加列加索引会连带改 {@code SchemaMigrationTest} 钉死的版本集与列清单。
 *
 * <p>{@code rulesSnapshot} 存的是**生成当时的**辅料单价与损耗率,用于追溯和
 * 「按当前价重算」时说明"这张单当时按什么参数算的"(§8.8、§15.7)。
 */
@TableName("t_plan_quote")
public class PlanQuote {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long planId;

    /** 材料费合计,元。 */
    private BigDecimal materialTotal;

    /** 辅料费,元。 */
    private BigDecimal auxTotal;

    /** 辅料单价快照,元/㎡。 */
    private BigDecimal auxUnitPrice;

    /** 计辅料的面积合计(各部位有效面积之和),㎡。 */
    private BigDecimal auxArea;

    /** 损耗率,小数(0.0600 = 6%)。 */
    private BigDecimal lossRate;

    /** 总价 = 材料合计 + 辅料费,元。 */
    private BigDecimal totalAmount;

    /** 生成时参数快照的 JSON 原文(反序列化为 {@code QuoteResultView.RulesSnapshot})。 */
    private String rulesSnapshot;

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

    public BigDecimal getMaterialTotal() {
        return materialTotal;
    }

    public void setMaterialTotal(BigDecimal materialTotal) {
        this.materialTotal = materialTotal;
    }

    public BigDecimal getAuxTotal() {
        return auxTotal;
    }

    public void setAuxTotal(BigDecimal auxTotal) {
        this.auxTotal = auxTotal;
    }

    public BigDecimal getAuxUnitPrice() {
        return auxUnitPrice;
    }

    public void setAuxUnitPrice(BigDecimal auxUnitPrice) {
        this.auxUnitPrice = auxUnitPrice;
    }

    public BigDecimal getAuxArea() {
        return auxArea;
    }

    public void setAuxArea(BigDecimal auxArea) {
        this.auxArea = auxArea;
    }

    public BigDecimal getLossRate() {
        return lossRate;
    }

    public void setLossRate(BigDecimal lossRate) {
        this.lossRate = lossRate;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public String getRulesSnapshot() {
        return rulesSnapshot;
    }

    public void setRulesSnapshot(String rulesSnapshot) {
        this.rulesSnapshot = rulesSnapshot;
    }
}
