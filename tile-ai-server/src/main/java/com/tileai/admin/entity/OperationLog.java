package com.tileai.admin.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 后台操作日志(架构 §8.1 {@code t_operation_log};§4.4:所有写操作记入)。
 *
 * <p>本表<b>只增不改不删</b> —— 审计记录一旦写下就是事实。
 * 因此没有 {@code updated_at}(§8.1 也只为它列了 {@code created_at})。
 */
@TableName("t_operation_log")
public class OperationLog {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 操作人 —— {@code t_admin_user.id}。 */
    private Long adminId;

    /** 动作标识,如 {@code product.delete}。 */
    private String action;

    /** 目标资源类型,如 {@code product}。 */
    private String targetType;

    /** 目标资源 ID;无具体目标时为 null。 */
    private String targetId;

    /** 详情 JSON。**不得写入请求体原文** —— 可能含密码等敏感字段。 */
    private String detail;

    private String ip;

    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getAdminId() {
        return adminId;
    }

    public void setAdminId(Long adminId) {
        this.adminId = adminId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getTargetType() {
        return targetType;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    public String getTargetId() {
        return targetId;
    }

    public void setTargetId(String targetId) {
        this.targetId = targetId;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
