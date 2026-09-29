package com.tileai.quote.admin;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tileai.admin.audit.AdminOperation;
import com.tileai.admin.rbac.Permission;
import com.tileai.admin.rbac.RequiresPermission;
import com.tileai.common.api.ApiResponse;
import com.tileai.common.api.ListData;
import com.tileai.quote.QuoteParamService;
import com.tileai.quote.dto.QuoteParamUpsertRequest;
import com.tileai.quote.dto.QuoteParamView;

import jakarta.validation.Valid;

/**
 * 后台报价参数维护(契约 {@code openapi-admin.yaml} 的 quote-params 分组)。
 *
 * <pre>
 *   GET /api/v1/admin/quote-params   quote-param:read
 *   PUT /api/v1/admin/quote-params   quote-param:write
 * </pre>
 *
 * <p><b>两个权限点都只有 super_admin</b>(架构 §4.4 的对照表)—— operator
 * 与 editor 连读都不行。它直接决定所有报价金额,与账号管理同属高影响面配置。
 * 契约 {@code GET /admin/quote-params} 与 {@code PUT} 的 description 都写明了这一点。
 */
@RestController
@RequestMapping("/api/v1/admin/quote-params")
public class AdminQuoteParamController {

    private final QuoteParamService quoteParamService;

    public AdminQuoteParamController(QuoteParamService quoteParamService) {
        this.quoteParamService = quoteParamService;
    }

    @GetMapping
    @RequiresPermission(Permission.QUOTE_PARAM_READ)
    public ApiResponse<ListData<QuoteParamView>> list() {
        return ApiResponse.ok(ListData.of(quoteParamService.list()));
    }

    @PutMapping
    @RequiresPermission(Permission.QUOTE_PARAM_WRITE)
    @AdminOperation(action = "quote-param.upsert", targetType = "quote_param")
    public ApiResponse<Void> upsert(@Valid @RequestBody QuoteParamUpsertRequest request) {
        quoteParamService.upsert(request);
        return ApiResponse.ok();
    }
}
