package com.tileai.admin.auth;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.tileai.admin.AdminAccountService;
import com.tileai.admin.entity.AdminUser;
import com.tileai.common.error.BusinessException;
import com.tileai.common.error.ErrorCode;

/**
 * 后台登录(架构 §4.4;契约 {@code POST /admin/auth/login})。
 */
@Service
public class AdminAuthService {

    private static final Logger log = LoggerFactory.getLogger(AdminAuthService.class);

    private final AdminAccountService adminAccountService;
    private final PasswordEncoder passwordEncoder;

    /**
     * 用于"账号不存在时也做一次 BCrypt 比对"的哑哈希。
     * <p>在构造时现算,保证格式一定合法(手写常量容易写错格式导致比对直接抛异常,
     * 那样反而更慢、更容易被区分)。
     */
    private final String dummyHash;

    public AdminAuthService(AdminAccountService adminAccountService,
                            PasswordEncoder passwordEncoder) {
        this.adminAccountService = adminAccountService;
        this.passwordEncoder = passwordEncoder;
        this.dummyHash = passwordEncoder.encode("timing-equalizer-not-a-real-password");
    }

    /**
     * 校验用户名与密码。
     *
     * <p><b>失败一律返回 20005</b>,不区分"账号不存在""密码错误""账号已停用" ——
     * 区分开就等于提供了一个账号枚举接口。真实原因记在服务端日志里,靠 traceId 排查。
     *
     * @throws BusinessException {@code 20005} 账号或密码错误
     */
    public AdminUser authenticate(String username, String rawPassword) {
        Optional<AdminUser> found = adminAccountService.findByUsername(username);

        if (found.isEmpty()) {
            // 时序对齐:账号不存在时也跑一次 BCrypt。否则"立刻返回"与"比对后才返回"
            // 的耗时差异,足以被用来枚举哪些用户名是存在的。
            passwordEncoder.matches(rawPassword, dummyHash);
            log.warn("后台登录失败:账号不存在 username={}", username);
            throw new BusinessException(ErrorCode.ACCOUNT_OR_PASSWORD_WRONG);
        }

        AdminUser admin = found.get();
        if (!passwordEncoder.matches(rawPassword, admin.getPasswordHash())) {
            log.warn("后台登录失败:密码错误 username={}", username);
            throw new BusinessException(ErrorCode.ACCOUNT_OR_PASSWORD_WRONG);
        }

        if (!AdminUser.STATUS_ENABLED.equals(admin.getStatus())) {
            log.warn("后台登录失败:账号已停用 username={}", username);
            throw new BusinessException(ErrorCode.ACCOUNT_OR_PASSWORD_WRONG);
        }

        return admin;
    }
}
