package com.tileai.agent.dto;

import java.time.LocalDateTime;

/**
 * 会话视图(契约 {@code ChatSession})。
 *
 * <p>{@code id} 是 {@code String} —— 契约如此声明;取值是 {@code t_chat_session.id}
 * 的十进制字符串,同时也是传给 Python 的 sessionId。
 */
public record ChatSessionView(String id, String title, LocalDateTime createdAt) {
}
