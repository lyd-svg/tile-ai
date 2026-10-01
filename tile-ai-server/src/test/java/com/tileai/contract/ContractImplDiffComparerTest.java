package com.tileai.contract;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 比较器的<b>负向对照</b> —— 证明它在什么情况下会红。
 *
 * <p>为什么这一组非有不可:一个"永远返回无差异"的比较器,在真实代码恰好干净时,
 * 输出与正确比较器<b>完全一致</b>(两者都是"没发现问题")。只有拿合成输入喂它、
 * 并断言"这种情况必须报出来",才能把两者分开。
 *
 * <p>全部<b>不启 Spring</b>:比较器只吃两个值对象(见 {@link ContractImplDiff} 的类注释)。
 */
class ContractImplDiffComparerTest {

    // ------------------------------------------------------------ 路由层

    @Test
    @DisplayName("契约有、实现无 → 意外缺失(没挂账)")
    void missingRouteIsReported() {
        ContractImplDiff.Result r = diff(
                List.of(contract("GET", "/a"), contract("GET", "/b")),
                List.of(impl("GET", "/a")),
                GapRegistry.synthetic(List.of(), List.of()));

        assertEquals(1, r.unexpectedMissing().size());
        assertEquals("/b", r.unexpectedMissing().get(0).path());
        assertEquals(1, r.matched());
    }

    @Test
    @DisplayName("实现有、契约无 → 多余路由")
    void extraRouteIsReported() {
        ContractImplDiff.Result r = diff(
                List.of(contract("GET", "/a")),
                List.of(impl("GET", "/a"), impl("GET", "/c")),
                GapRegistry.synthetic(List.of(), List.of()));

        assertEquals(1, r.extraImpl().size());
        assertEquals("/c", r.extraImpl().get(0).path());
        assertTrue(r.hasFindings());
    }

    @Test
    @DisplayName("路径变量改名({id} vs {sku})不算差异")
    void pathVariableRenameIsNotADiff() {
        ContractImplDiff.Result r = diff(
                List.of(contract("GET", "/products/{id}")),
                List.of(impl("GET", "/products/{sku}")),
                GapRegistry.synthetic(List.of(), List.of()));

        assertEquals(1, r.matched());
        assertFalse(r.hasFindings(), "路径变量名不在比对范围内,不该报");
    }

    @Test
    @DisplayName("已挂账的缺失不算意外,挂账过了期要报")
    void knownGapAndStaleGap() {
        // 挂了账、实现确实没有 → 进"已知未开工",不算意外
        ContractImplDiff.Result planned = diff(
                List.of(contract("GET", "/a"), contract("GET", "/b")),
                List.of(impl("GET", "/a")),
                GapRegistry.synthetic(
                        List.of(new GapRegistry.Gap("GET", "/b", "T-9.9", "还没做")),
                        List.of()));
        assertEquals(1, planned.knownMissing().size());
        assertEquals(0, planned.unexpectedMissing().size());
        assertTrue(planned.staleGaps().isEmpty());

        // 同一份挂账,这次实现里已经有了 → 挂账过期
        ContractImplDiff.Result stale = diff(
                List.of(contract("GET", "/b")),
                List.of(impl("GET", "/b")),
                GapRegistry.synthetic(
                        List.of(new GapRegistry.Gap("GET", "/b", "T-9.9", "还没做")),
                        List.of()));
        assertEquals(1, stale.staleGaps().size());
        assertTrue(stale.hasFindings(), "挂账过期是「清单在漂移」的信号,必须让人看见");
    }

    @Test
    @DisplayName("锚点没命中 → 判红(路径拼接写错时不会静默全绿)")
    void brokenCanaryIsReported() {
        ContractImplDiff.Result r = diff(
                List.of(contract("GET", "/a")),
                List.of(impl("GET", "/a")),
                GapRegistry.synthetic(List.of(), List.of("POST /api/v1/nope")));

        assertEquals(1, r.brokenCanaries().size());
        assertTrue(r.hasFindings());
    }

    @Test
    @DisplayName("任一侧为空 → 直接抛,空集合不许进入比较")
    void emptySideIsNotSilentGreen() {
        assertThrows(IllegalStateException.class, () -> diff(
                List.of(contract("GET", "/a")), List.of(),
                GapRegistry.synthetic(List.of(), List.of())));
        assertThrows(IllegalStateException.class, () -> diff(
                List.of(), List.of(impl("GET", "/a")),
                GapRegistry.synthetic(List.of(), List.of())));
    }

    // ------------------------------------------------------------ 字段层

    @Test
    @DisplayName("响应少字段 / 多字段都要报")
    void responseFieldDiffIsReported() {
        ContractImplDiff.Result missing = diff(
                List.of(contract("GET", "/a", Shape.NONE, obj("id", "name"))),
                List.of(impl("GET", "/a", Shape.NONE, obj("id"))),
                GapRegistry.synthetic(List.of(), List.of()));
        assertEquals(1, missing.fieldDiffs().size());
        assertEquals(List.of("name"), missing.fieldDiffs().get(0).contractOnly());

        ContractImplDiff.Result extra = diff(
                List.of(contract("GET", "/a", Shape.NONE, obj("id"))),
                List.of(impl("GET", "/a", Shape.NONE, obj("id", "sneaky"))),
                GapRegistry.synthetic(List.of(), List.of()));
        assertEquals(List.of("sneaky"), extra.fieldDiffs().get(0).implOnly());
    }

    @Test
    @DisplayName("请求体少字段也要报")
    void requestFieldDiffIsReported() {
        ContractImplDiff.Result r = diff(
                List.of(contract("POST", "/a", obj("sku", "qty"), Shape.NONE)),
                List.of(impl("POST", "/a", obj("sku"), Shape.NONE)),
                GapRegistry.synthetic(List.of(), List.of()));

        assertEquals(1, r.fieldDiffs().size());
        assertEquals(ContractImplDiff.Where.REQUEST, r.fieldDiffs().get(0).where());
        assertEquals(List.of("qty"), r.fieldDiffs().get(0).contractOnly());
    }

    @Test
    @DisplayName("数组元素的字段要往里比(list[] 这一层)")
    void arrayItemFieldDiffIsReported() {
        ContractImplDiff.Result r = diff(
                List.of(contract("GET", "/a", Shape.NONE, Shape.array(obj("sku", "price")))),
                List.of(impl("GET", "/a", Shape.NONE, Shape.array(obj("sku")))),
                GapRegistry.synthetic(List.of(), List.of()));

        assertEquals(1, r.fieldDiffs().size());
        assertEquals("[]", r.fieldDiffs().get(0).fieldPath());
        assertEquals(List.of("price"), r.fieldDiffs().get(0).contractOnly());
    }

    @Test
    @DisplayName("嵌套对象的字段在第二层也要比")
    void nestedObjectFieldDiffIsReported() {
        // {"outer": {"a": .., "b": ..}}  vs  {"outer": {"a": ..}}
        ContractImplDiff.Result r = diff(
                List.of(contract("GET", "/a", Shape.NONE, objOf("outer", obj("a", "b")))),
                List.of(impl("GET", "/a", Shape.NONE, objOf("outer", obj("a")))),
                GapRegistry.synthetic(List.of(), List.of()));

        assertEquals(1, r.fieldDiffs().size());
        assertEquals("(顶层).outer", r.fieldDiffs().get(0).fieldPath());
        assertEquals(List.of("b"), r.fieldDiffs().get(0).contractOnly());
    }

    @Test
    @DisplayName("Map 响应记「跳过」而**不是**「通过」")
    void mapResponseIsSkippedNotPassed() {
        ContractImplDiff.Result r = diff(
                List.of(contract("GET", "/a", Shape.NONE, obj("id"))),
                List.of(impl("GET", "/a", Shape.NONE, Shape.MAP)),
                GapRegistry.synthetic(List.of(), List.of()));

        assertEquals(0, r.fieldDiffs().size(), "没比到东西,所以不算字段不一致");
        assertEquals(1, r.skipped().size(), "但也不能算通过 —— 必须出现在跳过区");
        assertEquals(ContractImplDiff.Group.SHAPE, r.skipped().get(0).group());
    }

    @Test
    @DisplayName("契约未声明请求体、实现却要求字段 → 要报")
    void undeclaredRequestBodyIsReported() {
        ContractImplDiff.Result r = diff(
                List.of(contract("POST", "/a", Shape.NONE, Shape.NONE)),
                List.of(impl("POST", "/a", obj("qty"), Shape.NONE)),
                GapRegistry.synthetic(List.of(), List.of()));

        assertEquals(1, r.fieldDiffs().size());
        assertEquals(List.of("qty"), r.fieldDiffs().get(0).implOnly());
    }

    // ------------------------------------------------------------ 枚举取值层

    @Test
    @DisplayName("枚举取值漂移 → 判红(§31.2 的真实形态:契约 [door,window],实现多出 bay_window)")
    void enumValueDriftIsReported() {
        ContractImplDiff.Result wider = diff(
                List.of(contract("POST", "/a", objOf("type", enumOf("door", "window")), Shape.NONE)),
                List.of(impl("POST", "/a",
                        objOf("type", enumOf("door", "window", "bay_window")), Shape.NONE)),
                GapRegistry.synthetic(List.of(), List.of()));

        assertEquals(1, wider.mismatchesOf(ContractImplDiff.Dimension.ENUM));
        ContractImplDiff.FieldMismatch m = wider.fieldMismatches().get(0);
        assertEquals(ContractImplDiff.Dimension.ENUM, m.dimension());
        assertEquals(ContractImplDiff.Where.REQUEST, m.where());
        assertEquals("(顶层).type", m.fieldPath());
        assertTrue(wider.hasFindings());

        // 反向也算漂移:实现比契约窄 —— 照契约传来的值会被实现拒掉
        ContractImplDiff.Result narrower = diff(
                List.of(contract("POST", "/a", objOf("type", enumOf("door", "window")), Shape.NONE)),
                List.of(impl("POST", "/a", objOf("type", enumOf("door")), Shape.NONE)),
                GapRegistry.synthetic(List.of(), List.of()));
        assertEquals(1, narrower.mismatchesOf(ContractImplDiff.Dimension.ENUM));
        assertTrue(narrower.hasFindings());
    }

    @Test
    @DisplayName("枚举取值一致 → 不报(否则新维度会天天假红)")
    void sameEnumValuesAreNotReported() {
        ContractImplDiff.Result r = diff(
                List.of(contract("POST", "/a", objOf("type", enumOf("door", "window")), Shape.NONE)),
                List.of(impl("POST", "/a", objOf("type", enumOf("window", "door")), Shape.NONE)),
                GapRegistry.synthetic(List.of(), List.of()));

        assertEquals(0, r.fieldMismatches().size(), "取值集合相同,与顺序无关");
        assertFalse(r.hasFindings());
    }

    @Test
    @DisplayName("契约声明了取值、实现侧推不出(走后台字典)→ 记跳过,既不算通过也不算红")
    void enumWithoutImplSourceIsSkippedNotPassed() {
        ContractImplDiff.Result r = diff(
                List.of(contract("POST", "/a", objOf("spaceType", enumOf("floor", "wall")), Shape.NONE)),
                List.of(impl("POST", "/a",
                        objOf("spaceType", scalar(Shape.JsonType.STRING)), Shape.NONE)),
                GapRegistry.synthetic(List.of(), List.of()));

        assertEquals(0, r.fieldMismatches().size(), "推不出不等于不一致");
        assertEquals(1, r.skipped().size(), "但也不能算通过 —— 必须出现在跳过区并计数");
        assertEquals(ContractImplDiff.Group.ENUM_UNKNOWN, r.skipped().get(0).group());
        assertFalse(r.hasFindings());
    }

    // ------------------------------------------------------------ 类型层

    @Test
    @DisplayName("类型:JSON Schema 里 integer ⊂ number,只有反方向才算差异")
    void integerSatisfiesNumberContract() {
        ContractImplDiff.Result ok = diff(
                List.of(contract("GET", "/a", Shape.NONE, objOf("price", scalar(Shape.JsonType.NUMBER)))),
                List.of(impl("GET", "/a", Shape.NONE, objOf("price", scalar(Shape.JsonType.INTEGER)))),
                GapRegistry.synthetic(List.of(), List.of()));
        assertEquals(0, ok.mismatchesOf(ContractImplDiff.Dimension.TYPE),
                "契约 number 收实现 integer 是合法的,不该报");

        ContractImplDiff.Result bad = diff(
                List.of(contract("GET", "/a", Shape.NONE, objOf("qty", scalar(Shape.JsonType.INTEGER)))),
                List.of(impl("GET", "/a", Shape.NONE, objOf("qty", scalar(Shape.JsonType.NUMBER)))),
                GapRegistry.synthetic(List.of(), List.of()));
        assertEquals(1, bad.mismatchesOf(ContractImplDiff.Dimension.TYPE),
                "契约 integer 却给了 number,小数传得进来 —— 必须报");
        assertTrue(bad.hasFindings());
    }

    @Test
    @DisplayName("类型:任一侧说不出类型就不比(不猜,也不假装一致)")
    void unknownTypeIsNotCompared() {
        // Shape.SCALAR 是"类型未知的裸标量",实现侧大量使用
        ContractImplDiff.Result r = diff(
                List.of(contract("GET", "/a", Shape.NONE, objOf("id", scalar(Shape.JsonType.STRING)))),
                List.of(impl("GET", "/a", Shape.NONE, objOf("id", Shape.SCALAR))),
                GapRegistry.synthetic(List.of(), List.of()));

        assertEquals(0, r.mismatchesOf(ContractImplDiff.Dimension.TYPE));
        assertFalse(r.hasFindings());
    }

    // ------------------------------------------------------------ 叶子计数(别把"没判过"混进"比过")

    @Test
    @DisplayName("叶子计数:两侧类型都说得出 → 记「比过」")
    void typedLeafIsCountedAsCompared() {
        ContractImplDiff.Result r = diff(
                List.of(contract("GET", "/a", Shape.NONE, objOf("id", scalar(Shape.JsonType.STRING)))),
                List.of(impl("GET", "/a", Shape.NONE, objOf("id", scalar(Shape.JsonType.STRING)))),
                GapRegistry.synthetic(List.of(), List.of()));
        assertEquals(1, r.comparedLeaves());
        assertEquals(0, r.uncomparedLeaves(), "判过的叶子不该被算成「没判过」");
    }

    @Test
    @DisplayName("叶子计数:一侧类型推不出 / 深度截断 → 记「没判过」,不得算进「比过」")
    void untypedLeafIsCountedAsUncompared() {
        // 这两个数拆开的理由:早先只有一个计数器,标签写着"未对位",实际把**所有**叶子
        // 都算进去了 —— 于是"没判过"被"比过"的几百分淹没,而"跳过 ≠ 通过"就丢在这里。
        ContractImplDiff.Result untyped = diff(
                List.of(contract("GET", "/a", Shape.NONE, objOf("id", scalar(Shape.JsonType.STRING)))),
                List.of(impl("GET", "/a", Shape.NONE, objOf("id", Shape.SCALAR))),
                GapRegistry.synthetic(List.of(), List.of()));
        assertEquals(0, untyped.comparedLeaves(), "类型推不出的叶子没有真的判过");
        assertEquals(1, untyped.uncomparedLeaves());

        ContractImplDiff.Result truncated = diff(
                List.of(contract("GET", "/a", Shape.NONE, objOf("deep", Shape.TRUNCATED))),
                List.of(impl("GET", "/a", Shape.NONE, objOf("deep", Shape.TRUNCATED))),
                GapRegistry.synthetic(List.of(), List.of()));
        assertEquals(0, truncated.comparedLeaves(), "深度截断的叶子属「没判过」");
        assertEquals(1, truncated.uncomparedLeaves());
        assertFalse(truncated.hasFindings(),
                "「没判过」不是分叉、不判红 —— 但它必须被计数,绝不能当成一致");
    }

    // ------------------------------------------------------------ 必填层

    @Test
    @DisplayName("必填只在请求体侧比:响应侧即便写了 required 也不算")
    void requiredIsOnlyComparedOnRequestSide() {
        ContractImplDiff.Result response = diff(
                List.of(contract("GET", "/a", Shape.NONE, requiredObj(Set.of("id"), "id"))),
                List.of(impl("GET", "/a", Shape.NONE, obj("id"))),
                GapRegistry.synthetic(List.of(), List.of()));
        assertEquals(0, response.mismatchesOf(ContractImplDiff.Dimension.REQUIRED),
                "Java 的响应没有「必填」概念,不该比");

        ContractImplDiff.Result request = diff(
                List.of(contract("POST", "/a", requiredObj(Set.of("sku"), "sku"), Shape.NONE)),
                List.of(impl("POST", "/a", obj("sku"), Shape.NONE)),
                GapRegistry.synthetic(List.of(), List.of()));
        assertEquals(1, request.mismatchesOf(ContractImplDiff.Dimension.REQUIRED));
        assertEquals(ContractImplDiff.Where.REQUEST, request.fieldMismatches().get(0).where());
        assertTrue(request.hasFindings());
    }

    // ------------------------------------------------------------ 字段级挂账(三段规则)

    @Test
    @DisplayName("字段级挂账被命中 → 折叠进「已知差异」,可见但不判红")
    void fieldGapHitIsVisibleButNotRed() {
        GapRegistry.FieldGap gap = new GapRegistry.FieldGap(
                "POST", "/a", "request", "type", "enum", "T-9.9", "有意大一圈");

        ContractImplDiff.Result r = diff(
                List.of(contract("POST", "/a", objOf("type", enumOf("door", "window")), Shape.NONE)),
                List.of(impl("POST", "/a",
                        objOf("type", enumOf("door", "window", "bay_window")), Shape.NONE)),
                GapRegistry.syntheticWithFieldGaps(List.of(), List.of(), List.of(gap)));

        assertEquals(1, r.fieldGapHits().size());
        assertEquals(0, r.fieldMismatches().size(), "挂了账的不算意外分叉");
        assertTrue(r.staleFieldGaps().isEmpty());
        assertTrue(r.unevaluatedFieldGaps().isEmpty());
        assertFalse(r.hasFindings());
    }

    @Test
    @DisplayName("字段级挂账过期(本次已一致)→ 判红,豁免表不许变成漂移的藏身处")
    void staleFieldGapIsRed() {
        GapRegistry.FieldGap gap = new GapRegistry.FieldGap(
                "POST", "/a", "request", "type", "enum", "T-9.9", "曾经不一致");

        ContractImplDiff.Result r = diff(
                List.of(contract("POST", "/a", objOf("type", enumOf("door", "window")), Shape.NONE)),
                List.of(impl("POST", "/a", objOf("type", enumOf("door", "window")), Shape.NONE)),
                GapRegistry.syntheticWithFieldGaps(List.of(), List.of(), List.of(gap)));

        assertEquals(1, r.staleFieldGaps().size());
        assertTrue(r.hasFindings());
    }

    @Test
    @DisplayName("字段级挂账未生效(端点还没开工)→ 单列,不判红")
    void unevaluatedFieldGapOnMissingEndpointIsNotRed() {
        GapRegistry.FieldGap gap = new GapRegistry.FieldGap(
                "POST", "/a", "request", "type", "enum", "T-9.9", "还没开工");

        ContractImplDiff.Result r = diff(
                List.of(contract("POST", "/a", objOf("type", enumOf("door", "window")), Shape.NONE),
                        contract("POST", "/keep", Shape.NONE, Shape.NONE)),
                List.of(impl("POST", "/keep", Shape.NONE, Shape.NONE)),
                GapRegistry.syntheticWithFieldGaps(
                        List.of(new GapRegistry.Gap("POST", "/a", "T-9.9", "还没开工")),
                        List.of(), List.of(gap)));

        assertEquals(1, r.unevaluatedFieldGaps().size());
        assertTrue(r.staleFieldGaps().isEmpty(),
                "端点还没做时,挂账不许被误判「过期」—— 否则会天天红");
        assertFalse(r.hasFindings());
    }

    @Test
    @DisplayName("字段级挂账未生效(维度推不出)→ 同样不判红")
    void unevaluatedFieldGapOnUninferableDimensionIsNotRed() {
        // 实现侧是走后台字典的字段,枚举取值推不出 —— 这一处本次根本没判过
        GapRegistry.FieldGap gap = new GapRegistry.FieldGap(
                "POST", "/a", "request", "spaceType", "enum", "T-9.9", "字典字段");

        ContractImplDiff.Result r = diff(
                List.of(contract("POST", "/a", objOf("spaceType", enumOf("floor", "wall")), Shape.NONE)),
                List.of(impl("POST", "/a",
                        objOf("spaceType", scalar(Shape.JsonType.STRING)), Shape.NONE)),
                GapRegistry.syntheticWithFieldGaps(List.of(), List.of(), List.of(gap)));

        assertEquals(1, r.unevaluatedFieldGaps().size(),
                "「没判过」不能被当成「判过且一致」");
        assertTrue(r.staleFieldGaps().isEmpty());
        assertFalse(r.hasFindings());
    }

    @Test
    @DisplayName("字段级挂账:重复键直接抛(两条挂账抢同一处,谁也说不清哪条算数)")
    void duplicateFieldGapKeyIsRejected() {
        GapRegistry.FieldGap a = new GapRegistry.FieldGap(
                "POST", "/a", "request", "type", "enum", "T-1", "第一条");
        GapRegistry.FieldGap b = new GapRegistry.FieldGap(
                "POST", "/a", "request", "type", "enum", "T-2", "第二条");

        assertThrows(IllegalStateException.class,
                () -> GapRegistry.syntheticWithFieldGaps(List.of(), List.of(), List.of(a, b)));
    }

    // ------------------------------------------------------------ 真契约(不启 Spring)

    @Test
    @DisplayName("真契约:跨文件 $ref、allOf 覆盖、信封剥离都按预期解析")
    void realContractIsParsedAsExpected() {
        ContractReader reader = new ContractReader();
        List<ContractEndpoint> all = reader.readAll();
        for (String file : ContractReader.FILES) {
            assertFalse(reader.read(file).isEmpty(), file + " 应该解析出端点");
        }

        ContractEndpoint products = byKey(all, "GET", "/api/v1/products");
        assertEquals(ContractEndpoint.Owner.JAVA, products.owner());

        Shape data = products.responseShape();
        assertEquals(Shape.Kind.OBJECT, data.kind(), "信封应被剥掉,留下 data");
        assertEquals(Set.of("list", "total", "page", "size"), data.fieldNames(),
                "PageData 的字段应经跨文件 $ref + allOf 合并得到");
        assertFalse(data.fieldNames().contains("code"), "信封字段不该出现在 data 里");

        Shape item = data.fields().get("list").item();
        assertEquals(Shape.Kind.OBJECT, item.kind(),
                "allOf 后一个成员覆写了 PageData 里开放的 items:{},应以 Sku 为准");
        assertTrue(item.fieldNames().contains("pricePerSqm"),
                "Sku 的字段应被解析出来,实际: " + item.fieldNames());
    }

    @Test
    @DisplayName("真契约:internal 的归属由 x-direction 自证")
    void internalOwnershipComesFromContract() {
        List<ContractEndpoint> all = new ContractReader().read("openapi-internal.yaml");

        assertEquals(ContractEndpoint.Owner.JAVA, byKey(all, "GET", "/internal/products/{sku}").owner());
        assertEquals(ContractEndpoint.Owner.JAVA, byKey(all, "POST", "/internal/generate/callback").owner());
        assertEquals(ContractEndpoint.Owner.PYTHON, byKey(all, "POST", "/internal/agent/chat").owner());
        assertEquals(ContractEndpoint.Owner.PYTHON,
                byKey(all, "POST", "/internal/agent/material-map").owner());
    }

    @Test
    @DisplayName("真清单:挂账项都带出处、豁免都带理由、锚点格式合法")
    void realGapRegistryIsWellFormed() {
        GapRegistry gaps = GapRegistry.load();

        assertFalse(gaps.knownGaps().isEmpty());
        assertFalse(gaps.canaries().isEmpty());
        // load() 里已经校验过 source/reason/锚点格式;这里再钉一遍数量不为零,
        // 防的是"清单被清空"这种最安静的失效
        assertNotNull(gaps.ignoreReason("/error"), "/error 应被豁免");
        assertNull(gaps.ignoreReason("/api/v1/products"), "正常路由不该被豁免");
    }

    // ------------------------------------------------------------ 脚手架

    private static ContractImplDiff.Result diff(List<ContractEndpoint> contract,
                                                List<ImplEndpoint> impl,
                                                GapRegistry gaps) {
        return ContractImplDiff.diff(contract, impl, gaps);
    }

    private static ContractEndpoint contract(String method, String path) {
        return contract(method, path, Shape.NONE, Shape.NONE);
    }

    private static ContractEndpoint contract(String method, String path,
                                             Shape request, Shape response) {
        return new ContractEndpoint(method, path, "openapi-test.yaml",
                ContractEndpoint.Owner.JAVA, request, response, "测试用");
    }

    private static ImplEndpoint impl(String method, String path) {
        return impl(method, path, Shape.NONE, Shape.NONE);
    }

    private static ImplEndpoint impl(String method, String path, Shape request, Shape response) {
        return new ImplEndpoint(method, path, "TestController", "handler", request, response,
                null, null);
    }

    private static Shape obj(String... names) {
        Map<String, Shape> fields = new LinkedHashMap<>();
        for (String name : names) {
            fields.put(name, Shape.SCALAR);
        }
        return Shape.object(fields);
    }

    private static Shape objOf(String name, Shape value) {
        Map<String, Shape> fields = new LinkedHashMap<>();
        fields.put(name, value);
        return Shape.object(fields);
    }

    /** 一个"必填集合非空"的对象 —— 顶上那条 Shape.object(fields) 不带必填。 */
    private static Shape requiredObj(Set<String> required, String... names) {
        Map<String, Shape> fields = new LinkedHashMap<>();
        for (String name : names) {
            fields.put(name, Shape.SCALAR);
        }
        return Shape.object(fields, required);
    }

    private static Shape scalar(Shape.JsonType type) {
        return Shape.scalar(type, Set.of());
    }

    /** 只有这几个取值可填的标量(契约 enum / 实现 @Pattern、Java enum 都归到这里)。 */
    private static Shape enumOf(String... values) {
        return Shape.scalar(Shape.JsonType.STRING, Set.of(values));
    }

    private static ContractEndpoint byKey(List<ContractEndpoint> all, String method, String path) {
        PathKey key = PathKey.of(method, path);
        return all.stream().filter(e -> e.key().equals(key)).findFirst()
                .orElseThrow(() -> new AssertionError("契约里找不到 " + key));
    }
}
