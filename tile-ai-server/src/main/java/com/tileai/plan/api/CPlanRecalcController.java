package com.tileai.plan.api;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tileai.common.api.ApiResponse;
import com.tileai.common.security.AuthPrincipal;
import com.tileai.common.security.CurrentPrincipal;
import com.tileai.plan.PlanRecalcService;
import com.tileai.quote.dto.QuoteResultView;

/**
 * 「按当前价格重算」(契约 {@code openapi-c.yaml} §15.7;T-1.15)。
 *
 * <pre>
 *   POST /api/v1/plans/{id}/recalc   按当前价格重算报价
 * </pre>
 *
 * <h2>为什么单独一个控制器</h2>
 *
 * <p>{@link CPlanController} 的类注释里已经写下「{@code recalc} 刻意不在本类里」。
 * 塞进去再改注释,等于推翻一条已经写下的设计 —— 而分出来没有任何代价:两个
 * {@code @RestController} 共享同一个 {@code @RequestMapping} 前缀完全合法,
 * 契约↔实现比对读的是 Spring 的路由表,两处都会被扫到。
 *
 * <h2>没有 {@code @RequestBody} —— 这是不变量 6</h2>
 *
 * <p>部位、面积、开口一律取自<b>方案快照</b>,不接受前端传参。收了参数,
 * {@code /quote/calc} 那套校验就可以被绕过(前端把面积填成 1 ㎡ 去探价)。
 * {@code PlanRecalcContractTest} 用反射钉着"方法上不存在 {@code @RequestBody}"这一条 ——
 * 光靠契约文件写"无请求体"只能证明契约是这么写的,证明不了实现没多收。
 *
 * <p>路由保护在 {@code SecurityConfig} 里已配 {@code /api/v1/plans/**},本类
 * <b>不加安全注解</b>,与 {@link CPlanController} 同一口径:两处判定迟早改漏一处。
 */
@RestController
@RequestMapping("/api/v1/plans")
public class CPlanRecalcController {

    private final PlanRecalcService recalcService;

    public CPlanRecalcController(PlanRecalcService recalcService) {
        this.recalcService = recalcService;
    }

    /**
     * 重算并返回重算后的报价。
     *
     * <p>返回体就是 {@code /quote/calc} 的那个 {@code QuoteResult} 形状 ——
     * 契约 {@code data: QuoteResult}。失败时的处置见
     * {@link PlanRecalcService}:方案不可用是 30001,算不出来是 90001 且<b>什么都不改</b>。
     */
    @PostMapping("/{id}/recalc")
    public ApiResponse<QuoteResultView> recalc(@PathVariable long id) {
        AuthPrincipal principal = CurrentPrincipal.requireUser();
        return ApiResponse.ok(recalcService.recalc(principal.id(), id));
    }
}
