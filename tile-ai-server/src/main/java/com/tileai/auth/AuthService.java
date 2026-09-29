package com.tileai.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tileai.auth.entity.User;
import com.tileai.auth.mapper.UserMapper;
import com.tileai.common.error.BusinessException;
import com.tileai.common.error.ErrorCode;

import java.time.LocalDateTime;

/**
 * C 端账户的领域逻辑(架构 §4.1 的 auth/account 模块;PRD §5.1)。
 *
 * <p>登录标识是 {@code username}(唯一)—— v0.7 起由手机号改为用户名,
 * 短信验证码通道已取消(架构 §15.6)。
 *
 * <h2>两条安全约束,改动时不要放松</h2>
 * <ol>
 *   <li><b>登录失败一律 20005</b>,不区分「用户名不存在」「密码错误」「账号已停用」。
 *       区分开等于对外提供一个<b>账号枚举接口</b> —— 攻击者可借此批量探测哪些用户名已注册。
 *       真实原因只记在服务端日志里。</li>
 *   <li><b>用户名不存在时也跑一次 BCrypt</b>(时序对齐)。否则"立刻返回"与"比对后才返回"
 *       的耗时差异,足以被用来枚举账号 —— 与第 1 条要防的是同一件事,只是从响应体换成了响应时间。</li>
 * </ol>
 */
@Service
public class AuthService {

    /** 密码长度区间,来自 PRD §5.1「6–20 位」。 */
    public static final int PASSWORD_MIN = 6;
    public static final int PASSWORD_MAX = 20;

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    /**
     * 用于"用户名不存在时也做一次 BCrypt 比对"的哑哈希。
     * <p>构造时现算,保证格式一定合法(手写常量容易写错格式,反而更慢、更容易被区分)。
     */
    private final String dummyHash;

    public AuthService(UserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.dummyHash = passwordEncoder.encode("timing-equalizer-not-a-real-password");
    }

    // ------------------------------------------------------------------

    /**
     * 注册(契约 {@code POST /auth/register})。
     *
     * <p>注册成功即建立登录态 —— v0.7 起注册是一次请求,不再有「验证码登录 → 待设密码」两阶段。
     *
     * @throws BusinessException 20003 用户名已注册;20004 密码格式不合规
     */
    @Transactional
    public User register(String username, String rawPassword) {
        // 先校验密码格式:让"用户名重复 + 密码也不合规"的请求报密码问题,
        // 而不是先暴露"这个用户名已被占用"
        validatePasswordFormat(rawPassword);

        String trimmed = username.trim();
        if (findByUsername(trimmed) != null) {
            throw new BusinessException(ErrorCode.USERNAME_ALREADY_REGISTERED,
                    "用户名已被占用: " + trimmed);
        }

        User user = new User();
        user.setUsername(trimmed);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setStatus(User.STATUS_ENABLED);
        userMapper.insert(user);

        log.info("新用户注册成功 userId={} username={}", user.getId(), trimmed);
        return user;
    }

    /**
     * 登录(契约 {@code POST /auth/login})。
     *
     * @throws BusinessException {@code 20005} 账号或密码错误(不区分具体原因,见类注释)
     */
    @Transactional
    public User login(String username, String rawPassword) {
        User user = findByUsername(username == null ? null : username.trim());

        if (user == null) {
            // 时序对齐:账号不存在时也跑一次 BCrypt(见类注释第 2 条)
            passwordEncoder.matches(rawPassword, dummyHash);
            log.warn("登录失败:用户名不存在 username={}", username);
            throw new BusinessException(ErrorCode.ACCOUNT_OR_PASSWORD_WRONG);
        }

        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            log.warn("登录失败:密码错误 userId={}", user.getId());
            throw new BusinessException(ErrorCode.ACCOUNT_OR_PASSWORD_WRONG);
        }

        if (!User.STATUS_ENABLED.equals(user.getStatus())) {
            // 不暴露"账号存在但被停用" —— 那同样是一条账号存在性的信息
            log.warn("登录失败:账号已停用 userId={}", user.getId());
            throw new BusinessException(ErrorCode.ACCOUNT_OR_PASSWORD_WRONG);
        }

        // 记录最近登录时间(§8.1 的字段)。只更新这一列,避免整体写回覆盖并发改动。
        User patch = new User();
        patch.setId(user.getId());
        patch.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(patch);

        // 让调用方拿到的时间也反映本次登录,免得响应里少了它
        user.setLastLoginAt(patch.getLastLoginAt());
        return user;
    }

    /**
     * 修改密码(契约 {@code POST /auth/password},需登录)。
     *
     * @throws BusinessException 20005 原密码错误;20004 新密码格式不合规
     */
    @Transactional
    public void changePassword(long userId, String oldPassword, String newPassword) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            // 令牌签名有效,但用户已被删除 —— 不能放行
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }

        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            log.warn("修改密码失败:原密码错误 userId={}", userId);
            throw new BusinessException(ErrorCode.ACCOUNT_OR_PASSWORD_WRONG);
        }

        validatePasswordFormat(newPassword);

        User patch = new User();
        patch.setId(userId);
        patch.setPasswordHash(passwordEncoder.encode(newPassword));
        userMapper.updateById(patch);

        log.info("用户修改密码 userId={}", userId);
    }

    // ------------------------------------------------------------------

    public User findByUsername(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        return userMapper.selectOne(Wrappers.<User>lambdaQuery()
                .eq(User::getUsername, username));
    }

    /**
     * 密码格式校验。
     *
     * <p>抛的是 <b>20004</b> 而不是 {@code @Size} 会带来的 10001 ——
     * 契约对注册/改密接口声明的密码格式错误码就是 20004。见 {@code AuthRequests} 的说明。
     */
    private static void validatePasswordFormat(String rawPassword) {
        if (rawPassword == null
                || rawPassword.length() < PASSWORD_MIN
                || rawPassword.length() > PASSWORD_MAX) {
            throw new BusinessException(ErrorCode.PASSWORD_FORMAT_INVALID,
                    "密码长度须为 " + PASSWORD_MIN + "–" + PASSWORD_MAX + " 位");
        }
    }
}
