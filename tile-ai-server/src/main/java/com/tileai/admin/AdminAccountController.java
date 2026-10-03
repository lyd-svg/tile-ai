package com.tileai.admin;

import java.util.Map;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tileai.admin.audit.AdminOperation;
import com.tileai.admin.auth.AdminUserView;
import com.tileai.admin.dto.AdminUserUpsertRequest;
import com.tileai.admin.rbac.Permission;
import com.tileai.admin.rbac.RequiresPermission;
import com.tileai.common.api.ApiResponse;
import com.tileai.common.api.PageData;
import com.tileai.common.security.CurrentPrincipal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 后台账号与权限(契约 {@code openapi-admin.yaml} 的 accounts 分组;架构 §4.4、§8.1)。
 *
 * <pre>
 *   GET    /api/v1/admin/accounts        account:manage
 *   POST   /api/v1/admin/accounts        account:manage
 *   PUT    /api/v1/admin/accounts/{id}   account:manage
 *   DELETE /api/v1/admin/accounts/{id}   account:manage
 * </pre>
 *
 * <p><b>整组只有 super_admin 一个角色够得着</b>({@code account:manage} 只给了它),
 * 所以"运营能看日志、但碰不到账号"这件事是由权限点自己保证的,不靠前端藏按钮。
 *
 * <p><b>操作人一律取自当前会话</b>({@link CurrentPrincipal}),绝不从请求体里读 ——
 * 否则客户端可以自称是任何账号,防呆规则("不能删/停用自己")会被一句话绕过。
 *
 * <p><b>写接口都打了 {@code @AdminOperation}</b>,两个 GET 没打:
 * 读日志不该产生日志,否则查一次就多一条。
 *
 * <p>{@code @Validated} 是为了让 {@code @RequestParam} 上的 {@code @Min}/{@code @Max}
 * 生效(方法参数级校验);请求体上的 {@code @Valid} 由 Spring MVC 自己处理。
 * 两者最终都落到 10001。
 */
@RestController
@RequestMapping("/api/v1/admin/accounts")
@Validated
public class AdminAccountController {

    /** 契约 {@code PageParam} 的默认值。 */
    private static final String DEFAULT_PAGE = "1";
    /** 契约 {@code SizeParam} 的默认值;上限 100 也来自契约。 */
    private static final String DEFAULT_SIZE = "20";

    private final AdminAccountAdminService accountService;

    public AdminAccountController(AdminAccountAdminService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    @RequiresPermission(Permission.ACCOUNT_MANAGE)
    public ApiResponse<PageData<AdminUserView>> list(
            @RequestParam(defaultValue = DEFAULT_PAGE) @Min(value = 1, message = "页码从 1 开始") int page,
            @RequestParam(defaultValue = DEFAULT_SIZE) @Min(value = 1, message = "每页条数至少为 1")
            @Max(value = 100, message = "每页条数最多 100") int size) {
        return ApiResponse.ok(accountService.page(page, size));
    }

    @PostMapping
    @RequiresPermission(Permission.ACCOUNT_MANAGE)
    @AdminOperation(action = "account.create", targetType = "account")
    public ApiResponse<Map<String, Long>> create(@Valid @RequestBody AdminUserUpsertRequest request) {
        long id = accountService.create(request);
        // 契约声明响应 data 为 { id },不是账号全量
        return ApiResponse.ok(Map.of("id", id));
    }

    @PutMapping("/{id}")
    @RequiresPermission(Permission.ACCOUNT_MANAGE)
    @AdminOperation(action = "account.update", targetType = "account", targetIdParam = "id")
    public ApiResponse<Void> update(@PathVariable long id,
                                    @Valid @RequestBody AdminUserUpsertRequest request) {
        accountService.update(id, request, CurrentPrincipal.requireAdmin().id());
        return ApiResponse.ok();
    }

    @DeleteMapping("/{id}")
    @RequiresPermission(Permission.ACCOUNT_MANAGE)
    @AdminOperation(action = "account.delete", targetType = "account", targetIdParam = "id")
    public ApiResponse<Void> delete(@PathVariable long id) {
        accountService.delete(id, CurrentPrincipal.requireAdmin().id());
        return ApiResponse.ok();
    }
}
