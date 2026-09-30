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

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import com.tileai.common.api.ApiResponse;

/**
 * 从 <b>Spring 自己的路由表</b>读出实现侧真实注册的每一条路由。
 *
 * <h2>为什么不用 springdoc 导出一份 OpenAPI</h2>
 *
 * <p>那是路线图里 T-2.1 的正规做法,但放在"每做完一组接口就量一次"这个场景里不划算:
 * springdoc 按 Java 类名生成 schema 键({@code SkuView} vs 契约的 {@code Sku}),要么维护
 * 一张随 DTO 增长、<b>且不会因为分叉而变红</b>的名字映射表,要么放弃"按名比";而它多给的
 * 类型/必填/枚举,恰恰是本次明确排除的三项。它还是自动配置 —— 只要在 classpath 上就会被
 * 每个 {@code @SpringBootTest} 拉进上下文,改变上下文缓存键,并在启动期遍历全部 handler
 * 建 schema(任一处抛异常,所有测试一起挂)。
 *
 * <p><b>路由表 + Jackson 内省</b>给出的正是"路径 + 方法 + 字段名",且生产产物零改动。
 */
final class ImplReader {

    /** 方法条件为空时(理论上只有 `@RequestMapping` 不限定方法)记为通配。 */
    static final String WILDCARD = "*";

    private static final int MAX_DEPTH = 2;

    /** 测试源码里的探针控制器——不该被当成"实现"。 */
    private static final String TEST_CLASSES_MARKER = "test-classes";

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
        if (raw.isEnum() || isScalar(raw)) {
            return Shape.SCALAR;
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
        for (BeanPropertyDefinition property : properties) {
            // getPrimaryType() 是**已代入泛型实参**后的类型,不必自己剥壳
            fields.put(property.getName(), shapeOf(property.getPrimaryType(), depth + 1));
        }
        return Shape.object(fields);
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
