package com.tileai.layout;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.tileai.layout.model.LayoutAreas;
import com.tileai.layout.model.LayoutModel;
import com.tileai.layout.model.LayoutSpace;

/**
 * 户型面积推导(架构 §8.3「面积推导公式」,v1.5 补充)。
 *
 * <pre>
 *   floor = length × width
 *   wall  = 2 × (length + width) × height      // 周长 × 层高
 *   local = 0                                   // 腰线/花砖按实际铺贴,无默认值
 * </pre>
 *
 * <p><b>只补不覆盖</b>:若调用方已显式给出 {@code areas},原样采用 ——
 * 人工核过的准确面积不该被公式改写。
 *
 * <p><b>算的是毛面积</b>。扣减门窗开口得到有效面积是报价环节的事(§8.8),
 * 不在这里做:同一份户型既可能用于出图也可能用于报价,毛面积是更基础的口径。
 *
 * <p><b>为什么这个类值得单独存在</b>:面积口径一旦分歧会直接改变报价金额。
 * 把公式收在一处、由测试钉住,比让每个调用点各算一遍安全得多。
 */
public final class LayoutAreaCalculator {

    /**
     * 面积小数位。
     *
     * <p>取 4 位与架构 §8.4 的 {@code t_plan_part.gross_area} / {@code area}
     * ({@code DECIMAL(10,4)})一致 —— 面积是报价乘法的输入,提前四舍五入到 2 位
     * 会在"面积 × 单价"里被放大成金额误差。
     */
    public static final int AREA_SCALE = 4;

    private static final BigDecimal TWO = new BigDecimal("2");

    private LayoutAreaCalculator() {
    }

    /** 对整份户型补齐面积;已有面积的直接沿用。 */
    public static LayoutModel withDerivedAreas(LayoutModel model) {
        if (model == null || model.spaces() == null) {
            return model;
        }
        return new LayoutModel(model.spaces().stream()
                .map(LayoutAreaCalculator::withDerivedAreas)
                .toList());
    }

    /** 对单个空间补齐面积;已有面积的直接沿用。 */
    public static LayoutSpace withDerivedAreas(LayoutSpace space) {
        if (space == null || space.areas() != null) {
            return space;
        }
        return new LayoutSpace(space.type(), space.name(), space.length(), space.width(),
                space.height(), space.wallCount(), space.openings(), compute(space));
    }

    /**
     * 按公式计算毛面积。
     *
     * <p>不判断 {@code areas} 是否已存在 —— 需要"只补不覆盖"语义请用
     * {@link #withDerivedAreas(LayoutSpace)}。
     */
    public static LayoutAreas compute(LayoutSpace space) {
        BigDecimal floor = space.length().multiply(space.width());
        BigDecimal wall = space.length().add(space.width())
                .multiply(TWO)
                .multiply(space.height());
        return new LayoutAreas(scale(floor), scale(wall), scale(BigDecimal.ZERO));
    }

    private static BigDecimal scale(BigDecimal value) {
        return value.setScale(AREA_SCALE, RoundingMode.HALF_UP);
    }
}
