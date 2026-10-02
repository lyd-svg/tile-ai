package com.tileai.agent;

import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tileai.agent.dto.ChatRequest;
import com.tileai.common.agent.AgentChatStreamClient;
import com.tileai.common.agent.ChatEvent;
import com.tileai.common.error.ErrorCode;

/**
 * 把一次对话从"收到请求"编排到"推完最后一帧"。
 *
 * <p><b>线程模型</b>(这是本类最要紧的一件事):
 * <ul>
 *   <li>{@code start} 跑在 <b>Servlet 请求线程</b>上 —— 只做"建会话 / 落 user 消息 /
 *       建 emitter / 提交任务",立刻返回。它会先把 {@code user} 消息落库,
 *       所以即便随后流失败,会话列表也有条目。</li>
 *   <li>{@code pump} 跑在 {@code agentChatExecutor} 线程上 —— 打开到 Python 的流、
 *       逐帧转发、累积文本、结束时落 assistant 消息。</li>
 * </ul>
 *
 * <p><b>{@code SecurityContextHolder} 与 {@code TraceContext} 都是 ThreadLocal,
 * 不会跨线程传播</b> —— 所以 userId 与 traceId 都在 {@code start} 里取好、当作参数
 * 传进 {@code pump};{@code pump} 内不得再去调 {@code CurrentPrincipal}。
 *
 * <p><b>失败一律变成 error 帧</b>:流一旦开始,HTTP 状态码就改不了(契约原文),
 * 抛异常只会把连接硬断,前端就分不清"模型报错"与"网络断了"。唯一走普通 JSON 的,
 * 是<b>开流之前</b>的失败(未登录 / 会话越权 / 参数为空)。
 */
@Component
public class AgentChatOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(AgentChatOrchestrator.class);

    /** 契约里的结束标记,字面量,不是 JSON。 */
    private static final String DONE_MARKER = "[DONE]";

    private final AgentChatStreamClient streamClient;
    private final ChatSessionService sessionService;
    private final com.tileai.common.agent.AgentProperties agentProperties;
    private final ObjectMapper objectMapper;
    private final ThreadPoolTaskExecutor executor;

    public AgentChatOrchestrator(AgentChatStreamClient streamClient,
                                 ChatSessionService sessionService,
                                 com.tileai.common.agent.AgentProperties agentProperties,
                                 ObjectMapper objectMapper,
                                 @Qualifier(AgentChatConfig.EXECUTOR_BEAN_NAME) ThreadPoolTaskExecutor executor) {
        this.streamClient = streamClient;
        this.sessionService = sessionService;
        this.agentProperties = agentProperties;
        this.objectMapper = objectMapper;
        this.executor = executor;
    }

    /**
     * 受理一次对话,返回 SSE 发射器。
     *
     * <p>本方法返回之前,任何失败都抛 {@code BusinessException} → 由全局异常处理器
     * 给出普通的 JSON 信封(未登录 401 / 会话 30001 / 参数 10001)。
     *
     * @param userId  当前登录业主(调用方已从 SecurityContext 取好)
     * @param traceId 当前链路追踪 id(同样已取好 —— ThreadLocal 不跨线程)
     */
    public SseEmitter start(long userId, ChatRequest request, String traceId) {
        long sessionId = resolveSession(userId, request);
        sessionService.appendUserMessage(sessionId, request.message());

        SseEmitter emitter = new SseEmitter(agentProperties.getChatTimeout().toMillis());
        AtomicBoolean finished = new AtomicBoolean(false);

        emitter.onCompletion(() -> finished.set(true));
        emitter.onError(error -> finished.set(true));
        emitter.onTimeout(() -> {
            // 超时是"服务端等太久",不是客户端断开 —— 尽量给一帧 error 再收尾,
            // 让前端知道该重试而不是以为网络断了。
            if (finished.compareAndSet(false, true)) {
                trySendError(emitter, ErrorCode.INTERNAL_ERROR.code(),
                        ErrorCode.INTERNAL_ERROR.defaultMessage(), traceId);
                emitter.complete();
            }
        });

        try {
            executor.execute(() -> pump(userId, sessionId, request.message(), traceId, emitter, finished));
        } catch (TaskRejectedException rejected) {
            // 线程池已满:当场拒绝,给一帧 error 而不是让请求悬着
            log.warn("对话线程池已满,拒绝请求 userId={}", userId);
            trySendError(emitter, ErrorCode.INTERNAL_ERROR.code(),
                    ErrorCode.INTERNAL_ERROR.defaultMessage(), traceId);
            emitter.complete();
            finished.set(true);
        }
        return emitter;
    }

    /** 有 sessionId 就取本人那条(否则 30001),没有就新建。 */
    private long resolveSession(long userId, ChatRequest request) {
        if (StringUtils.hasText(request.sessionId())) {
            return sessionService.requireOwned(userId, request.sessionId()).getId();
        }
        return sessionService.createSession(userId, request.message());
    }

    /**
     * 真正干活的那条线程。
     *
     * <p>会话 id 与 userId 都是参数(不是从 ThreadLocal 拿的)—— 见类注释。
     */
    private void pump(long userId, long sessionId, String message, String traceId,
                      SseEmitter emitter, AtomicBoolean finished) {
        String sessionKey = String.valueOf(sessionId);
        StringBuilder answer = new StringBuilder();
        JsonNode refs = null;
        boolean done = false;
        boolean errored = false;

        try {
            // 首帧:sessionId 由 Java 下发(会话权威在 Java;Python 不发这一帧)
            emit(emitter, Map.of("sessionId", sessionKey));

            try (AgentChatStreamClient.AgentChatStream stream =
                         streamClient.open(sessionKey, userId, message, traceId)) {
                Iterator<ChatEvent> events = stream.events();
                while (events.hasNext()) {
                    if (finished.get()) {
                        return;
                    }
                    ChatEvent event = events.next();
                    if (event instanceof ChatEvent.SessionFrame) {
                        // 吞掉:保证 C 端恰好在首帧收到一次 sessionId(见 ChatEvent 注释)
                        continue;
                    } else if (event instanceof ChatEvent.Delta delta) {
                        answer.append(delta.delta());
                        emit(emitter, Map.of("delta", delta.delta()));
                    } else if (event instanceof ChatEvent.Refs refsEvent) {
                        refs = refsEvent.refs();
                        emit(emitter, Map.of("refs", refs));
                    } else if (event instanceof ChatEvent.Error errorEvent) {
                        errored = true;
                        trySendError(emitter, errorEvent.code(), errorEvent.message(), traceId);
                        break;
                    } else if (event instanceof ChatEvent.Done) {
                        done = true;
                        break;
                    }
                }
            }

            if (done) {
                // 只在正常结束落 assistant —— 半条答案不该沉淀成"历史事实"
                sessionService.appendAssistantMessage(sessionId, answer.toString(), writeRefs(refs));
                emitDone(emitter);
            } else if (!errored) {
                // 流提前结束:既没有 [DONE] 也没有 error,按失败处理
                log.warn("对话流意外结束(无 [DONE] 且无 error) session={}", sessionKey);
                trySendError(emitter, ErrorCode.INTERNAL_ERROR.code(),
                        ErrorCode.INTERNAL_ERROR.defaultMessage(), traceId);
            }
        } catch (Exception error) {
            log.error("对话处理失败 session={} traceId={}", sessionKey, traceId, error);
            if (!errored) {
                trySendError(emitter, ErrorCode.INTERNAL_ERROR.code(),
                        ErrorCode.INTERNAL_ERROR.defaultMessage(), traceId);
            }
        } finally {
            finished.set(true);
            emitter.complete();
        }
    }

    // ------------------------------------------------------------------
    // 帧写出
    // ------------------------------------------------------------------

    /** 发一帧 JSON。{@code SseEmitter.event().data(Object)} 走的是 {@code toString()},
     *  所以必须自己先序列化成 JSON 字符串。 */
    private void emit(SseEmitter emitter, Object payload) throws IOException {
        emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(payload)));
    }

    private void emitDone(SseEmitter emitter) throws IOException {
        emitter.send(SseEmitter.event().data(DONE_MARKER));
    }

    /** 失败帧。码要按 §10.4 约定 1 处理:未定义码折 90001,且 90001 不带内部细节。 */
    private void trySendError(SseEmitter emitter, int code, String message, String traceId) {
        ErrorCode mapped = ErrorCode.of(code).orElse(ErrorCode.INTERNAL_ERROR);
        String text = mapped == ErrorCode.INTERNAL_ERROR
                ? ErrorCode.INTERNAL_ERROR.defaultMessage()
                : (StringUtils.hasText(message) ? message : mapped.defaultMessage());

        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", mapped.code());
        error.put("message", text);
        error.put("traceId", traceId);
        try {
            emit(emitter, Map.of("error", error));
        } catch (Exception sendFailure) {
            // 客户端多半已经断开 —— 记一条日志即可,不要让它冒泡到线程池
            log.debug("发送 error 帧失败(客户端可能已断开):{}", sendFailure.getMessage());
        }
    }

    private String writeRefs(JsonNode refs) {
        if (refs == null || refs.isNull()) {
            return null;
        }
        return refs.toString();
    }
}
