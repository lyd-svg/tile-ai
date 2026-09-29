package com.tileai.common.probe;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.tileai.common.api.ApiResponse;

/**
 * T-0.4 验证用的受保护端点 —— <b>仅存在于测试源码中</b>。
 *
 * <p>路径刻意与真实契约路径同构(如 {@code /api/v1/plans/...}、
 * {@code /api/v1/admin/...}),这样测的是 {@code SecurityConfig} 里那份
 * <b>真实路径规则</b>,而不是另造一套只对测试生效的规则。
 * 后者会让"测试全绿但线上路径没被保护"这种事悄悄发生。
 */
@RestController
public class SecurityProbeController {

    /** 受保护的 C 端路径 —— 需要 ROLE_USER。 */
    @GetMapping("/api/v1/plans/probe")
    public ApiResponse<Map<String, Object>> protectedC() {
        return ApiResponse.ok(Map.of("scope", "protected-c"));
    }

    /** 后台路径 —— 需要 ROLE_ADMIN。 */
    @GetMapping("/api/v1/admin/probe")
    public ApiResponse<Map<String, Object>> admin() {
        return ApiResponse.ok(Map.of("scope", "admin"));
    }

    /** 公开的 C 端路径 —— 浏览免登录(PRD §5.1)。 */
    @GetMapping("/api/v1/products/probe")
    public ApiResponse<Map<String, Object>> publicC() {
        return ApiResponse.ok(Map.of("scope", "public-c"));
    }
}
