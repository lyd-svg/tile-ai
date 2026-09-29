package com.tileai.common.security;

import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.tileai.common.error.BusinessException;
import com.tileai.common.error.ErrorCode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T-0.4:黑名单与一次性令牌 —— <b>需要真实 Redis</b>。
 *
 * <p>默认不参与构建(否则无 Redis 的环境 {@code mvn test} 会红)。显式启用:
 *
 * <pre>
 *   mvn -s D:\dev-tools\maven-settings.xml test "-Dredis.verify=true"
 * </pre>
 *
 * <p>与 SchemaMigrationTest 同样的原则:不写成"连不上就 SKIP"——
 * 跳过看起来像通过。这里没 Redis 就是失败,必须显式开关才会运行。
 *
 * <p>用真实 Redis 而不是内存替身:要验的恰恰是<b>原子语义</b>
 * ({@code getAndDelete} 的一次性、TTL 的自动过期),替身给不了这个保证。
 *
 * <p><b>类名为什么是 {@code *Test} 而不是 {@code *IT}</b>:Maven Surefire 默认只扫
 * {@code *Test} / {@code Test*} / {@code *Tests},{@code *IT} 是 Failsafe 的约定。
 * 叫 {@code IT} 会让整个类<b>被静默跳过</b> —— 只有在核对测试总数时才会发现,
 * 而"没跑"和"跑过了"在报告里长得几乎一样。本项目区分"需外部依赖"统一用
 * 属性开关({@code -Dxxx.verify=true}),不靠命名约定。
 */
@EnabledIfSystemProperty(named = "redis.verify", matches = "true",
        disabledReason = "需要 Redis;用 -Dredis.verify=true 显式启用(T-0.4 验证)")
@SpringBootTest(properties = {"spring.flyway.enabled=false"})
class RedisTokenStoreTest {

    @Autowired
    private TokenBlacklist blacklist;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private AuthSessionService sessions;

    @Autowired
    private StringRedisTemplate redis;

    @AfterEach
    void cleanUp() {
        // 测试自己造的键自己清 —— refresh 令牌的剩余寿命有 14 天,
        // 不清的话会在开发用的 Redis 里积一堆垃圾。
        var keys = redis.keys("auth:*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    // ---------------------------------------------------------------
    // refresh 黑名单
    // ---------------------------------------------------------------

    @Test
    @DisplayName("拉黑后能查到;TTL 随令牌剩余有效期")
    void blacklistRoundTrip() {
        assertFalse(blacklist.isBlacklisted("jti-1"));

        blacklist.blacklist("jti-1", Duration.ofMinutes(5));
        assertTrue(blacklist.isBlacklisted("jti-1"));

        Long ttl = redis.getExpire(RedisTokenBlacklist.KEY_PREFIX + "jti-1");
        assertNotNull(ttl);
        assertTrue(ttl > 0 && ttl <= 300, "TTL 应接近 5 分钟,实际 " + ttl);
    }

    @Test
    @DisplayName("剩余寿命为零的令牌不入名单(已过期,校验本就不通过)")
    void alreadyExpiredTokenIsNotStored() {
        blacklist.blacklist("jti-zero", Duration.ZERO);
        assertFalse(blacklist.isBlacklisted("jti-zero"));
    }

    @Test
    @DisplayName("登出后 refresh 令牌立即失效 —— T-0.4 的核心判据")
    void logoutInvalidatesRefreshToken() {
        AuthSessionService.SessionCookies session = sessions.start(9L, SubjectType.USER);
        String refreshToken = session.refresh().getValue();

        // 登出前:能换到新的 access
        assertNotNull(sessions.refreshAccess(refreshToken), "登出前刷新应成功");

        // 登出
        sessions.logout(refreshToken);

        // 登出后:同一个 refresh 令牌必须失效
        BusinessException ex = assertThrows(BusinessException.class,
                () -> sessions.refreshAccess(refreshToken),
                "登出后 refresh 令牌仍可用 —— 登出等于没做");
        assertEquals(ErrorCode.UNAUTHENTICATED, ex.errorCode());
    }

    @Test
    @DisplayName("登出是幂等的:同一个令牌重复登出不报错")
    void logoutIsIdempotent() {
        AuthSessionService.SessionCookies session = sessions.start(9L, SubjectType.USER);
        String refreshToken = session.refresh().getValue();

        sessions.logout(refreshToken);
        sessions.logout(refreshToken);
        sessions.logout("完全无效的字符串");
        sessions.logout(null);
    }

    @Test
    @DisplayName("登出会清掉两个 Cookie,且 Path 与下发时一致")
    void logoutClearsBothCookies() {
        AuthSessionService.SessionCookies session = sessions.start(9L, SubjectType.USER);
        AuthSessionService.SessionCookies cleared = sessions.logout(
                session.refresh().getValue());

        assertEquals(session.access().getPath(), cleared.access().getPath());
        assertEquals(session.refresh().getPath(), cleared.refresh().getPath());
        assertEquals(0, cleared.access().getMaxAge().getSeconds());
        assertEquals(0, cleared.refresh().getMaxAge().getSeconds());
    }

    @Test
    @DisplayName("refresh 令牌不能当 access 用(经 AuthSessionService 再确认一次)")
    void accessTokenCannotBeRefreshed() {
        String access = jwtService.issueAccess(9L, SubjectType.USER);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> sessions.refreshAccess(access));
        assertEquals(ErrorCode.UNAUTHENTICATED, ex.errorCode());
    }

}
