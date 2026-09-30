package com.tileai.generate.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 生成 / 解析任务(架构 §8.6 {@code t_generation_task};契约 {@code GET /generate/{taskId}})。
 *
 * <p>状态机恒为 {@code pending → running → success / failed}。{@code failed} 不是终态 ——
 * 补偿扫描重投后可以回到 {@code running}(架构 §6.4 的可重试语义)。
 *
 * <p><b>{@code taskId} 是幂等键</b>(对外唯一,库里有 {@code uk_task_task_id}):
 * 回调可能重复到达,所有状态更新都以它为准并带条件(见
 * {@code generate.GenerateCallbackService})。主键 {@code id} 不对外。
 *
 * <p><b>{@code payload} 存的是入参快照,原样往返</b>(列类型 JSON)。这里刻意声明为
 * {@code String} 而不是映射成一个对象 —— 与 {@code t_outbox.payload} 同一个理由:
 * "落库时的数据"与"读出来时的数据"必须是同一份,中途多做一次序列化/反序列化只会
 * 多一处可能对不上的地方。需要结构化读取时由调用方自己用 ObjectMapper 解。
 *
 * <p><b>没有 {@code @TableField} 标注时间戳</b>:{@code created_at} / {@code updated_at}
 * 都由 MySQL 默认值维护。{@code updated_at} 带 {@code ON UPDATE},因此每次状态或进度
 * 变更都会刷新它 —— 这正是补偿扫描能靠它判断"任务是否还活着"的前提。
 */
@TableName("t_generation_task")
public class GenerationTask {

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_RUNNING = "running";
    public static final String STATUS_SUCCESS = "success";
    public static final String STATUS_FAILED = "failed";

    /** 效果图生成。 */
    public static final String TYPE_IMAGE = "image";

    /** 户型解析(本任务不用,列在枚举里以示完整——契约 {@code TaskType})。 */
    public static final String TYPE_PARSE = "parse";

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 对外唯一任务号,幂等去重键。 */
    private String taskId;

    private Long userId;

    /** 覆盖原方案时携带;新建方案时为空,成功回调里回填新方案 id。 */
    private Long planId;

    private String type;

    private String status;

    /** 0–100,一期前端每 2s 轮询。 */
    private Integer progress;

    /** 入参快照(契约 {@code GenerateTaskPayload} 的 JSON),原样往返。 */
    private String payload;

    /** 结果引用:成功时存封面图 URL(图片类任务的"代表作")。 */
    private String resultRef;

    /** 失败原因。长度上限 1000,写入前需截断。 */
    private String error;

    /** 第三方模型侧任务号(本期替身不产生,留给真模型)。 */
    private String externalTaskId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getPlanId() {
        return planId;
    }

    public void setPlanId(Long planId) {
        this.planId = planId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getProgress() {
        return progress;
    }

    public void setProgress(Integer progress) {
        this.progress = progress;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public String getResultRef() {
        return resultRef;
    }

    public void setResultRef(String resultRef) {
        this.resultRef = resultRef;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String getExternalTaskId() {
        return externalTaskId;
    }

    public void setExternalTaskId(String externalTaskId) {
        this.externalTaskId = externalTaskId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
