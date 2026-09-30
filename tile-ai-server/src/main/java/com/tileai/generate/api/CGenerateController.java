package com.tileai.generate.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tileai.common.api.ApiResponse;
import com.tileai.common.error.ErrorCode;
import com.tileai.common.security.AuthPrincipal;
import com.tileai.common.security.CurrentPrincipal;
import com.tileai.generate.GenerateService;
import com.tileai.generate.dto.GenerateAccepted;
import com.tileai.generate.dto.GenerateRequest;
import com.tileai.generate.dto.TaskProgressView;

import jakarta.validation.Valid;

/**
 * C 端生成任务(契约 {@code openapi-c.yaml} 的 generate 分组)。
 *
 * <pre>
 *   POST /api/v1/generate            受理(异步),返回 taskId
 *   GET  /api/v1/generate/{taskId}   轮询进度 / 结果
 * </pre>
 *
 * <p>两个接口都需登录,身份取自 Cookie(项目约定:不用 {@code Authorization} 头)。
 * 路由保护在 {@code SecurityConfig.PROTECTED_C_PATHS} 里已经配了
 * {@code /api/v1/generate/**},本类不需要再加注解。
 *
 * <p><b>异步不是可选的设计</b>:一次生成最长 4 张 × ≤60s,同步接口会先被网关超时掐掉。
 * 所以受理只负责"记下来 + 排上队",立刻返回;结果靠轮询拿(一期 2s 一次)。
 */
@RestController
@RequestMapping("/api/v1/generate")
public class CGenerateController {

    private final GenerateService generateService;

    public CGenerateController(GenerateService generateService) {
        this.generateService = generateService;
    }

    /**
     * 受理生成。
     *
     * <p>参数校验(至少 1 个部位、面积非负、角度 1–4、户型必填)由 {@code @Valid}
     * 触发,失败即 **10001 + {@code data.errors[]}**,形状与其他 C 端接口一致。
     *
     * <p>返回的 {@code status} 是 {@code pending} 而不是 {@code running}:
     * 此刻消息只是<b>登记进了发件箱</b>,还没被投递器发出去,更没有 Python 接手。
     * {@code running} 由 Python 真正开工时的回调翻(见
     * {@code openapi-verification.md} §35.2)—— 否则用户会看到一个长期不动的进度条。
     */
    @PostMapping
    public ApiResponse<GenerateAccepted> generate(@Valid @RequestBody GenerateRequest request) {
        AuthPrincipal principal = CurrentPrincipal.requireUser();
        return ApiResponse.ok(generateService.accept(principal.id(), request));
    }

    /**
     * 查进度 / 结果。
     *
     * <p><b>失败时返回 40001,并且 {@code data} 里带着 TaskProgress。</b>
     * 只给一个错误码是不够的:前端拿不到 {@code taskId} 就分不清"是我这次的任务失败了"
     * 还是"我查错了任务",而且进度条会停在一个说不清的位置。契约把 40001 列为本接口的
     * 失败码,但 {@code TaskProgress} 里没有 error 字段 —— 失败原因走
     * {@code ApiResponse.message},记在 {@code openapi-verification.md} §35.3。
     *
     * <p>业务码仍是 HTTP 200(项目约定:仅 401/403 用真实状态码)。
     */
    @GetMapping("/{taskId}")
    public ApiResponse<TaskProgressView> progress(@PathVariable String taskId) {
        AuthPrincipal principal = CurrentPrincipal.requireUser();
        GenerateService.ProgressResult result = generateService.progress(principal.id(), taskId);

        if (result.failureMessage() != null) {
            return ApiResponse.fail(ErrorCode.GENERATE_FAILED, result.failureMessage(), result.view());
        }
        return ApiResponse.ok(result.view());
    }
}
