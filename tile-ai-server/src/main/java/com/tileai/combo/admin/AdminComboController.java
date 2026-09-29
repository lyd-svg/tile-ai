package com.tileai.combo.admin;

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
import com.tileai.admin.rbac.Permission;
import com.tileai.admin.rbac.RequiresPermission;
import com.tileai.combo.ComboService;
import com.tileai.combo.dto.ComboUpsertRequest;
import com.tileai.combo.dto.ComboView;
import com.tileai.common.api.ApiResponse;
import com.tileai.common.api.PageData;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 后台推荐搭配维护(契约 {@code openapi-admin.yaml} 的 combos 分组)。
 *
 * <pre>
 *   GET    /api/v1/admin/combos        combo:read
 *   POST   /api/v1/admin/combos        combo:write
 *   GET    /api/v1/admin/combos/{id}   combo:read
 *   PUT    /api/v1/admin/combos/{id}   combo:write
 *   DELETE /api/v1/admin/combos/{id}   combo:delete
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/admin/combos")
@Validated
public class AdminComboController {

    private static final String DEFAULT_PAGE = "1";
    private static final String DEFAULT_SIZE = "20";

    private final ComboService comboService;

    public AdminComboController(ComboService comboService) {
        this.comboService = comboService;
    }

    @GetMapping
    @RequiresPermission(Permission.COMBO_READ)
    public ApiResponse<PageData<ComboView>> list(
            @RequestParam(required = false) String space,
            @RequestParam(required = false) String style,
            @RequestParam(defaultValue = DEFAULT_PAGE) @Min(value = 1, message = "页码从 1 开始") int page,
            @RequestParam(defaultValue = DEFAULT_SIZE) @Min(value = 1, message = "每页条数至少为 1")
            @Max(value = 100, message = "每页条数最多 100") int size) {
        return ApiResponse.ok(comboService.page(space, style, page, size));
    }

    @PostMapping
    @RequiresPermission(Permission.COMBO_WRITE)
    @AdminOperation(action = "combo.create", targetType = "combo")
    public ApiResponse<Map<String, Long>> create(@Valid @RequestBody ComboUpsertRequest request) {
        return ApiResponse.ok(Map.of("id", comboService.create(request)));
    }

    @GetMapping("/{id}")
    @RequiresPermission(Permission.COMBO_READ)
    public ApiResponse<ComboView> detail(@PathVariable long id) {
        return ApiResponse.ok(comboService.get(id));
    }

    @PutMapping("/{id}")
    @RequiresPermission(Permission.COMBO_WRITE)
    @AdminOperation(action = "combo.update", targetType = "combo", targetIdParam = "id")
    public ApiResponse<Void> update(@PathVariable long id,
                                    @Valid @RequestBody ComboUpsertRequest request) {
        comboService.update(id, request);
        return ApiResponse.ok();
    }

    @DeleteMapping("/{id}")
    @RequiresPermission(Permission.COMBO_DELETE)
    @AdminOperation(action = "combo.delete", targetType = "combo", targetIdParam = "id")
    public ApiResponse<Void> delete(@PathVariable long id) {
        comboService.delete(id);
        return ApiResponse.ok();
    }
}
