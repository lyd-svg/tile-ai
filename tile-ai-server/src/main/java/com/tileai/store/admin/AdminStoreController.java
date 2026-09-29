package com.tileai.store.admin;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tileai.admin.audit.AdminOperation;
import com.tileai.admin.rbac.Permission;
import com.tileai.admin.rbac.RequiresPermission;
import com.tileai.common.api.ApiResponse;
import com.tileai.store.StoreService;
import com.tileai.store.dto.StoreUpsertRequest;
import com.tileai.store.dto.StoreView;

import jakarta.validation.Valid;

/**
 * 后台门店信息维护(契约 {@code openapi-admin.yaml} 的 store 分组)。
 *
 * <pre>
 *   GET /api/v1/admin/store   store:read
 *   PUT /api/v1/admin/store   store:write
 * </pre>
 *
 * <p>只有两个接口:线下为<b>单店</b>(PRD §7.6),没有新增与删除。
 * C 端展示门店的 {@code GET /store} 属 T-1.14。
 */
@RestController
@RequestMapping("/api/v1/admin/store")
public class AdminStoreController {

    private final StoreService storeService;

    public AdminStoreController(StoreService storeService) {
        this.storeService = storeService;
    }

    @GetMapping
    @RequiresPermission(Permission.STORE_READ)
    public ApiResponse<StoreView> get() {
        return ApiResponse.ok(storeService.get());
    }

    @PutMapping
    @RequiresPermission(Permission.STORE_WRITE)
    @AdminOperation(action = "store.update", targetType = "store")
    public ApiResponse<Void> update(@Valid @RequestBody StoreUpsertRequest request) {
        storeService.update(request);
        return ApiResponse.ok();
    }
}
