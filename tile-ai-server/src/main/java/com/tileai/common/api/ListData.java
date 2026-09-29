package com.tileai.common.api;

import java.util.List;

/**
 * 非分页的列表包装,作为 {@code ApiResponse.data} 使用。
 *
 * <p>契约里 {@code /furnish}、{@code /quote-params}、{@code /dicts} 三个列表接口的
 * {@code data} 都是 <b>{@code { list: [...] }} 而不带 {@code total/page/size}</b> ——
 * 它们返回的是配置项全集(字典、陈设矩阵、参数表),数量可控且前端需要全量,
 * 分页反而添乱。
 *
 * <p>与 {@link PageData} 分开而不是复用后者把分页字段填 0:那样前端无法区分
 * "真的只有 0 条"和"这个接口不分页"。契约怎么声明就怎么给。
 */
public record ListData<T>(List<T> list) {

    public static <T> ListData<T> of(List<T> list) {
        return new ListData<>(list);
    }
}
