package com.tileai.generate;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.tileai.generate.dto.PartSelection;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * T-1.9:{@link PartCompletionService} 的**纯函数**部分(判缺 + 合并)。
 *
 * <p>合并规则是这一卡最容易出错的地方,而且错了**不会报错、只会悄悄错**:
 * AI 覆盖了用户选的砖、或者偷偷补了一个"局部"、或者把同一部位补成两条 ——
 * 每一种都要能画出图、能报出价,只是结果不是用户要的。所以逐条钉住。
 *
 * <p>不起 Spring(没有数据库、没有 MockMvc)—— 这些规则本来就与框架无关。
 */
class PartCompletionServiceTest {

    private static PartSelection part(String part, String sku) {
        return new PartSelection(part, sku, null, null);
    }

    // ------------------------------------------------------------------
    // 缺哪些部位
    // ------------------------------------------------------------------

    @Test
    @DisplayName("缺的是用户没选的那几个(顺序固定:先地面后墙面)")
    void missingIsThePartsNotChosen() {
        assertEquals(List.of("wall"), PartCompletionService.missingOf(List.of(part("floor", "F"))));
        assertEquals(List.of("floor"), PartCompletionService.missingOf(List.of(part("wall", "W"))));
        assertEquals(List.of("floor", "wall"), PartCompletionService.missingOf(List.of()));
    }

    @Test
    @DisplayName("两个都选了就不缺 —— 一个部位都不必补")
    void nothingMissingWhenBothChosen() {
        assertEquals(List.of(),
                PartCompletionService.missingOf(List.of(part("floor", "F"), part("wall", "W"))));
    }

    @Test
    @DisplayName("**局部(local)不在补齐范围内** —— 它是可选部位,PRD §5.2.4")
    void localIsNeverCompletable() {
        // 只选了 local:要补的是 floor + wall,**不该出现 local**
        List<String> missing = PartCompletionService.missingOf(List.of(part("local", "L")));

        assertEquals(List.of("floor", "wall"), missing);
        assertEquals(false, missing.contains("local"));
    }

    @Test
    @DisplayName("同部位多块砖时,那个部位不算缺")
    void samePartTwiceStillCountsAsChosen() {
        assertEquals(List.of("wall"),
                PartCompletionService.missingOf(List.of(part("floor", "F1"), part("floor", "F2"))));
    }

    // ------------------------------------------------------------------
    // 合并
    // ------------------------------------------------------------------

    @Test
    @DisplayName("用户项一律保留,并**强制标回 user**")
    void userPartsAreKeptAndMarkedUser() {
        List<PartSelection> merged = PartCompletionService.merge(
                List.of(new PartSelection("floor", "F", new BigDecimal("3.6"), PartSelection.SOURCE_AI)),
                List.of());

        assertEquals(1, merged.size());
        assertEquals(PartSelection.SOURCE_USER, merged.get(0).source(),
                "外部传什么 source 都不算数 —— 用户选的就是 user");
        assertEquals(new BigDecimal("3.6"), merged.get(0).area(), "用户的面积原样保留");
    }

    @Test
    @DisplayName("补齐项标 ai,且**面积留空**(由服务端依户型派生)")
    void completedPartsAreMarkedAiWithoutArea() {
        List<PartSelection> merged = PartCompletionService.merge(
                List.of(part("floor", "F")),
                List.of(new PartSelection("wall", "W", new BigDecimal("9.9"), PartSelection.SOURCE_AI)));

        assertEquals(2, merged.size());
        PartSelection wall = merged.get(1);
        assertEquals("wall", wall.part());
        assertEquals("W", wall.sku());
        assertEquals(PartSelection.SOURCE_AI, wall.source());
        assertEquals(null, wall.area(), "面积只能由 Java 派生一次,不采信外部传的");
    }

    @Test
    @DisplayName("**同部位冲突:用户赢** —— AI 不得覆盖用户已选的砖")
    void userWinsOnSamePart() {
        List<PartSelection> merged = PartCompletionService.merge(
                List.of(part("floor", "USER-FLOOR")),
                List.of(part("floor", "AI-FLOOR"), part("wall", "AI-WALL")));

        assertEquals(2, merged.size(), "用户的 floor + 补的 wall");
        assertEquals("USER-FLOOR", merged.get(0).sku());
        assertEquals("AI-WALL", merged.get(1).sku());
    }

    @Test
    @DisplayName("**局部与未知部位一律丢弃** —— 防 Python 越界补 local")
    void localAndUnknownPartsAreDropped() {
        List<PartSelection> merged = PartCompletionService.merge(
                List.of(),
                List.of(part("local", "L"), part("ceiling", "C"), part("wall", "W")));

        assertEquals(1, merged.size());
        assertEquals("wall", merged.get(0).part());
    }

    @Test
    @DisplayName("缺 sku 的补齐项丢弃(不能让一个空白 SKU 流进报价)")
    void blankSkuIsDropped() {
        List<PartSelection> merged = PartCompletionService.merge(
                List.of(), List.of(part("wall", "  "), part("floor", null)));

        assertEquals(0, merged.size());
    }

    @Test
    @DisplayName("同一部位只接受一条补齐(防重复回显)")
    void onlyOneCompletionPerPart() {
        List<PartSelection> merged = PartCompletionService.merge(
                List.of(), List.of(part("wall", "W1"), part("wall", "W2")));

        assertEquals(1, merged.size());
        assertEquals("W1", merged.get(0).sku(), "先到的留下");
    }
}
