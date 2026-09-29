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
            return;
        }

        // 损耗率是"比率":必须落在 (0, 1) 区间。
        // 1 表示 100% 损耗,业务上无意义 —— 出现它几乎一定是量纲写错了。
        if (value.compareTo(BigDecimal.ONE) >= 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID,
                    label() + "必须以小数存储(0.06 表示 6%),当前值 " + value.toPlainString()
                            + " 看起来是按百分数填的");
        }
    }
}
