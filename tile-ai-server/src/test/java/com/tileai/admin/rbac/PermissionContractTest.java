package com.tileai.admin.rbac;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.tileai.RepoDocs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 把 {@link Permission} 与契约 {@code openapi-admin.yaml} 中标注的权限点<b>逐项绑住</b>。
 *
 * <p>这是 T-12(契约 ↔ 实现)在 RBAC 维度的切片:契约里每个后台接口都写了
 * "权限点 xxx",实现侧必须有对应的枚举常量。<b>契约新增一个权限点而代码没跟上</b>,
 * 或者<b>代码里留着一个契约已删除的权限点</b>,这个测试都会红。
 *
 * <p>不依赖 Spring、不依赖数据库。
 */
class PermissionContractTest {

    /**
     * 契约中以"权限点 xxx"形式标注的位置。
     * 例:{@code 权限点 product:read。§10.3}、{@code 权限点 quote-param:write(**仅 super_admin**)}。
     */
    private static final Pattern PERMISSION_IN_CONTRACT =
            Pattern.compile("权限点\\s*([a-z][a-z-]*:[a-z][a-z-]*)");

    @Test
    @DisplayName("契约里出现的权限点与 Permission 枚举完全一致")
    void enumMatchesContract() {
        Set<String> fromContract = permissionsInContract();
        Set<String> fromEnum = Arrays.stream(Permission.values())
                .map(Permission::code)
                .collect(Collectors.toCollection(TreeSet::new));

        assertTrue(fromContract.size() >= 20,
                "从契约里只解析出 " + fromContract.size() + " 个权限点,解析规则可能已失效: " + fromContract);

        assertEquals(fromContract, fromEnum,
                "实现与契约的权限点集合不一致 —— 契约独有: " + diff(fromContract, fromEnum)
                        + ";实现独有: " + diff(fromEnum, fromContract));
    }

    @Test
    @DisplayName("权限点命名符合 <资源>:<动作> 约定")
    void codesFollowNamingConvention() {
        for (Permission permission : Permission.values()) {
            assertTrue(permission.code().matches("[a-z][a-z-]*:[a-z][a-z-]*"),
                    "权限点命名不符 <资源>:<动作> 约定: " + permission.code());
        }
    }

    @Test
    @DisplayName("Permission.of 能反查,未知值返回 null(由调用方失败关闭)")
    void ofResolvesKnownCodes() {
        assertEquals(Permission.PRODUCT_WRITE, Permission.of("product:write"));
        assertEquals(Permission.QUOTE_PARAM_WRITE, Permission.of("quote-param:write"));
        assertEquals(null, Permission.of("no-such:permission"));
        assertEquals(null, Permission.of(null));
    }

    private static Set<String> permissionsInContract() {
        String admin = RepoDocs.text("openapi-admin.yaml");
        Matcher matcher = PERMISSION_IN_CONTRACT.matcher(admin);
        Set<String> found = new LinkedHashSet<>();
        while (matcher.find()) {
            found.add(matcher.group(1));
        }
        return new TreeSet<>(found);
    }

    private static Set<String> diff(Set<String> a, Set<String> b) {
        Set<String> out = new LinkedHashSet<>(a);
        out.removeAll(b);
        return out;
    }
}
