package com.tileai.agent.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * C 端发起的对话请求(契约 {@code openapi-c.yaml#/paths/~1agent~1chat} 的请求体)。
 *
 * <p>{@code sessionId} 首次可省略(服务端建会话并在流的首帧下发);{@code message} 必填。
 *
 * <p>注意:<b>流一旦开始就不能再改 HTTP 状态码</b>。所以这里的 {@code @NotBlank}
 * 只在"开流之前"有意义 —— 校验失败会走普通的 10001 JSON 信封(不开流),
 * 而不是 SSE 的 error 帧。两条失败路径的取舍见 {@code AgentChatController}。
 */
public record ChatRequest(String sessionId,

                          @NotBlank(message = "消息不能为空")
                          String message) {
}
