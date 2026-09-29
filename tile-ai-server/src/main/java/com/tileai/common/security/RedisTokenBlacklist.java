package com.tileai.common.security;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * {@link TokenBlacklist} 的 Redis 实现。
 *
 * <p>值的语义很简单:键存在即被拉黑。值本身不重要,存 "1" 即可 ——
 * <b>不存明文令牌</b>,只存 jti,这样 Redis 一旦泄露也不会直接暴露可用令牌。
 *
 * <p>Redis 不可用时这里会抛异常(而非静默放行)。这是刻意的:登出/吊销失败若被吞掉,
 * 会变成"用户以为登出了、实际令牌仍有效"——那种失败必须让调用方看见。
 */
@Component
public class RedisTokenBlacklist implements TokenBlacklist {

    static final String KEY_PREFIX = "auth:blacklist:refresh:";

    private static final String PRESENT = "1";

    private final StringRedisTemplate redis;

    public RedisTokenBlacklist(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void blacklist(String jti, Duration ttl) {
        if (ttl.isZero() || ttl.isNegative()) {
            // 令牌已过期,校验本就不会通过,无需入名单。
            return;
        }
        redis.opsForValue().set(KEY_PREFIX + jti, PRESENT, ttl);
    }

    @Override
    public boolean isBlacklisted(String jti) {
        if (jti == null || jti.isBlank()) {
            return false;
        }
        return Boolean.TRUE.equals(redis.hasKey(KEY_PREFIX + jti));
    }
}
