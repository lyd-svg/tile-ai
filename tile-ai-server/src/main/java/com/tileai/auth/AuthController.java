package com.tileai.auth;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tileai.auth.dto.AuthRequests;
import com.tileai.auth.dto.UserView;
import com.tileai.auth.entity.User;
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
 * C 端注册 / 登录 / 账号(契约 {@code openapi-c.yaml} 的 auth 分组)。
 *
 * <pre>
 *   POST /api/v1/auth/register   注册(公开)
 *   POST /api/v1/auth/login      登录(公开)
 *   POST /api/v1/auth/password   修改密码(需登录)
 *   POST /api/v1/auth/logout     退出登录(需登录)
 *   POST /api/v1/auth/refresh    刷新访问令牌(公开,凭 refresh Cookie)
 * </pre>
 *
 * <p>登录态一律经 {@code Set-Cookie} 下发,**令牌不进响应体**
 * (CLAUDE.md 约定;前端零存储)。唯一的响应体内容是 {@link UserView}。
 *
 * <p>v0.7 起注册与登录**各是一次请求** —— 不再有「验证码登录 → 待设密码」两阶段,
 * 因此也没有临时令牌({@code tempToken})与 Bearer 通道。
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthSessionService authSessionService;
    private final AuthCookies authCookies;

    public AuthController(AuthService authService,
                          AuthSessionService authSessionService,
                          AuthCookies authCookies) {
        this.authService = authService;
        this.authSessionService = authSessionService;
        this.authCookies = authCookies;
    }

    /** 注册 —— 成功即登录,直接下发 Cookie。 */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserView>> register(
            @Valid @RequestBody AuthRequests.Register request) {
        User user = authService.register(request.username(), request.password());
        return withSession(user);
    }

    /** 登录。失败一律 20005,不区分原因(见 {@link AuthService} 的类注释)。 */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<UserView>> login(
            @Valid @RequestBody AuthRequests.Login request) {
        User user = authService.login(request.username(), request.password());
        return withSession(user);
    }

    /** 修改密码 —— 需要登录态,身份取自 Cookie。 */
    @PostMapping("/password")
    public ApiResponse<Void> changePassword(
            @Valid @RequestBody AuthRequests.ChangePassword request) {
        AuthPrincipal principal = CurrentPrincipal.requireUser();
        authService.changePassword(principal.id(), request.oldPassword(), request.newPassword());
        return ApiResponse.ok();
    }

    /**
     * 退出登录 —— 把 refresh 令牌拉黑并清 Cookie。
     *
     * <p>refresh 令牌缺失或无效时**不报错**,照样清 Cookie:登出必须"总能成功",
     * 否则用户会卡在登录态里。
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest request) {
        AuthSessionService.SessionCookies cleared =
                authSessionService.logout(authCookies.refreshTokenOf(request, SubjectType.USER),
                        SubjectType.USER);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE,
                        cleared.access().toString(), cleared.refresh().toString())
                .body(ApiResponse.ok());
    }

    /**
     * 刷新访问令牌 —— 凭 refresh_token Cookie 换一个新的 access_token。
     *
     * <p>契约把该接口描述为"刷新<b>访问</b>令牌",因此**不轮换** refresh_token 本身:
     * 它保持 14 天有效期,直到登出或过期。见 {@code AuthSessionService#refreshAccess}。
     */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<Void>> refresh(HttpServletRequest request) {
        String refreshToken = authCookies.refreshTokenOf(request, SubjectType.USER);
        if (!StringUtils.hasText(refreshToken)) {
            // 没有 refresh 令牌 = 从未登录或 Cookie 已被清 —— 属"未登录",不是"令牌过期"
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE,
                        authSessionService.refreshAccess(refreshToken, SubjectType.USER).toString())
                .body(ApiResponse.ok());
    }

    // ------------------------------------------------------------------

    /** 建立会话:下发 access + refresh 两个 Cookie,响应体只放用户信息。 */
    private ResponseEntity<ApiResponse<UserView>> withSession(User user) {
        AuthSessionService.SessionCookies cookies =
                authSessionService.start(user.getId(), SubjectType.USER);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE,
                        cookies.access().toString(), cookies.refresh().toString())
                .body(ApiResponse.ok(UserView.of(user)));
    }

}
