package com.tileai.common.security;

import java.time.Duration;

/**
 * refresh_token 黑名单(架构 §3:Redis 承载"登录会话 / refresh 黑名单")。
 *
 * <p>用途是<b>登出与令牌吊销</b>:JWT 天生无状态、签发后无法撤回,所以"登出"必须靠
 * 一份外部名单把尚未过期的 refresh 令牌作废。
 *
 * <p>黑名单项只需存活到该令牌<b>自然过期</b>为止 —— 过期之后校验本就通不过,
 * 再留着只是白占内存。所以 {@link #blacklist} 的 TTL 传的是令牌剩余有效期。
 *
 * <p>抽成接口是为了让纯逻辑测试能注入替身;生产实现见
 * {@link RedisTokenBlacklist}。
 */
public interface TokenBlacklist {

    /**
     * 把令牌拉黑。
     *
     * @param jti 令牌唯一 ID(不是令牌本身 —— 不把明文令牌写进 Redis)
     * @param ttl 存活时长,取令牌剩余有效期
     */
    void blacklist(String jti, Duration ttl);

    boolean isBlacklisted(String jti);
}
