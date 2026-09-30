package com.tileai.common.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tileai.common.api.ErrorItem;
import com.tileai.common.api.ValidationErrors;
import com.tileai.common.error.BusinessException;
import com.tileai.common.error.ErrorCode;
import com.tileai.common.trace.TraceContext;

/**
 * 调用 Python 智能体服务(架构 §6.1;契约 {@code openapi-internal.yaml})。
 *
 * <p>Java 侧**唯一**的出网口,几条约定集中在此,不要在别处直接发 HTTP:
 *
 * <ul>
 *   <li><b>服务令牌</b>走 {@code X-Service-Token}(§13.6,仅环境变量);</li>
 *   <li><b>traceId 透传</b>走 {@code X-Trace-Id} —— 跨进程串联同一条链路(§13.7)。
 *       没有它,"用户报错"与"Python 日志"就对不上;</li>
 *   <li><b>只透传已定义的业务码</b>(§10.4 约定 1)—— 见 {@link #translate}。</li>
 * </ul>
 *
 * <p>用 Spring 自带的 {@link RestClient}(Boot 3.2+)而不是引第三方客户端 ——
 * 一次内部调用不值得为它加一个依赖。
 */
@Component
public class AgentClient {

    private static final Logger log = LoggerFactory.getLogger(AgentClient.class);

    private final AgentProperties props;
    private final ObjectMapper objectMapper;
    private final RestClient client;

    public AgentClient(AgentProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;

        long timeoutMillis = props.getTimeout().toMillis();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        // 超时必须设:没有超时的内部调用会把上游请求线程一起拖住
        factory.setConnectTimeout((int) timeoutMillis);
        factory.setReadTimeout((int) timeoutMillis);

        this.client = RestClient.builder()
                .baseUrl(props.getBaseUrl())
                .requestFactory(factory)
                .build();
    }

    /**
     * POST 一个 JSON 并解包 `ApiResponse.data`。
     *
     * <p>Python 的业务失败(HTTP 200 + code≠0)与网络层故障(连不上、超时)
     * 最终都抛 {@link BusinessException} —— 对调用方而言都是"这次调用没成",
     * 不值得分两种异常;差别体现在错误码上(业务失败带原码,故障折 90001)。
     */
    public JsonNode post(String path, Object body) {
        String traceId = TraceContext.getOrCreate();

        String raw;
        try {
            raw = client.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(TraceContext.HEADER, traceId)
                    .headers(headers -> {
                        if (StringUtils.hasText(props.getServiceToken())) {
                            headers.set("X-Service-Token", props.getServiceToken());
                        }
                    })
                    .body(body)
                    .retrieve()
                    .body(String.class);
        } catch (Exception error) {
            // 连不上 / 超时 / 5xx —— 属服务故障。对外只给 90001 的固定文案,
            // 真实原因连 traceId 进日志(§10.5:不向用户暴露内部细节)。
            log.error("调用智能体服务失败 path={} traceId={}", path, traceId, error);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }

        return translate(path, traceId, raw);
    }

    /**
     * 把 Python 的响应体翻译成本服务的语义。
     *
     * <p><b>只透传已定义的业务码</b>(架构 §10.4 约定 1):Python 返回 10001,
     * Java 就抛 10001 并带上它的 `data.errors[]` —— 用户能据此知道缺什么。
     * 但返回一个**本服务不认识**的码时折算成 90001,而不是原样透出去,
     * 否则内部实现细节(甚至第三方 SDK 的错误码)会漏到用户界面。
     */
    private JsonNode translate(String path, String traceId, String raw) {
        JsonNode envelope;
        try {
            envelope = objectMapper.readTree(raw);
        } catch (Exception error) {
            log.error("智能体服务返回的不是 JSON path={} traceId={} body={}", path, traceId, raw, error);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }

        int code = envelope.path("code").asInt(ErrorCode.INTERNAL_ERROR.code());
        if (code == ErrorCode.SUCCESS.code()) {
            return envelope.path("data");
        }

        Optional<ErrorCode> known = ErrorCode.of(code);
        if (known.isEmpty()) {
            log.error("智能体服务返回了未定义的业务码 code={} path={} traceId={}", code, path, traceId);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }

        log.info("智能体服务返回业务失败 code={} path={} traceId={}", code, path, traceId);
        throw asException(known.get(), envelope);
    }

    /** 把 Python 的 `data.errors[]` 收进异常,让逐项原因能一路到达用户。 */
    private static BusinessException asException(ErrorCode code, JsonNode envelope) {
        String message = envelope.path("message").asText(code.defaultMessage());
        JsonNode errors = envelope.path("data").path("errors");

        if (!errors.isArray() || errors.isEmpty()) {
            return new BusinessException(code, message);
        }

        List<ErrorItem> items = new ArrayList<>();
        for (JsonNode node : errors) {
            items.add(new ErrorItem(node.path("field").asText(""), node.path("reason").asText(""), null));
        }
        return new BusinessException(code, message, ValidationErrors.of(items));
    }
}
