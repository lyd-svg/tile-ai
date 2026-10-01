package com.tileai.plan;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.tileai.layout.model.LayoutModel;
import com.tileai.layout.model.LayoutSpace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * 定空间规则的<b>四条分支</b>(T-1.15)。
 *
 * <p>这段逻辑从 {@code PlanAutoSaveService} 原样搬进 {@link PlanSpaces},由生成路径与
 * 重算路径共用。为什么值得单测:它是"生成时算出的价"与"重算后的价"是否相等的唯一
 * 变量 —— 只要两边对同一个户型定出不同的空间,面积就会不同,金额就会分叉,
 * 而两边各自的集成测试都可能是绿的。
 *
 * <p>第三条分支(多空间、无匹配 → 取第一个并 WARN)**不改报错**:出图已经成功、
 * 模型调用也计过费了,仅仅因为定不出面积就把方案丢掉是最坏的选择。
 * 但它也意味着:<b>多空间户型并不存在"保守不报价"的短路</b> ——
 * {@code openapi-verification.md} §41.2 曾如此声称,已按事实订正(见 §42)。
 */
class PlanSpacesTest {

    @Test
    @DisplayName("户型为空 / 没有空间 → null(面积推不出来,由调用方决定怎么处置)")
    void emptyLayoutHasNoSpace() {
        assertNull(PlanSpaces.resolve(null, "bathroom"), "layout 为 null");
        assertNull(PlanSpaces.resolve(new LayoutModel(List.of()), "bathroom"), "spaces 为空清单");
    }

    @Test
    @DisplayName("只有一个空间 → 就是它,不看 type 对不对得上")
    void singleSpaceWinsRegardlessOfType() {
        LayoutSpace only = space("bathroom");

        // 用户录了个卫生间、却把空间选成"其他" —— 这种不一致不该让方案存不下来
        assertSame(only, PlanSpaces.resolve(new LayoutModel(List.of(only)), "other"));
        assertSame(only, PlanSpaces.resolve(new LayoutModel(List.of(only)), null));
    }

    @Test
    @DisplayName("多空间 → 取 type 与请求 space 相同的第一个")
    void multiSpacePicksTheMatchingType() {
        LayoutSpace living = space("living_room");
        LayoutSpace bath1 = space("bathroom");
        LayoutSpace bath2 = space("bathroom");

        assertSame(bath1, PlanSpaces.resolve(new LayoutModel(List.of(living, bath1, bath2)), "bathroom"),
                "同类型出现两次时取**第一个** —— 顺序必须稳定,否则两次算价可能取到不同空间");
        assertSame(living, PlanSpaces.resolve(new LayoutModel(List.of(living, bath1, bath2)), "living_room"));
    }

    @Test
    @DisplayName("多空间且没有匹配的 → 取第一个(不抛异常),而不是保守地不报价")
    void multiSpaceWithoutMatchFallsBackToTheFirst() {
        LayoutSpace first = space("living_room");
        LayoutSpace second = space("bathroom");
        LayoutModel layout = new LayoutModel(List.of(first, second));

        assertSame(first, PlanSpaces.resolve(layout, "kitchen"),
                "没有匹配时取第一个并记 WARN —— **不报错**。若这里改成返回 null,"
                        + "『多空间户型落进保守分支』那件事才会成立;当前事实是不落");
        assertSame(first, PlanSpaces.resolve(layout, null), "space 为 null 走同一兜底");
    }

    @Test
    @DisplayName("空间的 type 为 null 时不会被误当成匹配")
    void nullTypeNeverMatches() {
        LayoutSpace noType = space(null);
        LayoutSpace bath = space("bathroom");

        assertSame(bath, PlanSpaces.resolve(new LayoutModel(List.of(noType, bath)), "bathroom"),
                "type 为 null 的空间不能因为 spaceKey 也是 null 就被选中");
    }

    private static LayoutSpace space(String type) {
        return new LayoutSpace(type, null, new BigDecimal("3.00"), new BigDecimal("2.00"),
                new BigDecimal("2.70"), 4, null, null);
    }
}
