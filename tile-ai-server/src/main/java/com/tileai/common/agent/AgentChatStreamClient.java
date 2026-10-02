package com.tileai.common.agent;

import java.util.Iterator;

/**
 * 流式调用 Python 的 {@code POST /internal/agent/chat}。
 *
 * <p>与 {@link AgentClient} 分开,是因为两者形态根本不同:{@code AgentClient} 是
 * 同步请求-响应(拿到整段再返回),而这里是<b>边收边给</b>。把流式硬塞进 RestClient
 * 只会得到一个"缓冲完再返回"的假流式。
 *
 * <p>做成接口是为了测试能打桩:集成测试注入一个可编程的替身,断言 Java 侧的
 * 转发、累积与落库 —— 不必真的起一个 Python。
 */
public interface AgentChatStreamClient {

    /**
     * 打开一条流。<b>本方法会发起网络连接</b>(惰性也可以在 {@code events()} 首次迭代时)。
     *
     * @throws com.tileai.common.error.BusinessException 连不上/非 200 —— 由编排器转成 error 帧
     */
    AgentChatStream open(String sessionId, long userId, String message, String traceId);

    /** 一条已打开的流。用完必须 {@link #close()} —— 否则连接泄漏。 */
    interface AgentChatStream extends AutoCloseable {

        /** 逐帧迭代。<b>不抛异常</b>的做法由实现决定(读失败在编排器里兜)。 */
        Iterator<ChatEvent> events();

        @Override
        void close();
    }
}
