package com.tileai.agent.dto;

import java.util.List;

/** 会话详情(契约 {@code GET /agent/chat/sessions/{id}} 的 {@code data}:{@code {session, messages}})。 */
public record SessionDetailView(ChatSessionView session, List<ChatMessageView> messages) {
}
