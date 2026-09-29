package com.tileai.admin.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 后台账号(架构 §8.1 {@code t_admin_user})。
 *
 * <p>字段与 §8.1 的逐字段清单一致。属性名 camelCase、列名 snake_case,
 * 映射由 MyBatis-Plus 的命名策略完成(CLAUDE.md 命名约定)。
 *
 * <p><b>本类绝不返回给前端</b> —— 它含 {@code passwordHash}。
 * 对外一律用 {@code AdminUserView}(契约 {@code AdminUserView},不含密码)。
 */
@TableName("t_admin_user")
public class AdminUser {

    /** 账号状态取值,与契约 {@code AdminUserView.status} 的 enum 一致。 */
    public static final String STATUS_ENABLED = "enabled";
    public static final String STATUS_DISABLED = "disabled";

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String username;

    /** BCrypt 密文。任何日志、响应体、异常消息中都不得出现。 */
    private String passwordHash;

    /** 角色 code,对应 {@code AdminRole} 的取值。 */
    private String role;

    private String status;

    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    /** 供日志使用 —— 刻意不覆盖 toString 输出密码。 */
    @Override
    public String toString() {
        return "AdminUser{id=" + id + ", username='" + username + "', role='" + role
                + "', status='" + status + "'}";
    }
}
