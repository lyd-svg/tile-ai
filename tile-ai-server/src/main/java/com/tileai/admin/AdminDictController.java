package com.tileai.admin;

import java.util.Map;

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
import com.tileai.admin.dto.DictUpsertRequest;
import com.tileai.admin.dto.DictView;
import com.tileai.admin.rbac.Permission;
import com.tileai.admin.rbac.RequiresPermission;
import com.tileai.common.api.ApiResponse;
import com.tileai.common.api.ListData;

import jakarta.validation.Valid;

/**
 * 后台空间 / 风格字典维护(契约 {@code openapi-admin.yaml} 的 dicts 分组)。
 *
 * <pre>
 *   GET    /api/v1/admin/dicts        dict:read   (含 disabled)
 *   POST   /api/v1/admin/dicts        dict:write  (仅 super_admin/operator)
 *   PUT    /api/v1/admin/dicts/{id}   dict:write
 *   DELETE /api/v1/admin/dicts/{id}   dict:write  (被引用 → 30002)
 * </pre>
 *
 * <p>放在 {@code com.tileai.admin} 而非新建模块:架构 §4.1 把字典归在「后台管理」,
 * 且它本身就是"后台可配置枚举"的载体(§8.5)。
 */
@RestController
@RequestMapping("/api/v1/admin/dicts")
public class AdminDictController {

    private final DictService dictService;

    public AdminDictController(DictService dictService) {
        this.dictService = dictService;
    }

    @GetMapping
    @RequiresPermission(Permission.DICT_READ)
    public ApiResponse<ListData<DictView>> list(
            @RequestParam(required = false) String type) {
        return ApiResponse.ok(ListData.of(dictService.list(type)));
    }

    @PostMapping
    @RequiresPermission(Permission.DICT_WRITE)
    @AdminOperation(action = "dict.create", targetType = "dict")
    public ApiResponse<Map<String, Long>> create(@Valid @RequestBody DictUpsertRequest request) {
        // 契约该接口的 description 写明"返回新建项 id"
        return ApiResponse.ok(Map.of("id", dictService.create(request)));
    }

    @PutMapping("/{id}")
    @RequiresPermission(Permission.DICT_WRITE)
    @AdminOperation(action = "dict.update", targetType = "dict", targetIdParam = "id")
    public ApiResponse<Void> update(@PathVariable long id,
                                    @Valid @RequestBody DictUpsertRequest request) {
        dictService.update(id, request);
        return ApiResponse.ok();
    }

    @DeleteMapping("/{id}")
    @RequiresPermission(Permission.DICT_WRITE)
    @AdminOperation(action = "dict.delete", targetType = "dict", targetIdParam = "id")
    public ApiResponse<Void> delete(@PathVariable long id) {
        dictService.delete(id);
        return ApiResponse.ok();
    }
}
