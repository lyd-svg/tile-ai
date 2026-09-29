package com.tileai.layout.admin;

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
import com.tileai.common.api.ApiResponse;
import com.tileai.common.api.PageData;
import com.tileai.layout.LayoutTemplateService;
import com.tileai.layout.dto.UnitTemplateUpsertRequest;
import com.tileai.layout.dto.UnitTemplateView;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 后台户型模板维护(契约 {@code openapi-admin.yaml} 的 templates 分组)。
 *
 * <pre>
 *   GET    /api/v1/admin/templates        template:read
 *   POST   /api/v1/admin/templates        template:write
 *   GET    /api/v1/admin/templates/{id}   template:read
 *   PUT    /api/v1/admin/templates/{id}   template:write
 *   DELETE /api/v1/admin/templates/{id}   template:delete
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/admin/templates")
@Validated
public class AdminTemplateController {

    private static final String DEFAULT_PAGE = "1";
    private static final String DEFAULT_SIZE = "20";

    private final LayoutTemplateService templateService;

    public AdminTemplateController(LayoutTemplateService templateService) {
        this.templateService = templateService;
    }

    @GetMapping
    @RequiresPermission(Permission.TEMPLATE_READ)
    public ApiResponse<PageData<UnitTemplateView>> list(
            @RequestParam(required = false) Integer roomCount,
            @RequestParam(required = false) String areaRange,
            @RequestParam(required = false) String structure,
            @RequestParam(defaultValue = DEFAULT_PAGE) @Min(value = 1, message = "页码从 1 开始") int page,
            @RequestParam(defaultValue = DEFAULT_SIZE) @Min(value = 1, message = "每页条数至少为 1")
            @Max(value = 100, message = "每页条数最多 100") int size) {
        return ApiResponse.ok(templateService.page(roomCount, areaRange, structure, page, size));
    }

    @PostMapping
    @RequiresPermission(Permission.TEMPLATE_WRITE)
    @AdminOperation(action = "template.create", targetType = "unit_template")
    public ApiResponse<Map<String, Long>> create(
            @Valid @RequestBody UnitTemplateUpsertRequest request) {
        return ApiResponse.ok(Map.of("id", templateService.create(request)));
    }

    @GetMapping("/{id}")
    @RequiresPermission(Permission.TEMPLATE_READ)
    public ApiResponse<UnitTemplateView> detail(@PathVariable long id) {
        return ApiResponse.ok(templateService.get(id));
    }

    @PutMapping("/{id}")
    @RequiresPermission(Permission.TEMPLATE_WRITE)
    @AdminOperation(action = "template.update", targetType = "unit_template", targetIdParam = "id")
    public ApiResponse<Void> update(@PathVariable long id,
                                    @Valid @RequestBody UnitTemplateUpsertRequest request) {
        templateService.update(id, request);
        return ApiResponse.ok();
    }

    @DeleteMapping("/{id}")
    @RequiresPermission(Permission.TEMPLATE_DELETE)
    @AdminOperation(action = "template.delete", targetType = "unit_template", targetIdParam = "id")
    public ApiResponse<Void> delete(@PathVariable long id) {
        templateService.delete(id);
        return ApiResponse.ok();
    }
}
