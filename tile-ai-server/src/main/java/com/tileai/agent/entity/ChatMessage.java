package com.tileai.agent.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 对话消息(架构 §8.6 {@code t_chat_message};契约 {@code ChatMessage})。
 *
 * <p>长期消息由 <b>Java</b> 落库(架构 §4.2):Python 侧只存 Redis 短期上下文,
 * 有 TTL 会过期,这里才是"用户翻历史时看得到"的那份。
 *
 * <p><b>{@code refs} 以字符串读写</b>(DB 列是 JSON 类型):用
 * {@code JacksonTypeHandler} 处理泛型 {@code List<SkuRef>} 会在反序列化时丢元素类型
 * (退化成 {@code List<LinkedHashMap>}),所以改成在 Service 层用 {@code ObjectMapper}
 * 显式转换 —— 依赖更少,行为更可预期。MySQL 会把合法的 JSON 字符串写入 JSON 列。
 */
@TableName("t_chat_message")
public class ChatMessage {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long sessionId;

    /** user / assistant(契约 enum)。 */
    private String role;

    private String content;

    /** refs 的 JSON 文本(如 {@code [{"sku":"TL-3012","name":"雪瓷白","part":"wall"}]});可为 null。 */
    private String refs;

    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getRefs() {
        return refs;
    }

    public void setRefs(String refs) {
        this.refs = refs;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
