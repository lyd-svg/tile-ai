package com.tileai;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * 从契约 YAML 中取出 schema,并把内部 {@code $ref} 内联成真实定义。
 *
 * <p>为什么要自己内联,而不是让校验库去解析引用:契约里的 {@code $ref} 形如
 * {@code #/components/schemas/LayoutSpace},是相对<b>整份 OpenAPI 文档</b>的指针。
 * 若只把 {@code LayoutModel} 这一个节点交给校验库,它找不到 {@code components},
 * 引用会解析失败。自己内联层次清晰,也不受校验库版本差异影响。
 */
public final class ContractSchema {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String REF_PREFIX = "#/components/schemas/";

    private ContractSchema() {
    }

    /** 取出某个 schema 并内联全部内部引用。 */
    public static JsonNode resolved(String file, String schemaName) {
        Map<String, Object> schemas = RepoDocs.child(
                RepoDocs.child(RepoDocs.yaml(file), "components"), "schemas");
        JsonNode schemasNode = MAPPER.valueToTree(schemas);

        JsonNode target = schemasNode.get(schemaName);
        if (target == null) {
            throw new IllegalStateException(file + " 中找不到 schema: " + schemaName);
        }
        return inline(target.deepCopy(), schemasNode, new LinkedHashSet<>());
    }

    private static JsonNode inline(JsonNode node, JsonNode schemas, Set<String> visiting) {
        if (node.isObject()) {
            JsonNode ref = node.get("$ref");
            if (ref != null && ref.isTextual()) {
                String text = ref.asText();
                if (!text.startsWith(REF_PREFIX)) {
                    throw new IllegalStateException("暂不支持的引用形式: " + text);
                }
                String name = text.substring(REF_PREFIX.length());
                if (!visiting.add(name)) {
                    // 契约里的模型是树形,不该出现环;真出现了说明契约有误,应当暴露而不是死循环
                    throw new IllegalStateException("契约中出现循环引用: " + name + " ← " + visiting);
                }
                JsonNode resolved = inline(schemas.get(name).deepCopy(), schemas, visiting);
                visiting.remove(name);
                return resolved;
            }
            ObjectNode out = MAPPER.createObjectNode();
            node.fields().forEachRemaining(
                    entry -> out.set(entry.getKey(), inline(entry.getValue(), schemas, visiting)));
            return out;
        }
        if (node.isArray()) {
            ArrayNode out = MAPPER.createArrayNode();
            node.forEach(item -> out.add(inline(item, schemas, visiting)));
            return out;
        }
        return node;
    }
}
