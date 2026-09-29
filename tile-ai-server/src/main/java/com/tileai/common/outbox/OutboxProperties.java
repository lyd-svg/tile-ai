package com.tileai.common.outbox;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 发件箱投递配置(架构 §6.4)。
 */
@ConfigurationProperties(prefix = "tile.outbox")
public class OutboxProperties {

    /**
     * 是否启用投递器。
     *
     * <p>默认启用。个别测试(如纯 Web 切片的集成测试)可关掉,避免在没起 MQ 的
     * 环境里每 2 秒刷一次投递失败日志。
     */
    private boolean enabled = true;

    /** 每轮最多处理多少条,避免一次扫描把内存与 MQ 打满。 */
    private int batchSize = 100;

    /** 超过这个重试次数就标记 failed 并停止(由全量重建兜底)。 */
    private int maxRetry = 10;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public int getMaxRetry() {
        return maxRetry;
    }

    public void setMaxRetry(int maxRetry) {
        this.maxRetry = maxRetry;
    }
}
