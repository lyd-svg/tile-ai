package com.tileai.contract;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.tileai.RepoDocs;

/**
 * 把三份带 {@code paths} 的契约读成 {@link ContractEndpoint} 列表。
 *
 * <h2>为什么不复用现成的 {@code ContractSchema}</h2>
 *
 * <p>它只认 {@code #/components/schemas/} 这种同文件引用,遇到本仓库里大量的
 * {@code ./openapi-common.yaml#/components/schemas/X} <b>会直接抛异常</b>;
 * 而且它<b>完全不懂 {@code allOf}</b> —— 而响应信封恰恰是
 * {@code allOf: [ApiResponse, {properties: {data: ...}}]} 拼出来的。所以另写一份。
 *
 * <h2>两处刻意的取舍</h2>
 *
 * <ul>
 *   <li><b>深度上限 2</b> —— 比到"顶层字段 + 数组元素字段"为止。再深就报
 *       {@link Shape.Kind#TRUNCATED},不假装比过。</li>
 *   <li><b>只认 {@code application/json}</b> —— 其余 media type(SSE、二进制)一律
 *       记「无法比对」,不猜。</li>
 * </ul>
 *
 * <h2>{@code $ref} 的基准文件</h2>
 *
 * <p>引用是相对<b>它所在的那份文件</b>,不是相对调用方。{@code openapi-c.yaml} 里
 * {@code $ref} 到 common 的 {@code Sku},而 {@code Sku.status} 又写着
 * {@code '#/components/schemas/ProductStatus'} —— 这个 {@code #} 指的是 <b>common</b>,
 * 不是 c。所以解析必须<b>整棵树一次做完</b>并把当前文件带着走(见 {@link #resolve}),
 * 只带一个顶层 file 参数会在跨文件时找错地方。
 */
final class ContractReader {

    /** 带 {@code paths} 的三份契约(common 是纯组件库,没有路径)。 */
    static final List<String> FILES = List.of(
            "openapi-c.yaml", "openapi-admin.yaml", "openapi-internal.yaml");

    private static final Set<String> METHODS =
            Set.of("get", "post", "put", "patch", "delete");

    private static final int MAX_DEPTH = 2;
    private static final int MAX_REF_DEPTH = 20;

    private final Map<String, Map<String, Object>> docs = new HashMap<>();

    /** 读全部三份契约。 */
    List<ContractEndpoint> readAll() {
        List<ContractEndpoint> all = new ArrayList<>();
        for (String file : FILES) {
            all.addAll(read(file));
        }
        return all;
    }

    /** 读一份契约。无 {@code paths} 时返回空列表(common 就是这种情况)。 */
    List<ContractEndpoint> read(String file) {
        Map<String, Object> doc = doc(file);
        Object pathsNode = doc.get("paths");
        if (!(pathsNode instanceof Map)) {
            return List.of();
        }
        String prefix = serverPathPrefix(doc);

        List<ContractEndpoint> out = new ArrayList<>();
        for (Map.Entry<String, Object> pathEntry : asMap(pathsNode).entrySet()) {
            if (!(pathEntry.getValue() instanceof Map)) {
                continue;
            }
            String fullPath = prefix + pathEntry.getKey();
            for (Map.Entry<String, Object> opEntry
                    : asMap(pathEntry.getValue()).entrySet()) {
                String method = opEntry.getKey().toLowerCase();
                if (!METHODS.contains(method) || !(opEntry.getValue() instanceof Map)) {
                    continue;
                }
                Map<String, Object> op = asMap(opEntry.getValue());
                out.add(new ContractEndpoint(
                        method.toUpperCase(),
                        fullPath,
                        file,
                        owner(file, op),
                        requestShape(file, op),
                        responseShape(file, op),
                        str(op.get("summary"))));
            }
        }
        return out;
    }

    // ------------------------------------------------------------ 归属

    /**
     * 该操作由谁实现。c / admin 是纯对外 API,全归 Java(不可变量 3);
     * internal <b>由契约自证</b> —— 逐条读操作上的 {@code x-direction}。
     */
    private ContractEndpoint.Owner owner(String file, Map<String, Object> op) {
        if (!file.contains("internal")) {
            return ContractEndpoint.Owner.JAVA;
        }
        String direction = str(op.get("x-direction"));
        if (direction.isEmpty()) {
            throw new IllegalStateException(
                    file + " 的某个操作缺 x-direction,无法判定归属:" + op.get("summary"));
        }
        // 形如 "Java → Python" / "Python → Java":箭头右侧是接收方,即实现方
        String[] parts = direction.split("→");
        String target = parts[parts.length - 1].trim();
        if (target.startsWith("Python")) {
            return ContractEndpoint.Owner.PYTHON;
        }
        if (target.startsWith("Java")) {
            return ContractEndpoint.Owner.JAVA;
        }
        throw new IllegalStateException("看不懂的 x-direction: " + direction);
    }

    // ------------------------------------------------------------ servers 前缀

    /**
     * 取 {@code servers[0].url} 的<b>路径部分</b>作为前缀。
     *
     * <p>刻意<b>不做 URI 解析</b> —— internal 的 host 里写着 {@code {python-agent-host}}
     * 这种占位符,{@code URI.create} 会直接抛异常。纯字符串切分反而稳:
     * {@code /api/v1} → 前缀 {@code /api/v1};{@code http://host:8080} → 前缀为空,
     * 于是 internal 的全路径不会被重复拼接。
     */
    private String serverPathPrefix(Map<String, Object> doc) {
        Object servers = doc.get("servers");
        if (!(servers instanceof List<?> list) || list.isEmpty()) {
            return "";
        }
        Object first = list.get(0);
        if (!(first instanceof Map)) {
            return "";
        }
        String url = str(asMap(first).get("url"));
        if (url.isEmpty()) {
            return "";
        }
        if (!url.startsWith("/")) {
            int scheme = url.indexOf("://");
            int slash = scheme < 0 ? url.indexOf('/') : url.indexOf('/', scheme + 3);
            url = slash < 0 ? "" : url.substring(slash);
        }
        while (url.length() > 1 && url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url.equals("/") ? "" : url;
    }

    // ------------------------------------------------------------ 请求体

    /**
     * 请求体 {@code application/json} 的形态。无请求体 → {@link Shape#NONE}。
     *
     * <p>只到请求体的直接属性(深度 1)—— 实际用到的请求体都是一层对象,
     * 再深既无人读也徒增噪声。
     */
    private Shape requestShape(String file, Map<String, Object> op) {
        Object requestBody = op.get("requestBody");
        if (!(requestBody instanceof Map)) {
            return Shape.NONE;
        }
        Object schema = jsonSchema(asMap(requestBody).get("content"));
        if (schema == null) {
            return Shape.NONE;
        }
        return shapeOf(file, schema, 1);
    }

    // ------------------------------------------------------------ 响应体

    /**
     * 响应体里 {@code data} 的形态 —— <b>不含信封本身</b>。
     *
     * <p>信封那几个字段(code/message/traceId/data)是恒定的,由测试里的自检锚点盯着,
     * 不必每条路径重复比较。
     */
    private Shape responseShape(String file, Map<String, Object> op) {
        Object responses = op.get("responses");
        if (!(responses instanceof Map)) {
            return Shape.UNKNOWN;
        }
        Object ok = asMap(responses).get("200");
        if (!(ok instanceof Map)) {
            return Shape.UNKNOWN;
        }
        Object schema = jsonSchema(asMap(ok).get("content"));
        if (schema == null) {
            return Shape.NONE;
        }
        Map<String, Object> props = propertiesOf(merged(file, schema));
        if (props == null || !props.containsKey("data")) {
            // 裸 ApiResponse:契约只说"有个 data",没说里面有什么 —— 无从比
            return Shape.UNKNOWN;
        }
        return shapeOf(file, props.get("data"), 1);
    }

    /** 从 content 里取 {@code application/json} 的 schema;没有则 null。 */
    private Object jsonSchema(Object contentNode) {
        if (!(contentNode instanceof Map)) {
            return null;
        }
        Object json = asMap(contentNode).get("application/json");
        if (!(json instanceof Map)) {
            return null;
        }
        return asMap(json).get("schema");
    }

    // ------------------------------------------------------------ 形态

    /**
     * 把 schema 归一成 {@link Shape}。
     *
     * <p>数组<b>不消耗深度</b> —— 这样"顶层对象 → 数组元素对象"正好落进两级上限,
     * 与"比到数组元素字段"的口径一致。
     */
    private Shape shapeOf(String file, Object schemaNode, int depth) {
        if (depth > MAX_DEPTH) {
            return Shape.TRUNCATED;
        }
        Map<String, Object> schema = merged(file, schemaNode);
        if (schema == null || schema.isEmpty()) {
            return Shape.UNKNOWN;
        }
        if ("array".equals(str(schema.get("type")))) {
            return Shape.array(shapeOf(file, schema.get("items"), depth));
        }
        Map<String, Object> props = propertiesOf(schema);
        if (props != null && !props.isEmpty()) {
            // 入口那句 `depth > MAX_DEPTH` 已经兜住了超深的情况;这里不能再收一道,
            // 否则"数组元素对象的字段"(正好落在第 2 层)会被误判成超限。
            Map<String, Shape> fields = new LinkedHashMap<>();
            for (Map.Entry<String, Object> e : props.entrySet()) {
                fields.put(e.getKey(), shapeOf(file, e.getValue(), depth + 1));
            }
            return Shape.object(fields);
        }
        if (schema.containsKey("enum")
                || Set.of("string", "integer", "number", "boolean")
                        .contains(str(schema.get("type")))) {
            return Shape.SCALAR;
        }
        // 没有 properties 的 object(如 additionalProperties: true)或 `items: {}` 这类
        // 开放占位:契约没声明字段,不是"字段为空"
        return Shape.UNKNOWN;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> propertiesOf(Map<String, Object> schema) {
        if (schema == null) {
            return null;
        }
        Object props = schema.get("properties");
        return props instanceof Map ? (Map<String, Object>) props : null;
    }

    // ------------------------------------------------------------ $ref 与 allOf

    /** 解析 {@code $ref} 并摊平 {@code allOf},得到一个自洽的 schema。 */
    private Map<String, Object> merged(String file, Object schemaNode) {
        Object resolved = resolve(file, schemaNode, 0);
        if (!(resolved instanceof Map)) {
            return null;
        }
        return flatten(asMap(resolved), 0);
    }

    /**
     * 把节点里的 {@code $ref} <b>就地解析掉</b>,返回一棵没有引用的树。
     *
     * <p>递归是必须的:跨文件引用进来之后,子节点里的 {@code #/...} 是相对
     * <b>被引用的那份文件</b>的,所以每进一层都要把 {@code currentFile} 换成新的基准。
     *
     * <p>{@code $ref} 旁边还挂着键时(如补一段 description),以本地键为准。
     */
    private Object resolve(String currentFile, Object node, int depth) {
        if (depth > MAX_REF_DEPTH) {
            return node;
        }
        if (node instanceof List<?> list) {
            List<Object> out = new ArrayList<>(list.size());
            for (Object item : list) {
                out.add(resolve(currentFile, item, depth));
            }
            return out;
        }
        if (!(node instanceof Map)) {
            return node;
        }
        Map<String, Object> map = asMap(node);
        Object ref = map.get("$ref");
        if (ref instanceof String refText) {
            String targetFile = refFile(refText, currentFile);
            Object target = walk(targetFile, refPointer(refText), refText, currentFile);
            Object resolved = resolve(targetFile, target, depth + 1);
            if (!(resolved instanceof Map)) {
                return resolved;
            }
            Map<String, Object> combined = new LinkedHashMap<>(asMap(resolved));
            for (Map.Entry<String, Object> e : map.entrySet()) {
                if (!"$ref".equals(e.getKey())) {
                    combined.put(e.getKey(), resolve(currentFile, e.getValue(), depth));
                }
            }
            return combined;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : map.entrySet()) {
            out.put(e.getKey(), resolve(currentFile, e.getValue(), depth));
        }
        return out;
    }

    /** {@code ./openapi-common.yaml#/...} → {@code openapi-common.yaml};{@code #/...} → 当前文件。 */
    private static String refFile(String refText, String currentFile) {
        int hash = refText.indexOf('#');
        String filePart = hash < 0 ? refText : refText.substring(0, hash);
        if (filePart.isEmpty()) {
            return currentFile;
        }
        // './openapi-common.yaml' → 'openapi-common.yaml'
        return filePart.replaceFirst("^\\./", "");
    }

    private static String refPointer(String refText) {
        int hash = refText.indexOf('#');
        return hash < 0 ? "" : refText.substring(hash + 1);
    }

    private Object walk(String file, String pointer, String refText, String fromFile) {
        Object node = doc(file);
        for (String segment : pointer.split("/")) {
            if (segment.isEmpty()) {
                continue;
            }
            if (!(node instanceof Map)) {
                throw new IllegalStateException("悬空引用: " + refText
                        + "(引用于 " + fromFile + ")");
            }
            node = asMap(node).get(segment.replace("~1", "/").replace("~0", "~"));
        }
        if (node == null) {
            throw new IllegalStateException("解析不到的引用: " + refText
                    + " —— 已按基准文件 " + file + " 去找 " + pointer
                    + ",没有(引用于 " + fromFile + ")");
        }
        return node;
    }

    /**
     * 摊平 {@code allOf}:成员按出现顺序叠加(靠后的覆盖靠前的),
     * 最后叠加节点自身的键 —— 与 OpenAPI 的常识一致。
     */
    private Map<String, Object> flatten(Map<String, Object> schema, int depth) {
        if (schema == null || depth > MAX_REF_DEPTH) {
            return null;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        Object allOf = schema.get("allOf");
        if (allOf instanceof List<?> list) {
            for (Object member : list) {
                if (member instanceof Map) {
                    flattenInto(out, asMap(member), depth + 1);
                }
            }
        }
        Map<String, Object> self = new LinkedHashMap<>(schema);
        self.remove("allOf");
        flattenInto(out, self, depth + 1);
        return out;
    }

    @SuppressWarnings("unchecked")
    private void flattenInto(Map<String, Object> out, Map<String, Object> member, int depth) {
        if (depth > MAX_REF_DEPTH) {
            return;
        }
        Map<String, Object> flat = member.containsKey("allOf")
                ? flatten(member, depth)
                : member;
        if (flat == null) {
            return;
        }
        for (Map.Entry<String, Object> e : flat.entrySet()) {
            if ("properties".equals(e.getKey()) && e.getValue() instanceof Map) {
                Map<String, Object> props = out.get("properties") instanceof Map
                        ? new LinkedHashMap<>((Map<String, Object>) out.get("properties"))
                        : new LinkedHashMap<>();
                props.putAll((Map<String, Object>) e.getValue());
                out.put("properties", props);
            } else if ("required".equals(e.getKey()) && e.getValue() instanceof List) {
                List<Object> required = out.get("required") instanceof List
                        ? new ArrayList<>((List<Object>) out.get("required"))
                        : new ArrayList<>();
                for (Object item : (List<Object>) e.getValue()) {
                    if (!required.contains(item)) {
                        required.add(item);
                    }
                }
                out.put("required", required);
            } else {
                out.put(e.getKey(), e.getValue());
            }
        }
    }

    // ------------------------------------------------------------ 杂项

    private Map<String, Object> doc(String file) {
        Map<String, Object> cached = docs.get(file);
        if (cached == null) {
            cached = RepoDocs.yaml(file);
            docs.put(file, cached);
        }
        return cached;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object node) {
        return (Map<String, Object>) node;
    }

    private static String str(Object node) {
        return node == null ? "" : String.valueOf(node);
    }
}
