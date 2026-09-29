package com.tileai.quote;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tileai.common.api.ApiResponse;
import com.tileai.quote.dto.QuoteCalcRequest;
import com.tileai.quote.dto.QuoteResultView;

import jakarta.validation.Valid;

/**
 * 报价预览(契约 {@code openapi-c.yaml} {@code POST /api/v1/quote/calc}),
 * 报价单页使用。
 *
 * <p><b>公开接口</b>(契约 {@code security: []})—— 报价预览不强制登录,
 * 与 PRD §5.1「浏览免登录;生成/保存/问 AI 才需登录」一致。
 *
 * <p>本接口只做预览,**不落库**。方案报价的持久化在生成流程里(§11.2),
 * 由 T-1.12 负责;两者复用同一个 {@link QuoteService},规则只有一份。
 */
@RestController
@RequestMapping("/api/v1/quote")
public class QuoteController {

    private final QuoteService quoteService;

    public QuoteController(QuoteService quoteService) {
        this.quoteService = quoteService;
    }

    @PostMapping("/calc")
    public ApiResponse<QuoteResultView> calc(@Valid @RequestBody QuoteCalcRequest request) {
        return ApiResponse.ok(quoteService.calculate(request));
    }
}
