package com.tileai.common.security;

/**
 * 令牌主体类型,写入 JWT 的 {@code styp} 声明。
 *
 * <p>C 端业主与 B 端后台账号共用同一套 Cookie 鉴权方案(契约 {@code cookieAuth} 同时
 * 标在 c / admin 两份契约上),但两者的身份体系是分开的两张表
 * ({@code t_user} / {@code t_admin_user})。<b>必须区分</b>:否则一个业主的令牌
 * 就能拿去访问后台接口,反之亦然。
 */
public enum SubjectType {

    /** C 端业主,对应 {@code t_user}。 */
    USER,

    /**
     * B 端后台账号,对应 {@code t_admin_user}。
     * <p>具体角色(super_admin / operator / editor)与其权限点由 T-0.5 的 RBAC 承载,
     * 不写进令牌 —— 角色变更后旧令牌不该继续按旧权限生效。
     */
    ADMIN
}
