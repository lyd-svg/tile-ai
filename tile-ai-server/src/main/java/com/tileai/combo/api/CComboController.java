package com.tileai.combo.api;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tileai.combo.ComboService;
import com.tileai.combo.dto.ComboView;
import com.tileai.common.api.ApiResponse;
import com.tileai.common.api.PageData;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * C 端推荐搭配展示(契约 {@code openapi-c.yaml} 的 combo 分组;PRD §5.2.2 路径 B)。
 *
 * <pre>
 *   GET /api/v1/combos       推荐搭配列表(公开)
 *   GET /api/v1/combos/{id}  搭配详情(公开)
 * </pre>
 *
 * <p><b>只返回上架搭配</b> —— 与后台列表的区别是刻意的(后台要能管理下架项)。
 *
 * <p>搭配由运营在后台<b>人工维护</b>,不做自动组合(PRD §5.2.2 决策 #2)。
 * 本接口只负责读。
 *
 * <p>items 里的 {@code part} 恒为 {@code floor} / {@code wall} / {@code local}
 * 三值(契约 T-9;一期不含美缝)。
 */
@RestController
@RequestMapping("/api/v1/combos")
@Validated
public class CComboController {

    private static final String DEFAULT_PAGE = "1";
    private static final String DEFAULT_SIZE = "20";

    private final ComboService comboService;

    public CComboController(ComboService comboService) {
        this.comboService = comboService;
    }

    @GetMapping
    public ApiResponse<PageData<ComboView>> list(
            @RequestParam(required = false) String space,
            @RequestParam(required = false) String style,
            @RequestParam(defaultValue = DEFAULT_PAGE) @Min(value = 1, message = "页码从 1 开始") int page,
            @RequestParam(defaultValue = DEFAULT_SIZE) @Min(value = 1, message = "每页条数至少为 1")
            @Max(value = 100, message = "每页条数最多 100") int size) {
        return ApiResponse.ok(comboService.pageOnSale(space, style, page, size));
    }

    /** 详情;不存在**或已下架**都返回 30001。 */
    @GetMapping("/{id}")
    public ApiResponse<ComboView> detail(@PathVariable long id) {
        return ApiResponse.ok(comboService.getOnSale(id));
    }
}
