package com.tileai.admin.auth;

import jakarta.validation.constraints.NotBlank;

/**
 * 后台登录请求体。
 *
 * <p>对应契约 {@code openapi-admin.yaml} {@code POST /admin/auth/login} 的
 * {@code required: [username, password]}。
 *
 * <p>刻意<b>不</b>加长度/格式约束:契约未规定,加了就会拒掉契约认为合法的请求。
 * 防超大报文由容器层的请求体上限负责。
 */
public record AdminLoginRequest(@NotBlank String username, @NotBlank String password) {
}
