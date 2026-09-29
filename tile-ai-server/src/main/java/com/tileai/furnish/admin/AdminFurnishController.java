package com.tileai.furnish.admin;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tileai.admin.audit.AdminOperation;
import com.tileai.admin.rbac.Permission;
import com.tileai.admin.rbac.RequiresPermission;
import com.tileai.common.api.ApiResponse;
import com.tileai.common.api.ListData;
import com.tileai.furnish.FurnishStyleService;
import com.tileai.furnish.dto.FurnishStyleUpsertRequest;
import com.tileai.furnish.dto.FurnishStyleView;

import jakarta.validation.Valid;

/**
 * 后台陈设风格配置(契约 {@code openapi-admin.yaml} 的 furnish 分组)。
 *
 * <pre>
 *   GET    /api/v1/admin/furnish        furnish:read
 *   PUT    /api/v1/admin/furnish        furnish:write
 *   DELETE /api/v1/admin/furnish/{id}   furnish:delete
 * </pre>
 *
 * <p>注意契约里<b>没有 POST</b>:写入一律走 PUT 的 upsert 语义(以 space+style 唯一),
 * 没有"只新增"的入口 —— 这正是要防的"矩阵里出现重复组合"。
 */
@RestController
@RequestMapping("/api/v1/admin/furnish")
public class AdminFurnishController {

    private final FurnishStyleService furnishStyleService;

    public AdminFurnishController(FurnishStyleService furnishStyleService) {
        this.furnishStyleService = furnishStyleService;
    }

    @GetMapping
    @RequiresPermission(Permission.FURNISH_READ)
    public ApiResponse<ListData<FurnishStyleView>> list(
            @RequestParam(required = false) String space,
            @RequestParam(required = false) String style) {
        return ApiResponse.ok(ListData.of(furnishStyleService.list(space, style)));
    }

    @PutMapping
    @RequiresPermission(Permission.FURNISH_WRITE)
    @AdminOperation(action = "furnish.upsert", targetType = "furnish_style")
    public ApiResponse<Void> upsert(@Valid @RequestBody FurnishStyleUpsertRequest request) {
        furnishStyleService.upsert(request);
        return ApiResponse.ok();
    }

    @DeleteMapping("/{id}")
    @RequiresPermission(Permission.FURNISH_DELETE)
    @AdminOperation(action = "furnish.delete", targetType = "furnish_style", targetIdParam = "id")
    public ApiResponse<Void> delete(@PathVariable long id) {
        furnishStyleService.delete(id);
        return ApiResponse.ok();
    }
}
