package com.tileai.admin.rbac;

import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import com.tileai.admin.AdminAccountService;
import com.tileai.common.error.BusinessException;
import com.tileai.common.error.ErrorCode;
import com.tileai.common.security.AuthPrincipal;
import com.tileai.common.security.CurrentPrincipal;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 权限点校验(架构 §4.4)。
 *
 * <p>只对标注了 {@link RequiresPermission} 的处理器生效。<b>未标注的不干预</b> ——
 * 后台路径本身已由 {@code SecurityConfig} 要求 {@code ROLE_ADMIN} 兜底,
 * 所以"忘了标注解"的后果是"任何后台角色都能调",而不是"谁都能调"。
 *
 * <p>校验顺序(每一步都必须过):
 * <ol>
 *   <li>当前是<b>后台身份</b> —— 业主令牌走到这里直接 10002;</li>
 *   <li>账号<b>仍然存在且启用</b> —— 停用的管理员立刻失去所有权限(不复用令牌里的快照);</li>
 *   <li>角色<b>确实拥有</b>该权限点。</li>
 * </ol>
 *
 * <p>抛出的 {@link BusinessException} 会被 {@code GlobalExceptionHandler} 接住,
 * 统一变成 HTTP 403 + 业务码 10004 —— <b>不能</b>在这里自己写响应,
 * 否则状态码口径会散成两处。
 */
@Component
public class AdminPermissionInterceptor implements HandlerInterceptor {

    private final AdminAccountService adminAccountService;

    public AdminPermissionInterceptor(AdminAccountService adminAccountService) {
        this.adminAccountService = adminAccountService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        RequiresPermission required = resolveRequirement(handlerMethod);
        if (required == null) {
            return true;
        }

        AuthPrincipal principal = CurrentPrincipal.requireAdmin();

        // 现查角色,不用令牌里的任何快照 —— 降权/停用要立即生效(见 AdminAccountService)
        AdminRole role = adminAccountService.roleOf(principal.id())
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED));

        if (!role.has(required.value())) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "当前角色(" + role.code() + ")无 " + required.value().code() + " 权限");
        }
        return true;
    }

    /** 方法上的注解优先,其次取类上的默认值;都没有则返回 null。 */
    private static RequiresPermission resolveRequirement(HandlerMethod handlerMethod) {
        RequiresPermission onMethod = handlerMethod.getMethodAnnotation(RequiresPermission.class);
        return onMethod != null
                ? onMethod
                : handlerMethod.getBeanType().getAnnotation(RequiresPermission.class);
    }
}
