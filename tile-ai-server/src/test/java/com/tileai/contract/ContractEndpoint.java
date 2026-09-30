package com.tileai.contract;

import java.util.Set;
import java.util.TreeSet;

/**
 * 契约侧的一个操作(一个 path + 一个 HTTP 方法)。
 *
 * @param method        HTTP 方法(大写)
 * @param path          已与 {@code servers[].url} 拼好的完整路径
 * @param file          来自哪份契约(报告里要标出处)
 * @param owner         该由谁实现 —— 见 {@link Owner}
 * @param requestShape  请求体 {@code application/json} 的形态;没声明请求体时为
 *                      {@link Shape#NONE},声明了但无从比对时为 {@link Shape#UNKNOWN}
 * @param responseShape 响应体里 {@code data} 的形态(**不含信封本身**)
 * @param summary       契约里的 summary,报告里给出人话
 */
record ContractEndpoint(String method,
                        String path,
                        String file,
                        Owner owner,
                        Shape requestShape,
                        Shape responseShape,
                        String summary) {

    /**
     * 该操作的实现方。
     *
     * <p><b>由契约自证,不手写清单</b> —— c / admin 全归 Java(它们就是对外 API,
     * 见不可变量 3);internal 逐条读操作上的 {@code x-direction}。
     */
    enum Owner {
        JAVA,
        PYTHON
    }

    PathKey key() {
        return PathKey.of(method, path);
    }

    boolean declaresRequestBody() {
        return requestShape.kind() != Shape.Kind.NONE;
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

    /** 供报告展示:方法 + 路径。 */
    String brief() {
        return String.format("%-6s %s", method, path);
    }

    /** 出处:文件名 + summary,便于人去翻契约。 */
    String origin() {
        return file + " · " + (summary == null || summary.isBlank() ? "(无 summary)" : summary);
    }
}
