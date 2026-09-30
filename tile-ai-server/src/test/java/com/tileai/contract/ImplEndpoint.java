package com.tileai.contract;

import java.util.Set;
import java.util.TreeSet;

/**
 * 实现侧的一条真实路由 —— 从 Spring 自己的路由表里读出来的,不是人写的。
 *
 * <p>刻意<b>不持有 {@code HandlerMethod}</b>(只留类名与方法名的字符串):这样比较器与
 * 负向对照测试都能凭空合成它,不必启动 Spring 上下文。
 *
 * @param method        HTTP 方法(大写;映射未限定方法时为 {@code *})
 * @param path          Spring 注册的 pattern
 * @param bean          处理它的 bean 类名
 * @param handler       处理方法名
 * @param requestShape  {@code @RequestBody} 参数的形态;没有请求体时为 {@link Shape#NONE}
 * @param responseShape 剥掉 {@code ResponseEntity} / {@code ApiResponse} 之后的形态
 * @param skipReason    非 null 表示这条路由<b>无法参与比对</b>及原因(如"非 JSON 响应")
 * @param skipGroup     跳过原因的分组名(报告里按组收拢,免得十几条探针把信号淹掉)
 */
record ImplEndpoint(String method,
                    String path,
                    String bean,
                    String handler,
                    Shape requestShape,
                    Shape responseShape,
                    String skipReason,
                    String skipGroup) {

    ImplEndpoint {
        requestShape = requestShape == null ? Shape.NONE : requestShape;
        responseShape = responseShape == null ? Shape.UNKNOWN : responseShape;
    }

    PathKey key() {
        return PathKey.of(method, path);
    }

    boolean skippable() {
        return skipReason != null;
    }

    /** 请求体属性名;请求体不是具名对象时为空集(此时看 {@link #requestShape()})。 */
    Set<String> requestFields() {
        return requestShape.kind() == Shape.Kind.OBJECT
                ? requestShape.fieldNames()
                : Set.of();
    }

    Set<String> sortedRequestFields() {
        return new TreeSet<>(requestFields());
    }

    String brief() {
        return String.format("%-6s %s", method, path);
    }

    /** 处理它的类#方法 —— 报告里指到具体代码。 */
    String where() {
        return bean + "#" + handler;
    }
}
