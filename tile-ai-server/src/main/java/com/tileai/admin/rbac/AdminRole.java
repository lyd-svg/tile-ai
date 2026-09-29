package com.tileai.admin.rbac;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 后台角色及其权限集合(架构 §4.4)。
 *
 * <p><b>本类就是 §4.4 那张「权限点 → 角色」对照表的代码化</b>,两者必须逐格一致 ——
 * 由 {@code AdminRoleMatrixTest} 断言。改动这里等同于改架构文档;
 * 顺序是先改 §4.4,再改本类(CLAUDE.md:改接口的顺序不可颠倒)。
 *
 * <p>该表存在的意义:§4.4 原先只有散文式描述,结果契约写 `quote-param:write`
 * 「仅 super_admin/operator」,而正文写 operator「不可管理报价参数」——
 * 两处直接矛盾,做 RBAC 时无从下手。显式对照表把这类歧义消灭在文档层。
 */
public enum AdminRole {

    /** 超级管理员 —— 全部权限(契约 §4.4:拥有 {@code *})。 */
    SUPER_ADMIN("super_admin", EnumSet.allOf(Permission.class)),

    /** 运营 —— 日常业务与配置维护;不含报价参数与账号。 */
    OPERATOR("operator", EnumSet.of(
            Permission.PRODUCT_READ,
            Permission.PRODUCT_WRITE,
            Permission.PRODUCT_IMPORT,
            Permission.PRODUCT_DELETE,
            Permission.PRODUCT_PUBLISH,
            Permission.COMBO_READ,
            Permission.COMBO_WRITE,
            Permission.COMBO_DELETE,
            Permission.TEMPLATE_READ,
            Permission.TEMPLATE_WRITE,
            Permission.TEMPLATE_DELETE,
            Permission.FURNISH_READ,
            Permission.FURNISH_WRITE,
            Permission.FURNISH_DELETE,
            Permission.STORE_READ,
            Permission.STORE_WRITE,
            Permission.DICT_READ,
            Permission.DICT_WRITE,
            Permission.LOG_READ)),

    /**
     * 内容编辑 —— 仅产品库与搭配的创建/编辑,外加字典只读。
     *
     * <p>边界(§4.4):不可删除、不可上下架、不可批量导入、不可改报价参数与账号;
     * 不含户型模板 / 陈设 / 门店。
     */
    EDITOR("editor", EnumSet.of(
            Permission.PRODUCT_READ,
            Permission.PRODUCT_WRITE,
            Permission.COMBO_READ,
            Permission.COMBO_WRITE,
            Permission.DICT_READ));

    private static final Map<String, AdminRole> BY_CODE = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(AdminRole::code, r -> r));

    private final String code;
    private final Set<Permission> permissions;

    AdminRole(String code, Set<Permission> permissions) {
        this.code = code;
        this.permissions = permissions;
    }

    /** 数据库 {@code t_admin_user.role} 中存储的值。 */
    public String code() {
        return code;
    }

    public Set<Permission> permissions() {
        return EnumSet.copyOf(permissions);
    }

    public boolean has(Permission permission) {
        return permissions.contains(permission);
    }

    /**
     * 按 code 反查。
     *
     * <p>未知角色返回 {@code null} —— <b>调用方必须按"无权限"处理(失败关闭)</b>,
     * 绝不能悄悄放行。数据库里出现意外角色值时,宁可挡住也不能放过去。
     */
    public static AdminRole of(String code) {
        return code == null ? null : BY_CODE.get(code);
    }
}
