package com.tileai.plan.api;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tileai.common.api.ApiResponse;
import com.tileai.common.security.AuthPrincipal;
import com.tileai.common.security.CurrentPrincipal;
import com.tileai.plan.PlanShareService;
import com.tileai.plan.dto.ShareResultView;

/**
 * 分享方案的<b>写</b>侧(契约 {@code openapi-c.yaml} 的 share 分组;T-1.13)。
 *
 * <pre>
 *   POST   /api/v1/plans/{id}/share   生成分享链接
 *   DELETE /api/v1/plans/{id}/share   废除分享链接
 * </pre>
 *
 * <h2>为什么单独一个控制器</h2>
 *
 * <p>{@link CPlanController} 的类注释里已经写下「{@code /plans/{id}/share} 刻意不在本类」。
 * 分出来没有代价:两个 {@code @RestController} 共享同一个 {@code @RequestMapping}
 * 前缀完全合法,契约↔实现比对读的是 Spring 的路由表,两处都会被扫到。
 *
 * <h2>两个方法都没有请求体</h2>
 *
 * <p>契约里这两条只有 path 参数。<b>生成分享不接受任何参数</b>:
 * 没有"有效期几小时""允许谁看"这类开关 —— 上游从没定义过它们
 * (架构 §13.5 的"访问范围控制"未展开),凭空造一个只会变成没人能解释的默认值。
 * 令牌怎么生成、链接怎么拼,全部由服务端定({@link PlanShareService})。
 *
 * <p>{@code PlanShareContractTest} 用反射钉着"方法上不存在 {@code @RequestBody}" ——
 * 光靠契约文件写"无请求体"只能证明契约是这么写的,证明不了实现没多收。
 *
 * <h2>公开的那条不在这里</h2>
 *
 * <p>{@code GET /api/v1/share/{token}} 是<b>不需要登录</b>的,单独放在
 * {@link CShareController} —— 那个类的边界就是安全边界。
 *
 * <p>本类的路由保护在 {@code SecurityConfig} 里已配 {@code /api/v1/plans/**},
 * <b>不加安全注解</b>,与 {@link CPlanController} 同一口径:两处判定迟早改漏一处。
 */
@RestController
@RequestMapping("/api/v1/plans")
public class CPlanShareController {

    private final PlanShareService shareService;

    public CPlanShareController(PlanShareService shareService) {
        this.shareService = shareService;
    }

    /**
     * 生成分享链接。
     *
     * <p><b>重复调用返回同一个令牌</b> —— 已经发出去的链接不会被作废,
     * 理由见 {@link PlanShareService#share}。
     *
     * <p>返回体里 {@code qrCodeUrl} 永远不出现(恒为 null):二维码由前端画
     * (2026-10-01 决策,见 {@link ShareResultView})。
     */
    @PostMapping("/{id}/share")
    public ApiResponse<ShareResultView> share(@PathVariable long id) {
        AuthPrincipal principal = CurrentPrincipal.requireUser();
        return ApiResponse.ok(shareService.share(principal.id(), id));
    }

    /**
     * 废除分享链接 —— 旧链接<b>立刻</b>失效。
     *
     * <p>返回<b>裸信封</b>({@code ApiResponse<Void>},没有 {@code data})——
     * 契约里这条的响应就是 {@code $ref ApiResponse}。顺手回一个新状态看着更"方便",
     * 但那是契约里不存在的形状。
     *
     * <p><b>幂等</b>:重复废除不会报错(第二次什么也没变,但用户看到的都是"已经废除了")。
     */
    @DeleteMapping("/{id}/share")
    public ApiResponse<Void> revoke(@PathVariable long id) {
        AuthPrincipal principal = CurrentPrincipal.requireUser();
        shareService.revoke(principal.id(), id);
        return ApiResponse.ok();
    }
}
