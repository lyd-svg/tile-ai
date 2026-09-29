package com.tileai.admin.auth;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tileai.admin.AdminAccountService;
import com.tileai.admin.entity.AdminUser;
import com.tileai.common.api.ApiResponse;
import com.tileai.common.error.BusinessException;
import com.tileai.common.error.ErrorCode;
import com.tileai.common.security.AuthCookies;
import com.tileai.common.security.AuthPrincipal;
import com.tileai.common.security.AuthSessionService;
import com.tileai.common.security.CurrentPrincipal;
import com.tileai.common.security.SubjectType;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * 后台会话(契约 {@code openapi-admin.yaml} 的 admin-auth 分组,架构 §10.3)。
 *
 * <pre>
 *   POST /api/v1/admin/auth/login    后台登录(公开)
 *   POST /api/v1/admin/auth/logout   退出登录(需后台会话)
 *   GET  /api/v1/admin/auth/me       取当前登录账号(需后台会话)
 * </pre>
 *
 * <p>登录成功后按 §10.1 的 D1 方案下发 <b>HttpOnly Cookie</b>,
 * <b>令牌不进响应体</b> —— 响应体里只有 {@code AdminUserView}(不含密码、不含令牌)。
 *
 * <p><b>三个接口都不需要权限点</b> —— 它们是"我是谁"的接口,不是"我能做什么";
 * {@code super_admin} / {@code operator} / {@code editor} 一视同仁。
 * 登录是唯一不需要权限点的<b>写</b>接口,因为它就是取得身份的入口。
 */
@RestController
@RequestMapping("/api/v1/admin/auth")
public class AdminAuthController {

    private final AdminAuthService adminAuthService;
    private final AdminAccountService adminAccountService;
    private final AuthSessionService authSessionService;
    private final AuthCookies authCookies;

    public AdminAuthController(AdminAuthService adminAuthService,
                               AdminAccountService adminAccountService,
                               AuthSessionService authSessionService,
                               AuthCookies authCookies) {
        this.adminAuthService = adminAuthService;
        this.adminAccountService = adminAccountService;
        this.authSessionService = authSessionService;
        this.authCookies = authCookies;
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

    /**
     * 退出登录 —— 把 refresh 令牌拉黑并清 Cookie。
     *
     * <p>refresh 令牌缺失或无效时<b>不报错</b>,照样清 Cookie:登出必须"总能成功",
     * 否则用户会卡在登录态里。与 C 端 {@code /auth/logout} 同口径,
     * 裁决经过见 {@code openapi-verification.md} §20.3。
     *
     * <p>接口本身仍<b>需要登录态</b>(Spring Security 侧 {@code /api/v1/admin/**}
     * 除登录外一律要后台身份)—— 做成"无需登录"等于对外提供一个人人可调的清 Cookie 接口。
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest request) {
        AuthSessionService.SessionCookies cleared =
                authSessionService.logout(
                        authCookies.refreshTokenOf(request, SubjectType.ADMIN), SubjectType.ADMIN);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE,
                        cleared.access().toString(), cleared.refresh().toString())
                .body(ApiResponse.ok());
    }

    /**
     * 刷新访问令牌 —— 凭 {@code admin_refresh_token} Cookie 换新的 {@code admin_access_token}。
     *
     * <p><b>为什么后台必须有它</b>:两端会话分离后,后台有了自己的 refresh 令牌
     * (Path=`/api/v1/admin/auth`,覆盖本组端点)。没有刷新端点的话,那个令牌
     * 就只是个存下来却用不上的东西 —— 后台会话 2 小时后仍然只能重新登录。
     *
     * <p>与 C 端 `/auth/refresh` 同口径:只换 access,**不轮换 refresh**。
     */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<Void>> refresh(HttpServletRequest request) {
        String refreshToken = authCookies.refreshTokenOf(request, SubjectType.ADMIN);
        if (!StringUtils.hasText(refreshToken)) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE,
                        authSessionService.refreshAccess(refreshToken, SubjectType.ADMIN).toString())
                .body(ApiResponse.ok());
    }

    /**
     * 取当前登录账号 —— 供前端在页面刷新后恢复会话。
     *
     * <p><b>为什么必须有它</b>:令牌在 HttpOnly Cookie 里,前端读不到(§10.1 的 D1 方案),
     * 刷新后无法自行得知"当前是谁、什么角色"。没有本接口,前端只能把账号信息缓存在本地,
     * 那是把<b>展示状态</b>当成了凭证来源 —— 账号被停用或降权后,界面会继续按旧角色渲染。
     * 有它之后,每次加载都以服务端当下的事实为准。
     *
     * <p>账号已停用或不存在时抛 <b>10002</b> 而不是 30001:对调用方而言这是
     * "会话已失效,需要重新登录",与"资源不存在"是两回事 —— 前者前端要清态并跳登录页,
     * 后者只需提示。
     */
    @GetMapping("/me")
    public ApiResponse<AdminUserView> me() {
        AuthPrincipal principal = CurrentPrincipal.requireAdmin();
        AdminUser admin = adminAccountService.findEnabledById(principal.id())
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED));
        return ApiResponse.ok(AdminUserView.of(admin));
    }
}
