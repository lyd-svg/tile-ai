package com.tileai.admin.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tileai.admin.DictService;
import com.tileai.admin.dto.DictView;
import com.tileai.common.api.ApiResponse;
import com.tileai.common.api.ListData;

/**
 * C 端数据字典查询(契约 {@code openapi-c.yaml} 的 dict 分组;D-14 定稿)。
 *
 * <pre>
 *   GET /api/v1/dicts  空间类型 / 风格等枚举(公开,只读)
 * </pre>
 *
 * <p><b>前端下拉与提交校验都必须以此接口为准,不得硬编码</b>(D-14)——
 * 后台新增一个房间类型或风格,前端不需要发版。契约里的 enum
 * ({@code living_room} / {@code modern} …)只是**内置默认值**。
 *
 * <p>与后台 {@code /admin/dicts} 的唯一差异:<b>只返回 {@code status=enabled} 的项</b>。
 * 响应 `data` 为 `{ list: [...] }`,与后台同构。
 */
@RestController
@RequestMapping("/api/v1/dicts")
public class CDictController {

    private final DictService dictService;

    public CDictController(DictService dictService) {
        this.dictService = dictService;
    }

    @GetMapping
    public ApiResponse<ListData<DictView>> list(
            @RequestParam(required = false) String type) {
        return ApiResponse.ok(ListData.of(dictService.listEnabled(type)));
    }
}
