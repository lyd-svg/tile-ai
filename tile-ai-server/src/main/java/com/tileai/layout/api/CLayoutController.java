package com.tileai.layout.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tileai.common.api.ApiResponse;
import com.tileai.common.api.PageData;
import com.tileai.layout.LayoutParseService;
import com.tileai.layout.LayoutTemplateService;
import com.tileai.layout.dto.UnitTemplateView;
import com.tileai.layout.model.LayoutModel;

import jakarta.validation.Valid;

/**
 * C 端户型(契约 {@code openapi-c.yaml} 的 layout 分组,§5.3)。
 *
 * <pre>
 *   GET  /api/v1/layout/templates   户型模板列表(公开)
 *   POST /api/v1/layout/parse       自然语言/参数 → 结构化户型(公开)
 * </pre>
 *
 * <p>两个接口都是 {@code security: []} —— 户型录入发生在**登录之前**
 * (PRD §5.1:浏览免登录;生成/保存才要登录),拿完户型才谈生成。
 *
 * <p>与后台的区别:后台 {@code /admin/templates} **返回含下架项**(要能管理),
 * 这里**只返回上架项** —— 与产品库同一口径(§21.3):下架的模板不该出现在选择列表里。
 */
@RestController
@RequestMapping("/api/v1/layout")
public class CLayoutController {

    private final LayoutTemplateService templateService;
    private final LayoutParseService parseService;

    public CLayoutController(LayoutTemplateService templateService,
                             LayoutParseService parseService) {
        this.templateService = templateService;
        this.parseService = parseService;
    }

    /**
     * 模板列表。三个筛选项与后台一致(§5.3.1),但**只看得到上架项**。
     */
    @GetMapping("/templates")
    public ApiResponse<PageData<UnitTemplateView>> templates(
            @RequestParam(required = false) Integer roomCount,
            @RequestParam(required = false) String areaRange,
            @RequestParam(required = false) String structure,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {

        return ApiResponse.ok(templateService.pageOnSale(roomCount, areaRange, structure, page, size));
    }

    /**
     * 解析户型。
     *
     * <p>`params` 由 `@Valid` 触发嵌套校验 —— {@code LayoutSpace} 上的约束
     * 会产出 **10001 + `data.errors[]`**,与自然语言路径的失败形状一致。
     */
    @PostMapping("/parse")
    public ApiResponse<LayoutModel> parse(@Valid @RequestBody ParseLayoutRequest request) {
        return ApiResponse.ok(parseService.parse(request.text(), request.params()));
    }

    /**
     * 契约 `POST /layout/parse` 的请求体。
     *
     * <p>两个字段都可空,但**不能同时为空** —— 该组合由 {@link LayoutParseService}
     * 判成 10001(而不是靠 `@NotNull`,因为单看某一个字段都允许为空)。
     */
    public record ParseLayoutRequest(String text, @Valid LayoutModel params) {
    }
}
