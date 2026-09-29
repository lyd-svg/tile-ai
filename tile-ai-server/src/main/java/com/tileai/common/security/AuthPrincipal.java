package com.tileai.common.security;

/**
 * 已认证主体,放进 Spring Security 的 {@code Authentication}。
 *
 * <p>刻意<b>只带身份</b>(id + 类型),不带角色/权限 —— 角色在 T-0.5 的 RBAC 里按请求
 * 现查(带缓存)。原因:若把角色写进令牌,后台管理员被降权后,其旧令牌会在最长
 * 2 小时内继续按旧权限放行。权限判断读的是"当下的事实",不是"签发时的快照"。
 *
 * <p>v0.7 起登录改为「用户名 + 密码」,注册与登录各是一次请求,**不再有临时令牌** ——
 * 因此本类只需区分"业主还是后台账号",不再需要令牌种类。
 *
 * @param id   t_user.id 或 t_admin_user.id,取决于 {@code type}
 * @param type 主体类型
 */
public record AuthPrincipal(long id, SubjectType type) {

    public static AuthPrincipal user(long userId) {
        return new AuthPrincipal(userId, SubjectType.USER);
    }

    public static AuthPrincipal admin(long adminId) {
        return new AuthPrincipal(adminId, SubjectType.ADMIN);
    }
}
