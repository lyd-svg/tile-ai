package com.tileai.common.outbox;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T-0.13:发件箱投递的退避策略(架构 §6.4:「失败按<b>指数退避</b>重试」)。
 *
 * <p>纯单元测试,不依赖 MQ 与数据库。
 */
class OutboxDispatcherTest {

    @Test
    @DisplayName("退避按 2^n 秒增长")
    void backoffGrowsExponentially() {
        assertEquals(Duration.ofSeconds(2), OutboxDispatcher.backoff(1));
        assertEquals(Duration.ofSeconds(4), OutboxDispatcher.backoff(2));
        assertEquals(Duration.ofSeconds(8), OutboxDispatcher.backoff(3));
        assertEquals(Duration.ofSeconds(16), OutboxDispatcher.backoff(4));
    }

    @Test
    @DisplayName("退避有上限 —— 否则一次短暂故障会让消息等上几小时")
    void backoffIsCapped() {
        assertEquals(Duration.ofMinutes(5), OutboxDispatcher.backoff(10));
        assertEquals(Duration.ofMinutes(5), OutboxDispatcher.backoff(30));
    }

    @Test
    @DisplayName("退避单调不减")
    void backoffIsMonotonic() {
        Duration previous = Duration.ZERO;
        for (int retry = 1; retry <= 15; retry++) {
            Duration current = OutboxDispatcher.backoff(retry);
            assertTrue(current.compareTo(previous) >= 0,
                    "第 " + retry + " 次的退避不应短于上一次");
            previous = current;
        }
    }

    @Test
    @DisplayName("极大的重试次数不会溢出成负数(用 Math.min 夹住指数)")
    void hugeRetryCountDoesNotOverflow() {
        // 2^31 秒已经溢出 int;实现里对指数做了夹取,这里确认不会出现负的退避
        assertTrue(OutboxDispatcher.backoff(100).compareTo(Duration.ZERO) > 0);
        assertTrue(OutboxDispatcher.backoff(Integer.MAX_VALUE).compareTo(Duration.ZERO) > 0);
    }
}
