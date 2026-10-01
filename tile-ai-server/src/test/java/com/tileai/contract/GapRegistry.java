package com.tileai.contract;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.yaml.snakeyaml.Yaml;

/**
 * 读「挂账 / 豁免 / 锚点」清单(类路径上的 {@value #RESOURCE})。
 *
 * <p>存在的理由:一个还没开工的接口和一个<b>被悄悄做没了</b>的接口,在"契约有、实现无"
 * 这个现象上长得一模一样。若不区分,第一遍清单会是一大片噪声,久而久之没人看 ——
 * 典型的门禁疲劳。所以未开工必须<b>登记出处</b>,登记过的折叠,没登记的高亮。
 *
 * <p>三条硬约束(在 {@link #load()} 里当场校验,不靠自觉):
 * <ol>
 *   <li>{@code source} 不许为空 —— 挂账必须指明出处(任务号或需求条目);</li>
 *   <li>豁免必须写明理由;</li>
 *   <li>锚点(canary)格式必须是 {@code "METHOD /path"}。</li>
 * </ol>
 */
final class GapRegistry {

    static final String RESOURCE = "/contract-impl-gaps.yaml";

    /** 一条挂账:契约里写了、实现里还没有,且<b>知道为什么</b>。 */
    record Gap(String method, String path, String source, String note) {

        PathKey key() {
            return PathKey.of(method, path);
        }

        String brief() {
            return key().toString();
        }
    }

    /**
     * 一条<b>字段级</b>挂账:端点两侧都在,但某个字段在某个维度上确实不一致,且知道为什么。
     *
     * <p>与 {@link Gap} 的区别:那个管"整条路由还没做",这个管"路由做了,但这一处对不上"。
     * 两者都<b>必须带出处</b>,且都会因为"过期"(这次已经一致了)判红。
     *
     * @param where      {@code request} / {@code response}
     * @param fieldPath  报告里那种去掉 {@code (顶层).} 前缀的路径,如 {@code parts[].area}
     * @param dimension  {@code type} / {@code enum} / {@code required}
     */
    record FieldGap(String method,
                    String path,
                    String where,
                    String fieldPath,
                    String dimension,
                    String source,
                    String note) {

        /** 与 {@link ContractImplDiff#mismatchKey} 同一套归一化 —— 命中判定必须逐字一致。 */
        String key() {
            return ContractImplDiff.mismatchKey(method, path, where, fieldPath, dimension);
        }

        String brief() {
            return String.format("%-6s %s", method, path)
                    + " · " + where + " · " + fieldPath + " · " + dimension
                    + " · 出处 " + source;
        }
    }

    private static final Set<String> WHERE_VALUES = Set.of("request", "response");
    private static final Set<String> DIMENSION_VALUES = Set.of("type", "enum", "required");

    private final List<Gap> knownGaps;
    private final Map<String, String> ignoredImplPaths;
    private final List<String> canaries;
    private final List<FieldGap> fieldGaps;
    private final Map<String, FieldGap> fieldGapsByKey;

    GapRegistry(List<Gap> knownGaps,
                Map<String, String> ignoredImplPaths,
                List<String> canaries,
                List<FieldGap> fieldGaps) {
        this.knownGaps = List.copyOf(knownGaps);
        this.ignoredImplPaths = Map.copyOf(ignoredImplPaths);
        this.canaries = List.copyOf(canaries);
        this.fieldGaps = List.copyOf(fieldGaps);
        Map<String, FieldGap> byKey = new LinkedHashMap<>();
        for (FieldGap gap : fieldGaps) {
            FieldGap clash = byKey.put(gap.key(), gap);
            if (clash != null) {
                throw new IllegalStateException("字段级挂账有重复键: " + gap.brief());
            }
        }
        this.fieldGapsByKey = Map.copyOf(byKey);
    }

    /** 供负向对照测试凭空合成。 */
    static GapRegistry synthetic(List<Gap> knownGaps, List<String> canaries) {
        return new GapRegistry(knownGaps, Map.of(), canaries, List.of());
    }

    /** 供负向对照测试凭空合成(带字段级挂账)。 */
    static GapRegistry syntheticWithFieldGaps(List<Gap> knownGaps,
                                              List<String> canaries,
                                              List<FieldGap> fieldGaps) {
        return new GapRegistry(knownGaps, Map.of(), canaries, fieldGaps);
    }

    /**
     * 从类路径读清单。
     *
     * <p>读不到就<b>抛异常</b>,不当成"没有挂账" —— 那会让全部未开工接口瞬间变成"意外缺失",
     * 或者更糟:如果有人同时把清单清空,缺失会被静默当成正常。
     */
    static GapRegistry load() {
        try (InputStream in = GapRegistry.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("类路径上找不到 " + RESOURCE
                        + " —— 挂账清单不能缺,否则缺失的接口无从区分「未开工」与「出事了」");
            }
            return parse(new Yaml().load(in));
        } catch (java.io.IOException e) {
            throw new IllegalStateException("读取失败: " + RESOURCE, e);
        }
    }

    @SuppressWarnings("unchecked")
    private static GapRegistry parse(Map<String, Object> doc) {
        if (doc == null) {
            throw new IllegalStateException(RESOURCE + " 是空的");
        }

        List<Gap> gaps = new ArrayList<>();
        for (Object item : list(doc.get("knownGaps"))) {
            Map<String, Object> node = map(item);
            String method = str(node.get("method")).toUpperCase();
            String path = str(node.get("path"));
            String source = str(node.get("source"));
            String note = str(node.get("note"));
            if (method.isEmpty() || !path.startsWith("/")) {
                throw new IllegalStateException("挂账项缺 method/path: " + node);
            }
            if (source.isEmpty()) {
                throw new IllegalStateException(
                        "挂账项缺 source(出处),不许裸挂: " + method + " " + path);
            }
            gaps.add(new Gap(method, path, source, note));
        }

        Map<String, String> ignored = new LinkedHashMap<>();
        for (Object item : list(doc.get("ignoredImplPaths"))) {
            Map<String, Object> node = map(item);
            String path = str(node.get("path"));
            String reason = str(node.get("reason"));
            if (path.isEmpty() || reason.isEmpty()) {
                throw new IllegalStateException("豁免项必须同时写 path 与 reason: " + node);
            }
            ignored.put(path, reason);
        }

        List<String> canaries = new ArrayList<>();
        for (Object item : list(doc.get("canaries"))) {
            String text = str(item).trim();
            if (!text.matches("(?i)(GET|POST|PUT|PATCH|DELETE)\\s+/.*")) {
                throw new IllegalStateException(
                        "锚点格式应为 \"METHOD /path\",实际是: " + text);
            }
            canaries.add(text);
        }
        if (canaries.isEmpty()) {
            throw new IllegalStateException(
                    RESOURCE + " 的 canaries 为空 —— 没有锚点,路径拼接写错时会静默全绿");
        }

        List<FieldGap> fieldGaps = new ArrayList<>();
        for (Object item : list(doc.get("fieldGaps"))) {
            Map<String, Object> node = map(item);
            String method = str(node.get("method")).toUpperCase();
            String path = str(node.get("path"));
            String where = str(node.get("where")).toLowerCase();
            String fieldPath = str(node.get("field"));
            String dimension = str(node.get("dimension")).toLowerCase();
            String source = str(node.get("source"));
            String note = str(node.get("note"));
            if (method.isEmpty() || !path.startsWith("/")) {
                throw new IllegalStateException("字段级挂账缺 method/path: " + node);
            }
            if (!WHERE_VALUES.contains(where)) {
                throw new IllegalStateException(
                        "字段级挂账的 where 只能是 request / response,实际是: " + where);
            }
            if (!DIMENSION_VALUES.contains(dimension)) {
                throw new IllegalStateException(
                        "字段级挂账的 dimension 只能是 type / enum / required,实际是: " + dimension);
            }
            if ("required".equals(dimension) && !"request".equals(where)) {
                throw new IllegalStateException(
                        "必填只在请求体侧比,where 不能是 response: " + method + " " + path);
            }
            if (fieldPath.isEmpty()) {
                throw new IllegalStateException("字段级挂账缺 field(字段路径): " + node);
            }
            if (source.isEmpty()) {
                throw new IllegalStateException(
                        "字段级挂账缺 source(出处),不许裸挂: " + method + " " + path
                                + " " + fieldPath + " " + dimension);
            }
            fieldGaps.add(new FieldGap(method, path, where, fieldPath, dimension, source, note));
        }

        return new GapRegistry(gaps, ignored, canaries, fieldGaps);
    }

    // ------------------------------------------------------------ 查询

    /** 这条契约端点的缺失是否已挂账。 */
    Gap gapFor(String method, String path) {
        PathKey key = PathKey.of(method, path);
        for (Gap gap : knownGaps) {
            if (gap.key().equals(key)) {
                return gap;
            }
        }
        return null;
    }

    /** 实现侧的这条路由是否被豁免;不豁免时为 null。 */
    String ignoreReason(String implPath) {
        String path = PathKey.normalize(implPath);
        for (Map.Entry<String, String> entry : ignoredImplPaths.entrySet()) {
            String pattern = PathKey.normalize(entry.getKey());
            if (pattern.endsWith("/**")) {
                String prefix = pattern.substring(0, pattern.length() - 3);
                if (path.equals(prefix) || path.startsWith(prefix + "/")) {
                    return entry.getValue();
                }
            } else if (pattern.equals(path)) {
                return entry.getValue();
            }
        }
        return null;
    }

    /** 这一处字段差异是否已挂账;没挂时返回 null。 */
    FieldGap fieldGapFor(String method, String path, String where,
                         String fieldPath, String dimension) {
        return fieldGapsByKey.get(
                ContractImplDiff.mismatchKey(method, path, where, fieldPath, dimension));
    }

    List<FieldGap> fieldGaps() {
        return fieldGaps;
    }

    List<Gap> knownGaps() {
        return knownGaps;
    }

    List<String> canaries() {
        return canaries;
    }

    // ------------------------------------------------------------ 小工具

    private static List<?> list(Object node) {
        return node instanceof List<?> l ? l : List.of();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object node) {
        if (!(node instanceof Map)) {
            throw new IllegalStateException("期望 mapping,实际是: " + node);
        }
        return (Map<String, Object>) node;
    }

    private static String str(Object node) {
        return node == null ? "" : String.valueOf(node).trim();
    }
}
