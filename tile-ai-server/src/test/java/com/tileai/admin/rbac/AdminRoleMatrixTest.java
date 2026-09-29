package com.tileai.admin.rbac;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.tileai.RepoDocs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 把 {@link AdminRole} 的权限集合与架构 §4.4 的「权限点 → 角色」对照表<b>逐格绑住</b>。
 *
 * <p>这张表存在的理由就是消灭歧义 —— 所以它绝不能在代码里被"重新解释"一遍。
 * 本测试直接解析 {@code tile-ai-architecture.md} §4.4 的 Markdown 表格,
 * 与枚举逐格比对:<b>文档改一格而代码没跟,或者代码私自改了权限,都会红。</b>
 *
 * <p>不依赖 Spring、不依赖数据库。
 */
class AdminRoleMatrixTest {

    /** 形如 {@code | `product:read` | ✓ | ✓ | ✓ |}。 */
    private static final Pattern MATRIX_ROW = Pattern.compile(
            "^\\|\\s*`([a-z][a-z-]*:[a-z][a-z-]*)`\\s*\\|\\s*([✓—])\\s*\\|\\s*([✓—])\\s*\\|\\s*([✓—])\\s*\\|\\s*$");

    private static final int COL_SUPER_ADMIN = 2;
    private static final int COL_OPERATOR = 3;
    private static final int COL_EDITOR = 4;

    @Test
    @DisplayName("三个角色的权限集合与架构 §4.4 对照表逐格一致")
    void roleMatrixMatchesArchitecture() {
        Map<Permission, boolean[]> expected = parseMatrixFromArchitecture();

        assertFalse(expected.isEmpty(),
                "未从架构 §4.4 解析到权限矩阵 —— 表格格式可能已改,请同步本测试的解析规则");
        assertEquals(Permission.values().length, expected.size(),
                "§4.4 对照表的行数应与 Permission 枚举数量一致;不一致说明两边有遗漏");

        for (Map.Entry<Permission, boolean[]> entry : expected.entrySet()) {
            Permission permission = entry.getKey();
            boolean[] cells = entry.getValue();

            assertRoleHas(AdminRole.SUPER_ADMIN, permission, cells[0]);
            assertRoleHas(AdminRole.OPERATOR, permission, cells[1]);
            assertRoleHas(AdminRole.EDITOR, permission, cells[2]);
        }
    }

    @Test
    @DisplayName("几处曾产生歧义的边界,单独钉死")
    void knownAmbiguousBoundariesArePinned() {
        // 报价参数:三方曾经互相矛盾(契约写"仅 super_admin/operator",§4.4 正文写 operator 不可管理)。
        // 2026-09-29 裁决为「仅 super_admin」—— 读写都不给 operator。
        assertTrue(AdminRole.SUPER_ADMIN.has(Permission.QUOTE_PARAM_WRITE));
        assertTrue(AdminRole.SUPER_ADMIN.has(Permission.QUOTE_PARAM_READ));
        assertFalse(AdminRole.OPERATOR.has(Permission.QUOTE_PARAM_WRITE),
                "operator 不得改报价参数 —— 它直接决定所有报价金额");
        assertFalse(AdminRole.OPERATOR.has(Permission.QUOTE_PARAM_READ),
                "operator 连读报价参数都不行(§4.4「不可管理」含读)");
        assertFalse(AdminRole.EDITOR.has(Permission.QUOTE_PARAM_WRITE));

        // 批量导入:upsert 能成批覆盖既有 SKU,只给 operator
        assertTrue(AdminRole.OPERATOR.has(Permission.PRODUCT_IMPORT));
        assertFalse(AdminRole.EDITOR.has(Permission.PRODUCT_IMPORT),
                "editor 不得批量导入 —— 影响面远大于单条编辑");

        // 字典:operator 可写,editor 只读
        assertTrue(AdminRole.OPERATOR.has(Permission.DICT_WRITE));
        assertTrue(AdminRole.EDITOR.has(Permission.DICT_READ));
        assertFalse(AdminRole.EDITOR.has(Permission.DICT_WRITE));

        // editor 的职权边界:可创建但不可删除、不可上下架
        assertTrue(AdminRole.EDITOR.has(Permission.PRODUCT_WRITE));
        assertFalse(AdminRole.EDITOR.has(Permission.PRODUCT_DELETE));
        assertFalse(AdminRole.EDITOR.has(Permission.PRODUCT_PUBLISH));

        // editor 不含户型模板 / 陈设 / 门店 / 日志 / 账号
        assertFalse(AdminRole.EDITOR.has(Permission.TEMPLATE_READ));
        assertFalse(AdminRole.EDITOR.has(Permission.FURNISH_READ));
        assertFalse(AdminRole.EDITOR.has(Permission.STORE_READ));
        assertFalse(AdminRole.EDITOR.has(Permission.LOG_READ));
        assertFalse(AdminRole.EDITOR.has(Permission.ACCOUNT_MANAGE));
    }

    @Test
    @DisplayName("super_admin 拥有全部权限(契约 §4.4:拥有 *)")
    void superAdminHasEverything() {
        assertEquals(EnumSet.allOf(Permission.class), AdminRole.SUPER_ADMIN.permissions());
    }

    @Test
    @DisplayName("未知角色返回 null —— 调用方必须失败关闭,不能放行")
    void unknownRoleReturnsNull() {
        assertEquals(null, AdminRole.of("some_new_role"));
        assertEquals(null, AdminRole.of(null));
        assertEquals(AdminRole.OPERATOR, AdminRole.of("operator"));
    }

    @Test
    @DisplayName("permissions() 返回副本,外部改不动内部集合")
    void permissionsIsDefensiveCopy() {
        Set<Permission> copy = AdminRole.EDITOR.permissions();
        copy.clear();
        assertTrue(AdminRole.EDITOR.has(Permission.PRODUCT_READ),
                "角色内部权限集合被外部清空了 —— permissions() 必须返回副本");
    }

    // ------------------------------------------------------------------

    private static void assertRoleHas(AdminRole role, Permission permission, boolean expected) {
        assertEquals(expected, role.has(permission),
                "§4.4 对照表中 " + role.code() + " 对 " + permission.code()
                        + " 的标记是 " + (expected ? "✓" : "—") + ",与实现不符");
    }

    /** 解析架构 §4.4 的「权限点 → 角色」表格。 */
    private static Map<Permission, boolean[]> parseMatrixFromArchitecture() {
        String architecture = RepoDocs.text("tile-ai-architecture.md");
        Map<Permission, boolean[]> matrix = new LinkedHashMap<>();
        Set<String> unknownCodes = new LinkedHashSet<>();

        for (String line : architecture.split("\n")) {
            Matcher matcher = MATRIX_ROW.matcher(line.strip());
            if (!matcher.matches()) {
                continue;
            }
            Permission permission = Permission.of(matcher.group(1));
            if (permission == null) {
                unknownCodes.add(matcher.group(1));
                continue;
            }
            matrix.put(permission, new boolean[]{
                    "✓".equals(matcher.group(COL_SUPER_ADMIN)),
                    "✓".equals(matcher.group(COL_OPERATOR)),
                    "✓".equals(matcher.group(COL_EDITOR))});
        }

        assertTrue(unknownCodes.isEmpty(),
                "§4.4 对照表里出现了 Permission 枚举没有的权限点: " + unknownCodes);
        return matrix;
    }
}
