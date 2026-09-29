package com.tileai.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.tileai.auth.entity.User;

/**
 * 登录用户信息的对外视图。
 *
 * <p>契约:{@code openapi-common.yaml#/components/schemas/UserInfo},
 * {@code required: [userId, username]}。
 *
 * <p><b>单独建视图而不是直接返回实体</b>:实体含 {@code passwordHash}。
 * 靠"记得加 @JsonIgnore"来防泄露太脆弱 —— 漏一次就是明文凭据外泄。
 *
 * <p>{@code nickname} 未设置时整个省略而非输出 {@code null}:契约把它标为 nullable,
 * 但省略更稳妥(T-0.9 的 schema 校验已证明:声明为 string 又没标 nullable 的字段
 * 收到显式 null 会被判为类型不符)。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserView(Long userId, String username, String nickname) {

    public static UserView of(User user) {
        return new UserView(user.getId(), user.getUsername(), user.getNickname());
    }
}
