package com.tileai.generate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.amqp.rabbit.core.ChannelCallback;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import com.tileai.common.error.BusinessException;
import com.tileai.common.error.ErrorCode;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 受理前"队列有没有人在听"的判定。
 *
 * <p>守的是一个**只在真机上才暴露**的场景:Python 没启动时,任务会永远停在
 * {@code pending},前端一直显示"正在生成…(已排队)",用户要盯着一个进度恒为 0 的
 * 转圈等三到四分钟,最后收到一句行话(2026-10-02 实测)。
 */
@DisplayName("GenerationConsumerProbe —— 受理前探测消费端")
class GenerationConsumerProbeTest {

    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final GenerationConsumerProbe probe = new GenerationConsumerProbe(rabbitTemplate);

    private void stubConsumerCount(Long count) {
        when(rabbitTemplate.execute(ArgumentMatchers.<ChannelCallback<Long>>any())).thenReturn(count);
    }

    @Test
    @DisplayName("有消费者 ⇒ 放行")
    void consumerPresentIsAllowed() {
        stubConsumerCount(3L);

        assertTrue(probe.isConsumerPresent());
    }

    @Test
    @DisplayName("读到 0 ⇒ 判定无人消费")
    void zeroConsumerIsDetected() {
        stubConsumerCount(0L);

        assertFalse(probe.isConsumerPresent());
    }

    /**
     * fail-open 是**刻意**的:探测本身抖动(AMQP 抽风)不该把正常用户挡在门外 ——
     * 那种情况下宁可照常受理、让补偿扫描兜底,也不要误伤。
     */
    @Test
    @DisplayName("探测抛异常 ⇒ 按『有消费者』放行(fail-open)")
    void probeFailureFailsOpen() {
        when(rabbitTemplate.execute(ArgumentMatchers.<ChannelCallback<Long>>any()))
                .thenThrow(new RuntimeException("AMQP 抖了一下"));

        assertTrue(probe.isConsumerPresent(), "探测故障不该阻断正常业务");
    }

    @Test
    @DisplayName("返回 null ⇒ 同样放行")
    void nullCountFailsOpen() {
        stubConsumerCount(null);

        assertTrue(probe.isConsumerPresent());
    }

    @Test
    @DisplayName("requireConsumer:没人听时抛 40002,且文案是可操作的")
    void requireConsumerThrowsBusy() {
        stubConsumerCount(0L);

        BusinessException error = assertThrows(BusinessException.class, probe::requireConsumer);

        assertEquals(ErrorCode.GENERATE_BUSY, error.errorCode(),
                "用契约里早就定义好的 40002,而不是新造一个码");
        assertEquals(GenerationConsumerProbe.UNAVAILABLE_MESSAGE, error.getMessage());
        assertTrue(error.getMessage().contains("请稍后重试"), "要给用户一句能照着做的话");
    }

    @Test
    @DisplayName("requireConsumer:有人在听时不抛")
    void requireConsumerPassesWhenPresent() {
        stubConsumerCount(1L);

        assertDoesNotThrow(probe::requireConsumer);
    }
}
