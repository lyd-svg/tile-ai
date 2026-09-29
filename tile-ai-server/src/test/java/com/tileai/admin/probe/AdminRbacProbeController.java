package com.tileai.admin.probe;

import java.util.Map;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tileai.admin.audit.AdminOperation;
import com.tileai.admin.rbac.Permission;
import com.tileai.admin.rbac.RequiresPermission;
import com.tileai.common.api.ApiResponse;

/**
 * T-0.5 验证用的后台探针端点 —— <b>仅存在于测试源码中</b>。
 *
 * <p>刻意挂在 {@code /api/v1/admin/rbac-probe/**} 下,而不是真实的
 * {@code /api/v1/admin/products} 等路径 —— 后者自 T-0.6 起会有真实实现,
 * 在测试里先占住会造成映射冲突。RBAC 的校验是<b>注解驱动</b>的,
 * 与路径无关,所以用探针路径一样能验到真实的拦截器与权限矩阵。
 *
 * <p>每个端点标注的权限点与契约中对应接口的标注一致,便于逐条对照。
 */
@RestController
@RequestMapping("/api/v1/admin/rbac-probe")
public class AdminRbacProbeController {

    @GetMapping("/products")
    @RequiresPermission(Permission.PRODUCT_READ)
    public ApiResponse<Map<String, Object>> listProducts() {
        return ApiResponse.ok(Map.of("ok", true));
    }

    @PostMapping("/products")
    @RequiresPermission(Permission.PRODUCT_WRITE)
    @AdminOperation(action = "product.create", targetType = "product")
    public ApiResponse<Map<String, Object>> createProduct() {
        return ApiResponse.ok(Map.of("ok", true));
    }

    @DeleteMapping("/products/{id}")
    @RequiresPermission(Permission.PRODUCT_DELETE)
    @AdminOperation(action = "product.delete", targetType = "product", targetIdParam = "id")
    public ApiResponse<Map<String, Object>> deleteProduct(@PathVariable String id) {
        return ApiResponse.ok(Map.of("ok", true));
    }

    @PatchMapping("/products/{id}/status")
    @RequiresPermission(Permission.PRODUCT_PUBLISH)
    @AdminOperation(action = "product.publish", targetType = "product", targetIdParam = "id")
    public ApiResponse<Map<String, Object>> publishProduct(@PathVariable String id) {
        return ApiResponse.ok(Map.of("ok", true));
    }

    @PostMapping("/products/import")
    @RequiresPermission(Permission.PRODUCT_IMPORT)
    public ApiResponse<Map<String, Object>> importProducts() {
        return ApiResponse.ok(Map.of("ok", true));
    }

    @PutMapping("/quote-params")
    @RequiresPermission(Permission.QUOTE_PARAM_WRITE)
    @AdminOperation(action = "quote-param.upsert", targetType = "quote_param")
    public ApiResponse<Map<String, Object>> upsertQuoteParam() {
        return ApiResponse.ok(Map.of("ok", true));
    }

    @PutMapping("/dicts/{id}")
    @RequiresPermission(Permission.DICT_WRITE)
    @AdminOperation(action = "dict.update", targetType = "dict", targetIdParam = "id")
    public ApiResponse<Map<String, Object>> updateDict(@PathVariable String id) {
        return ApiResponse.ok(Map.of("ok", true));
    }

    @GetMapping("/accounts")
    @RequiresPermission(Permission.ACCOUNT_MANAGE)
    public ApiResponse<Map<String, Object>> listAccounts() {
        return ApiResponse.ok(Map.of("ok", true));
    }

    @GetMapping("/logs")
    @RequiresPermission(Permission.LOG_READ)
    public ApiResponse<Map<String, Object>> listLogs() {
        return ApiResponse.ok(Map.of("ok", true));
    }
}
