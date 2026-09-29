package com.tileai.common.probe;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tileai.common.api.ApiResponse;
import com.tileai.common.error.BusinessException;
import com.tileai.common.error.ErrorCode;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * T-0.3 验证用的探针端点 —— <b>仅存在于测试源码中</b>,不会进入生产 jar。
 *
 * <p>用途:在没有真实业务接口的情况下,把统一响应体与全局异常处理的每一条分支
 * 都跑一遍(成功 / 各类业务码 / 校验失败 / 未预期异常)。
 * 它会随 T-0.6 起的真实接口逐步被取代,但保留成本极低、覆盖价值明确。
 */
@RestController
@RequestMapping("/probe")
public class ProbeController {

    /** 用于触发 @Valid 校验失败的请求体。 */
    public record EchoRequest(@NotBlank String name, @Min(1) Integer qty) {
    }

    @GetMapping("/ok")
    public ApiResponse<Map<String, Object>> ok() {
        return ApiResponse.ok(Map.of("hello", "world"));
    }

    @GetMapping("/empty")
    public ApiResponse<Void> empty() {
        return ApiResponse.ok();
    }

    @GetMapping("/not-found")
    public ApiResponse<Void> notFound() {
        throw new BusinessException(ErrorCode.NOT_FOUND, "SKU 不存在: SKU-123");
    }

    @GetMapping("/unauth")
    public ApiResponse<Void> unauth() {
        throw new BusinessException(ErrorCode.UNAUTHENTICATED);
    }

    @GetMapping("/expired")
    public ApiResponse<Void> expired() {
        throw new BusinessException(ErrorCode.TOKEN_EXPIRED);
    }

    @GetMapping("/forbidden")
    public ApiResponse<Void> forbidden() {
        throw new BusinessException(ErrorCode.FORBIDDEN);
    }

    @GetMapping("/rate-limited")
    public ApiResponse<Void> rateLimited() {
        throw new BusinessException(ErrorCode.RATE_LIMITED);
    }

    @PostMapping("/validate")
    public ApiResponse<Void> validate(@Valid @RequestBody EchoRequest request) {
        return ApiResponse.ok();
    }

    @GetMapping("/boom")
    public ApiResponse<Void> boom() {
        // 故意在异常消息里放"敏感信息",验证 90001 不会把它透给前端。
        throw new IllegalStateException("内部细节:数据库口令 jdbc:mysql://root:pw@10.0.0.1/tile_ai");
    }
}
