package com.tileai.common.api;

import java.util.List;

/**
 * 分页数据包装,作为 {@code ApiResponse.data} 使用。
 *
 * <p>契约:{@code openapi-common.yaml#/components/schemas/PageData},
 * {@code required: [list, total, page, size]}(契约校验表 T-3)。
 *
 * <p><b>{@code data} 是对象而不是数组</b> —— 契约把 {@code ApiResponse.data} 声明为
 * {@code type: object},列表必须裹在这一层里。直接返回数组既不合法,
 * 也让"分页"失去了承载 total/page/size 的位置。
 */
public record PageData<T>(List<T> list, long total, int page, int size) {

    public static <T> PageData<T> of(List<T> list, long total, int page, int size) {
        return new PageData<>(list, total, page, size);
    }
}
