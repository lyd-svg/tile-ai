package com.tileai.internal;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tileai.common.api.ApiResponse;
import com.tileai.generate.GenerateCallbackService;
import com.tileai.generate.dto.GenerateCallbackRequest;

import jakarta.validation.Valid;

/**
 * 生成结果回传(契约 {@code openapi-internal.yaml} 的
 * {@code POST /internal/generate/callback})。
 *
 * <p>Python → Java 方向,只在内网可达(网关阻断 {@code /internal/*}),
 * 并叠加服务令牌校验({@code ServiceTokenInterceptor})。
 *
 * <p><b>幂等</b>:同一个 {@code taskId} 会被回调很多次(开工、每张图的进度、终局),
 * 重复的终局回调返回成功但什么都不做。实现全部在
 * {@link GenerateCallbackService} —— 控制器这层只是解码 + 转交,
 * 不在这里判断状态、也不改写它给的错误码(那码是 Python 的判据,Java 只负责忠实记录)。
 */
@RestController
@RequestMapping("/internal/generate")
public class InternalGenerateController {

    private final GenerateCallbackService callbackService;

    public InternalGenerateController(GenerateCallbackService callbackService) {
        this.callbackService = callbackService;
    }

    /**
     * 回传结果。
     *
     * <p>{@code data} 恒为 null —— 契约的响应只声明了"受理结果",回传方(出图的
     * Python)不需要从响应里读任何东西。给一个空对象反而会让人以为里面有内容契约没写。
     *
     * <p><b>失败也要让 Python 能区分</b>:任务不存在是 40003、参数不对是 10001。
     * Python 侧对 40003 的处理应当是"别重试了"(任务都不在,重试一万次也不在),
     * 对 90001 才是"重试"。响应体里的 {@code code} 足够它分辨。
     */
    @PostMapping("/callback")
    public ApiResponse<Void> callback(@Valid @RequestBody GenerateCallbackRequest request) {
        callbackService.handle(request);
        return ApiResponse.ok();
    }
}
