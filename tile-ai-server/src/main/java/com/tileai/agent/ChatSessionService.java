package com.tileai.agent;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tileai.agent.dto.ChatMessageView;
import com.tileai.agent.dto.ChatSessionView;
import com.tileai.agent.dto.SessionDetailView;
import com.tileai.agent.dto.SkuRefView;
import com.tileai.agent.entity.ChatMessage;
import com.tileai.agent.entity.ChatSession;
import com.tileai.agent.mapper.ChatMessageMapper;
import com.tileai.agent.mapper.ChatSessionMapper;
import com.tileai.common.api.PageData;
import com.tileai.common.error.BusinessException;
import com.tileai.common.error.ErrorCode;

/**
 * 会话与消息的持久化(架构 §4.2 / §8.6)。
 *
 * <p><b>会话权威在 Java</b>:sessionId 由 {@code t_chat_session.id} 生成,
 * 也是传给 Python 的那个字符串。Python 侧只在 Redis 存有 TTL 的短期上下文,
 * 长期消息一律由这里落库。
 */
@Service
public class ChatSessionService {

    private static final Logger log = LoggerFactory.getLogger(ChatSessionService.class);

    /**
     * 30001 的唯一措辞,与 {@code PlanOwnership.NOT_FOUND_MESSAGE} 同一口径。
     *
     * <p><b>刻意不区分</b>"不存在 / 不属于本人 / 不是数字":分开就会变成一个
     * 可枚举他人会话 id 的探针。
     */
    public static final String NOT_FOUND_MESSAGE = "会话不存在";

    /** 标题长度上限,与 {@code t_chat_session.title VARCHAR(200)} 对齐(按码点截断)。 */
    private static final int TITLE_MAX_CODE_POINTS = 200;

    private final ChatSessionMapper sessionMapper;
    private final ChatMessageMapper messageMapper;
    private final ObjectMapper objectMapper;

    public ChatSessionService(ChatSessionMapper sessionMapper,
                              ChatMessageMapper messageMapper,
                              ObjectMapper objectMapper) {
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.objectMapper = objectMapper;
    }

    // ------------------------------------------------------------------
    // 会话
    // ------------------------------------------------------------------

    /** 新建会话,返回自增 id。标题取首条用户消息的截断。 */
    @Transactional
    public long createSession(long userId, String firstMessage) {
        ChatSession session = new ChatSession();
        session.setUserId(userId);
        session.setTitle(titleOf(firstMessage));
        sessionMapper.insert(session);
        return session.getId();
    }

    /**
     * 取本人在用的会话;解析失败 / 不存在 / 不属于本人一律 30001(同一措辞)。
     *
     * @throws BusinessException 30001
     */
    @Transactional(readOnly = true)
    public ChatSession requireOwned(long userId, String sessionId) {
        Long id = parseId(sessionId);
        if (id == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, NOT_FOUND_MESSAGE);
        }
        ChatSession session = sessionMapper.selectOne(Wrappers.<ChatSession>lambdaQuery()
                .eq(ChatSession::getId, id)
                .eq(ChatSession::getUserId, userId));
        if (session == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, NOT_FOUND_MESSAGE);
        }
        return session;
    }

    /** 我的会话列表,按创建时间倒序(表无 updated_at,故不按"最近活动"排;契约未要求)。 */
    @Transactional(readOnly = true)
    public PageData<ChatSessionView> page(long userId, int page, int size) {
        Page<ChatSession> result = sessionMapper.selectPage(new Page<>(page, size),
                Wrappers.<ChatSession>lambdaQuery()
                        .eq(ChatSession::getUserId, userId)
                        .orderByDesc(ChatSession::getCreatedAt)
                        .orderByDesc(ChatSession::getId));

        List<ChatSessionView> list = result.getRecords().stream()
                .map(session -> new ChatSessionView(
                        String.valueOf(session.getId()),
                        session.getTitle(),
                        session.getCreatedAt()))
                .toList();

        return PageData.of(list, result.getTotal(), page, size);
    }

    /** 会话详情:会话本体 + 全部消息(按 id 升序,即时间顺序)。 */
    @Transactional(readOnly = true)
    public SessionDetailView detail(long userId, String sessionId) {
        ChatSession session = requireOwned(userId, sessionId);
        List<ChatMessageView> messages = messageMapper.selectList(
                        Wrappers.<ChatMessage>lambdaQuery()
                                .eq(ChatMessage::getSessionId, session.getId())
                                .orderByAsc(ChatMessage::getId))
                .stream()
                .map(this::toView)
                .toList();
        return new SessionDetailView(
                new ChatSessionView(String.valueOf(session.getId()), session.getTitle(),
                        session.getCreatedAt()),
                messages);
    }

    // ------------------------------------------------------------------
    // 消息
    // ------------------------------------------------------------------

    /**
     * 追加一条用户消息。
     *
     * <p>在<b>开流之前</b>调用:这样会话列表立刻有条目,即便随后流失败,
     * "用户问过什么"也不会丢。
     */
    @Transactional
    public void appendUserMessage(long sessionId, String content) {
        append(sessionId, "user", content, null);
    }

    /**
     * 追加一条助手消息(仅正常结束 {@code [DONE]} 时调用)。
     *
     * <p><b>中途失败不落</b> —— 半条答案沉淀成"历史事实",下次模型会把它当成自己说过的话。
     */
    @Transactional
    public void appendAssistantMessage(long sessionId, String content, String refsJson) {
        append(sessionId, "assistant", content, refsJson);
    }

    private void append(long sessionId, String role, String content, String refsJson) {
        ChatMessage message = new ChatMessage();
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setContent(content);
        message.setRefs(refsJson);
        messageMapper.insert(message);
    }

    // ------------------------------------------------------------------
    // 转换
    // ------------------------------------------------------------------

    private ChatMessageView toView(ChatMessage message) {
        return new ChatMessageView(
                message.getId(),
                message.getRole(),
                message.getContent(),
                parseRefs(message.getRefs()),
                message.getCreatedAt());
    }

    /** 解析 refs JSON。坏数据不该让整条详情接口失败 —— 记一条告警,返回空表。 */
    private List<SkuRefView> parseRefs(String refsJson) {
        if (refsJson == null || refsJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(refsJson, new TypeReference<List<SkuRefView>>() { });
        } catch (Exception error) {
            log.warn("会话消息的 refs 无法解析,按空表处理:{}", error.getMessage());
            return List.of();
        }
    }

    private static Long parseId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(sessionId.trim());
        } catch (NumberFormatException error) {
            return null;
        }
    }

    /** 标题 = 首条消息的截断(**按码点**,与 {@code PlanNames} 的上限口径一致)。 */
    private static String titleOf(String message) {
        String text = message == null ? "" : message.strip();
        if (text.isEmpty()) {
            return "新对话";
        }
        int count = text.codePointCount(0, text.length());
        if (count <= TITLE_MAX_CODE_POINTS) {
            return text;
        }
        int end = text.offsetByCodePoints(0, TITLE_MAX_CODE_POINTS);
        return text.substring(0, end);
    }
}
