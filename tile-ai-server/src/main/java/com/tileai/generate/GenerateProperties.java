package com.tileai.generate;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 生成编排的调优参数(架构 §6.4「长时间停留」「超过阈值」的具体数值)。
 *
 * <p><b>这些数字上游一个都没有。</b>架构只说"长时间停留",AsyncAPI 只说"超过阈值" ——
 * 所以它们是本任务定下的决策,连同理由一起记在
 * {@code openapi-verification.md} §35.1。改这里等于改那节。
 *
 * <p>由 {@code @ConfigurationPropertiesScan} 自动注册。
 */
@ConfigurationProperties(prefix = "tile.generate")
public class GenerateProperties {

    /**
     * {@code pending} 停留多久算卡住。
     *
     * <p>60 秒的依据:投递器每 2 秒扫一次、退避 2/4/8…300 秒。**还要看有没有待发的
     * 发件箱行** —— 有就说明投递器还在处理它,重投纯属重复;那行 {@code sent} 之后
     * 却没了下文,或已经 {@code failed},静默 60 秒才是真卡住。
     */
    private Duration pendingStuckAfter = Duration.ofSeconds(60);

    /**
     * {@code running} 停留多久算卡住。
     *
     * <p>360 秒 = 最坏真实耗时(4 张 × 60 秒,§7.3 的单图时限)的 1.5 倍。
     * <b>活着</b>的任务扫不到它:每次进度回调都会写库并刷新 {@code updated_at},
     * 这正是 {@code running} 阈值敢这么定的前提(§35.2)。
     */
    private Duration runningStuckAfter = Duration.ofSeconds(360);

    /**
     * 重投上限。
     *
     * <p>每次重投都可能等于 4 次真实模型调用 —— 与索引同步那种"重试几乎不要钱"的
     * 场景不同,这里必须收得紧。到 3 次判 {@code failed}(40001)。
     */
    private int maxAttempts = 3;

    /**
     * 硬截止:任务创建超过这么久,无论处于什么状态都判失败。
     *
     * <p>专门给 {@code running} 分支兜底 —— 如果有个任务一直"活着"地卡着
     * (比如回调把 {@code updated_at} 刷得很勤但永远到不了 success),
     * 单靠 {@code runningStuckAfter} 是永远等不到的。
     */
    private Duration hardDeadline = Duration.ofMinutes(30);

    /**
     * 是否启用补偿扫描。
     *
     * <p>默认启用。<b>集成测试可关掉</b>:它每 60 秒跑一次,会在测试断言任务仍是
     * {@code pending} 的时候把它挪走 —— 这类干扰很难在失败信息里看出来。
     */
    private boolean compensationEnabled = true;

    /**
     * 受理前是否要求"生成队列上有消费者"。
     *
     * <p>默认要求。没有消费端时<b>当场返回 40002</b>,而不是受理之后让用户
     * 盯着一个进度永远为 0 的转圈看三到四分钟(见
     * {@link GenerationConsumerProbe})。
     *
     * <p>⚠️ <b>集成测试必须关掉它</b>:跑测试的机器上<b>没有 Python 消费端</b>,
     * consumerCount 恒为 0,不关的话凡是要调用 {@code POST /generate} 的用例会整批红。
     * 手法与 {@code compensationEnabled} 完全一样。
     */
    private boolean requireConsumer = true;

    public Duration getPendingStuckAfter() {
        return pendingStuckAfter;
    }

    public void setPendingStuckAfter(Duration pendingStuckAfter) {
        this.pendingStuckAfter = pendingStuckAfter;
    }

    public Duration getRunningStuckAfter() {
        return runningStuckAfter;
    }

    public void setRunningStuckAfter(Duration runningStuckAfter) {
        this.runningStuckAfter = runningStuckAfter;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public Duration getHardDeadline() {
        return hardDeadline;
    }

    public void setHardDeadline(Duration hardDeadline) {
        this.hardDeadline = hardDeadline;
    }

    public boolean isCompensationEnabled() {
        return compensationEnabled;
    }

    public void setCompensationEnabled(boolean compensationEnabled) {
        this.compensationEnabled = compensationEnabled;
    }

    public boolean isRequireConsumer() {
        return requireConsumer;
    }

    public void setRequireConsumer(boolean requireConsumer) {
        this.requireConsumer = requireConsumer;
    }
}
