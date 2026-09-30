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

    private static ContractEndpoint byKey(List<ContractEndpoint> all, String method, String path) {
        PathKey key = PathKey.of(method, path);
        return all.stream().filter(e -> e.key().equals(key)).findFirst()
                .orElseThrow(() -> new AssertionError("契约里找不到 " + key));
    }
}
