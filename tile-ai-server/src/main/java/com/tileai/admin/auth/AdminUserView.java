package com.tileai.admin.auth;

import java.time.LocalDateTime;

import com.tileai.admin.entity.AdminUser;

/**
 * 后台账号的对外视图。
 *
 * <p>对应契约 {@code openapi-common.yaml#/components/schemas/AdminUserView},
 * {@code required: [id, username, role]}。
 *
 * <p><b>单独建一个视图而不是直接返回实体</b>:实体含 {@code passwordHash}。
 * 靠"记得加 @JsonIgnore"来防泄露太脆弱 —— 漏一次就是明文凭据外泄。
 * 用白名单式的视图类,新增字段时默认不会被带出去。
 */
public record AdminUserView(Long id, String username, String role, String status,
                            LocalDateTime createdAt) {

    public static AdminUserView of(AdminUser admin) {
        return new AdminUserView(admin.getId(), admin.getUsername(), admin.getRole(),
                admin.getStatus(), admin.getCreatedAt());
    }
}
