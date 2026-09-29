package com.tileai.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * C 端注册 / 登录 / 改密的请求体。
 *
 * <p>对应契约 {@code openapi-c.yaml} 的 {@code /auth/register}、{@code /auth/login}、
 * {@code /auth/password}。
 *
 * <p><b>密码长度为什么不在这里用 {@code @Size} 校验</b>:契约给这几个接口声明的
 * 密码格式错误码是 <b>20004</b>(密码格式不合规),而 {@code @Size} 失败会走
 * 「参数校验失败 10001」。两者语义不同 —— 10001 是"请求本身不合法",
 * 20004 是"这个字段内容不合业务规则"。所以长度校验放在服务层抛 20004;
 * 这里只用 {@code @NotBlank} 拦住"压根没传"的情况。
 */
public final class AuthRequests {

    private AuthRequests() {
    }

    /** 注册。契约 {@code required: [username, password]}。 */
    public record Register(
            @NotBlank(message = "用户名不能为空")
            @Size(max = 50, message = "用户名不能超过 50 字符")
            String username,

            @NotBlank(message = "密码不能为空")
            String password) {
    }

    /** 登录。契约 {@code required: [username, password]}。 */
    public record Login(
            @NotBlank(message = "用户名不能为空")
            String username,

            @NotBlank(message = "密码不能为空")
            String password) {
    }

    /** 修改密码。契约 {@code required: [oldPassword, newPassword]}。 */
    public record ChangePassword(
            @NotBlank(message = "原密码不能为空")
            String oldPassword,

            @NotBlank(message = "新密码不能为空")
            String newPassword) {
    }
}
