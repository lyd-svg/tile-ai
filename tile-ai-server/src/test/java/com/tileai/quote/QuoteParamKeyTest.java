package com.tileai.quote;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.tileai.common.error.BusinessException;
import com.tileai.common.error.ErrorCode;
import com.tileai.quote.entity.QuoteParamKey;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T-0.11:报价参数的量纲校验(D-19 / D-15)。
 *
 * <p>拦的是一类<b>不会报错的错</b>:损耗率该填 {@code 0.06} 却填了 {@code 6}。
 * 数据库收得下、接口返回成功、页面看起来正常 —— 只有所有报价金额悄悄偏了 100 倍。
 * 这类问题对账时才发现,且极难定位,所以必须在写入侧就拒掉。
 */
class QuoteParamKeyTest {

    @Test
    @DisplayName("D-19:辅料单价以 元/㎡ 计,正整数即可")
    void auxUnitPriceAcceptsPositiveValues() {
        assertDoesNotThrow(() -> QuoteParamKey.AUX_UNIT_PRICE.validate(new BigDecimal("20")));
        assertDoesNotThrow(() -> QuoteParamKey.AUX_UNIT_PRICE.validate(new BigDecimal("35.50")));
        // 单价大于 1 是正常的 —— 它和"比率必须小于 1"是两套量纲
        assertDoesNotThrow(() -> QuoteParamKey.AUX_UNIT_PRICE.validate(new BigDecimal("268")));
    }

    @Test
    @DisplayName("辅料单价超出上限会被拒(2026-10-07 补)")
    void auxUnitPriceRejectsAbsurdValues() {
        // ⚠️ 此前单价量纲在 validate 里**直接 return** —— 一个字都不校验。
        // 填个天文数字照样存进去,而它乘在**每一个**报价金额上,
        // 量级一大就撑爆 t_plan_quote.total_amount 的 DECIMAL(12,2)。
        BusinessException ex = assertThrows(BusinessException.class,
                () -> QuoteParamKey.AUX_UNIT_PRICE.validate(new BigDecimal("100000000")));

        assertEquals(ErrorCode.PARAM_INVALID, ex.errorCode());

        // ⚠️ **上限本身必须放行** —— 这条防的是"修过头把正常值也挡住"。
        assertDoesNotThrow(() -> QuoteParamKey.AUX_UNIT_PRICE.validate(QuoteParamKey.MAX_UNIT_PRICE));
    }

    @Test
    @DisplayName("D-15:损耗率以小数存储 —— 0.06 表示 6%,合法")
    void lossRateAcceptsFractions() {
        assertDoesNotThrow(() -> QuoteParamKey.LOSS_RATE_MIN.validate(new BigDecimal("0.06")));
        assertDoesNotThrow(() -> QuoteParamKey.LOSS_RATE_MAX.validate(new BigDecimal("0.08")));
        assertDoesNotThrow(() -> QuoteParamKey.LOSS_RATE_MIN.validate(new BigDecimal("0.05")));
    }

    @Test
    @DisplayName("D-15 反例:损耗率填百分数(6 而非 0.06)会被拒")
    void lossRateRejectsPercentStyleValues() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> QuoteParamKey.LOSS_RATE_MIN.validate(new BigDecimal("6")));

        assertEquals(ErrorCode.PARAM_INVALID, ex.errorCode());
        assertTrue(ex.getMessage().contains("0.06"),
                "错误信息应明确指出正确写法,否则运营不知道该填什么: " + ex.getMessage());
    }

    @Test
    @DisplayName("损耗率为 1 也会被拒 —— 100% 损耗业务上无意义,几乎一定是量纲写错")
    void lossRateRejectsOneAndAbove() {
        assertThrows(BusinessException.class,
                () -> QuoteParamKey.LOSS_RATE_MAX.validate(BigDecimal.ONE));
        assertThrows(BusinessException.class,
                () -> QuoteParamKey.LOSS_RATE_MAX.validate(new BigDecimal("1.5")));
    }

    @Test
    @DisplayName("零与负数一律拒绝")
    void nonPositiveIsRejected() {
        assertThrows(BusinessException.class,
                () -> QuoteParamKey.AUX_UNIT_PRICE.validate(BigDecimal.ZERO));
        assertThrows(BusinessException.class,
                () -> QuoteParamKey.LOSS_RATE_MIN.validate(new BigDecimal("-0.06")));
        assertThrows(BusinessException.class,
                () -> QuoteParamKey.LOSS_RATE_MIN.validate(null));
    }

    @Test
    @DisplayName("三个参数键与契约 enum 一致")
    void keysMatchContract() {
        assertEquals("aux_unit_price", QuoteParamKey.AUX_UNIT_PRICE.code());
        assertEquals("loss_rate_min", QuoteParamKey.LOSS_RATE_MIN.code());
        assertEquals("loss_rate_max", QuoteParamKey.LOSS_RATE_MAX.code());
        assertEquals(null, QuoteParamKey.of("no_such_key"));
    }

    @Test
    @DisplayName("只有辅料单价是单价量纲,两个损耗率是比率量纲")
    void unitPriceFlagIsCorrect() {
        assertTrue(QuoteParamKey.AUX_UNIT_PRICE.isUnitPrice());
        assertTrue(!QuoteParamKey.LOSS_RATE_MIN.isUnitPrice());
        assertTrue(!QuoteParamKey.LOSS_RATE_MAX.isUnitPrice());
    }
}
