package com.tileai.common.agent;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Python 侧 SSE 流的一帧(D-7 冻结的事件集)。
 *
 * <p>这一层只负责"把 Python 的帧翻译成 Java 认得的东西",不做业务判断 ——
 * 事件的合法性、互斥、顺序由 {@code AgentChatOrchestrator} 与测试负责。
 *
 * <p>{@code refs} 故意保留 {@link JsonNode} 原样:Python 已经逐个校验过 SKU 的真实性,
 * Java 再解析一遍就是两处实现同一件事,还会因为 DTO 字段对不上而悄悄丢字段。
 */
public sealed interface ChatEvent {

    /**
     * sessionId 帧。
     *
     * <p><b>Python 侧正常不发这一帧</b>(会话权威在 Java,首帧由 Java 下发);
     * 若真收到了,编排器会<b>吞掉</b>它 —— 保证 C 端"恰好一帧 sessionId"。
     */
    record SessionFrame(String sessionId) implements ChatEvent {
    }

    /** 一段增量文本。 */
    record Delta(String delta) implements ChatEvent {
    }

    /** 可采纳的推荐项(SkuRef 数组)。 */
    record Refs(JsonNode refs) implements ChatEvent {
    }

    /** 失败帧。<b>与 {@link Done} 互斥</b>,且必为最后一帧。 */
    record Error(int code, String message, String traceId) implements ChatEvent {
    }

    /** 正常结束(`data: [DONE]`)。 */
    record Done() implements ChatEvent {
    }
}
