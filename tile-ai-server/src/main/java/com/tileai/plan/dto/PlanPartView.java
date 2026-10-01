package com.tileai.plan.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 方案部位—瓷砖映射 + 报价分项(契约 {@code openapi-common.yaml#/components/schemas/PlanPart})。
 *
 * <p>契约 {@code required: [part, sku, materialFee]},九个属性里其余六个可选。
 *
 * <p><b>费用列可空,且 null 会被整条省略</b>(类上的 {@code NON_NULL})。这不是偷懒:
 * 报价算不出来时(面积未定 / 参数没配 / 产品下架)方案照样保存,此时没有任何一个
 * 真实的数字可以填 —— 填 0 会让用户看到一张"¥0 的报价单"。契约把 {@code materialFee}
 * 标为 required 是**照「报价一定算得出来」的理想情况写的**,而实现必须面对
 * "这一次算不出"。两者的差记在 {@code openapi-verification.md} §41。
 *
 * @param part         部位({@code floor} / {@code wall} / {@code local})
 * @param sku          瓷砖编号
 * @param productName  生成时快照的产品名(不是产品库的当前值)
 * @param pricePerSqm  生成时快照单价,元/㎡
 * @param grossArea    毛面积(同部位同砖合并后),㎡
 * @param area         有效面积(扣开口后),㎡
 * @param lossRate     损耗率,小数(0.0600 = 6%)
 * @param materialFee  该分项材料费,元
 * @param source       该部位的砖是用户选的还是 AI 补的(契约 {@code PartSource})
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PlanPartView(
        String part,
        String sku,
        String productName,
        BigDecimal pricePerSqm,
        BigDecimal grossArea,
        BigDecimal area,
        BigDecimal lossRate,
        BigDecimal materialFee,
        String source) {
}
