package com.tileai.common.outbox;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

/**
 * 往发件箱投递待发消息(架构 §6.4)。
 *
 * <p><b>调用点必须在业务事务内。</b>这是 Outbox 模式的全部意义所在:
 * 业务数据与待发消息同事务提交,才能保证"数据改了但消息没发"不会发生。
 * 若在事务提交之后再写 outbox,"写完 outbox 就崩"又会退回原来的丢消息问题。
 *
 * <p>因此本类<b>不标注 {@code @Transactional}</b> —— 它要借用调用方已有的事务。
 * 自己开事务会让它脱离业务事务,模式就失效了。
 */
@Service
public class OutboxService {

    private final OutboxMapper outboxMapper;

    public OutboxService(OutboxMapper outboxMapper) {
        this.outboxMapper = outboxMapper;
    }

    /**
     * 登记一条待发消息。
     *
     * @param aggregateType 业务类型,见 {@link OutboxMessage} 的常量
     * @param aggregateId   业务主键(索引同步用 eventId)
     * @param queue         目标队列,同时用作路由键
     * @param payloadJson   消息体 JSON,投递时原样发出
     */
    public OutboxMessage enqueue(String aggregateType, String aggregateId, String queue,
                                 String payloadJson) {
        OutboxMessage message = new OutboxMessage();
        message.setAggregateType(aggregateType);
        message.setAggregateId(aggregateId);
        message.setQueue(queue);
        message.setPayload(payloadJson);
        message.setStatus(OutboxMessage.STATUS_PENDING);
        message.setRetryCount(0);
        // 立即可投递;退避由投递器在失败后推后
        message.setNextRetryAt(LocalDateTime.now());
        outboxMapper.insert(message);
        return message;
    }
}
