package com.tileai.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 参数校验失败(10001)的明细元素,放于 {@code ApiResponse.data.errors[]}。
 *
 * <p>契约:{@code openapi-common.yaml#/components/schemas/ErrorItem},
 * {@code required: [field, reason]},{@code rejectedValue} 可选。
 *
 * <p>注意契约里该 schema 带 {@code x-todo} —— 元素字段结构上游未定义、由契约作者推断。
 * 本类按契约<b>现状</b>实现;若该 x-todo 被裁决修改,此处需同步。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorItem(String field, String reason, Object rejectedValue) {

    public static ErrorItem of(String field, String reason) {
        return new ErrorItem(field, reason, null);
    }
}
