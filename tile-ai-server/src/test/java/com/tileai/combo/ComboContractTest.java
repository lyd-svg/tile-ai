package com.tileai.combo;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import com.tileai.RepoDocs;
import com.tileai.combo.dto.ComboUpsertRequest;
import com.tileai.combo.dto.ComboView;
import com.tileai.common.model.Part;

import jakarta.validation.constraints.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 把推荐搭配的 DTO 与契约 schema <b>逐字段绑住</b>,并额外钉住部位枚举(契约 T-9)。
 *
 * <p>做法与 T-0.6 的 {@code ProductContractTest} 一致:T-0.6 判据里那条
 * "实现导出的 OpenAPI 与契约一致"需要 T-12 的导出设施(排在 T-2.1),
 * 这里用<b>字段级绑定</b>覆盖同一类漂移,详见 {@code openapi-verification.md} §12.1。
 */
class ComboContractTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ------------------------------------------------------------------
    // 字段绑定
    // ------------------------------------------------------------------

    @Test
    @DisplayName("ComboUpsertRequest 的字段集合 == 契约 ComboUpsert 的属性集合")
    void upsertRequestMatchesContract() {
        assertSameFields(
                jsonFields(ComboUpsertRequest.class),
                schemaProperties("openapi-admin.yaml", "ComboUpsert"));
    }

    @Test
    @DisplayName("ComboUpsertRequest.items[] 的字段集合 == 契约 ComboItem 的属性集合")
    void upsertItemMatchesContract() {
        assertSameFields(
                jsonFields(ComboUpsertRequest.Item.class),
                schemaProperties("openapi-common.yaml", "ComboItem"));
    }

    @Test
    @DisplayName("ComboView 的字段集合 == 契约 Combo 的属性集合")
    void comboViewMatchesContract() {
        assertSameFields(
                jsonFields(ComboView.class),
                schemaProperties("openapi-common.yaml", "Combo"));
    }

    @Test
    @DisplayName("ComboView.items[] 的字段集合 == 契约 ComboItem 的属性集合")
    void comboViewItemMatchesContract() {
        assertSameFields(
                jsonFields(ComboView.Item.class),
                schemaProperties("openapi-common.yaml", "ComboItem"));
    }

    // ------------------------------------------------------------------
    // 契约 T-9:部位恒为三值
    // ------------------------------------------------------------------

    @Test
    @DisplayName("T-9:part 的取值恒为 floor / wall / local,且与 Part 枚举一致")
    void partIsAlwaysThreeValues() {
        // 契约里 ComboItem.part 指向 common 的 Part 枚举
        assertEquals(Set.of("floor", "wall", "local"),
                new TreeSet<>(Part.codes()),
                "Part 枚举漂了 —— 一期不含美缝(PRD §5.2.2)");

        // 实现里 part 是用 @Pattern 表达的,必须与枚举不漂移。
        // 否则会出现"DTO 收下了服务端不认的部位"这类只在运行时才暴露的问题。
        assertEquals(Part.codes(), partPatternValues(),
                "ComboUpsertRequest.Item.part 的 @Pattern 与 Part 枚举不一致");
    }

    @Test
    @DisplayName("契约 ComboUpsert.items 声明 minItems: 1 —— 空搭配应被拒")
    void itemsAreRequired() {
        @SuppressWarnings("unchecked")
        Map<String, Object> schemas = ReDoc.schemas("openapi-admin.yaml");
        @SuppressWarnings("unchecked")
        Map<String, Object> comboUpsert = (Map<String, Object>) schemas.get("ComboUpsert");

        @SuppressWarnings("unchecked")
        List<String> required = (List<String>) comboUpsert.get("required");
        assertTrue(required.contains("items"), "契约要求 items 必填: " + required);

        @SuppressWarnings("unchecked")
        Map<String, Object> items = (Map<String, Object>)
                ((Map<String, Object>) comboUpsert.get("properties")).get("items");
        assertEquals(1, items.get("minItems"), "契约声明 items 至少 1 项");
    }

    // ------------------------------------------------------------------

    private static Set<String> partPatternValues() {
        for (RecordComponent component : ComboUpsertRequest.Item.class.getRecordComponents()) {
            if (!"part".equals(component.getName())) {
                continue;
            }
            Pattern pattern = component.getAccessor().getAnnotation(Pattern.class);
            assertNotNull(pattern, "part 字段应有 @Pattern 约束");
            return Arrays.stream(pattern.regexp().split("\\|"))
                    .collect(Collectors.toSet());
        }
        throw new AssertionError("ComboUpsertRequest.Item 中找不到 part 字段");
    }

    private static void assertSameFields(Set<String> actual, Set<String> expected) {
        Set<String> missing = new LinkedHashSet<>(expected);
        missing.removeAll(actual);
        Set<String> extra = new LinkedHashSet<>(actual);
        extra.removeAll(expected);
        assertEquals(expected, actual,
                "与契约 schema 不一致 —— 契约有而实现无: " + missing + ";实现有而契约无: " + extra);
    }

    /** 取一个类型的实际 JSON 字段名集合(经 Jackson 内省,含 @JsonIgnore 等影响)。 */
    private static Set<String> jsonFields(Class<?> type) {
        JavaType javaType = MAPPER.getTypeFactory().constructType(type);
        BeanDescription description = MAPPER.getSerializationConfig().introspect(javaType);
        Set<String> names = new LinkedHashSet<>();
        for (BeanPropertyDefinition property : description.findProperties()) {
            names.add(property.getName());
        }
        return new TreeSet<>(names);
    }

    private static Set<String> schemaProperties(String file, String schemaName) {
        @SuppressWarnings("unchecked")
        Map<String, Object> schema = (Map<String, Object>) ReDoc.schemas(file).get(schemaName);
        assertTrue(schema != null, file + " 中找不到 schema: " + schemaName);
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        return new TreeSet<>(properties.keySet());
    }

    /** 薄封装,避免每处都写一长串 {@code RepoDocs.child(...)}。 */
    private static final class ReDoc {
        @SuppressWarnings("unchecked")
        static Map<String, Object> schemas(String file) {
            return RepoDocs.child(
                    RepoDocs.child(RepoDocs.yaml(file), "components"), "schemas");
        }
    }
}
