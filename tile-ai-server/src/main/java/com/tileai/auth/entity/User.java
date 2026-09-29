package com.tileai.auth.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * C 端用户(架构 §8.1 {@code t_user})。
 *
 * <p>登录标识是 <b>{@code username}</b>(唯一)—— v0.7 起由手机号改为用户名,
 * 见 PRD §5.1 与架构 §15.6。
 *
 * <p><b>本类绝不返回给前端</b> —— 它含 {@code passwordHash}。
 * 对外一律用 {@code UserView}(契约 {@code UserInfo},不含密码)。
 * 与后台的 {@code AdminUser} 是同一套做法:用白名单式的视图类,
 * 新增字段时默认不会被带出去。
 */
@TableName("t_user")
public class User {

    public static final String STATUS_ENABLED = "enabled";
    public static final String STATUS_DISABLED = "disabled";

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String username;

    /** BCrypt 密文。任何日志、响应体、异常消息中都不得出现。 */
    private String passwordHash;

    private String nickname;

    private String status;

    private LocalDateTime lastLoginAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

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

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(LocalDateTime lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
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

    /** 供日志使用 —— 刻意不输出密码哈希。 */
    @Override
    public String toString() {
        return "User{id=" + id + ", username='" + username + "', status='" + status + "'}";
    }
}
