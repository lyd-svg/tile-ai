package com.tileai.contract;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 契约 ↔ 实现的<b>纯比较器</b>:输入两组端点 + 挂账清单,输出结论与报告文本。
 *
 * <p>刻意<b>不认识 Spring、也不读文件</b> —— 这样负向对照测试可以用合成输入直接调它,
 * 证明它在什么情况下会红。一个"永远返回无差异"的比较器,在真实代码恰好干净时,
 * 输出与正确比较器完全一致;只有负向输入能把两者分开。
 *
 * <h2>比对范围(用户拍板)</h2>
 *
 * <p><b>路径 + HTTP 方法 + 字段名</b>。不比类型 / 必填 / 枚举值。
 *
 * <h2>两处口径</h2>
 *
 * <ul>
 *   <li><b>顶层判得细、嵌套只往同形态深入</b> —— 端点自身的请求体与响应 data 会区分
 *       "没有""有但不是具名对象""是具名对象"三种情况并显式记账;再往里只递归
 *       OBJECT↔OBJECT、ARRAY↔ARRAY,其余组合(类型不同、Map、超深)只累加计数,
 *       不逐条列 —— 否则一个"契约是 string、实现是 object"就能刷出几十行噪声。</li>
 *   <li><b>跳过 ≠ 通过</b> —— 无法比对的一律进"跳过"区并给出原因,与"字段不一致"平级渲染。</li>
 * </ul>
 */
final class ContractImplDiff {

    /** 比对的是哪一块。 */
    enum Where {
        REQUEST("请求体"),
        RESPONSE("响应 data");

        private final String label;

        Where(String label) {
            this.label = label;
        }

        String label() {
            return label;
        }
    }

    /** 某一层上字段名对不齐。 */
    record FieldDiff(String method,
                     String path,
                     Where where,
                     String fieldPath,
                     List<String> contractOnly,
                     List<String> implOnly) {

        String brief() {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("%-6s %s", method, path))
                    .append(" · ").append(where.label())
                    .append(" · ").append(fieldPath);
            if (!contractOnly.isEmpty()) {
                sb.append(" | 契约有实现无: ").append(contractOnly);
            }
            if (!implOnly.isEmpty()) {
                sb.append(" | 实现有契约无: ").append(implOnly);
            }
            return sb.toString();
        }
    }

    /**
     * 一条"无法比对"的记录。
     *
     * @param side  哪一侧(整条 / 请求体 / 响应 data / 实现)
     * @param group 分组名 —— 报告按组收拢。十几条测试探针逐条列只会把真信号淹掉
     */
    record Skipped(String method, String path, String side, String group, String reason) {

        String brief() {
            return String.format("%-6s %s", method, path) + " · " + side + " · " + reason;
        }
    }

    /** 跳过分组名。 */
    static final class Group {

        /** 两侧形态对不上(如契约是具名对象、实现返回 {@code Map})。 */
        static final String SHAPE = "形态无法对位";
        /** 实现侧被豁免(见 contract-impl-gaps.yaml)。 */
        static final String IGNORED = "实现侧被豁免";

        private Group() {
        }
    }

    /** 契约端点撞上了已挂账的缺失。 */
    record GapHit(GapRegistry.Gap gap, ContractEndpoint endpoint) {

        String brief() {
            return String.format("%-6s %s", endpoint.method(), endpoint.path())
                    + " · 出处 " + gap.source()
                    + (gap.note().isEmpty() ? "" : " · " + gap.note());
        }
    }

    /** 挂账过期:登记说"没做",这次却匹配上了。 */
    record StaleGap(GapRegistry.Gap gap) {

        String brief() {
            return gap.brief() + " · 出处 " + gap.source() + " —— 已匹配上,挂账该删了";
        }
    }

    /**
     * 比对结论。计数全部用于恒等式断言 —— 两侧都不许有"凭空消失"的端点。
     *
     * <p>契约侧恒等式:{@code matched + skippedPair + unexpectedMissing + knownMissing == contractJava}
     * <br>实现侧恒等式:{@code matchedImpl + skippedImpl + ignoredImpl + extraImpl == implTotal}
     */
    record Result(int contractJava,
                  int contractPython,
                  int implTotal,
                  int matched,
                  int matchedImpl,
                  int skippedPairCount,
                  int skippedImplCount,
                  int ignoredImplCount,
                  int unknownDataCount,
                  int uncomparedLeaves,
                  List<ContractEndpoint> pythonOwned,
                  List<ContractEndpoint> unexpectedMissing,
                  List<GapHit> knownMissing,
                  List<ImplEndpoint> extraImpl,
                  List<Skipped> ignoredImpl,
                  List<Skipped> skipped,
                  List<FieldDiff> fieldDiffs,
                  List<StaleGap> staleGaps,
                  List<String> brokenCanaries) {

        /** 有"需要人看"的结论。跳过与已知未开工不算。 */
        boolean hasFindings() {
            return !unexpectedMissing.isEmpty()
                    || !extraImpl.isEmpty()
                    || !fieldDiffs.isEmpty()
                    || !staleGaps.isEmpty()
                    || !brokenCanaries.isEmpty();
        }

        /** 契约侧 Java 端点的恒等式:每一条都必须落进恰好一个桶。 */
        void assertContractIdentity() {
            int sum = matched + skippedPairCount
                    + unexpectedMissing.size() + knownMissing.size();
            if (sum != contractJava) {
                throw new IllegalStateException("契约侧计不上账: matched=" + matched
                        + " + skippedPair=" + skippedPairCount
                        + " + unexpected=" + unexpectedMissing.size()
                        + " + known=" + knownMissing.size()
                        + " = " + sum + ",但 Java 端点共 " + contractJava + " 条");
            }
        }

        /** 实现侧路由的恒等式。 */
        void assertImplIdentity() {
            int sum = matchedImpl + skippedImplCount + ignoredImplCount + extraImpl.size();
            if (sum != implTotal) {
                throw new IllegalStateException("实现侧计不上账: matched=" + matchedImpl
                        + " + skippedImpl=" + skippedImplCount
                        + " + ignored=" + ignoredImplCount
                        + " + extra=" + extraImpl.size()
                        + " = " + sum + ",但实现路由共 " + implTotal + " 条");
            }
        }
    }

    /** 可变计数盒 —— 递归里累加"没比到的叶子"。 */
    private static final class Counter {
        private int value;
    }

    private ContractImplDiff() {
    }

    // ------------------------------------------------------------ 主流程

    static Result diff(List<ContractEndpoint> contract,
                       List<ImplEndpoint> impl,
                       GapRegistry gaps) {
        if (contract.isEmpty()) {
            throw new IllegalStateException("契约侧解析出 0 条端点 —— 空集合不许进入比较");
        }
        if (impl.isEmpty()) {
            throw new IllegalStateException("实现侧读到 0 条路由 —— 空集合不许进入比较");
        }

        List<ContractEndpoint> sortedContract = new ArrayList<>(contract);
        sortedContract.sort(Comparator.comparing(ContractEndpoint::path)
                .thenComparing(ContractEndpoint::method));

        List<ImplEndpoint> sortedImpl = new ArrayList<>(impl);
        sortedImpl.sort(Comparator.comparing(ImplEndpoint::path)
                .thenComparing(ImplEndpoint::method));

        List<ContractEndpoint> pythonOwned = new ArrayList<>();
        List<ContractEndpoint> unexpectedMissing = new ArrayList<>();
        List<GapHit> knownMissing = new ArrayList<>();
        List<ImplEndpoint> extraImpl = new ArrayList<>();
        List<Skipped> ignoredImpl = new ArrayList<>();
        List<Skipped> skipped = new ArrayList<>();
        List<FieldDiff> fieldDiffs = new ArrayList<>();
        Counter leaves = new Counter();

        Map<PathKey, List<ImplEndpoint>> implByKey = new LinkedHashMap<>();
        for (ImplEndpoint endpoint : sortedImpl) {
            implByKey.computeIfAbsent(endpoint.key(), k -> new ArrayList<>()).add(endpoint);
        }

        Set<PathKey> javaContractKeys = new LinkedHashSet<>();
        int matched = 0;
        int skippedPairCount = 0;
        int unknownDataCount = 0;

        for (ContractEndpoint endpoint : sortedContract) {
            if (endpoint.owner() == ContractEndpoint.Owner.PYTHON) {
                pythonOwned.add(endpoint);
                continue;
            }
            javaContractKeys.add(endpoint.key());

            List<ImplEndpoint> candidates = implByKey.get(endpoint.key());
            if (candidates == null || candidates.isEmpty()) {
                GapRegistry.Gap gap = gaps.gapFor(endpoint.method(), endpoint.path());
                if (gap == null) {
                    unexpectedMissing.add(endpoint);
                } else {
                    knownMissing.add(new GapHit(gap, endpoint));
                }
                continue;
            }
            ImplEndpoint twin = candidates.get(0);
            if (twin.skippable()) {
                skippedPairCount++;
                skipped.add(new Skipped(endpoint.method(), endpoint.path(), "整条",
                        twin.skipGroup(), twin.skipReason() + "(" + twin.where() + ")"));
                continue;
            }
            matched++;

            // 契约没声明字段、实现也不是具名对象 → 这一块根本无从比。只报数,不逐条列:
            // 裸 ApiResponse 的接口有二三十个,逐条列出来会把真正的信号淹掉。
            unknownDataCount += silentGap(endpoint.requestShape(), twin.requestShape()) ? 1 : 0;
            unknownDataCount += silentGap(endpoint.responseShape(), twin.responseShape()) ? 1 : 0;

            if (!silentGap(endpoint.requestShape(), twin.requestShape())) {
                compareBody(fieldDiffs, skipped, leaves, endpoint, Where.REQUEST,
                        endpoint.requestShape(), twin.requestShape());
            }
            if (!silentGap(endpoint.responseShape(), twin.responseShape())) {
                compareBody(fieldDiffs, skipped, leaves, endpoint, Where.RESPONSE,
                        endpoint.responseShape(), twin.responseShape());
            }
        }

        // 实现侧:每条路由按 跳过 > 豁免 > 匹配 > 多余 的优先级,恰好归进一个桶
        int matchedImpl = 0;
        int skippedImplCount = 0;
        int ignoredImplCount = 0;
        for (ImplEndpoint endpoint : sortedImpl) {
            if (endpoint.skippable()) {
                skippedImplCount++;
                skipped.add(new Skipped(endpoint.method(), endpoint.path(), "实现",
                        endpoint.skipGroup(), endpoint.skipReason() + "(" + endpoint.where() + ")"));
                continue;
            }
            String ignoreReason = gaps.ignoreReason(endpoint.path());
            if (ignoreReason != null) {
                ignoredImplCount++;
                ignoredImpl.add(new Skipped(endpoint.method(), endpoint.path(), "实现",
                        Group.IGNORED, ignoreReason + "(" + endpoint.where() + ")"));
                continue;
            }
            if (javaContractKeys.contains(endpoint.key())) {
                matchedImpl++;
            } else {
                extraImpl.add(endpoint);
            }
        }

        // 挂账过期:登记为"没做",这次却在实现里出现了
        Set<PathKey> presentKeys = new LinkedHashSet<>();
        for (ContractEndpoint endpoint : sortedContract) {
            if (endpoint.owner() == ContractEndpoint.Owner.JAVA
                    && implByKey.containsKey(endpoint.key())) {
                presentKeys.add(endpoint.key());
            }
        }
        List<StaleGap> staleGaps = new ArrayList<>();
        for (GapRegistry.Gap gap : gaps.knownGaps()) {
            if (presentKeys.contains(gap.key())) {
                staleGaps.add(new StaleGap(gap));
            }
        }

        Result result = new Result(
                sortedContract.size() - pythonOwned.size(), pythonOwned.size(),
                sortedImpl.size(), matched, matchedImpl, skippedPairCount,
                skippedImplCount, ignoredImplCount, unknownDataCount, leaves.value,
                pythonOwned, unexpectedMissing, knownMissing, extraImpl, ignoredImpl,
                skipped, fieldDiffs, staleGaps,
                checkCanaries(gaps, sortedContract, implByKey));

        result.assertContractIdentity();
        result.assertImplIdentity();
        return result;
    }

    // ------------------------------------------------------------ 一整块的比对

    /**
     * 契约没声明字段、实现那边也不是具名对象 —— 这一块<b>无从比</b>。
     *
     * <p>这类只报数不逐条列。反例(要报的)是"契约没声明、实现却给了具名字段":
     * 那说明契约漏写了前端必须知道的东西,属于真信号,会落到 {@link #compareBody} 里报出来。
     */
    private static boolean silentGap(Shape contractShape, Shape implShape) {
        return contractShape.kind() == Shape.Kind.UNKNOWN
                && implShape.kind() != Shape.Kind.OBJECT;
    }

    /**
     * 比"一整块"(端点自己的请求体 / 响应 data)。
     *
     * <p>这里判得细:没有 / 有但不是具名对象 / 是具名对象,三种情况分开记账。
     */
    private static void compareBody(List<FieldDiff> diffs,
                                    List<Skipped> skipped,
                                    Counter leaves,
                                    ContractEndpoint endpoint,
                                    Where where,
                                    Shape contractShape,
                                    Shape implShape) {
        Shape.Kind ck = contractShape.kind();
        Shape.Kind ik = implShape.kind();

        if (ck == Shape.Kind.NONE && ik == Shape.Kind.NONE) {
            return;   // 两边都没这一块 —— 一致
        }
        if (ck == Shape.Kind.OBJECT && ik == Shape.Kind.OBJECT) {
            diffFields(diffs, leaves, endpoint, where, "(顶层)", contractShape, implShape);
            return;
        }
        if (ck == Shape.Kind.ARRAY && ik == Shape.Kind.ARRAY) {
            compareNested(diffs, leaves, endpoint, where, "[]",
                    contractShape.item(), implShape.item());
            return;
        }
        if (ck == Shape.Kind.NONE && ik == Shape.Kind.OBJECT) {
            // 契约没声明请求体/响应字段,实现却给了 —— 也要报,别让它悄悄溜过
            diffs.add(new FieldDiff(endpoint.method(), endpoint.path(), where, "(顶层)",
                    List.of(), new ArrayList<>(implShape.fieldNames())));
            return;
        }
        if (ck == Shape.Kind.OBJECT && ik == Shape.Kind.NONE) {
            diffs.add(new FieldDiff(endpoint.method(), endpoint.path(), where, "(顶层)",
                    new ArrayList<>(contractShape.fieldNames()), List.of()));
            return;
        }
        skipped.add(new Skipped(endpoint.method(), endpoint.path(), where.label(),
                Group.SHAPE, "契约=" + contractShape.label() + ", 实现=" + implShape.label()));
    }

    /** 同一层上比字段名,再对公共字段往里走一层。 */
    private static void diffFields(List<FieldDiff> diffs,
                                   Counter leaves,
                                   ContractEndpoint endpoint,
                                   Where where,
                                   String fieldPath,
                                   Shape contractShape,
                                   Shape implShape) {
        Set<String> contractFields = new TreeSet<>(contractShape.fieldNames());
        Set<String> implFields = new TreeSet<>(implShape.fieldNames());

        List<String> contractOnly = new ArrayList<>(contractFields);
        contractOnly.removeAll(implFields);
        List<String> implOnly = new ArrayList<>(implFields);
        implOnly.removeAll(contractFields);

        if (!contractOnly.isEmpty() || !implOnly.isEmpty()) {
            diffs.add(new FieldDiff(endpoint.method(), endpoint.path(), where, fieldPath,
                    contractOnly, implOnly));
        }
        for (String name : contractFields) {
            if (!implFields.contains(name)) {
                continue;
            }
            compareNested(diffs, leaves, endpoint, where, fieldPath + "." + name,
                    contractShape.fields().get(name), implShape.fields().get(name));
        }
    }

    /**
     * 嵌套层:只往<b>同形态</b>里走。
     *
     * <p>其余组合(类型不同、Map、additionalProperties、超过深度上限)只累加计数 ——
     * 那些属于"类型层"的差异,不在本次范围内,逐条列出来只会淹没真正要看的字段名差异。
     * 计数会出现在报告页眉(作"嵌套层未对位的叶子"),所以也没被藏起来。
     */
    private static void compareNested(List<FieldDiff> diffs,
                                      Counter leaves,
                                      ContractEndpoint endpoint,
                                      Where where,
                                      String fieldPath,
                                      Shape contractShape,
                                      Shape implShape) {
        Shape.Kind ck = contractShape.kind();
        Shape.Kind ik = implShape.kind();
        if (ck == Shape.Kind.OBJECT && ik == Shape.Kind.OBJECT) {
            diffFields(diffs, leaves, endpoint, where, fieldPath, contractShape, implShape);
            return;
        }
        if (ck == Shape.Kind.ARRAY && ik == Shape.Kind.ARRAY) {
            compareNested(diffs, leaves, endpoint, where, fieldPath + "[]",
                    contractShape.item(), implShape.item());
            return;
        }
        leaves.value++;
    }

    // ------------------------------------------------------------ 锚点

    /**
     * 锚点必须<b>两侧都在</b>(Python 归属的只查契约侧 —— 它本来就该由 Python 实现)。
     *
     * <p>这是防"最隐蔽的假绿"的最后一层:路径前缀拼接写错时,一条都匹配不上,
     * 报告会空空如也,看起来像全对。
     */
    private static List<String> checkCanaries(GapRegistry gaps,
                                              List<ContractEndpoint> contract,
                                              Map<PathKey, List<ImplEndpoint>> implByKey) {
        Map<PathKey, ContractEndpoint> contractByKey = new LinkedHashMap<>();
        for (ContractEndpoint endpoint : contract) {
            contractByKey.putIfAbsent(endpoint.key(), endpoint);
        }
        List<String> broken = new ArrayList<>();
        for (String canary : gaps.canaries()) {
            int space = canary.indexOf(' ');
            PathKey key = PathKey.of(canary.substring(0, space), canary.substring(space + 1));
            ContractEndpoint endpoint = contractByKey.get(key);
            if (endpoint == null) {
                broken.add(canary + " —— 契约侧没有这条路径(锚点写错了,或契约改了名)");
                continue;
            }
            if (endpoint.owner() == ContractEndpoint.Owner.JAVA && !implByKey.containsKey(key)) {
                broken.add(canary + " —— 实现侧没读到这条路由(可能路径前缀拼接写错)");
            }
        }
        return broken;
    }

    // ------------------------------------------------------------ 渲染

    static String render(Result r) {
        StringBuilder sb = new StringBuilder();
        sb.append("# 契约 ↔ 实现比对清单(T-2.1 骨架)\n\n");
        sb.append("> 由 `ContractImplDiffTest` 自动生成,请勿手改。\n")
                .append("> 比对范围:**路径 + HTTP 方法 + 字段名**;不比类型 / 必填 / 枚举值。\n\n");

        sb.append("## 页眉计数\n\n| 项 | 数 |\n| --- | ---: |\n");
        row(sb, "契约端点(Java 归属)", r.contractJava());
        row(sb, "契约端点(Python 归属,本骨架不比)", r.contractPython());
        row(sb, "实现路由(从路由表读出)", r.implTotal());
        row(sb, "两边匹配上并比过", r.matched());
        row(sb, "匹配上但整条跳过(非 JSON 等)", r.skippedPairCount());
        row(sb, "契约有实现无 · **意外缺失**", r.unexpectedMissing().size());
        row(sb, "契约有实现无 · 已挂账(已知未开工)", r.knownMissing().size());
        row(sb, "实现有契约无 · 多余路由", r.extraImpl().size());
        row(sb, "实现侧被豁免", r.ignoredImplCount());
        row(sb, "字段不一致", r.fieldDiffs().size());
        row(sb, "契约未声明字段 · 只报数(裸 ApiResponse 等,无从比)", r.unknownDataCount());
        row(sb, "嵌套层未对位的叶子", r.uncomparedLeaves());
        row(sb, "挂账已过期", r.staleGaps().size());
        row(sb, "锚点未命中", r.brokenCanaries().size());
        sb.append('\n');

        sb.append("**本骨架覆盖不到**(不许读成「已绑住」):类型 / 必填 / 格式 / ")
                .append("**枚举值**(§31.2 那次真实分叉正是枚举值)· 状态码与业务错误码 · ")
                .append("query / path / header 参数名 · 嵌套深度 > 2 · `Map` 与 ")
                .append("`additionalProperties` · 运行期改写(序列化 inclusion、")
                .append("`@ControllerAdvice`)。\n\n");

        if (r.hasFindings()) {
            sb.append("## ⚠ 需要人看的\n\n");
            block(sb, "锚点未命中(比对本身可能没跑对)", r.brokenCanaries());
            block(sb, "契约有、实现无 —— **意外缺失**(没有挂账出处)",
                    r.unexpectedMissing().stream()
                            .map(e -> e.brief() + " · " + e.origin()).toList());
            block(sb, "实现有、契约无 —— 多余路由", r.extraImpl().stream()
                    .map(e -> e.brief() + " · " + e.where()).toList());
            block(sb, "字段不一致", r.fieldDiffs().stream().map(FieldDiff::brief).toList());
            block(sb, "挂账已过期", r.staleGaps().stream().map(StaleGap::brief).toList());
        } else {
            sb.append("## ✅ 需要人看的:无\n\n");
        }

        sb.append("## 已知未开工(挂了账,带出处)\n\n");
        if (r.knownMissing().isEmpty()) {
            sb.append("(无)\n\n");
        } else {
            sb.append("| 方法 | 路径 | 出处 | 说明 |\n| --- | --- | --- | --- |\n");
            for (GapHit hit : r.knownMissing()) {
                sb.append("| ").append(hit.endpoint().method())
                        .append(" | `").append(hit.endpoint().path()).append("` | ")
                        .append(hit.gap().source()).append(" | ")
                        .append(hit.gap().note()).append(" |\n");
            }
            sb.append('\n');
        }

        block(sb, "Python 归属(本骨架不比,见 openapi-verification.md §36)",
                r.pythonOwned().stream().map(e -> e.brief() + " · " + e.origin()).toList());

        skippedBlock(sb, "无法比对(跳过 ≠ 通过)", r.skipped());
        skippedBlock(sb, "实现侧被豁免", r.ignoredImpl());

        return sb.toString();
    }

    /**
     * 跳过区按原因分组渲染。
     *
     * <p>逐条列"测试源码探针"是纯噪声(它们每次运行都在,且永远无法比对),会把
     * 真正要看的东西挤下去 —— 所以那一组只报数。其余组逐条列:它们数量少,且每条都可能
     * 是真实盲区。
     */
    private static void skippedBlock(StringBuilder sb, String title, List<Skipped> entries) {
        sb.append("## ").append(title).append('\n');
        if (entries.isEmpty()) {
            sb.append("\n(无)\n\n");
            return;
        }
        Map<String, List<Skipped>> byGroup = new LinkedHashMap<>();
        for (Skipped entry : entries) {
            byGroup.computeIfAbsent(entry.group(), k -> new ArrayList<>()).add(entry);
        }
        sb.append("\n共 ").append(entries.size()).append(" 条,按原因分组:\n\n");
        for (Map.Entry<String, List<Skipped>> group : byGroup.entrySet()) {
            sb.append("### ").append(group.getKey())
                    .append("(").append(group.getValue().size()).append(" 条)\n\n");
            if (ImplReader.Group.TEST_PROBE.equals(group.getKey())) {
                sb.append("测试源码里的探针控制器,会被组件扫描注册进路由表。")
                        .append("它们不属于对外契约,也不参与比对 —— 明细见测试源码,此处不逐条列。\n\n");
                continue;
            }
            for (Skipped entry : group.getValue()) {
                sb.append("- ").append(entry.brief()).append('\n');
            }
            sb.append('\n');
        }
    }

    private static void row(StringBuilder sb, String name, int value) {
        sb.append("| ").append(name).append(" | ").append(value).append(" |\n");
    }

    private static void block(StringBuilder sb, String title, List<String> lines) {
        sb.append("## ").append(title).append('\n');
        if (lines.isEmpty()) {
            sb.append("\n(无)\n\n");
            return;
        }
        sb.append("\n共有 ").append(lines.size()).append(" 条:\n\n");
        for (String line : lines) {
            sb.append("- ").append(line).append('\n');
        }
        sb.append('\n');
    }
}
