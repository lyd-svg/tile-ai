package com.tileai.generate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tileai.common.mq.RabbitTopologyConfig;
import com.tileai.common.outbox.OutboxMapper;
import com.tileai.common.outbox.OutboxMessage;
import com.tileai.common.outbox.OutboxService;
import com.tileai.generate.dto.GenerateTaskPayload;
import com.tileai.generate.entity.GenerationTask;
import com.tileai.generate.mapper.GenerationTaskMapper;

/**
 * 生成任务的补偿扫描(架构 §6.4「长时间停留的任务」)。
 *
 * <p>它是整套异步管道的<b>安全网</b>,不是主路径。消息已经落在发件箱里,
 * 理论上不会丢;但下面这些事都能让一个任务永远停在半路,而且都不报错:
 *
 * <ul>
 *   <li>发件箱那条 {@code failed} 了(投递重试到上限)—— 消息就此没有下文;</li>
 *   <li>Python 消费端崩在"取了消息、还没回调"之间;</li>
 *   <li>回调发出去了但 Java 那次请求正好挂了。</li>
 * </ul>
 *
 * <p>这几种情况的共同点是 <b>任务状态不再变化</b>,所以判据就是
 * "停留在 {@code pending}/{@code running} 太久"({@code updated_at} 是 MySQL
 * 的 {@code ON UPDATE} 自动维护的,每次进度回调都会刷新它 —— 活着的任务扫不到)。
 * 阈值与理由见 {@link GenerateProperties},完整口径记在
 * {@code openapi-verification.md} §35.1。
 *
 * <h2>为什么不给这些写操作包事务</h2>
 *
 * <p>与 {@link com.tileai.common.outbox.OutboxDispatcher} 同一取舍:全项目的定时任务
 * 都不开事务。若在 {@code @Scheduled} 方法上标 {@code @Transactional} 再自调用,
 * Spring 的代理压根不生效("看着有事务、其实没有"),比没有更危险。真的在半途崩了也不
 * 致命 —— 下一次扫描仍会看到这个任务卡着。因此这里的写法是:每条语句各自提交,
 * 并把<b>顺序</b>安排成"崩了就只是多投一次",而不是"崩了就少投一次"。
 */
@Component
public class GenerateCompensationScanner {

    private static final Logger log = LoggerFactory.getLogger(GenerateCompensationScanner.class);

    /** 一轮最多处理多少个任务。安全网不需要吞吐,需要的是不要把一次定时扫描拉成几秒。 */
    private static final int SCAN_BATCH = 50;

    /** 放弃时写给用户看的原因(进 {@code t_generation_task.error},失败回调会把它回给前端)。 */
    private static final String GIVE_UP_MESSAGE = "生成任务长时间无响应,重试已达上限";

    private final GenerationTaskMapper taskMapper;
    private final OutboxMapper outboxMapper;
    private final OutboxService outboxService;
    private final GenerateProperties properties;
    private final ObjectMapper objectMapper;

    public GenerateCompensationScanner(GenerationTaskMapper taskMapper, OutboxMapper outboxMapper,
                                       OutboxService outboxService, GenerateProperties properties,
                                       ObjectMapper objectMapper) {
        this.taskMapper = taskMapper;
        this.outboxMapper = outboxMapper;
        this.outboxService = outboxService;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${tile.generate.compensation-interval:60s}")
    public void scheduled() {
        if (!properties.isCompensationEnabled()) {
            return;
        }
        try {
            int handled = scanOnce();
            if (handled > 0) {
                log.warn("补偿扫描处理了 {} 个停滞的生成任务", handled);
            }
        } catch (Exception e) {
            // 定时任务抛出异常会让后续调度一并停掉(Spring 的默认行为),
            // 那等于把安全网拆了。这里必须自己吞掉,只记日志。
            log.error("补偿扫描失败", e);
        }
    }

    /**
     * 跑一轮扫描。抽成公开方法是为了让测试能直接驱动它,而不用等 60 秒的定时。
     *
     * <p>三件事,顺序不能换:
     * <ol>
     *   <li><b>硬截止</b>先判 —— 超时的任务无论卡不卡都该死,先清掉它,
     *       免得它又白拿一次重投机会;</li>
     *   <li>{@code pending} 停滞 → 重投(除非投递器还在处理它);</li>
     *   <li>{@code running} 停滞 → 重投。</li>
     * </ol>
     *
     * @return 本轮处理(重投或判失败)的任务数
     */
    public int scanOnce() {
        LocalDateTime now = LocalDateTime.now();
        int handled = 0;

        // ① 硬截止。**必须单独查一次,不能挂在"停滞"分支里**:这一条要挡的正是那种
        //    "一直在回调、看起来一直活着、却永远到不了 success"的任务 —— 它的
        //    updated_at 不断被刷新,按停滞阈值永远扫不到。挂在停滞分支里等于没写。
        for (GenerationTask task : taskMapper.selectList(Wrappers.<GenerationTask>lambdaQuery()
                .in(GenerationTask::getStatus, GenerationTask.STATUS_PENDING,
                        GenerationTask.STATUS_RUNNING)
                .le(GenerationTask::getCreatedAt, now.minus(properties.getHardDeadline()))
                .last("LIMIT " + SCAN_BATCH))) {
            log.warn("生成任务超过硬截止({} 分钟),判 failed taskId={} createdAt={}",
                    properties.getHardDeadline().toMinutes(), task.getTaskId(), task.getCreatedAt());
            giveUp(task);
            handled++;
        }

        // ② pending 停滞。有属于它的待发消息就跳过(投递器还在处理,重投纯属重复,
        //    见 hasPendingOutbox);没有才是真卡住。
        for (GenerationTask task : stuck(GenerationTask.STATUS_PENDING,
                properties.getPendingStuckAfter(), now)) {
            if (hasPendingOutbox(task.getTaskId())) {
                continue;
            }
            handled++;
            resend(task, now);
        }

        // ③ running 停滞。不看发件箱 —— 那个阶段消息早投出去了。
        for (GenerationTask task : stuck(GenerationTask.STATUS_RUNNING,
                properties.getRunningStuckAfter(), now)) {
            handled++;
            resend(task, now);
        }

        return handled;
    }

    /**
     * 取停留超时的任务。
     *
     * <p>两个状态各查一次而不是一次查两个:它们用的是<b>不同阈值</b>,
     * 合成一条 SQL 得写成 {@code (status='pending' AND updated_at < a) OR (status='running' AND updated_at < b)},
     * 那种条件用不上 {@code idx_task_status(status, updated_at)} 的复合前缀排序。
     * 分开查两条,各自都是标准的"状态等值 + 时间范围",索引正好命中。
     */
    private List<GenerationTask> stuck(String status, Duration threshold, LocalDateTime now) {
        return taskMapper.selectList(Wrappers.<GenerationTask>lambdaQuery()
                .eq(GenerationTask::getStatus, status)
                .le(GenerationTask::getUpdatedAt, now.minus(threshold))
                .orderByAsc(GenerationTask::getUpdatedAt)
                .last("LIMIT " + SCAN_BATCH));
    }

    /**
     * 这个任务在发件箱里还有没有待发的消息。
     *
     * <p><b>这一条不能省。</b>任务的 {@code updated_at} 在投递成功那一刻<b>不会</b>变
     * (投递器只改 {@code t_outbox},按设计不动业务状态 —— 见 §35.2),所以一个
     * 刚受理、还在等投递器扫描的任务,看起来就是一个"60 秒没动过"的卡住任务。
     * 不判这一下,补偿扫描会在发件箱消息发出之前抢先重投一次,每个慢一点的
     * 任务都平白多出一份重复消息。
     */
    private boolean hasPendingOutbox(String taskId) {
        return outboxMapper.selectCount(Wrappers.<OutboxMessage>lambdaQuery()
                .eq(OutboxMessage::getAggregateType, OutboxMessage.AGGREGATE_TASK)
                .eq(OutboxMessage::getAggregateId, taskId)
                .eq(OutboxMessage::getStatus, OutboxMessage.STATUS_PENDING)) > 0;
    }

    /**
     * 一个停滞任务的处理:重投,或者放弃。
     *
     * <p>用 {@code attempt}(消息体里)而不是 {@code t_outbox.retry_count} 计数 ——
     * 后者是<b>MQ 投递</b>的失败次数,与"这次生成总共试了几轮"不是一回事,
     * 一次重投会新开一条 outbox 行(从 retry_count=0 开始)。见 §35.5。
     */
    private void resend(GenerationTask task, LocalDateTime now) {
        GenerateTaskPayload payload;
        try {
            payload = objectMapper.readValue(task.getPayload(), GenerateTaskPayload.class);
        } catch (JsonProcessingException e) {
            // 快照坏了就没法重投(投出去也是一条 Python 解不开的消息)。判失败,
            // 让用户看到一个明确的结局,而不是无限等一个永远不动的进度条。
            log.error("停滞任务的快照无法解析,判失败 taskId={}", task.getTaskId(), e);
            giveUp(task);
            return;
        }

        int attempt = payload.attempt() == null ? GenerateTaskPayload.INITIAL_ATTEMPT : payload.attempt();
        if (attempt >= properties.getMaxAttempts()) {
            log.warn("生成任务重投已达上限({} 次),判 failed taskId={}", attempt, task.getTaskId());
            giveUp(task);
            return;
        }

        GenerateTaskPayload next = payload.withAttempt(attempt + 1);
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(next);
        } catch (JsonProcessingException e) {
            log.error("重投消息无法序列化,判失败 taskId={}", task.getTaskId(), e);
            giveUp(task);
            return;
        }

        // 顺序刻意如此:先登记新消息、再回写 attempt。
        // 反过来写的话,两条语句之间崩掉就变成"attempt 涨了、消息没发" ——
        // 白烧一次重试机会。现在这个顺序崩掉只是"多投一次旧 attempt 的消息",
        // 而重投本来就是幂等的(Python 侧以 taskId 去重)。
        outboxService.enqueue(OutboxMessage.AGGREGATE_TASK, task.getTaskId(),
                RabbitTopologyConfig.QUEUE_GENERATE, payloadJson);
        taskMapper.updatePayload(task.getTaskId(), payloadJson);

        log.warn("生成任务停滞(状态={} 已 {} 秒未更新),第 {} 次重投 taskId={}",
                task.getStatus(),
                Duration.between(task.getUpdatedAt(), now).toSeconds(),
                attempt + 1,
                task.getTaskId());
    }

    private void giveUp(GenerationTask task) {
        int progress = task.getProgress() == null ? 0 : task.getProgress();
        taskMapper.markFailed(task.getTaskId(), progress, GIVE_UP_MESSAGE);
    }
}
