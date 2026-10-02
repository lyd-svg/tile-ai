package com.tileai.agent.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 可采纳的推荐项(契约 {@code openapi-common.yaml#/components/schemas/SkuRef},
 * {@code required: [sku, part]})。
 *
 * <p>这是 SSE 的 {@code refs} 帧、也是 {@code ChatMessage.refs} 的元素。
 * Java 侧**原样透传** Python 给的列表(Python 已逐个回问
 * {@code /internal/products/{sku}} 校验过,查不到的不会出现在这里)——
 * Java 不再重复校验,否则就是两处实现同一件事。
 *
 * <p>{@code name} 可空(契约里非必填),用 {@code NON_NULL} 省掉 {@code "name": null}。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SkuRefView(String sku, String name, String part) {
}
