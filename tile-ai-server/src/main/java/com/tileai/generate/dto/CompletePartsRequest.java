package com.tileai.generate.dto;

import java.util.List;

/**
 * 调 Python 补齐端点({@code POST /internal/agent/complete-parts})的请求体。
 * 契约 {@code openapi-internal.yaml} 的 {@code CompletePartsRequest}。
 *
 * <p><b>字段名必须与契约逐字一致</b>({@code space} / {@code style} / {@code selectedParts})——
 * 契约 ↔ 实现比对按字段名判,改个名就会被判成"契约里有个字段实现没有"。
 */
public record CompletePartsRequest(String space, String style, List<PartSelection> selectedParts) {
}
