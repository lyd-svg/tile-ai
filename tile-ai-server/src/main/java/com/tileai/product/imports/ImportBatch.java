package com.tileai.product.imports;

import java.time.LocalDateTime;
import java.util.List;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;

/**
 * 导入批次(架构 §8.7 {@code t_import_batch};表定义于架构 v1.3,迁移 {@code V7})。
 *
 * <p>字段与契约 {@code ImportStatus} 一一对应:
 * {@code batchId / status / total / successCount / failedCount / errors / errorReportUrl}。
 */
@TableName(value = "t_import_batch", autoResultMap = true)
public class ImportBatch {

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_RUNNING = "running";
    public static final String STATUS_SUCCESS = "success";
    public static final String STATUS_FAILED = "failed";

    /** {@code biz_type}:imported 的对象类型。一期只有产品库,其余装配数据后续复用同一张表。 */
    public static final String BIZ_PRODUCT = "product";

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 对外唯一标识,即契约里的 {@code batchId}。 */
    private String batchId;

    private String bizType;

    private String mode;

    private String status;

    private Integer totalCount;

    private Integer successCount;

    private Integer failedCount;

    /** 错误行明细,元素 {@code {row, reason}}(§8.7:错误报告含行号 + 原因)。 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<ImportRowError> errors;

    /** 错误报告下载地址;对象存储未定(P0-4)前为空,明细由 {@code errors} 直接返回。 */
    private String errorReportUrl;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getBatchId() {
        return batchId;
    }

    public void setBatchId(String batchId) {
        this.batchId = batchId;
    }

    public String getBizType() {
        return bizType;
    }

    public void setBizType(String bizType) {
        this.bizType = bizType;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(Integer totalCount) {
        this.totalCount = totalCount;
    }

    public Integer getSuccessCount() {
        return successCount;
    }

    public void setSuccessCount(Integer successCount) {
        this.successCount = successCount;
    }

    public Integer getFailedCount() {
        return failedCount;
    }

    public void setFailedCount(Integer failedCount) {
        this.failedCount = failedCount;
    }

    public List<ImportRowError> getErrors() {
        return errors;
    }

    public void setErrors(List<ImportRowError> errors) {
        this.errors = errors;
    }

    public String getErrorReportUrl() {
        return errorReportUrl;
    }

    public void setErrorReportUrl(String errorReportUrl) {
        this.errorReportUrl = errorReportUrl;
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
