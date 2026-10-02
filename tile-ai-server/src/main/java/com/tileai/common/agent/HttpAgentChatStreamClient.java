package com.tileai.common.agent;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tileai.common.error.BusinessException;
import com.tileai.common.error.ErrorCode;
import com.tileai.common.trace.TraceContext;

/**
 * {@link AgentChatStreamClient} 的 HTTP 实现。
 *
 * <p>用 <b>JDK 自带的 {@code java.net.http.HttpClient}</b>({@code BodyHandlers.ofLines()})
 * 而不是引 WebFlux:本项目是 Spring MVC(servlet)栈,为了一个流式客户端把整个
 * WebFlux 拖进来,只会让"到底哪套在跑"变得含糊。{@code ofLines()} 按行产出,
 * 天然就是 SSE 的消费形态,且零新依赖。
 *
 * <p>服务令牌 {@code X-Service-Token}、traceId {@code X-Trace-Id} 的约定与
 * {@link AgentClient} 一致 —— 令牌留空时不发该头。
 */
@Component
public class HttpAgentChatStreamClient implements AgentChatStreamClient {

    private static final Logger log = LoggerFactory.getLogger(HttpAgentChatStreamClient.class);

    private static final String CHAT_PATH = "/internal/agent/chat";
    private static final long CONNECT_TIMEOUT_SECONDS = 5;

    private final AgentProperties props;
    private final ObjectMapper objectMapper;
    private final SseEventParser parser;
    private final HttpClient http;

    public HttpAgentChatStreamClient(AgentProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.parser = new SseEventParser(objectMapper);
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
                // ⚠️ **必须显式钉成 HTTP/1.1**。HttpClient 默认 HTTP/2,而明文 http:// 下
                // 它会先发一个 `Upgrade: h2c` 的升级请求;uvicorn(FastAPI 默认的服务器)
                // 不支持 h2c,直接回 **400 Invalid HTTP request** —— 表现为"对话永远失败",
                // 但本地 MockMvc 测试照过(那里根本没有真 HTTP)。2026-10-02 联调实测踩到。
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    @Override
    public AgentChatStream open(String sessionId, long userId, String message, String traceId) {
        String body;
        try {
            body = objectMapper.writeValueAsString(Map.of(
                    "sessionId", sessionId,
                    "userId", userId,
                    "message", message));
        } catch (Exception error) {
            log.error("序列化对话请求失败", error);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(props.getBaseUrl() + CHAT_PATH))
                .timeout(props.getChatTimeout())
                .header("Content-Type", "application/json; charset=utf-8")
                .header(TraceContext.HEADER, traceId)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        if (StringUtils.hasText(props.getServiceToken())) {
            builder.header("X-Service-Token", props.getServiceToken());
        }

        HttpResponse<Stream<String>> response;
        try {
            response = http.send(builder.build(), HttpResponse.BodyHandlers.ofLines());
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            log.error("调用智能体对话接口被中断 traceId={}", traceId, error);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        } catch (IOException error) {
            log.error("调用智能体对话接口失败 traceId={}", traceId, error);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }

        if (response.statusCode() != 200) {
            response.body().close();
            log.error("智能体对话接口返回非 200 status={} traceId={}", response.statusCode(), traceId);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
        return new HttpStream(response.body());
    }

    /** 把一行行文本懒解析成 {@link ChatEvent}。 */
    private final class HttpStream implements AgentChatStream {

        private final Stream<String> lines;

        private HttpStream(Stream<String> lines) {
            this.lines = lines;
        }

        @Override
        public Iterator<ChatEvent> events() {
            Iterator<String> source = lines.iterator();
            return new Iterator<>() {

                private ChatEvent pending;

                @Override
                public boolean hasNext() {
                    while (pending == null && source.hasNext()) {
                        Optional<ChatEvent> parsed = parser.parseLine(source.next());
                        if (parsed.isPresent()) {
                            pending = parsed.get();
                        }
                    }
                    return pending != null;
                }

                @Override
                public ChatEvent next() {
                    if (!hasNext()) {
                        throw new NoSuchElementException("SSE 流已结束");
                    }
                    ChatEvent event = pending;
                    pending = null;
                    return event;
                }
            };
        }

        @Override
        public void close() {
            lines.close();
        }
    }
}
