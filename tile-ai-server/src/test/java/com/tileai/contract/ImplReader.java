package com.tileai.contract;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.springframework.core.ResolvableType;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import com.tileai.common.api.ApiResponse;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * 从 <b>Spring 自己的路由表</b>读出实现侧真实注册的每一条路由。
 *
 * <h2>为什么不用 springdoc 导出一份 OpenAPI</h2>
 *
 * <p>那是路线图里 T-2.1 的正规写法,但放在"每做完一组接口就量一次"这个场景里不划算:
 * springdoc 按 Java 类名生成 schema 键({@code SkuView} vs 契约的 {@code Sku}),要么维护
 * 一张随 DTO 增长、<b>且不会因为分叉而变红</b>的名字映射表,要么放弃"按名比"。它还是自动配置
 * —— 只要在 classpath 上就会被每个 {@code @SpringBootTest} 拉进上下文,改变上下文缓存键,
 * 并在启动期遍历全部 handler 建 schema(任一处抛异常,所有测试一起挂),而且会让
 * {@code /v3/api-docs} 落在 {@code SecurityConfig} 那个 {@code permitAll} 兜底之下,变成公网可拉。
 *
 * <p><b>路由表 + Jackson 内省</b>给出的是"路径 + 方法 + 字段名 + 类型 + 必填 + 枚举值",
 * 且<b>生产产物零改动</b>(2026-10-01 用户裁决,见 openapi-verification.md §40)。
 *
 * <h2>枚举值从哪来(2026-10-01 新增)</h2>
 *
 * <p>两条来源,缺一不可:
 * <ol>
 *   <li>Java {@code enum} 常量 —— 用 ObjectMapper 序列化取名,自动尊重
 *       {@code @JsonValue} / 命名策略 / {@code @JsonProperty};</li>
 *   <li>{@code String} + {@code @Pattern(regexp="a|b|c")} —— <b>本仓库的主力形态</b>。
 *       实测全仓 16 处 {@code @Pattern} 全是这种简单交替(如 {@code door|window}、
 *       {@code floor|wall|local})。只认 {@code Class.isEnum()} 会把它们全部漏掉,
 *       而 §31.2 那次真实分叉恰恰长这个样子。</li>
 * </ol>
 *
 * <p>走后台字典的字段(空间 / 风格)推不出取值集合 —— 留空,由比较器记「无法比对」,
 * <b>绝不猜</b>。
 */
final class ImplReader {

    /** 方法条件为空时(理论上只有 `@RequestMapping` 不限定方法)记为通配。 */
    static final String WILDCARD = "*";

    /**
     * 深度上限,与契约侧同一口径。
     *
     * <p>2026-10-01 由 2 抬到 5:契约的 {@code OpeningType} 落在
     * {@code GenerateRequest.layout.spaces[].openings[].type},深度 5 —— 深度 2 够不到它。
     */
    private static final int MAX_DEPTH = 5;

    /** 测试源码里的探针控制器——不该被当成"实现"。 */
    private static final String TEST_CLASSES_MARKER = "test-classes";

    /**
     * 认得出来的"枚举式"正则:<b>全是字面量、用 {@code |} 交替</b>。
     *
     * <p>刻意只认这种 —— 格式校验类正则(如 {@code ^\d{4}$})被切成"枚举"会造出假分叉,
     * 而那种误判比漏掉更难查。
     */
    private static final java.util.regex.Pattern SIMPLE_ALTERNATION =
            java.util.regex.Pattern.compile("[A-Za-z0-9_.\\-]+(\\|[A-Za-z0-9_.\\-]+)+");

    private final ObjectMapper mapper;

    ImplReader(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    List<ImplEndpoint> read(RequestMappingHandlerMapping mapping) {
        List<ImplEndpoint> out = new ArrayList<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry
                : mapping.getHandlerMethods().entrySet()) {
            RequestMappingInfo info = entry.getKey();
            HandlerMethod handler = entry.getValue();

            String testSourceReason = testSourceReason(handler);
            Set<String> httpMethods = new TreeSet<>();
            info.getMethodsCondition().getMethods()
                    .forEach(m -> httpMethods.add(m.name()));
            if (httpMethods.isEmpty()) {
                httpMethods.add(WILDCARD);
            }

            JavaType response = responsePayloadType(handler);
            Shape responseShape = response == null ? Shape.NONE : shapeOf(response, 1);
            JavaType request = requestPayloadType(handler);
            // 没有 @RequestBody 就是"没有请求体"(NONE),与"有请求体但比不了"(UNKNOWN)要分开
            Shape requestShape = request == null ? Shape.NONE : shapeOf(request, 1);

            for (String pattern : info.getPatternValues()) {
                out.add(new ImplEndpoint(
                        httpMethods.size() == 1 ? httpMethods.iterator().next() : WILDCARD,
                        pattern,
                        handler.getBeanType().getSimpleName(),
                        handler.getMethod().getName(),
                        requestShape,
                        responseShape,
                        skipReason(testSourceReason, responseShape),
                        skipGroup(testSourceReason, responseShape)));
            }
        }
        return out;
    }

    /** 整条无法比对时的原因;可比对时为 null。 */
    private String skipReason(String testSourceReason, Shape responseShape) {
        if (testSourceReason != null) {
            return testSourceReason;
        }
        return streamReason(responseShape);
    }

    /** 原因的分组名 —— 报告按组收拢,十几条探针不会把信号淹掉。 */
    private String skipGroup(String testSourceReason, Shape responseShape) {
        if (testSourceReason != null) {
            return Group.TEST_PROBE;
        }
        if (streamReason(responseShape) != null) {
            return Group.NOT_JSON;
        }
        return null;
    }

    /** 跳过分组名。 */
    static final class Group {

        static final String TEST_PROBE = "探针(仅测试源码)";
        static final String NOT_JSON = "非 JSON 响应";

        private Group() {
        }
    }

    /** 探针控制器只活在测试源码里,不该进"实现有契约无"的清单。 */
    private String testSourceReason(HandlerMethod handler) {
        Class<?> beanType = handler.getBeanType();
        try {
            var source = beanType.getProtectionDomain().getCodeSource();
            if (source != null && source.getLocation() != null
                    && source.getLocation().getPath().contains(TEST_CLASSES_MARKER)) {
                return "仅存在于测试源码的探针控制器(会被组件扫描注册)";
            }
        } catch (RuntimeException ignored) {
            // 拿不到 CodeSource 时不判定为测试源码 —— 宁可多列一条,也不要漏掉真实路由
        }
        return null;
    }

    /** 响应不是 JSON(SSE / 流)时整条记「无法比对」。 */
    private String streamReason(Shape responseShape) {
        return responseShape.kind() == Shape.Kind.BINARY
                ? "响应不是 JSON(流 / 二进制),本骨架不解析"
                : null;
    }

    // ------------------------------------------------------------ 类型 → 形态

    /**
     * 仅供负向对照测试:不启 Spring,直接按 Java 类型算形态(见 {@code ImplReaderShapeTest})。
     *
     * <p>存在的理由:枚举取值与必填这两个维度的读取逻辑(注解多来源兜底、{@code @Pattern}
     * 的切分)是最容易<b>静默失灵</b>的地方 —— 失灵时报告照样全绿,只是再也不会报错。
     * 只有拿合成 DTO 直接喂进来,才能证明"确实读到了",而不是"没报错"。
     */
    Shape describe(JavaType type) {
        return shapeOf(type, 1);
    }

    private Shape shapeOf(JavaType type, int depth) {
        if (type == null) {
            return Shape.UNKNOWN;
        }
        if (depth > MAX_DEPTH) {
            return Shape.TRUNCATED;
        }
        Class<?> raw = type.getRawClass();
        if (raw == void.class || raw == Void.class) {
            return Shape.NONE;
        }
        if (raw == byte[].class || raw == Byte[].class
                || Resource.class.isAssignableFrom(raw)
                || InputStream.class.isAssignableFrom(raw)
                || SseEmitter.class.isAssignableFrom(raw)
                || StreamingResponseBody.class.isAssignableFrom(raw)) {
            return Shape.BINARY;
        }
        if (Map.class.isAssignableFrom(raw)) {
            return Shape.MAP;
        }
        if (raw.isEnum()) {
            return Shape.scalar(Shape.JsonType.STRING, enumConstants(raw));
        }
        if (isScalar(raw)) {
            return Shape.scalar(scalarType(raw), Set.of());
        }
        if (type.isArrayType() || Collection.class.isAssignableFrom(raw)
                || Iterable.class.isAssignableFrom(raw)) {
            // 数组不消耗深度:元素对象的字段仍落在同一层 —— 与契约侧口径一致
            return Shape.array(shapeOf(type.getContentType(), depth));
        }

        BeanDescription description = mapper.getSerializationConfig().introspect(type);
        List<BeanPropertyDefinition> properties = description.findProperties();
        if (properties.isEmpty()) {
            return Shape.UNKNOWN;
        }
        Map<String, Shape> fields = new LinkedHashMap<>();
        Set<String> required = new TreeSet<>();
        for (BeanPropertyDefinition property : properties) {
            // getPrimaryType() 是**已代入泛型实参**后的类型,不必自己剥壳
            fields.put(property.getName(), valueShape(property, depth + 1));
            if (isRequired(property)) {
                required.add(property.getName());
            }
        }
        return Shape.object(fields, required);
    }

    /**
     * 一个属性的形态 —— 先按类型算,再补上 {@code @Pattern} 里的取值集合。
     *
     * <p>只有"本来就算标量、且没有别的枚举来源"时才补,免得到数组/对象上乱套。
     */
    private Shape valueShape(BeanPropertyDefinition property, int depth) {
        Shape base = shapeOf(property.getPrimaryType(), depth);
        if (base.kind() != Shape.Kind.SCALAR || base.hasEnumValues()) {
            return base;
        }
        Set<String> fromPattern = enumFromPattern(property);
        return fromPattern.isEmpty() ? base : Shape.scalar(base.type(), fromPattern);
    }

    private static Shape.JsonType scalarType(Class<?> raw) {
        if (raw == boolean.class || raw == Boolean.class) {
            return Shape.JsonType.BOOLEAN;
        }
        if (raw == byte.class || raw == short.class || raw == int.class || raw == long.class
                || raw == Byte.class || raw == Short.class || raw == Integer.class
                || raw == Long.class || raw == java.math.BigInteger.class) {
            return Shape.JsonType.INTEGER;
        }
        if (raw == float.class || raw == double.class
                || raw == Float.class || raw == Double.class
                || raw == java.math.BigDecimal.class) {
            return Shape.JsonType.NUMBER;
        }
        return Shape.JsonType.STRING;
    }

    private static boolean isScalar(Class<?> raw) {
        return raw.isPrimitive()
                || CharSequence.class.isAssignableFrom(raw)
                || Number.class.isAssignableFrom(raw)
                || Boolean.class == raw
                || Character.class == raw
                || java.util.Date.class.isAssignableFrom(raw)
                || Temporal.class.isAssignableFrom(raw)
                || raw.getName().startsWith("java.time.");
    }

    // ------------------------------------------------------------ 枚举值

    /** Java {@code enum} 的常量 → 它们的 <b>JSON 序列化名</b>(尊重 @JsonValue / 命名策略)。 */
    private Set<String> enumConstants(Class<?> raw) {
        Object[] constants = raw.getEnumConstants();
        Set<String> out = new TreeSet<>();
        if (constants == null) {
            return out;
        }
        for (Object constant : constants) {
            out.add(jsonName(constant));
        }
        return out;
    }

    private String jsonName(Object constant) {
        try {
            var node = mapper.valueToTree(constant);
            if (node != null && node.isValueNode()) {
                return node.asText();
            }
        } catch (RuntimeException ignored) {
            // 序列化不出来就退回枚举常量名 —— 宁可名字对不上,也不要漏掉这个枚举
        }
        return constant instanceof Enum<?> e ? e.name() : String.valueOf(constant);
    }

    /** {@code @Pattern(regexp="a|b|c")} 里的取值集合;不是"简单交替"时返回空集。 */
    private static Set<String> enumFromPattern(BeanPropertyDefinition property) {
        for (AnnotatedMember member : members(property)) {
            Pattern annotation = member.getAnnotation(Pattern.class);
            if (annotation == null) {
                continue;
            }
            String regexp = annotation.regexp();
            if (SIMPLE_ALTERNATION.matcher(regexp).matches()) {
                return new TreeSet<>(List.of(regexp.split("\\|")));
            }
        }
        return Set.of();
    }

    // ------------------------------------------------------------ 必填

    /**
     * 该属性是否必填。
     *
     * <p>只认会"拒绝 null/空"的注解:{@code @NotNull} / {@code @NotBlank} / {@code @NotEmpty} /
     * {@code @JsonProperty(required=true)}。
     * <b>{@code @Valid} / {@code @Size} / {@code @DecimalMin} / {@code @Pattern} 一律不算</b> ——
     * 它们不拒绝 null。
     */
    private static boolean isRequired(BeanPropertyDefinition property) {
        for (AnnotatedMember member : members(property)) {
            if (member.getAnnotation(NotNull.class) != null
                    || member.getAnnotation(NotBlank.class) != null
                    || member.getAnnotation(NotEmpty.class) != null) {
                return true;
            }
            JsonProperty jsonProperty = member.getAnnotation(JsonProperty.class);
            if (jsonProperty != null && jsonProperty.required()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 一个属性上所有可能挂注解的地方。
     *
     * <p>逐个来源都看,是因为 record 组件的注解落在哪一处依注解的 {@code @Target} 而定 ——
     * 只看 {@code getPrimaryMember()} 会让必填维度<b>静默失灵</b>(最典型的假绿)。
     */
    private static List<AnnotatedMember> members(BeanPropertyDefinition property) {
        List<AnnotatedMember> out = new ArrayList<>(4);
        add(out, property.getPrimaryMember());
        add(out, property.getField());
        add(out, property.getGetter());
        add(out, property.getSetter());
        add(out, property.getConstructorParameter());
        return out;
    }

    private static void add(List<AnnotatedMember> out, AnnotatedMember member) {
        if (member != null && !out.contains(member)) {
            out.add(member);
        }
    }

    // ------------------------------------------------------------ 剥壳

    /**
     * 剥掉 {@code ResponseEntity} 与 {@code ApiResponse},留下 {@code data} 的真实类型。
     * 契约侧的响应形态同样只描述 {@code data},两边对得上。
     */
    private JavaType responsePayloadType(HandlerMethod handler) {
        Method method = handler.getMethod();
        ResolvableType type = ResolvableType.forMethodReturnType(method, handler.getBeanType());
        Class<?> raw = type.resolve();
        if (raw == null) {
            return null;
        }
        if (ResponseEntity.class.isAssignableFrom(raw)) {
            type = type.getGeneric(0);
            raw = type.resolve();
        }
        if (raw != null && ApiResponse.class.isAssignableFrom(raw)) {
            type = type.getGeneric(0);
            raw = type.resolve();
        }
        if (raw == null) {
            return null;
        }
        return mapper.constructType(type.getType());
    }

    /** {@code @RequestBody} 参数的类型;没有请求体时 null。 */
    private JavaType requestPayloadType(HandlerMethod handler) {
        for (var parameter : handler.getMethodParameters()) {
            if (parameter.hasParameterAnnotation(RequestBody.class)) {
                return mapper.constructType(parameter.getGenericParameterType());
            }
        }
        return null;
    }
}
