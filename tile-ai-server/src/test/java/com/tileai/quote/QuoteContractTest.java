package com.tileai.quote;

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
import com.tileai.quote.dto.QuoteCalcRequest;
import com.tileai.quote.dto.QuoteResultView;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 把报价的 DTO 与契约 schema <b>逐字段绑住</b>。
 *
 * <p>手法与 T-0.6 的 {@code ProductContractTest}、搭配的 {@code ComboContractTest} 一致:
 * 不导出 OpenAPI,直接比对"实现会收/会发哪些字段"与"契约声明了哪些字段"。
 *
 * <h2>为什么这个文件非有不可</h2>
 *
 * <p>2026-09-30 的契约 ↔ 实现比对(见 {@code openapi-verification.md} §36)量出的第一处
 * 真实分叉就在本接口:<b>契约的 {@code PartSelection} 声明了 {@code source},而实现收不下</b>。
 * 字段级绑定至今是零散的(产品库、搭配各一份),报价这一块之前<b>一份都没有</b> ——
 * 这就是它能悄悄漂走的原因。本文件把这块补上。
 */
class QuoteContractTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 直接建校验器,不启 Spring —— 抛的是约束本身,不掺上下文。 */
    private static final Validator VALIDATOR =
            Validation.buildDefaultValidatorFactory().getValidator();

    // ------------------------------------------------------------------
    // 字段绑定:请求
    // ------------------------------------------------------------------

    @Test
    @DisplayName("QuoteCalcRequest 的字段集合 == 契约 QuoteCalcRequest 的属性集合")
    void requestMatchesContract() {
        assertSameFields(jsonFields(QuoteCalcRequest.class),
                schemaProperties("openapi-c.yaml", "QuoteCalcRequest"));
    }

    @Test
    @DisplayName("QuoteCalcRequest.parts[] 的字段集合 == 契约 PartSelection 的属性集合")
    void partsItemMatchesContract() {
        assertSameFields(jsonFields(QuoteCalcRequest.PartSelection.class),
                schemaProperties("openapi-common.yaml", "PartSelection"));
    }

    @Test
    @DisplayName("QuoteCalcRequest.openings[] 的字段集合 == 契约 QuoteCalcRequest.openings.items")
    void openingsItemMatchesContract() {
        assertSameFields(jsonFields(QuoteCalcRequest.PartOpenings.class),
                arrayItemProperties("openapi-c.yaml", "QuoteCalcRequest", "openings"));
    }

    // ------------------------------------------------------------------
    // 字段绑定:响应
    // ------------------------------------------------------------------

    @Test
    @DisplayName("QuoteResultView 的字段集合 == 契约 QuoteResult 的属性集合")
    void resultMatchesContract() {
        assertSameFields(jsonFields(QuoteResultView.class),
                schemaProperties("openapi-common.yaml", "QuoteResult"));
    }

    @Test
    @DisplayName("QuoteResultView.items[] 的字段集合 == 契约 QuoteItem 的属性集合")
    void resultItemMatchesContract() {
        assertSameFields(jsonFields(QuoteResultView.Item.class),
                schemaProperties("openapi-common.yaml", "QuoteItem"));
    }

    @Test
    @DisplayName("QuoteResultView.rulesSnapshot 的字段集合 == 契约 QuoteResult.rulesSnapshot")
    void rulesSnapshotMatchesContract() {
        assertSameFields(jsonFields(QuoteResultView.RulesSnapshot.class),
                objectFieldProperties("openapi-common.yaml", "QuoteResult", "rulesSnapshot"));
    }

    // ------------------------------------------------------------------
    // source:本轮实装的那一项(§36.8)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("source 可省略 —— 契约注明「请求中可省略」")
    void sourceIsOptional() throws Exception {
        QuoteCalcRequest request = read("""
                {"space":"bathroom",
                 "parts":[{"part":"floor","sku":"TL-2043","area":3.6}]}
                """);

        assertNull(request.parts().get(0).source(), "省略时就是 null,不给它编一个默认值");
        assertTrue(VALIDATOR.validate(request).isEmpty(),
                "省略 source 不该产生任何校验错:" + VALIDATOR.validate(request));
    }

    @Test
    @DisplayName("source 收得下 user / ai —— 契约声明了就必须收得下(§36.8 的第一处分叉)")
    void sourceIsAccepted() throws Exception {
        for (String source : List.of("user", "ai")) {
            QuoteCalcRequest request = read("""
                    {"space":"bathroom",
                     "parts":[{"part":"floor","sku":"TL-2043","area":3.6,"source":"%s"}]}
                    """.formatted(source));

            assertEquals(source, request.parts().get(0).source(),
                    "契约声明的字段被丢掉了 —— 这正是 §36.8 记的那处分叉");
            assertTrue(VALIDATOR.validate(request).isEmpty(),
                    "合法 source 不该被拦:" + VALIDATOR.validate(request));
        }
    }

    @Test
    @DisplayName("source 非法值 → 校验错,字段路径指到 parts[0].source")
    void illegalSourceIsRejected() throws Exception {
        QuoteCalcRequest request = read("""
                {"space":"bathroom",
                 "parts":[{"part":"floor","sku":"TL-2043","area":3.6,"source":"ai_completed"}]}
                """);

        Set<String> paths = VALIDATOR.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toCollection(TreeSet::new));

        assertTrue(paths.contains("parts[0].source"),
                "非法来源应被逐项指出(契约 PartSource 只有 user / ai),实际:" + paths);
    }

    @Test
    @DisplayName("source 的取值集合 == 契约 PartSource 的枚举 —— 加值必须两边一起加")
    void sourceValuesMatchContractEnum() {
        // 与 ComboContractTest 钉 part 枚举同一手法:实现用 @Pattern 表达枚举,
        // 那它与契约的 enum 就可能各走各的 —— 这类漂移不会让任何别的检查变红。
        assertEquals(contractEnum("openapi-common.yaml", "PartSource"),
                patternValues(QuoteCalcRequest.PartSelection.class, "source"),
                "QuoteCalcRequest.PartSelection.source 的 @Pattern 与契约 PartSource 枚举不一致");
    }

    // ------------------------------------------------------------------

    /** 用**严格**的 ObjectMapper 读请求:多一个契约里没有的字段会当场报错。 */
    private static QuoteCalcRequest read(String json) throws Exception {
        return MAPPER.readValue(json, QuoteCalcRequest.class);
    }

    private static Set<String> patternValues(Class<?> recordType, String fieldName) {
        for (RecordComponent component : recordType.getRecordComponents()) {
            if (!fieldName.equals(component.getName())) {
                continue;
            }
            Pattern pattern = component.getAccessor().getAnnotation(Pattern.class);
            assertNotNull(pattern, fieldName + " 字段应有 @Pattern 约束");
            return Arrays.stream(pattern.regexp().split("\\|")).collect(Collectors.toSet());
        }
        throw new AssertionError(recordType.getSimpleName() + " 中找不到字段 " + fieldName);
    }

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
        return propertyNames(schemaOf(file, schemaName));
    }

    /** 取某个数组属性的 items.properties 键集合。 */
    private static Set<String> arrayItemProperties(String file, String schemaName, String arrayField) {
        return propertyNames(child(schemaOf(file, schemaName), arrayField, "items"));
    }

    /** 取某个对象属性的 properties 键集合。 */
    private static Set<String> objectFieldProperties(String file, String schemaName, String field) {
        return propertyNames(child(schemaOf(file, schemaName), field, ""));
    }

    @SuppressWarnings("unchecked")
    private static Set<String> contractEnum(String file, String schemaName) {
        Object values = schemaOf(file, schemaName).get("enum");
        assertNotNull(values, file + " 的 " + schemaName + " 应有 enum");
        return new TreeSet<>((List<String>) values);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> schemaOf(String file, String schemaName) {
        Map<String, Object> schemas = RepoDocs.child(
                RepoDocs.child(RepoDocs.yaml(file), "components"), "schemas");
        Object schema = schemas.get(schemaName);
        assertNotNull(schema, file + " 中找不到 schema: " + schemaName);
        return (Map<String, Object>) schema;
    }

    /** 沿 properties → field →(可选)items 往下走一层,拿到的必须还是个 schema。 */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> child(Map<String, Object> schema, String field, String then) {
        Object declared = ((Map<String, Object>) schema.get("properties")).get(field);
        assertNotNull(declared, "该 schema 的 properties 里找不到 " + field);
        Map<String, Object> node = (Map<String, Object>) declared;
        if (!then.isEmpty()) {
            Object next = node.get(then);
            assertNotNull(next, field + " 缺少 " + then);
            node = (Map<String, Object>) next;
        }
        return node;
    }

    @SuppressWarnings("unchecked")
    private static Set<String> propertyNames(Map<String, Object> schema) {
        Object properties = schema.get("properties");
        assertNotNull(properties, "该 schema 没有 properties —— 是不是 $ref 没解析?");
        return new TreeSet<>(((Map<String, Object>) properties).keySet());
    }

    private static void assertSameFields(Set<String> actual, Set<String> expected) {
        Set<String> missing = new LinkedHashSet<>(expected);
        missing.removeAll(actual);
        Set<String> extra = new LinkedHashSet<>(actual);
        extra.removeAll(expected);
        assertEquals(expected, actual,
                "与契约 schema 不一致 —— 契约有而实现无: " + missing + ";实现有而契约无: " + extra);
    }
}
