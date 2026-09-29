package com.tileai.store.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.tileai.store.entity.Store;

/**
 * 门店信息的对外视图。
 *
 * <p>契约:{@code openapi-common.yaml#/components/schemas/Store},
 * {@code required: [name, address, phone]},{@code mapUrl} 为 nullable。
 *
 * <p>{@code mapUrl} 未填时<b>整个省略</b>而非输出 {@code null} ——
 * T-0.9 的 schema 校验已经证明:声明为 {@code string} 又没标 {@code nullable} 的字段,
 * 收到显式 null 会被判为类型不符。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record StoreView(Long id, String name, String address, String phone, String mapUrl) {

    public static StoreView of(Store entity) {
        return new StoreView(entity.getId(), entity.getName(), entity.getAddress(),
                entity.getPhone(), entity.getMapUrl());
    }
}
