package com.tileai.quote.entity;

import java.math.BigDecimal;
import java.util.Arrays;

import com.tileai.common.error.BusinessException;
import com.tileai.common.error.ErrorCode;

/**
 * 报价参数键(架构 §8.5 {@code t_quote_param};
 * 契约 {@code openapi-admin.yaml#/components/schemas/QuoteParamUpsert.paramKey} 的 enum)。
 *
 * <p><b>量纲是这个枚举存在的主要理由</b>(D-19):三个键的量纲不同 ——
 * {@code aux_unit_price} 是<b>元/㎡</b>,两个损耗率是<b>小数</b>(0.06 表示 6%)。
 * 量纲写错不会报错,只会让报价整体差 100 倍,所以把校验挂在枚举上。
 */
public enum QuoteParamKey {

    /** 辅料单价,单位 <b>元/㎡</b>(PRD §5.5:按行业标准取值)。 */
    AUX_UNIT_PRICE("aux_unit_price", "辅料单价", true),

    /**
     * <b>当前采用的损耗率</b>,小数(D-15:0.06 表示 6%)。
     *
     * <p>这是**唯一参与报价计算**的损耗率。另外两个 {@code loss_rate_min/max} 是它的
     * 允许范围(PRD §5.5「默认 6%,范围 5%~8%」),只用于校验与提示。
     *
     * <p>拆成三个键的原因:min/max 是一对**区间**,从中推不出当前采用的那个值,
     * 而损耗率乘在每一个报价金额上 —— 让实现去猜不会有任何报错。见架构 §8.8「参数来源」。
     */
    LOSS_RATE("loss_rate", "损耗率", false),

    /** 损耗率下限(**允许范围**,不参与计算)。 */
    LOSS_RATE_MIN("loss_rate_min", "损耗率下限", false),

    /** 损耗率上限(**允许范围**,不参与计算)。 */
    LOSS_RATE_MAX("loss_rate_max", "损耗率上限", false);

    /**
     * 单价量纲的**上限**(元/㎡)。
     *
     * <p>真实辅料单价(水泥砂浆等)在 20~50 之间,1000 已是 20 倍余量 ——
     * 它只挡"手滑多打几个零",不会误伤任何正常值。
     *
     * <p>⚠️ 2026-10-07 补:此前单价量纲在 {@link #validate} 里**直接 return**,
     * 一个字都不校验;而它乘在每一个报价金额上,量级一大就撑爆
     * {@code t_plan_quote.total_amount} 的 {@code DECIMAL(12,2)}。
     */
    public static final BigDecimal MAX_UNIT_PRICE = new BigDecimal("1000");

    private final String code;
    private final String label;
    /** 是否为单价量纲(元/㎡);否则为小数比率量纲。 */
    private final boolean unitPrice;

    QuoteParamKey(String code, String label, boolean unitPrice) {
        this.code = code;
        this.label = label;
        this.unitPrice = unitPrice;
    }

    public String code() {
        return code;
    }

    public String label() {
        return label;
    }

    public boolean isUnitPrice() {
        return unitPrice;
    }

    /**
     * 本参数是否<b>乘进报价金额</b> —— 改了它,全店所有已生成的报价立刻失真(T-1.15)。
     *
     * <p>白名单是**刻意逐项列举**的:{@code loss_rate_min/max} 与 {@code loss_rate} 一样
     * 都不是单价量纲,若按 {@link #isUnitPrice()} 反推会把它俩也算进来,
     * 于是「把允许范围从 5%~8% 调成 4%~9%」会错误地把全表方案标成过期 ——
     * 而这两个键根本不参与计算(见本枚举 {@code LOSS_RATE} 的注释)。
     * 将来新增参数键时,这个方法是唯一需要重新判断的地方。
     *
     * @return 指派价计算要用的那两个键({@code aux_unit_price} / {@code loss_rate})
     */
    public boolean affectsQuote() {
        return this == AUX_UNIT_PRICE || this == LOSS_RATE;
    }

    public static QuoteParamKey of(String code) {
        return code == null ? null
                : Arrays.stream(values()).filter(k -> k.code.equals(code)).findFirst().orElse(null);
    }

    /**
     * 按量纲校验取值。
     *
     * <p>拦的是这类错误:把 6% 写成 {@code 6} 而不是 {@code 0.06}。
     * 那会让报价金额整体偏离 100 倍,而<b>数据库、接口、页面都不会报错</b> ——
     * 只有对账时才会发现,且极难定位。此处直接拒绝。
     *
     * @throws BusinessException {@code 10001} 取值不符合该键的量纲
     */
    public void validate(BigDecimal value) {
        if (value == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, label() + "不能为空");
        }
        if (value.signum() <= 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, label() + "必须大于 0");
        }

        if (isUnitPrice()) {
            // ⚠️ **上限是 2026-10-07 补的**。此前单价量纲直接 `return` ——
            // **一个字都不校验**,填个一千万照样存进去,而它乘在**每一个**报价金额上,
            // 大一点就会撑爆 `t_plan_quote.total_amount` 的 DECIMAL(12,2)。
            if (value.compareTo(MAX_UNIT_PRICE) > 0) {
                throw new BusinessException(ErrorCode.PARAM_INVALID,
                        label() + "不能超过 " + MAX_UNIT_PRICE.toPlainString() + " 元/㎡,当前值 "
                                + value.toPlainString() + " —— 这个量级几乎一定是填错了");
            }
            return;
        }

        // 损耗率是"比率":必须落在 (0, 1) 区间。
        // 1 表示 100% 损耗,业务上无意义 —— 出现它几乎一定是量纲写错了。
        if (value.compareTo(BigDecimal.ONE) >= 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID,
                    label() + "必须以小数存储(0.06 表示 6%),当前值 " + value.toPlainString()
                            + " 看起来是按百分数填的");
        }

        // ⚠️ **「是否落在 5%~8% 的允许区间里」不在这里校验** ——
        // 那要读后台配的 `loss_rate_min/max`,而本枚举**不取数**(它没有 DB 访问)。
        // 那一条在 `QuoteParamService.upsert` 里做,与这次写入同一个事务。
        // (此前**两级都没有**:填 0.5 —— 50% 损耗 —— 照样能过,而它乘在
        //  每一个报价金额上,全店报价会一起膨胀 50 倍且没有任何提示。)
    }
}
