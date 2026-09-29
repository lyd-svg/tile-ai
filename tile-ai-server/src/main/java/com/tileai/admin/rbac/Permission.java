package com.tileai.admin.rbac;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 后台权限点(架构 §4.4)。
 *
 * <p>取值集合<b>必须</b>与契约 {@code openapi-admin.yaml} 中每个接口
 * {@code description} 里标注的"权限点 xxx"完全一致 —— 由
 * {@code PermissionContractTest} 直接解析契约 YAML 逐项比对。
 *
 * <p><b>角色 → 权限的映射不在这里</b>,在 {@link AdminRole}。
 * 契约只声明"某接口需要哪个权限点",不声明"哪个角色有该权限" ——
 * 后者以架构 §4.4 的「权限点 → 角色」对照表为唯一权威来源。
 */
public enum Permission {

    // ---- 产品库 ----
    PRODUCT_READ("product:read"),
    PRODUCT_WRITE("product:write"),
    PRODUCT_IMPORT("product:import"),
    PRODUCT_DELETE("product:delete"),
    PRODUCT_PUBLISH("product:publish"),

    // ---- 推荐搭配 ----
    COMBO_READ("combo:read"),
    COMBO_WRITE("combo:write"),
    COMBO_DELETE("combo:delete"),

    // ---- 户型模板 ----
    TEMPLATE_READ("template:read"),
    TEMPLATE_WRITE("template:write"),
    TEMPLATE_DELETE("template:delete"),

    // ---- 陈设风格 ----
    FURNISH_READ("furnish:read"),
    FURNISH_WRITE("furnish:write"),
    FURNISH_DELETE("furnish:delete"),

    // ---- 报价参数(仅 super_admin,见 §4.4)----
    QUOTE_PARAM_READ("quote-param:read"),
    QUOTE_PARAM_WRITE("quote-param:write"),

    // ---- 门店 ----
    STORE_READ("store:read"),
    STORE_WRITE("store:write"),

    // ---- 数据字典 ----
    DICT_READ("dict:read"),
    DICT_WRITE("dict:write"),

    // ---- 账号与日志 ----
    ACCOUNT_MANAGE("account:manage"),
    LOG_READ("log:read");

    private static final Map<String, Permission> BY_CODE = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(Permission::code, p -> p));

    private final String code;

    Permission(String code) {
        this.code = code;
    }

    /** 契约中书写的形式,如 {@code product:write}。 */
    public String code() {
        return code;
    }

    /**
     * 按 code 反查;未知返回 {@code null}(由调用方决定如何处理,不要吞掉)。
     *
     * <p>必须先判 null:{@code Collectors.toUnmodifiableMap} 产出的 Map
     * <b>连查询 null 键都会抛 NPE</b>(不像 HashMap 那样返回 null),
     * 直接 {@code BY_CODE.get(code)} 会让"传了个 null"变成 500 而不是可处理的返回值。
     */
    public static Permission of(String code) {
        return code == null ? null : BY_CODE.get(code);
    }
}
