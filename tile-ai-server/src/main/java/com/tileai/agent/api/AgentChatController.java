package com.tileai.agent.api;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.tileai.agent.AgentChatOrchestrator;
import com.tileai.agent.ChatSessionService;
import com.tileai.agent.dto.ChatRequest;
import com.tileai.agent.dto.ChatSessionView;
import com.tileai.agent.dto.SessionDetailView;
import com.tileai.common.api.ApiResponse;
import com.tileai.common.api.PageData;
import com.tileai.common.security.AuthPrincipal;
import com.tileai.common.security.CurrentPrincipal;
import com.tileai.common.trace.TraceContext;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * C 端「AI 咨询」(契约 {@code openapi-c.yaml} 的 agent 分组;PRD §5.2.3、架构 §11.3)。
 *
 * <pre>
 *   POST /api/v1/agent/chat              SSE 流式问答
 *   GET  /api/v1/agent/chat/sessions     对话记录列表
 *   GET  /api/v1/agent/chat/sessions/{id} 对话记录详情
 * </pre>
 *
 * <p>三条都要登录,身份取自 Cookie(项目约定)。路由保护在 {@code SecurityConfig} 里
 * 已配 {@code /api/v1/agent/**},本类<b>不加安全注解</b> —— 与 {@code CPlanController} 同口径,
 * 避免两处判定。
 *
 * <p><b>两条失败路径的取舍</b>(契约原文:流一旦开始,HTTP 状态码就改不了):
 * <ul>
 *   <li><b>开流之前</b>的失败 —— 未登录 401、会话越权 30001、{@code message} 为空 10001
 *       —— 走普通的 {@code ApiResponse} JSON 信封(由全局异常处理器出);</li>
 *   <li><b>开流之后</b>的失败 —— 只能是一帧 {@code {"error":{...}}},由编排器负责,
 *       且与 {@code [DONE]} 互斥。</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/agent")
@Validated
public class AgentChatController {

    private static final String DEFAULT_PAGE = "1";
    private static final String DEFAULT_SIZE = "20";

    private final AgentChatOrchestrator orchestrator;
    private final ChatSessionService sessionService;

    public AgentChatController(AgentChatOrchestrator orchestrator,
                               ChatSessionService sessionService) {
        this.orchestrator = orchestrator;
        this.sessionService = sessionService;
    }

    /**
     * 流式问答。
     *
     * <p>这里**必须先取好** {@code userId} 与 {@code traceId} 再交出去 —— 它们是
     * ThreadLocal,异步线程里取不到(见 {@code AgentChatOrchestrator} 的类注释)。
     */
    @PostMapping("/chat")
    public SseEmitter chat(@Valid @RequestBody ChatRequest request) {
        AuthPrincipal principal = CurrentPrincipal.requireUser();
        String traceId = TraceContext.getOrCreate();
        return orchestrator.start(principal.id(), request, traceId);
    }

    /** 对话记录列表,分页口径与 {@code /plans} 完全一致。 */
    @GetMapping("/chat/sessions")
    public ApiResponse<PageData<ChatSessionView>> sessions(
            @RequestParam(defaultValue = DEFAULT_PAGE) @Min(value = 1, message = "页码从 1 开始") int page,
            @RequestParam(defaultValue = DEFAULT_SIZE) @Min(value = 1, message = "每页条数至少为 1")
            @Max(value = 100, message = "每页条数最多 100") int size) {
        AuthPrincipal principal = CurrentPrincipal.requireUser();
        return ApiResponse.ok(sessionService.page(principal.id(), page, size));
    }

    /**
     * 对话记录详情。
     *
     * <p>{@code id} 是<b>字符串</b>(契约如此声明);不存在 / 不是本人的一律 30001,
     * 措辞与"不存在"一致 —— 否则它会变成一个可枚举他人会话的探针。
     */
    @GetMapping("/chat/sessions/{id}")
    public ApiResponse<SessionDetailView> session(@PathVariable String id) {
        AuthPrincipal principal = CurrentPrincipal.requireUser();
        return ApiResponse.ok(sessionService.detail(principal.id(), id));
    }
}
