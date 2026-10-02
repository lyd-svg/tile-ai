package com.tileai.agent.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 对话会话(架构 §8.6 {@code t_chat_session};契约 {@code ChatSession})。
 *
 * <p>表在 {@code V6__generation_chat_outbox.sql} 里就已建好(T-1.6 只用不建)——
 * 所以本任务**不需要新的迁移**。
 *
 * <p><b>id 是自增 BIGINT,但契约里 {@code ChatSession.id} 是 string。</b>
 * 对外用 {@code String.valueOf(id)} 暴露(与 {@code /plans/{id}} 暴露数字 id 的既有做法
 * 一致);这个字符串同时也是传给 Python 的 sessionId。
 *
 * <p>会话归属靠 {@code user_id} 判定 —— 续聊、详情、列表都只认本人。
 */
@TableName("t_chat_session")
public class ChatSession {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 由首条用户消息截断而来(见 {@code ChatSessionService.titleOf})。 */
    private String title;

    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
