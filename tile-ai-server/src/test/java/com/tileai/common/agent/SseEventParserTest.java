package com.tileai.common.agent;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T-1.6:{@link SseEventParser} 的纯函数测试。
 *
 * <p>SSE 事件集**不在任何门禁的覆盖范围内**(见 {@code openapi-verification.md} §40.4),
 * 所以这两件事只能靠本文件钉住:帧的识别规则、以及"坏帧不该让整条流断掉"。
 */
class SseEventParserTest {

    private final SseEventParser parser = new SseEventParser(new ObjectMapper());

    @Test
    @DisplayName("delta 帧 → Delta")
    void parsesDelta() {
        Optional<ChatEvent> event = parser.parseLine("data: {\"delta\":\"雪瓷白\"}");

        assertEquals("雪瓷白", assertInstanceOf(ChatEvent.Delta.class, event.orElseThrow()).delta());
    }

    @Test
    @DisplayName("refs 帧 → Refs(原样保留 JSON,不在此层解析结构)")
    void parsesRefs() {
        Optional<ChatEvent> event = parser.parseLine(
                "data: {\"refs\":[{\"sku\":\"TL-3012\",\"part\":\"wall\"}]}");

        ChatEvent.Refs refs = assertInstanceOf(ChatEvent.Refs.class, event.orElseThrow());
        assertEquals("TL-3012", refs.refs().get(0).path("sku").asText());
    }

    @Test
    @DisplayName("error 帧 → Error,三个字段都在")
    void parsesError() {
        Optional<ChatEvent> event = parser.parseLine(
                "data: {\"error\":{\"code\":50001,\"message\":\"超出范围\",\"traceId\":\"t-1\"}}");

        ChatEvent.Error error = assertInstanceOf(ChatEvent.Error.class, event.orElseThrow());
        assertEquals(50001, error.code());
        assertEquals("超出范围", error.message());
        assertEquals("t-1", error.traceId());
    }

    @Test
    @DisplayName("[DONE] 是字面量,不是 JSON")
    void parsesDone() {
        assertInstanceOf(ChatEvent.Done.class, parser.parseLine("data: [DONE]").orElseThrow());
        assertInstanceOf(ChatEvent.Done.class, parser.parseLine("data:[DONE]").orElseThrow());
    }

    @Test
    @DisplayName("sessionId 帧被识别出来(由编排器吞掉)")
    void parsesSessionFrame() {
        Optional<ChatEvent> event = parser.parseLine("data: {\"sessionId\":\"s_88\"}");

        assertEquals("s_88",
                assertInstanceOf(ChatEvent.SessionFrame.class, event.orElseThrow()).sessionId());
    }

    @Test
    @DisplayName("空行 / 心跳 / 注释 / 非 data 行一律跳过")
    void skipsNonDataLines() {
        assertTrue(parser.parseLine("").isEmpty());
        assertTrue(parser.parseLine("   ").isEmpty());
        assertTrue(parser.parseLine(": keep-alive").isEmpty());
        assertTrue(parser.parseLine("event: message").isEmpty());
        assertTrue(parser.parseLine("data:").isEmpty());
    }

    @Test
    @DisplayName("坏 JSON 跳过,不抛 —— 一条坏帧不该让整条流断掉")
    void skipsMalformedJson() {
        assertTrue(parser.parseLine("data: {不是 json}").isEmpty());
        assertTrue(parser.parseLine("data: [1,2,3]").isEmpty());
        assertTrue(parser.parseLine("data: {}").isEmpty());
    }

    @Test
    @DisplayName("null 行不抛")
    void handlesNull() {
        assertTrue(parser.parseLine(null).isEmpty());
    }
}
