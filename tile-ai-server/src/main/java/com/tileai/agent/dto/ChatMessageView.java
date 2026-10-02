package com.tileai.agent.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 消息视图(契约 {@code ChatMessage})。
 *
 * <p>注意类型差异:{@code id} 是 {@code Long}(契约 {@code type: integer}),
 * 而 {@code ChatSession.id} 是 {@code String} —— 两个 schema 本来就不一样,
 * 不要"顺手"统一。
 */
public record ChatMessageView(Long id,
                              String role,
                              String content,
                              List<SkuRefView> refs,
                              LocalDateTime createdAt) {
}
