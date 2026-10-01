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
import com.tileai.plan.dto.ShareResultView;
import com.tileai.plan.entity.Plan;
import com.tileai.plan.entity.PlanPart;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
                propertyNames(responseDataSchema("/plans/{id}/copy", "post")));
    }

    // ------------------------------------------------------------------
    // 重算(T-1.15):契约侧证明"没有请求体"
    // ------------------------------------------------------------------

    /**
     * 架构不变量 6 的契约侧一半。
     *
     * <p>实现侧那半在 {@code PlanRecalcContractTest}(反射断言方法上不存在
     * {@code @RequestBody})。两条都要:<b>契约文件里没有 {@code requestBody}</b>
     * 只说明契约是这么写的,证明不了实现没多收参数 —— 契约比对工具也看不到
     * "契约没声明、实现却收了"这种多余参数。
     */
    @Test
    @DisplayName("POST /plans/{id}/recalc 没有 requestBody —— 不变量 6")
    void recalcHasNoRequestBody() {
        assertNull(operation("/plans/{id}/recalc", "post").get("requestBody"),
                "重算不接受任何请求体:部位/面积/开口一律取自方案快照。"
                        + "收了参数,前端就能把面积填成 1 ㎡ 绕过 /quote/calc 的参数校验");
    }

    /** 返回的是 {@code /quote/calc} 的那个形状,不是另造一个。 */
    @Test
    @DisplayName("POST /plans/{id}/recalc 的响应 data 就是契约 QuoteResult")
    void recalcResponseIsQuoteResult() {
        Object data = responseDataSchema("/plans/{id}/recalc", "post");
        // 内联写一整个 QuoteResult 也行,但那会有两份形状可以各自漂移
        assertEquals("./openapi-common.yaml#/components/schemas/QuoteResult",
                map(data, "recalc.data").get("$ref"),
                "重算响应的 data 必须 $ref 到 QuoteResult —— 与 /quote/calc 同源");
    }

    // ------------------------------------------------------------------
    // 分享(T-1.13)
    // ------------------------------------------------------------------

    /**
     * 两条写接口都不接受请求体。
     *
     * <p>与不变量 6 同一类:契约里这两条只有 path 参数,没有"有效期""允许谁看"
     * 这类开关。实现侧那半在 {@code PlanShareContractTest}(反射)。
     */
    @Test
    @DisplayName("POST / DELETE /plans/{id}/share 都没有 requestBody")
    void shareWriteEndpointsHaveNoRequestBody() {
        assertNull(operation("/plans/{id}/share", "post").get("requestBody"),
                "生成分享不接受任何参数 —— 契约里没有 requestBody");
        assertNull(operation("/plans/{id}/share", "delete").get("requestBody"),
                "废除分享不接受任何参数");
    }

    /** 生成分享返回的是 {@code ShareResult},不是裸信封。 */
    @Test
    @DisplayName("POST /plans/{id}/share 的响应 data 是契约 ShareResult")
    void sharePostResponseIsShareResult() {
        Object data = responseDataSchema("/plans/{id}/share", "post");
        assertEquals("#/components/schemas/ShareResult", map(data, "share.data").get("$ref"),
                "生成分享的 data 必须 $ref 到 ShareResult");
    }

    /**
     * 废除分享返回的是<b>裸信封</b>。
     *
     * <p>这条看着像在挑刺,但它钉的是一个真实的分叉风险:顺手回一个"当前分享状态"
     * 会让实现多发一个契约里不存在的 {@code data} —— 而契约比对工具<b>看不到</b>
     * "契约没声明、实现却发了"这种多余字段(它比的是契约声明过的字段)。
     */
    @Test
    @DisplayName("DELETE /plans/{id}/share 的响应是裸 ApiResponse,没有 data")
    void shareDeleteResponseHasNoData() {
        Map<String, Object> responses = map(
                operation("/plans/{id}/share", "delete").get("responses"), "responses");
        Map<String, Object> schema = map(
                firstContent(map(responses.get("200"), "200")).get("schema"), "schema");

        assertEquals("./openapi-common.yaml#/components/schemas/ApiResponse", schema.get("$ref"),
                "废除分享的响应应当直接 $ref ApiResponse —— 契约里没有额外声明 data");
        assertNull(schema.get("allOf"),
                "出现了 allOf 说明契约给这条加了 data,实现与断言都要跟着改");
    }

    /**
     * 分享页是<b>公开</b>的 —— {@code security: []}。
     *
     * <p>这是契约里唯一一条 {@code security: []} 的 C 端接口(不是"没写 security",
     * 而是显式声明"不需要" —— 两者在 OpenAPI 里的含义完全不同:没写 = 继承全局默认,
     * 空数组 = 明确公开)。
     *
     * <p>实现侧靠 {@code SecurityConfig} 的"没被登记进受保护清单"来满足它,
     * 那是个很容易被后人改动的地方 —— 所以 {@code PlanShareIntegrationTest}
     * 会不带 Cookie 真的调一次。
     */
    @Test
    @DisplayName("GET /share/{token} 是公开的(security: []),data 是 PlanDetail")
    void sharePageIsPublic() {
        Map<String, Object> op = operation("/share/{token}", "get");

        Object security = op.get("security");
        assertNotNull(security, "契约里这条必须**显式**写 security: [] —— "
                + "漏写会继承全局默认(需要登录),那是另一种意思");
        assertTrue(((List<?>) security).isEmpty(),
                "分享页必须显式声明为公开(security: []),实际: " + security);

        assertEquals("./openapi-common.yaml#/components/schemas/PlanDetail",
                map(responseDataSchema("/share/{token}", "get"), "sharePage.data").get("$ref"),
                "分享页发的 data 与本人详情接口是**同一个 schema** —— "
                        + "这也是 PlanDetailAssembler 存在的理由");
    }

    @Test
    @DisplayName("ShareResultView 的字段集合 == 契约 ShareResult 的属性集合")
    void shareResultMatchesContract() {
        assertSameFields(jsonFields(ShareResultView.class),
                schemaProperties("openapi-c.yaml", "ShareResult"));
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

    /** 某条操作 200 响应里 {@code allOf[ApiResponse, {data: X}]} 那个 {@code X} 的 schema。 */
    private static Map<String, Object> responseDataSchema(String path, String method) {
        Map<String, Object> responses = map(operation(path, method).get("responses"), "responses");
        Map<String, Object> schema = map(firstContent(map(responses.get("200"), "200")).get("schema"),
                "schema");

        List<?> allOf = (List<?>) schema.get("allOf");
        assertNotNull(allOf,
                path + " 的 " + method + " 响应应是 allOf[ApiResponse, {data:…}],契约结构变了");
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
