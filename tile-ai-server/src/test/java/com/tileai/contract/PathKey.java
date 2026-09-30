package com.tileai.contract;

/**
 * (HTTP 方法, 归一化路径) —— 两侧端点互相匹配用的键。
 *
 * <p>归一化做三件事:① 路径变量名抹平({@code /products/{id}} 与 {@code /products/{sku}}
 * 视为同一条);② 折叠重复斜杠;③ 去掉尾部斜杠。
 *
 * <p><b>①是刻意的取舍</b>:契约里叫 {@code {id} }、实现里叫 {@code {sku}},这属于"名字不同、
 * 位置相同",不是分叉 —— 路径变量名不在本次比对范围内。若哪天真的改了<b>位置</b>
 * (如 {@code /products/{id}/images}),归一化后仍然不同,照样能红。
 */
record PathKey(String method, String path) {

    static PathKey of(String method, String path) {
        return new PathKey(method.toUpperCase(), normalize(path));
    }

    static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return "/";
        }
        String path = raw.trim().replaceAll("\\{([^}]*)}", "{}");
        path = path.replaceAll("/{2,}", "/");
        if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path.startsWith("/") ? path : "/" + path;
    }

    @Override
    public String toString() {
        return method + " " + path;
    }
}
