package com.tileai.plan.api;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tileai.common.api.ApiResponse;
import com.tileai.common.api.PageData;
import com.tileai.common.security.AuthPrincipal;
import com.tileai.common.security.CurrentPrincipal;
import com.tileai.plan.PlanService;
import com.tileai.plan.dto.PlanCopyResult;
import com.tileai.plan.dto.PlanDetailView;
import com.tileai.plan.dto.PlanRenameRequest;
import com.tileai.plan.dto.PlanSummaryView;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * C 端「我的方案」(契约 {@code openapi-c.yaml} 的 plan 分组;PRD §5.6、§11.4)。
 *
 * <pre>
 *   GET    /api/v1/plans            列表
 *   GET    /api/v1/plans/{id}       详情
 *   PATCH  /api/v1/plans/{id}       重命名
 *   DELETE /api/v1/plans/{id}       删除(逻辑删除)
 *   POST   /api/v1/plans/{id}/copy  另存为
 * </pre>
 *
 * <p>五条都要登录,身份取自 Cookie(项目约定:不用 {@code Authorization} 头,
 * 令牌也不进响应体)。路由保护在 {@code SecurityConfig} 里已经配了
 * {@code /api/v1/plans/**},本类<b>不加安全注解</b> —— 加了会变成两处判定,
 * 迟早有一处改漏。
 *
 * <p>{@code /plans/{id}/share}(T-1.13)与 {@code /plans/{id}/recalc}(T-1.15)
 * 刻意不在本类里:前者归分享,后者是"按当前价重算"。尤其 <b>{@code recalc} 没有
 * 请求体</b> —— 部位与面积一律取自方案快照,一旦让它收前端传参,
 * {@code /quote/calc} 的参数校验就可以被绕过(CLAUDE.md 架构不变量 6)。
 */
@RestController
@RequestMapping("/api/v1/plans")
@Validated
public class CPlanController {

    private static final String DEFAULT_PAGE = "1";
    private static final String DEFAULT_SIZE = "20";

    private final PlanService planService;

    public CPlanController(PlanService planService) {
        this.planService = planService;
    }

    /**
     * 我的方案列表。
     *
     * <p>{@code page} / {@code size} 的默认值与边界取自契约的
     * {@code PageParam}(默认 1,最小 1)与 {@code SizeParam}(默认 20,1–100)。
     * 越界的页码不算错(返回空列表 + 正确的 total),<b>越界的参数值才算</b> ——
     * 由 {@code @Min}/{@code @Max} 拦成 10001,和产品库列表同一套口径。
     */
    @GetMapping
    public ApiResponse<PageData<PlanSummaryView>> list(
            @RequestParam(defaultValue = DEFAULT_PAGE) @Min(value = 1, message = "页码从 1 开始") int page,
            @RequestParam(defaultValue = DEFAULT_SIZE) @Min(value = 1, message = "每页条数至少为 1")
            @Max(value = 100, message = "每页条数最多 100") int size) {
        AuthPrincipal principal = CurrentPrincipal.requireUser();
        return ApiResponse.ok(planService.page(principal.id(), page, size));
    }

    /** 详情。{@code layout} 是<b>明文</b>;不存在 / 已删除 / 不是本人的一律 30001。 */
    @GetMapping("/{id}")
    public ApiResponse<PlanDetailView> detail(@PathVariable long id) {
        AuthPrincipal principal = CurrentPrincipal.requireUser();
        return ApiResponse.ok(planService.detail(principal.id(), id));
    }

    /**
     * 重命名。
     *
     * <p>返回<b>裸信封</b>({@code ApiResponse<Void>},没有 {@code data})——
     * 契约里这条的响应就是 {@code $ref ApiResponse},没有额外声明 {@code data}。
     * 顺手回一个方案对象看着更"方便",但那是契约里不存在的形状。
     */
    @PatchMapping("/{id}")
    public ApiResponse<Void> rename(@PathVariable long id,
                                    @Valid @RequestBody PlanRenameRequest request) {
        AuthPrincipal principal = CurrentPrincipal.requireUser();
        planService.rename(principal.id(), id, request.name());
        return ApiResponse.ok();
    }

    /**
     * 删除(逻辑删除)。
     *
     * <p>二次确认在前端(契约的接口说明就是这么写的),后端只做逻辑删除。
     * 返回裸信封,同 {@link #rename}。
     *
     * <p><b>没有请求体</b>:契约里这条只有 path 参数。带 {@code {confirm:true}} 之类的
     * 参数会让"二次确认"变成一个后端开关 —— 而它本来就该是前端的一次交互。
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable long id) {
        AuthPrincipal principal = CurrentPrincipal.requireUser();
        planService.delete(principal.id(), id);
        return ApiResponse.ok();
    }

    /** 另存为,返回新方案 id(契约内联 schema {@code {planId}})。 */
    @PostMapping("/{id}/copy")
    public ApiResponse<PlanCopyResult> copy(@PathVariable long id) {
        AuthPrincipal principal = CurrentPrincipal.requireUser();
        return ApiResponse.ok(new PlanCopyResult(planService.copy(principal.id(), id)));
    }
}
