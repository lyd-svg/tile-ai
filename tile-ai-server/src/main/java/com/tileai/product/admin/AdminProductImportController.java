package com.tileai.product.admin;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.tileai.admin.audit.AdminOperation;
import com.tileai.admin.rbac.Permission;
import com.tileai.admin.rbac.RequiresPermission;
import com.tileai.common.api.ApiResponse;
import com.tileai.product.imports.ImportBatch;
import com.tileai.product.imports.ImportTemplateWriter;
import com.tileai.product.imports.ImportViews;
import com.tileai.product.imports.ProductImportService;

/**
 * 后台产品库批量导入(契约 {@code openapi-admin.yaml} 的三个 import 接口)。
 *
 * <pre>
 *   POST /api/v1/admin/products/import             product:import
 *   GET  /api/v1/admin/products/import/{batchId}   product:read
 *   GET  /api/v1/admin/products/import/template    product:read
 * </pre>
 *
 * <p>路径与 {@code AdminProductController} 的 {@code /products/{id}} 不冲突:
 * {@code /products/import} 是 POST 而 {@code /products/{id}} 只声明了
 * GET / PUT / DELETE;模板与批次查询都比 {@code /products/{id}} 多一段路径。
 * 另外 Spring MVC 对字面量 {@code /template} 的优先级高于 {@code /{batchId}}。
 */
@RestController
@RequestMapping("/api/v1/admin/products/import")
public class AdminProductImportController {

    private static final MediaType XLSX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final ProductImportService importService;
    private final ImportTemplateWriter templateWriter;

    public AdminProductImportController(ProductImportService importService,
                                        ImportTemplateWriter templateWriter) {
        this.importService = importService;
        this.templateWriter = templateWriter;
    }

    /**
     * 受理导入(异步),立即返回批次号。
     *
     * <p>操作日志的 {@code targetIdParam} 留空 —— 这里没有单一目标资源,
     * 批次号在响应体里,不在路径上。
     */
    @PostMapping
    @RequiresPermission(Permission.PRODUCT_IMPORT)
    @AdminOperation(action = "product.import", targetType = "product_import")
    public ApiResponse<ImportViews.ImportAccepted> accept(
            @RequestPart("file") MultipartFile file,
            @RequestParam("mode") String mode) {
        ImportBatch batch = importService.accept(file, mode);
        return ApiResponse.ok(new ImportViews.ImportAccepted(batch.getBatchId()));
    }

    /** 查询进度与结果(契约:失败码 30001 批次不存在)。 */
    @GetMapping("/{batchId}")
    @RequiresPermission(Permission.PRODUCT_READ)
    public ApiResponse<ImportViews.ImportStatus> status(@PathVariable String batchId) {
        return ApiResponse.ok(ImportViews.ImportStatus.of(importService.find(batchId)));
    }

    /**
     * 下载导入模板。
     *
     * <p>契约在同一个 200 响应下声明了 xlsx 与 CSV 两种内容类型,这里按
     * {@code Accept} 头选择 —— 这正是 OpenAPI 声明多 content type 的意图
     * (内容协商),而不是加一个契约里没有的查询参数。
     * 默认给 xlsx:Excel 打开 CSV 时的编码/前导零问题更多。
     */
    @GetMapping("/template")
    @RequiresPermission(Permission.PRODUCT_READ)
    public ResponseEntity<byte[]> template(
            @RequestHeader(value = HttpHeaders.ACCEPT, required = false) String accept) {

        // 判据是 "text/csv" —— 即契约中声明的那个内容类型,不是 text/plain
        boolean wantsCsv = accept != null && accept.contains("text/csv");
        byte[] body = wantsCsv ? templateWriter.csv() : templateWriter.xlsx();
        MediaType contentType = wantsCsv ? MediaType.parseMediaType("text/csv") : XLSX;

        return ResponseEntity.ok()
                .contentType(contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"product-import-template."
                                + (wantsCsv ? "csv" : "xlsx") + "\"")
                .body(body);
    }
}
