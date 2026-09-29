package com.tileai.common.outbox;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tileai.common.mq.RabbitTopologyConfig;

/**
 * 发件箱投递器(架构 §6.4)。
 *
 * <p>定时扫描 {@code t_outbox} 中到期未发的记录,投递到 MQ,成功后标记 {@code sent};
 * 失败按<b>指数退避</b>重试。消息体存在库里、原样发出 —— 不在投递时重新构造,
 * 避免"落库时的数据"与"发出时的数据"不一致。
 *
 * <p><b>退回阈值后标记 {@code failed} 并停止重试</b>:长期重试一条永远发不出的消息
 * 会让扫描队列无限增长。停下来的代价是可能漏同步,由 §15.2 要求的
 * <b>定期全量重建</b>兜底 —— 这正是"索引可重建"这条设计的意义。
 * {@code failed} 记录同时是 T-2.3 告警的抓手。
 *
 * <p><b>不做分布式锁</b>:一期 Java 可能跑 2 个实例(§12.1),两个实例会重复投递。
 * 重复投递是<b>允许</b>的 —— 契约要求消费端以 {@code eventId} 幂等去重(§6.4)。
 * 用幂等换掉分布式锁,是这套设计的既定取舍。
 */
@Component
public class OutboxDispatcher {

    private static final Logger log = LoggerFactory.getLogger(OutboxDispatcher.class);

    /** 退避上限:再久也不超过这个间隔,否则一次短暂故障会让消息等上几小时。 */
    private static final Duration MAX_BACKOFF = Duration.ofMinutes(5);

    private final OutboxMapper outboxMapper;
    private final RabbitTemplate rabbitTemplate;
    private final OutboxProperties properties;

    public OutboxDispatcher(OutboxMapper outboxMapper, RabbitTemplate rabbitTemplate,
                            OutboxProperties properties) {
        this.outboxMapper = outboxMapper;
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${tile.outbox.dispatch-interval:2s}")
    public void dispatch() {
        if (!properties.isEnabled()) {
            return;
        }
        List<OutboxMessage> batch = findDue();
        for (OutboxMessage message : batch) {
            dispatchOne(message);
        }
    }

    /**
     * 取一批到期的待发消息。
     *
     * <p>只取 {@code pending} —— {@code sent} 已投递完成,{@code failed} 是终态
     * (重试超限后停止,由全量重建兜底)。重试中的消息状态仍是 {@code pending},
     * 靠 {@code next_retry_at} 推后进行退避,所以这里不需要额外分支。
     */
    private List<OutboxMessage> findDue() {
        return outboxMapper.selectList(Wrappers.<OutboxMessage>lambdaQuery()
                .eq(OutboxMessage::getStatus, OutboxMessage.STATUS_PENDING)
                .le(OutboxMessage::getNextRetryAt, LocalDateTime.now())
                .orderByAsc(OutboxMessage::getId)
                .last("LIMIT " + properties.getBatchSize()));
    }

    private void dispatchOne(OutboxMessage message) {
        try {
            // 各队列以自身名称作为路由键(与拓扑声明一致)
            rabbitTemplate.convertAndSend(RabbitTopologyConfig.EXCHANGE, message.getQueue(),
                    message.getPayload());
            markSent(message);
        } catch (Exception e) {
            markRetryOrFail(message, e);
        }
    }

    private void markSent(OutboxMessage message) {
        OutboxMessage patch = new OutboxMessage();
        patch.setId(message.getId());
        patch.setStatus(OutboxMessage.STATUS_SENT);
        patch.setSentAt(LocalDateTime.now());
        outboxMapper.updateById(patch);
    }

    private void markRetryOrFail(OutboxMessage message, Exception cause) {
        int retry = message.getRetryCount() == null ? 0 : message.getRetryCount();
        int next = retry + 1;

        if (next >= properties.getMaxRetry()) {
            OutboxMessage patch = new OutboxMessage();
            patch.setId(message.getId());
            patch.setStatus(OutboxMessage.STATUS_FAILED);
            patch.setRetryCount(next);
            outboxMapper.updateById(patch);
            log.error("outbox 消息投递失败已达上限,标记 failed(队列={} 事件={}) —— "
                            + "该条索引变更漏同步,需靠全量重建兜底",
                    message.getQueue(), message.getAggregateId(), cause);
            return;
        }

        OutboxMessage patch = new OutboxMessage();
        patch.setId(message.getId());
        patch.setStatus(OutboxMessage.STATUS_PENDING);
        patch.setRetryCount(next);
        patch.setNextRetryAt(LocalDateTime.now().plus(backoff(next)));
        outboxMapper.updateById(patch);
        log.warn("outbox 投递失败,第 {} 次重试(队列={} 事件={})",
                next, message.getQueue(), message.getAggregateId());
    }

    /**
     * 指数退避:2^retry 秒,封顶 {@link #MAX_BACKOFF}。
     *
     * <p>退避的意义是给下游恢复的时间,而不是把重试排得密集 ——
     * MQ 整体不可用时,密集重试只会把日志刷满。
     */
    static Duration backoff(int retryCount) {
        long seconds = (long) Math.pow(2, Math.min(retryCount, 20));
        Duration backoff = Duration.ofSeconds(seconds);
        return backoff.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : backoff;
    }
}
