package com.tileai.admin;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tileai.admin.dto.OperationLogView;
import com.tileai.admin.rbac.Permission;
import com.tileai.admin.rbac.RequiresPermission;
import com.tileai.common.api.ApiResponse;
import com.tileai.common.api.PageData;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 后台操作日志(契约 {@code openapi-admin.yaml} 的 logs 分组;架构 §8.1、§4.4)。
 *
 * <pre>
 *   GET /api/v1/admin/logs   log:read(super_admin / operator)
 * </pre>
 *
 * <p><b>只有这一个接口</b> —— 日志没有写路径。写入是
 * {@code AdminAuditAspect} 的职责:它拦带 {@code @AdminOperation} 的方法,
 * 在<b>成功返回后</b>追加一行。所以这个控制器上<b>没有</b> {@code @AdminOperation},
 * 读日志不该产生日志(否则查一次日志就多一条日志)。
 *
 * <p>契约的 4 个可选筛选参数原样收下来交给 {@link AdminLogService} 解释:
 * {@code adminId}、{@code action}、{@code from}、{@code to}。
 * 时间收 {@code String} 而不是 {@code LocalDateTime} —— 前端传的是纯日期
 * ({@code yyyy-MM-dd}),Spring 的日期转换在两种写法之间没有统一的默认口径,
 * 由服务层显式解析更容易说清"纯日期按当日首/末时刻算"这条规则。
 */
@RestController
@RequestMapping("/api/v1/admin/logs")
@Validated
public class AdminLogController {

    /** 契约 {@code PageParam} 的默认值。 */
    private static final String DEFAULT_PAGE = "1";
    /** 契约 {@code SizeParam} 的默认值;上限 100 也来自契约。 */
    private static final String DEFAULT_SIZE = "20";

    private final AdminLogService adminLogService;

    public AdminLogController(AdminLogService adminLogService) {
        this.adminLogService = adminLogService;
    }

    @GetMapping
    @RequiresPermission(Permission.LOG_READ)
    public ApiResponse<PageData<OperationLogView>> list(
            @RequestParam(required = false) Long adminId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = DEFAULT_PAGE) @Min(value = 1, message = "页码从 1 开始") int page,
            @RequestParam(defaultValue = DEFAULT_SIZE) @Min(value = 1, message = "每页条数至少为 1")
            @Max(value = 100, message = "每页条数最多 100") int size) {
        return ApiResponse.ok(adminLogService.page(adminId, action, from, to, page, size));
    }
}
