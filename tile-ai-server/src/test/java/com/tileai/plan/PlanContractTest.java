package com.tileai.plan;

import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import com.tileai.RepoDocs;
import com.tileai.common.model.Part;
import com.tileai.plan.dto.PlanCopyResult;
import com.tileai.plan.dto.PlanDetailView;
import com.tileai.plan.dto.PlanImageView;
import com.tileai.plan.dto.PlanPartView;
import com.tileai.plan.dto.PlanRenameRequest;
import com.tileai.plan.dto.PlanSummaryView;
import com.tileai.plan.entity.Plan;
import com.tileai.plan.entity.PlanPart;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 把「我的方案」的 DTO 与契约 schema <b>逐字段绑住</b>。
 *
 * <p>手法与 {@code QuoteContractTest} / {@code ComboContractTest} /
 * {@code ProductContractTest} 一致:不导出 OpenAPI,直接比对"实现会发哪些字段"与
 * "契约声明了哪些字段"。
 *
 * <p><b>本文件里有两条负向对照,别当成多余的。</b>没有它们,上面那些
 * {@code assertEquals} 有可能只是因为"两边都没东西"而恒真 —— 那才是最坏的情况:
 * 测试全绿,而漏洞还在。负向对照断言的是"实体确实与契约不同",
 * 于是"谁把实体当 VO 返回"这件事一定会被上面某一条断言抓住。
 */
class PlanContractTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ------------------------------------------------------------------
    // 字段绑定:响应
    // ------------------------------------------------------------------

    @Test
    @DisplayName("PlanSummaryView 的字段集合 == 契约 PlanSummary 的属性集合")
    void summaryMatchesContract() {
        assertSameFields(jsonFields(PlanSummaryView.class),
                schemaProperties("openapi-common.yaml", "PlanSummary"));
    }

    @Test
    @DisplayName("PlanPartView 的字段集合 == 契约 PlanPart 的属性集合(含 source)")
    void partViewMatchesContract() {
        assertSameFields(jsonFields(PlanPartView.class),
                schemaProperties("openapi-common.yaml", "PlanPart"));
    }

    @Test
    @DisplayName("PlanImageView 的字段集合 == 契约 PlanImage 的属性集合")
    void imageViewMatchesContract() {
        assertSameFields(jsonFields(PlanImageView.class),
                schemaProperties("openapi-common.yaml", "PlanImage"));
    }

    @Test
    @DisplayName("PlanDetailView 的字段集合 == 契约 PlanDetail(allOf 两段合起来)")
    void detailMatchesContract() {
        assertSameFields(jsonFields(PlanDetailView.class), planDetailProperties());
    }

    /**
     * 详情是"摊平写"的(record 不能继承),所以字段是抄过来的 ——
     * 抄漏一个字段不会有任何别的检查变红,只有这一条会。
     */
    @Test
    @DisplayName("PlanDetailView 的前 10 个字段与 PlanSummaryView 逐个相同 —— 抄漏就红")
    void detailPrefixMirrorsSummary() {
        List<String> summary = componentNames(PlanSummaryView.class);
        List<String> detail = componentNames(PlanDetailView.class);

        assertEquals(summary, detail.subList(0, summary.size()),
                "详情的这 10 个字段必须与卡片信息**同序同名** ——"
                        + "契约是 allOf(把 PlanSummary 摊平在同一层),不是嵌套一个 summary 对象");
    }

    @Test
    @DisplayName("PlanDetailView 比 PlanSummary 多出来的正好是 layout / quote / images / shareToken")
    void detailExtrasMatchContract() {
        Set<String> extras = new LinkedHashSet<>(jsonFields(PlanDetailView.class));
        extras.removeAll(jsonFields(PlanSummaryView.class));

        assertEquals(new TreeSet<>(Set.of("layout", "quote", "images", "shareToken")),
                new TreeSet<>(extras));
    }

    // ------------------------------------------------------------------
    // 字段绑定:请求 / 结果
    // ------------------------------------------------------------------

    @Test
    @DisplayName("PlanRenameRequest 的字段集合 == 契约 PATCH /plans/{id} 请求体的属性集合")
    void renameRequestMatchesContract() {
        assertSameFields(jsonFields(PlanRenameRequest.class),
                propertyNames(requestBodySchema("/plans/{id}", "patch")));
    }

    @Test
    @DisplayName("PlanCopyResult 的字段集合 == 契约 POST /plans/{id}/copy 响应 data 的属性集合")
    void copyResultMatchesContract() {
        assertSameFields(jsonFields(PlanCopyResult.class),
                propertyNames(copyResponseDataSchema()));
    }

    // ------------------------------------------------------------------
    // 取值域:部位与来源
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Part 枚举 == 契约 Part 的 enum —— 一期恒为三值,加值必须两边一起加")
    void partEnumMatchesContract() {
        assertEquals(contractEnum("openapi-common.yaml", "Part"),
                new TreeSet<>(Part.codes()),
                "实现里的部位枚举与契约不一致 —— 契约 T-9 要求恒为 floor / wall / local"
                        + "(一期不含美缝)");
    }

    /**
     * 输出的取值必须原样发出,不能被 Jackson 改写(改名、加前缀、枚举转序号)。
     *
     * <p>这条看着像废话,但 {@code PlanPartView.part} / {@code .source} 在契约里都是
     * {@code $ref} 到枚举的字符串;一旦有人给 VO 加了 {@code @JsonProperty} 或改成枚举类型,
     * 契约比对工具<b>看不到</b>这类改写(它只比字段名与类型的大类),只有这里会红。
     */
    @Test
    @DisplayName("PlanPartView 能把契约里的每个 part / source 取值原样发出去")
    void partAndSourceValuesRoundTrip() throws Exception {
        Set<String> parts = contractEnum("openapi-common.yaml", "Part");
        Set<String> sources = contractEnum("openapi-common.yaml", "PartSource");

        for (String part : parts) {
            for (String source : sources) {
                PlanPartView view = new PlanPartView(part, "TL-2043", "雅灰石纹",
                        new BigDecimal("88.00"), new BigDecimal("3.6000"),
                        new BigDecimal("3.6000"), new BigDecimal("0.0600"),
                        new BigDecimal("335.81"), source);

                JsonNode node = MAPPER.valueToTree(view);
                assertEquals(part, node.path("part").asText());
                assertEquals(source, node.path("source").asText());
            }
        }
    }

    // ------------------------------------------------------------------
    // 负向对照 —— 这几条证明上面的断言不是恒真的装饰
    // ------------------------------------------------------------------

    @Test
    @DisplayName("负向对照:PlanPart 实体 ≠ 契约 PlanPart —— 谁把实体当 VO 返回就会被抓到")
    void entityIsNotTheContractShape() {
        Set<String> entityFields = jsonFields(PlanPart.class);
        Set<String> contractFields = schemaProperties("openapi-common.yaml", "PlanPart");

        assertNotEquals(contractFields, entityFields,
                "实体与契约恰好一样 —— 那前面那些断言就抓不到任何东西");
        assertTrue(entityFields.contains("planId"),
                "实体带着 planId(内部关联键),契约里没有 —— 这正是不能直接返回实体的理由之一");
        assertFalse(contractFields.contains("skuCode"),
                "契约要的是 sku,实体给的是 skuCode —— 字段名对不上,比对工具会红");
    }

    @Test
    @DisplayName("负向对照:Plan 实体带着 layoutEnc / userId —— 详情接口的 T-8 断言因此才有意义")
    void entityWouldLeakCiphertext() {
        Set<String> entityFields = jsonFields(Plan.class);

        assertTrue(entityFields.contains("layoutEnc"),
                "实体里必须能看到 layoutEnc(密文列),否则详情接口那条"
                        + "「响应里不得出现 layoutEnc」的断言就是空跑");
        assertTrue(entityFields.contains("userId"));
        assertTrue(entityFields.contains("isDeleted"));
    }

    @Test
    @DisplayName("负向对照:Plan 实体序列化出来的 JSON 里真的有 layoutEnc,且不是明文")
    void entitySerializationActuallyLeaks() throws Exception {
        Plan plan = new Plan();
        plan.setLayoutEnc(new byte[] {1, 2, 3, 4});
        plan.setName("卫生间 · 现代");

        String json = MAPPER.writeValueAsString(plan);
        assertTrue(json.contains("layoutEnc"), "实际 JSON:" + json);
        assertFalse(json.contains("\"spaces\""), "密文列不该是明文户型:" + json);
    }

    // ------------------------------------------------------------------
    // 契约读取
    // ------------------------------------------------------------------

    /** 契约 {@code PlanDetail} 是 allOf:第一段 PlanSummary,第二段四个字段 —— 这里手工并起来。 */
    private static Set<String> planDetailProperties() {
        Map<String, Object> schema = schemaOf("openapi-common.yaml", "PlanDetail");
        Object allOf = schema.get("allOf");
        assertNotNull(allOf, "PlanDetail 应当是 allOf —— 契约结构变了,这条断言要跟着改");

        Set<String> merged = new TreeSet<>();
        for (Object member : (List<?>) allOf) {
            @SuppressWarnings("unchecked")
            Map<String, Object> node = (Map<String, Object>) member;
            Object ref = node.get("$ref");
            if (ref != null) {
                merged.addAll(schemaProperties("openapi-common.yaml",
                        ((String) ref).substring(((String) ref).lastIndexOf('/') + 1)));
            } else {
                merged.addAll(propertyNames(node));
            }
        }
        return merged;
    }

    private static Map<String, Object> requestBodySchema(String path, String method) {
        Map<String, Object> requestBody = map(operation(path, method).get("requestBody"), "requestBody");
        return map(firstContent(requestBody).get("schema"), "schema");
    }

    /** {@code .../copy} 的响应体:{@code allOf[1].properties.data} 那个 schema。 */
    private static Map<String, Object> copyResponseDataSchema() {
        Map<String, Object> responses = map(operation("/plans/{id}/copy", "post").get("responses"),
                "responses");
        Map<String, Object> schema = map(firstContent(map(responses.get("200"), "200")).get("schema"),
                "schema");

        List<?> allOf = (List<?>) schema.get("allOf");
        assertNotNull(allOf, "响应应是 allOf[ApiResponse, {data:{planId}}],契约结构变了");
        @SuppressWarnings("unchecked")
        Map<String, Object> second = (Map<String, Object>) allOf.get(1);
        // 返回的是 data 的 schema 本身(不是它的 properties)—— 调用方用 propertyNames 取键
        return map(map(second.get("properties"), "properties").get("data"), "data");
    }

    /** 一个 requestBody / response 下的内容(契约里只有 {@code application/json} 一种)。 */
    private static Map<String, Object> firstContent(Map<String, Object> holder) {
        Map<String, Object> content = map(holder.get("content"), "content");
        assertFalse(content.isEmpty(), "content 是空的");
        return map(content.values().iterator().next(), "content.*");
    }

    private static Map<String, Object> operation(String path, String method) {
        Map<String, Object> paths = RepoDocs.child(RepoDocs.yaml("openapi-c.yaml"), "paths");
        return RepoDocs.child(RepoDocs.child(paths, path), method);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value, String what) {
        assertNotNull(value, what + " 缺失");
        return (Map<String, Object>) value;
    }

    // ------------------------------------------------------------------
    // 通用工具(与其他 *ContractTest 同一手法)
    // ------------------------------------------------------------------

    private static List<String> componentNames(Class<?> recordType) {
        return Arrays.stream(recordType.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
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
