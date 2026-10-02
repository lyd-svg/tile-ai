package com.tileai.generate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.ChannelCallback;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import com.tileai.common.error.BusinessException;
import com.tileai.common.error.ErrorCode;
import com.tileai.common.mq.RabbitTopologyConfig;

/**
 * 问一句"生成队列现在有没有消费端在听"。
 *
 * <h2>解决什么问题</h2>
 *
 * <p>受理只把消息投进 RabbitMQ 就返回了。若消费端不在(Python 没启动、正在重启),
 * <b>没有任何一路会告诉用户</b> —— 任务永远停在 {@code pending},前端一直显示
 * "正在生成…(已排队)"。补偿扫描要重投两轮、第三轮才判失败,用户盯着一个
 * <b>进度永远为 0</b> 的转圈看三到四分钟,最后收到一句行话。
 *
 * <p>实际踩到:2026-10-02,Python 智能体没启动,用户点生成后卡住,误判成
 * "大模型没额度"。把 Python 起起来之后那条任务 3 秒就成功了 ——
 * 它从来不是"失败",只是"<b>从来没人接手</b>"。
 *
 * <h2>为什么问队列,而不是问 Python 的 /health</h2>
 *
 * <p>那个 {@code /health} <b>只管进程活着</b>。它自己的注释写得很清楚:
 * "生成消费端启动失败只告警,<b>别把 /health 一起打掉</b>" ——
 * 也就是说<b>消费端挂了,/health 照样返回 200</b>,它答不了我们要问的问题。
 *
 * <p>而"这个队列有没有消费者"问的<b>就是那件事本身</b>。用
 * {@code RabbitTemplate.execute} 拿到底层 {@code Channel} 读
 * {@code consumerCount} —— 一次本地的 AMQP 被动查询,不改契约、不新增端点、
 * 也不额外去调 Python。
 *
 * <h2>探测失败时按"有消费端"放行(fail-open)</h2>
 *
 * <p>刻意的取舍:探测本身抖动(AMQP 抽风)不该把正常用户挡在门外 ——
 * 那种情况下宁可照常受理、让补偿扫描兜底,也不要误伤。探测只在<b>明确读到 0</b>
 * 时才拒绝。
 */
@Component
public class GenerationConsumerProbe {

    private static final Logger log = LoggerFactory.getLogger(GenerationConsumerProbe.class);

    /** 拒绝时给用户看的话。它最终落在前端提交失败的那条 `[role="alert"]` 上。 */
    static final String UNAVAILABLE_MESSAGE =
            "生成服务暂时不可用(可能未启动或正在重启),请稍后重试";

    private final RabbitTemplate rabbitTemplate;

    public GenerationConsumerProbe(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * 队列上是否有消费者。
     *
     * @return 明确读到 0 时返回 {@code false};读到正数、或探测本身出错时返回 {@code true}
     */
    public boolean isConsumerPresent() {
        try {
            // 显式写明回调类型:`RabbitTemplate#execute` 有多个重载,光靠 lambda
            // 推不出该用 ChannelCallback 还是 ConnectionCallback(编译期会报类型不明确)。
            Long count = rabbitTemplate.execute((ChannelCallback<Long>) channel ->
                    channel.consumerCount(RabbitTopologyConfig.QUEUE_GENERATE));
            if (count == null) {
                log.warn("探测生成队列消费端返回空,按『有消费端』放行");
                return true;
            }
            return count > 0;
        } catch (Exception error) {
            // fail-open:见类注释。探测故障不该阻断正常业务。
            log.warn("探测生成队列消费端失败,按『有消费端』放行:{}", error.toString());
            return true;
        }
    }

    /** 没有消费端就抛 40002 —— 让调用方一行接住。 */
    public void requireConsumer() {
        if (!isConsumerPresent()) {
            throw new BusinessException(ErrorCode.GENERATE_BUSY, UNAVAILABLE_MESSAGE);
        }
    }
}
