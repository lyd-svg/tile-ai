package com.tileai.admin.auth;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tileai.admin.entity.AdminUser;
import com.tileai.common.api.ApiResponse;
import com.tileai.common.security.AuthSessionService;
import com.tileai.common.security.SubjectType;

import jakarta.validation.Valid;

/**
 * 后台登录(契约 {@code openapi-admin.yaml} {@code POST /api/v1/admin/auth/login},
 * {@code security: []})。
 *
 * <p>登录成功后按 §10.1 的 D1 方案下发 <b>HttpOnly Cookie</b>,
 * <b>令牌不进响应体</b> —— 响应体里只有 {@code AdminUserView}(不含密码、不含令牌)。
 *
 * <p>本接口是后台唯一<b>不需要</b>权限点的写接口,因为它就是取得身份的入口。
 */
@RestController
@RequestMapping("/api/v1/admin/auth")
public class AdminAuthController {

    private final AdminAuthService adminAuthService;
    private final AuthSessionService authSessionService;

    public AdminAuthController(AdminAuthService adminAuthService,
                               AuthSessionService authSessionService) {
        this.adminAuthService = adminAuthService;
        this.authSessionService = authSessionService;
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AdminUserView>> login(
            @Valid @RequestBody AdminLoginRequest request) {

        AdminUser admin = adminAuthService.authenticate(request.username(), request.password());

        // 主体类型必须是 ADMIN:令牌里的 styp 决定它能走哪些接口,
        // 用 USER 签发的话,这个后台会话将无法访问任何后台接口,反之亦然。
        AuthSessionService.SessionCookies cookies =
                authSessionService.start(admin.getId(), SubjectType.ADMIN);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE,
                        cookies.access().toString(), cookies.refresh().toString())
                .body(ApiResponse.ok(AdminUserView.of(admin)));
    }
}
