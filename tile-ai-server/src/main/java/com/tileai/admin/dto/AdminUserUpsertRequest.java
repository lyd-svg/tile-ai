package com.tileai.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 新增 / 编辑后台账号的请求体。
 *
 * <p>契约:{@code openapi-admin.yaml#/components/schemas/AdminUserUpsert},
 * {@code required: [username, role]}。
 *
 * <p><b>为什么 {@code password} 上一个校验注解都没有</b>:它的必填性**取决于场景** ——
 * 新增时必填、编辑时留空表示"不修改"。一个 DTO 要同时服务两种语义,就不能在这里
 * 用 {@code @NotBlank} 卡死;长度也同理,留空时要跳过。
 *
 * <p>所以密码的校验放在服务层,且**复用 C 端那套**({@code AuthService.validatePasswordFormat})——
 * 规则只有一份,失败码是契约声明的 <b>20004</b>(不是 10001)。
 */
public record AdminUserUpsertRequest(

        @NotBlank(message = "用户名不能为空")
        @Size(max = 50, message = "用户名不能超过 50 字符")
        String username,

        /** 新增:必填;编辑:**留空或不传 = 不修改密码**。 */
        String password,

        @NotBlank(message = "角色不能为空")
        @Pattern(regexp = "super_admin|operator|editor",
                message = "角色只能是 super_admin / operator / editor")
        String role,

        /** 省略时:新建默认 `enabled`;编辑保持原状态不变。 */
        @Pattern(regexp = "enabled|disabled", message = "状态只能是 enabled 或 disabled")
        String status) {
}
