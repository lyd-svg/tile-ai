package com.tileai.common.mq;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 拓扑声明(异步契约 {@code asyncapi-internal.yaml};架构 §6.2、§13.4)。
 *
 * <p>拓扑固定为:
 * <pre>
 *   topic 交换机 tile.topic
 *     ├─ queue.generate        ← routing key: queue.generate
 *     └─ queue.index.sync      ← routing key: queue.index.sync
 *
 *   topic 交换机 tile.dlx(死信)
 *     ├─ queue.generate.dlq    ← routing key: queue.generate.dlq
 *     └─ queue.index.sync.dlq  ← routing key: queue.index.sync.dlq
 * </pre>
 *
 * <p><b>各队列以自身名称作为路由键绑定</b> —— 与契约的 servers 段说明一致。
 *
 * <p><b>队列与消息都声明为持久化</b>(§13.4 要求 MQ 持久化):否则 broker 重启会
 * 丢掉在途的生成任务与索引同步事件。
 *
 * <p>消费失败重试耗尽后由 broker 自动转入对应的 DLQ —— 这是 AMQP 的原生能力
 * (P0-3① 选 RabbitMQ 而非 Redis Stream 的核心理由之一)。DLQ 没有自动消费方,
 * 由人工或脚本兜底(§6.4)。
 *
 * <p>今天只有索引同步在用,但四个队列一次声明齐:拓扑是基础设施,
 * 提前建好可避免 T-1.11(生成编排)落地时再改一次。
 */
@Configuration
public class RabbitTopologyConfig {

    public static final String EXCHANGE = "tile.topic";
    public static final String DEAD_LETTER_EXCHANGE = "tile.dlx";

    public static final String QUEUE_INDEX_SYNC = "queue.index.sync";
    public static final String QUEUE_INDEX_SYNC_DLQ = "queue.index.sync.dlq";
    public static final String QUEUE_GENERATE = "queue.generate";
    public static final String QUEUE_GENERATE_DLQ = "queue.generate.dlq";

    @Bean
    public Declarables tileMqTopology() {
        TopicExchange exchange = new TopicExchange(EXCHANGE, true, false);
        TopicExchange deadLetterExchange = new TopicExchange(DEAD_LETTER_EXCHANGE, true, false);

        return new Declarables(
                exchange,
                deadLetterExchange,

                workQueue(QUEUE_GENERATE, QUEUE_GENERATE_DLQ),
                workQueue(QUEUE_INDEX_SYNC, QUEUE_INDEX_SYNC_DLQ),

                deadLetterQueue(QUEUE_GENERATE_DLQ),
                deadLetterQueue(QUEUE_INDEX_SYNC_DLQ),

                binding(QUEUE_GENERATE),
                binding(QUEUE_INDEX_SYNC),

                deadLetterBinding(QUEUE_GENERATE_DLQ),
                deadLetterBinding(QUEUE_INDEX_SYNC_DLQ));
    }

    /** 工作队列:消费失败且重试耗尽后,由 DLX 转投到对应的死信队列。 */
    private static Queue workQueue(String name, String deadLetterQueue) {
        return QueueBuilder.durable(name)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(deadLetterQueue)
                .build();
    }

    /** 死信队列:只进不出,由人工或运维脚本处理(§6.4)。 */
    private static Queue deadLetterQueue(String name) {
        return QueueBuilder.durable(name).build();
    }

    private static Binding binding(String queueName) {
        return BindingBuilder.bind(new Queue(queueName, true))
                .to(new TopicExchange(EXCHANGE, true, false))
                .with(queueName);
    }

    private static Binding deadLetterBinding(String queueName) {
        return BindingBuilder.bind(new Queue(queueName, true))
                .to(new TopicExchange(DEAD_LETTER_EXCHANGE, true, false))
                .with(queueName);
    }
}
