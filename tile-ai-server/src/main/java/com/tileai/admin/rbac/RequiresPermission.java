package com.tileai.admin.rbac;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注后台接口所需的权限点,由 {@link AdminPermissionInterceptor} 强制。
 *
 * <p><b>为什么用注解而不是在 SecurityConfig 里配路径规则</b>:
 * 契约是<b>按接口</b>标注权限点的,路径规则会把同一份信息搬到另一个文件里,
 * 于是 T-0.6 起的每个后台接口都要记得去 SecurityConfig 补一行 ——
 * 漏了不会报错,只会静默放行。注解贴在方法上,看得见、删不掉。
 *
 * <p>放在方法上优先;放在类上作为该 Controller 的默认值。
 * 两者都没有 → 拦截器不干预(由 SecurityConfig 的 {@code ROLE_ADMIN} 兜底)。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequiresPermission {

    /** 该接口需要的权限点 —— 取值应与契约中该接口 {@code description} 的标注一致。 */
    Permission value();
}
