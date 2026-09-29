package com.tileai.product;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import com.tileai.RepoDocs;
import com.tileai.common.api.PageData;
import com.tileai.product.dto.ProductUpsertRequest;
import com.tileai.product.dto.SkuView;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 把产品库的 DTO 与契约 schema <b>逐字段绑住</b>。
 *
 * <p>比对的是<b>实际 JSON 字段名</b>(经 Jackson 内省,含 {@code @JsonIgnore} 等影响),
 * 而不是 Java 字段名 —— 后者与对外契约没有必然关系。
 *
 * <h2>这覆盖了 T-0.6 验证判据的哪一部分</h2>
 *
 * <p>T-0.6 的判据写的是"<b>实现导出的 OpenAPI</b> 与契约片段结构一致"。
 * 那需要 T-12 的导出-比对基础设施(Java 侧用 springdoc 导出 {@code /v3/api-docs}),
 * 而任务清单明确把 T-12 排在 <b>T-2.1</b>(依赖"各实现模块基本就位")。
 *
 * <p>本测试是同一类漂移的<b>轻量替代</b>:不导出 OpenAPI,直接比对
 * "实现会产出/接受哪些字段"与"契约声明了哪些字段"。它能抓住
 * <b>字段名拼错、漏字段、多字段</b> —— 也就是结构不一致的主要形态。
 * 它<b>抓不到</b>的是 HTTP 方法/路径/状态码层面的偏差,那部分仍须等 T-2.1。
 */
class ProductContractTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @DisplayName("ProductUpsertRequest 的字段集合 == 契约 ProductUpsert 的属性集合")
    void upsertRequestMatchesContract() {
        Set<String> actual = jsonFields(ProductUpsertRequest.class);
        Set<String> expected = schemaProperties("openapi-admin.yaml", "ProductUpsert");

        assertEquals(expected, actual, diffMessage(expected, actual));
    }

    @Test
    @DisplayName("ProductUpsertRequest.images[] 的字段集合 == 契约中的 images.items")
    void upsertImagesMatchContract() {
        Set<String> actual = jsonFields(ProductUpsertRequest.ImageItem.class);
        Set<String> expected = nestedProperties("openapi-admin.yaml", "ProductUpsert", "images");

        assertEquals(expected, actual, diffMessage(expected, actual));
    }

    @Test
    @DisplayName("SkuView 的字段集合 == 契约 Sku 的属性集合")
    void skuViewMatchesContract() {
        Set<String> actual = jsonFields(SkuView.class);
        Set<String> expected = schemaProperties("openapi-common.yaml", "Sku");

        assertEquals(expected, actual, diffMessage(expected, actual));
    }

    @Test
    @DisplayName("SkuView.images[] 的字段集合 == 契约 Sku.images.items")
    void skuImagesMatchContract() {
        Set<String> actual = jsonFields(SkuView.ImageItem.class);
        Set<String> expected = nestedProperties("openapi-common.yaml", "Sku", "images");

        assertEquals(expected, actual, diffMessage(expected, actual));
    }

    @Test
    @DisplayName("PageData 的字段与必填项 == 契约(校验表 T-3)")
    void pageDataMatchesContract() {
        Set<String> actual = jsonFields(PageData.class);

        @SuppressWarnings("unchecked")
        Map<String, Object> pageData = RepoDocs.child(
                RepoDocs.child(RepoDocs.yaml("openapi-common.yaml"), "components"), "schemas");
        @SuppressWarnings("unchecked")
        Map<String, Object> schema = (Map<String, Object>) pageData.get("PageData");

        Set<String> expected = propertyNames(schema);
        assertEquals(expected, actual, diffMessage(expected, actual));

        @SuppressWarnings("unchecked")
        Set<String> required = new TreeSet<>((List<String>) schema.get("required"));
        assertEquals(Set.of("list", "total", "page", "size"), required,
                "契约要求 PageData 四个字段全部必填(T-3)");
    }

    @Test
    @DisplayName("契约 ProductUpsert 的必填项在实现里有对应的非空约束")
    void requiredFieldsAreValidated() {
        // 契约 required: [sku, name, pricePerSqm] —— 缺任何一个都该被 10001 拦住。
        // 这里做静态确认:三个字段确实存在(具体拦截行为由集成测试覆盖)。
        Set<String> fields = jsonFields(ProductUpsertRequest.class);
        assertTrue(fields.containsAll(Set.of("sku", "name", "pricePerSqm")),
                "契约声明必填的三个字段必须在实现中可接收: " + fields);
    }

    // ------------------------------------------------------------------

    /**
     * 取一个类型的实际 JSON 字段名集合。
     * <p>用 Jackson 内省而不是反射看 Java 字段 —— 对外契约由序列化行为决定。
     */
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
        Map<String, Object> schemas = RepoDocs.child(RepoDocs.child(RepoDocs.yaml(file), "components"),
                "schemas");
        @SuppressWarnings("unchecked")
        Map<String, Object> schema = (Map<String, Object>) schemas.get(schemaName);
        assertTrue(schema != null, file + " 中找不到 schema: " + schemaName);
        return propertyNames(schema);
    }

    /** 取某个数组属性的 items.properties 的键集合。 */
    private static Set<String> nestedProperties(String file, String schemaName, String arrayField) {
        @SuppressWarnings("unchecked")
        Map<String, Object> schemas = RepoDocs.child(RepoDocs.child(RepoDocs.yaml(file), "components"),
                "schemas");
        assertTrue(schemas.containsKey(schemaName),
                file + " 中没有 schema " + schemaName + ";可用的有: " + schemas.keySet());
        @SuppressWarnings("unchecked")
        Map<String, Object> schema = (Map<String, Object>) schemas.get(schemaName);

        // 必须先进 properties —— schema 的顶层键是 type / description / required / properties,
        // 字段名都在 properties 之下。
        @SuppressWarnings("unchecked")
        Map<String, Object> declared = (Map<String, Object>) schema.get("properties");
        assertTrue(declared != null, "schema " + schemaName + " 没有 properties");

        Object field = declared.get(arrayField);
        assertTrue(field instanceof Map,
                "schema " + schemaName + " 中找不到数组属性 " + arrayField
                        + " —— 该 schema 实际声明的属性是: " + declared.keySet());
        @SuppressWarnings("unchecked")
        Map<String, Object> fieldMap = (Map<String, Object>) field;

        Object items = fieldMap.get("items");
        assertTrue(items instanceof Map, arrayField + " 缺少 items 定义");
        @SuppressWarnings("unchecked")
        Map<String, Object> itemsMap = (Map<String, Object>) items;
        return propertyNames(itemsMap);
    }

    @SuppressWarnings("unchecked")
    private static Set<String> propertyNames(Map<String, Object> schema) {
        return new TreeSet<>(((Map<String, Object>) schema.get("properties")).keySet());
    }

    private static String diffMessage(Set<String> expected, Set<String> actual) {
        Set<String> missing = new LinkedHashSet<>(expected);
        missing.removeAll(actual);
        Set<String> extra = new LinkedHashSet<>(actual);
        extra.removeAll(expected);
        return "与契约 schema 不一致 —— 契约有而实现无: " + missing + ";实现有而契约无: " + extra;
    }
}
