package com.tileai.product.admin;

import java.util.Map;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
import com.tileai.product.ProductService;
import com.tileai.product.dto.ProductStatusRequest;
import com.tileai.product.dto.ProductUpsertRequest;
import com.tileai.product.dto.SkuView;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 后台产品库 CRUD(契约 {@code openapi-admin.yaml} 的 products 分组)。
 *
 * <p>路径与权限点逐条对应契约:
 * <pre>
 *   GET    /api/v1/admin/products             product:read
 *   POST   /api/v1/admin/products             product:write
 *   GET    /api/v1/admin/products/{id}        product:read
 *   PUT    /api/v1/admin/products/{id}        product:write
 *   DELETE /api/v1/admin/products/{id}        product:delete
 *   PATCH  /api/v1/admin/products/{id}/status product:publish
 * </pre>
 *
 * <p>{@code @Validated} 是为了让 {@code @RequestParam} 上的
 * {@code @Min}/{@code @Max} 生效(方法参数级校验);请求体上的 {@code @Valid}
 * 由 Spring MVC 自己处理。两者最终都落到 10001。
 *
 * <p>批量导入相关接口({@code /products/import*}、{@code /products/import/template})
 * 属 <b>T-0.7</b>,不在本类中。
 */
@RestController
@RequestMapping("/api/v1/admin/products")
@Validated
public class AdminProductController {

    /** 契约 {@code PageParam} 的默认值。 */
    private static final String DEFAULT_PAGE = "1";
    /** 契约 {@code SizeParam} 的默认值;上限 100 也来自契约。 */
    private static final String DEFAULT_SIZE = "20";

    private final ProductService productService;

    public AdminProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    @RequiresPermission(Permission.PRODUCT_READ)
    public ApiResponse<PageData<SkuView>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = DEFAULT_PAGE) @Min(value = 1, message = "页码从 1 开始") int page,
            @RequestParam(defaultValue = DEFAULT_SIZE) @Min(value = 1, message = "每页条数至少为 1")
            @Max(value = 100, message = "每页条数最多 100") int size) {
        return ApiResponse.ok(productService.page(keyword, status, page, size));
    }

    @PostMapping
    @RequiresPermission(Permission.PRODUCT_WRITE)
    @AdminOperation(action = "product.create", targetType = "product")
    public ApiResponse<Map<String, Long>> create(@Valid @RequestBody ProductUpsertRequest request) {
        long id = productService.create(request);
        // 契约声明响应 data 为 { id },不是 SKU 全量
        return ApiResponse.ok(Map.of("id", id));
    }

    @GetMapping("/{id}")
    @RequiresPermission(Permission.PRODUCT_READ)
    public ApiResponse<SkuView> detail(@PathVariable long id) {
        return ApiResponse.ok(productService.get(id));
    }

    @PutMapping("/{id}")
    @RequiresPermission(Permission.PRODUCT_WRITE)
    @AdminOperation(action = "product.update", targetType = "product", targetIdParam = "id")
    public ApiResponse<Void> update(@PathVariable long id,
                                    @Valid @RequestBody ProductUpsertRequest request) {
        productService.update(id, request);
        return ApiResponse.ok();
    }

    @DeleteMapping("/{id}")
    @RequiresPermission(Permission.PRODUCT_DELETE)
    @AdminOperation(action = "product.delete", targetType = "product", targetIdParam = "id")
    public ApiResponse<Void> delete(@PathVariable long id) {
        productService.delete(id);
        return ApiResponse.ok();
    }

    @PatchMapping("/{id}/status")
    @RequiresPermission(Permission.PRODUCT_PUBLISH)
    @AdminOperation(action = "product.publish", targetType = "product", targetIdParam = "id")
    public ApiResponse<Void> changeStatus(@PathVariable long id,
                                          @Valid @RequestBody ProductStatusRequest request) {
        productService.changeStatus(id, request.status());
        return ApiResponse.ok();
    }
}
