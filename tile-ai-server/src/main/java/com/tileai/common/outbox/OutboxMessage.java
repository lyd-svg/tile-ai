package com.tileai.common.outbox;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 发件箱记录(架构 §8.6 {@code t_outbox};§6.4 Outbox 模式)。
 *
 * <p><b>它解决的是"先落库、再投 MQ"会丢消息的问题</b>:业务数据与待发消息在
 * <b>同一个数据库事务</b>内写入,要么都成功要么都回滚;再由独立投递器扫描并投递。
 * 这样 MQ 短暂不可用不会丢事件 —— 消息已经落在库里了。
 *
 * <p>{@code payload} 存的就是要投递的消息体,投递器原样发出,不再重新构造 ——
 * 避免"落库时的数据"与"投递时的数据"不一致。
 */
@TableName("t_outbox")
public class OutboxMessage {

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_SENT = "sent";
    public static final String STATUS_FAILED = "failed";

    /** §8.6 的 aggregate_type 取值:生成任务 / 索引同步。 */
    public static final String AGGREGATE_TASK = "task";
    public static final String AGGREGATE_INDEX_SYNC = "index_sync";

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String aggregateType;

    /** 业务主键:索引同步时是事件 id(eventId),生成任务时是 taskId。 */
    private String aggregateId;

    /** 目标队列,如 {@code queue.index.sync};同时用作路由键。 */
    private String queue;

    /** 消息体 JSON,投递时原样发出。 */
    private String payload;

    private String status;

    private Integer retryCount;

    /** 下次可投递时间;指数退避靠它实现。 */
    private LocalDateTime nextRetryAt;

    private LocalDateTime createdAt;

    private LocalDateTime sentAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public void setAggregateType(String aggregateType) {
        this.aggregateType = aggregateType;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public void setAggregateId(String aggregateId) {
        this.aggregateId = aggregateId;
    }

    public String getQueue() {
        return queue;
    }

    public void setQueue(String queue) {
        this.queue = queue;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    public LocalDateTime getNextRetryAt() {
        return nextRetryAt;
    }

    public void setNextRetryAt(LocalDateTime nextRetryAt) {
        this.nextRetryAt = nextRetryAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public void setSentAt(LocalDateTime sentAt) {
        this.sentAt = sentAt;
    }
}
