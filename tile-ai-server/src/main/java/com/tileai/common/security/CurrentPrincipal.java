package com.tileai.common.security;

import java.util.Optional;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.tileai.common.error.BusinessException;
import com.tileai.common.error.ErrorCode;

/**
 * 读取当前请求的已认证主体。
 *
 * <p>业务代码不要直接摸 {@code SecurityContextHolder} —— 那样每个调用点都要自己
 * 判空、判主体类型。这里把规则收在一处:
 * <ul>
 *   <li><b>C 端与 B 端不互通</b> —— 两套 ID 空间(见 {@link AuthPrincipal}),混用会导致越权:</li>
 * </ul>
 * 例如业主令牌不该能调后台接口,反之亦然。
 */
public final class CurrentPrincipal {

    private CurrentPrincipal() {
    }

    /** 未认证时返回空,不抛异常 —— 适用于"有则用、无则略"的场景。 */
    public static Optional<AuthPrincipal> find() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthPrincipal principal) {
            return Optional.of(principal);
        }
        return Optional.empty();
    }

    /**
     * 要求一个<b>正式登录的 C 端业主</b>。
     *
     * @throws BusinessException 10002 未登录,或令牌不是业主身份
     */
    public static AuthPrincipal requireUser() {
        AuthPrincipal principal = require();
        if (principal.type() != SubjectType.USER) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        return principal;
    }

    /**
     * 要求一个<b>正式登录的后台账号</b>。
     *
     * @throws BusinessException 10002 未登录,或令牌不是后台身份
     */
    public static AuthPrincipal requireAdmin() {
        AuthPrincipal principal = require();
        if (principal.type() != SubjectType.ADMIN) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        return principal;
    }

    private static AuthPrincipal require() {
        return find().orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED));
    }
}
