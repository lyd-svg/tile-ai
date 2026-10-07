package com.tileai.layout;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.tileai.layout.model.LayoutAreas;
import com.tileai.layout.model.LayoutModel;
import com.tileai.layout.model.LayoutSpace;
import com.tileai.layout.model.Opening;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * T-0.9:面积推导公式(架构 §8.3「面积推导公式」,v1.5 补充)。
 *
 * <p>公式在本轮之前**上游是空白** —— 文档只定义了"有效面积 = 毛面积 − 开口",
 * 却没写毛面积本身怎么来。口径分歧会直接改变报价金额,所以这里逐条钉死。
 */
class LayoutAreaCalculatorTest {

    private static LayoutSpace space(String length, String width, String height) {
        return new LayoutSpace("bathroom", "卫生间",
                new BigDecimal(length), new BigDecimal(width), new BigDecimal(height),
                4, List.of(), null);
    }

    @Test
    @DisplayName("floor = 长 × 宽")
    void floorIsLengthTimesWidth() {
        LayoutAreas areas = LayoutAreaCalculator.compute(space("2.0", "1.8", "2.7"));
        assertDecimal("3.6000", areas.floor());
    }

    @Test
    @DisplayName("wall = 2 × (长 + 宽) × 层高,即周长 × 层高")
    void wallIsPerimeterTimesHeight() {
        // 2 × (2.0 + 1.8) × 2.7 = 20.52
        LayoutAreas areas = LayoutAreaCalculator.compute(space("2.0", "1.8", "2.7"));
        assertDecimal("20.5200", areas.wall());
    }

    @Test
    @DisplayName("local 默认为 0(腰线/花砖按实际铺贴,无默认值)")
    void localDefaultsToZero() {
        LayoutAreas areas = LayoutAreaCalculator.compute(space("5.0", "4.0", "2.8"));
        assertDecimal("0.0000", areas.local());
    }

    @Test
    @DisplayName("面积保留 4 位小数 —— 与 §8.4 的 DECIMAL(10,4) 一致")
    void areasKeepFourDecimals() {
        // 2.15 × 1.85 = 3.9775;若提前四舍五入到 2 位,会在"面积×单价"里被放大成金额误差
        LayoutAreas areas = LayoutAreaCalculator.compute(space("2.15", "1.85", "2.7"));
        assertDecimal("3.9775", areas.floor());
        assertEquals(4, areas.floor().scale());
    }

    @Test
    @DisplayName("公式与 §8.3 示例的 floor 一致(示例 floor=3.6 正是 2.0×1.8)")
    void derivationMatchesExampleFloor() {
        LayoutAreas areas = LayoutAreaCalculator.compute(space("2.0", "1.8", "2.7"));
        assertEquals(0, new BigDecimal("3.6").compareTo(areas.floor()),
                "floor 的公式与架构 §8.3 示例吻合");
    }

    @Test
    @DisplayName("wallCount 不参与公式:改墙面数不改变墙面面积")
    void wallCountDoesNotAffectArea() {
        LayoutSpace fourWalls = space("2.0", "1.8", "2.7");
        LayoutSpace sixWalls = new LayoutSpace("bathroom", "卫生间",
                new BigDecimal("2.0"), new BigDecimal("1.8"), new BigDecimal("2.7"),
                6, List.of(), null);

        assertDecimal("20.5200", LayoutAreaCalculator.compute(fourWalls).wall());
        assertDecimal("20.5200", LayoutAreaCalculator.compute(sixWalls).wall(),
                "wallCount 是独立的记录字段,不改变'周长×层高'的墙面面积口径");
    }

    @Test
    @DisplayName("开口不参与毛面积:开口是报价环节扣减的,不在这里减")
    void openingsDoNotReduceGrossArea() {
        LayoutSpace withOpenings = new LayoutSpace("bathroom", "卫生间",
                new BigDecimal("2.0"), new BigDecimal("1.8"), new BigDecimal("2.7"), 4,
                List.of(new Opening("door", new BigDecimal("0.8"), new BigDecimal("2.1"),
                                "back", new BigDecimal("0.55"), null),
                        new Opening("window", new BigDecimal("1.0"), new BigDecimal("1.2"),
                                "right", new BigDecimal("0.90"), new BigDecimal("0.9"))),
                null);

        assertDecimal("20.5200", LayoutAreaCalculator.compute(withOpenings).wall(),
                "算的是毛面积;扣开口得到有效面积属报价环节(§8.8)");
    }

    // ------------------------------------------------------------------
    // 只补不覆盖
    // ------------------------------------------------------------------

    @Test
    @DisplayName("已显式给出 areas 时原样采用,不被公式覆盖")
    void explicitAreasAreNotOverwritten() {
        LayoutAreas given = new LayoutAreas(
                new BigDecimal("99.1"), new BigDecimal("88.2"), new BigDecimal("7.3"));
        LayoutSpace withAreas = new LayoutSpace("bathroom", "卫生间",
                new BigDecimal("2.0"), new BigDecimal("1.8"), new BigDecimal("2.7"),
                4, List.of(), given);

        LayoutSpace result = LayoutAreaCalculator.withDerivedAreas(withAreas);
        assertSame(given, result.areas(),
                "人工核过的面积不该被公式改写 —— 架构 §8.3 明确'只补不覆盖'");
    }

    @Test
    @DisplayName("省略 areas 时补上,已有 areas 的空间保持原值")
    void derivesOnlyMissingAreas() {
        LayoutSpace missing = space("2.0", "1.8", "2.7");
        LayoutAreas given = new LayoutAreas(
                new BigDecimal("10.0"), new BigDecimal("20.0"), new BigDecimal("0.0"));
        LayoutSpace present = new LayoutSpace("bedroom", "卧室",
                new BigDecimal("3.0"), new BigDecimal("3.0"), new BigDecimal("2.8"),
                4, List.of(), given);

        LayoutModel model = LayoutAreaCalculator.withDerivedAreas(
                new LayoutModel(List.of(missing, present)));

        assertNotNull(model.spaces().get(0).areas(), "缺面积的空间应被补上");
        assertDecimal("3.6000", model.spaces().get(0).areas().floor());
        assertSame(given, model.spaces().get(1).areas(), "已有面积的空间应原样保留");
    }

    @Test
    @DisplayName("null 输入不炸:模板层的校验会先拦住它")
    void nullTolerance() {
        assertEquals(null, LayoutAreaCalculator.withDerivedAreas((LayoutSpace) null));
        assertEquals(null, LayoutAreaCalculator.withDerivedAreas((LayoutModel) null));
    }

    private static void assertDecimal(String expected, BigDecimal actual) {
        assertDecimal(expected, actual, null);
    }

    private static void assertDecimal(String expected, BigDecimal actual, String message) {
        assertNotNull(actual, message);
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                (message == null ? "" : message + " —— ")
                        + "期望 " + expected + ",实际 " + actual.toPlainString());
    }
}
