package com.tileai.common.agent;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 把一行 SSE 文本解析成 {@link ChatEvent}。
 *
 * <p><b>纯函数</b>,不碰网络、不碰线程 —— 所以能被直接单测。契约的事件集
 * 不在任何门禁的覆盖范围内(见 {@code openapi-verification.md} §40.4),
 * 解析器的每条分支只能靠 {@code SseEventParserTest} 钉住。
 *
 * <p>规则(契约):所有帧都是默认 {@code data:} 帧、没有 {@code event:} 名,
 * 靠 payload 的键区分;{@code [DONE]} 是字面量不是 JSON。
 * 空行 / 心跳 / 注释行 / 解析不了的 JSON 一律跳过(返回 empty)——
 * 一条坏帧不该让整条流断掉。
 */
public final class SseEventParser {

    private static final Logger log = LoggerFactory.getLogger(SseEventParser.class);

    private static final String DATA_PREFIX = "data:";
    private static final String DONE_MARKER = "[DONE]";

    private final ObjectMapper objectMapper;

    public SseEventParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 解析一行。非数据行、心跳、坏 JSON 都返回 {@link Optional#empty()}。 */
    public Optional<ChatEvent> parseLine(String line) {
        if (line == null) {
            return Optional.empty();
        }
        String trimmed = line.strip();
        if (!trimmed.startsWith(DATA_PREFIX)) {
            // 空行(帧分隔)、`: keep-alive` 注释、`event:` 行 —— 都不是数据
            return Optional.empty();
        }

        String payload = trimmed.substring(DATA_PREFIX.length()).strip();
        if (payload.isEmpty()) {
            return Optional.empty();
        }
        if (DONE_MARKER.equals(payload)) {
            return Optional.of(new ChatEvent.Done());
        }

        JsonNode node;
        try {
            node = objectMapper.readTree(payload);
        } catch (Exception error) {
            log.warn("跳过无法解析的 SSE 帧:{}", payload);
            return Optional.empty();
        }
        if (!node.isObject()) {
            return Optional.empty();
        }

        if (node.has("delta")) {
            return Optional.of(new ChatEvent.Delta(node.path("delta").asText("")));
        }
        if (node.has("refs")) {
            return Optional.of(new ChatEvent.Refs(node.path("refs")));
        }
        if (node.has("error")) {
            JsonNode error = node.path("error");
            return Optional.of(new ChatEvent.Error(
                    error.path("code").asInt(),
                    error.path("message").asText(""),
                    error.path("traceId").asText("")));
        }
        if (node.has("sessionId")) {
            return Optional.of(new ChatEvent.SessionFrame(node.path("sessionId").asText("")));
        }
        return Optional.empty();
    }
}
