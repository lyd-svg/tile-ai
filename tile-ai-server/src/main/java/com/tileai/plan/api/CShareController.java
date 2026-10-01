package com.tileai.plan.api;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tileai.common.api.ApiResponse;
import com.tileai.plan.PlanShareService;
import com.tileai.plan.dto.PlanDetailView;

/**
 * 公开只读分享页(契约 {@code openapi-c.yaml} {@code GET /share/{token}};T-1.13)。
 *
 * <pre>
 *   GET /api/v1/share/{token}   凭分享令牌读一份方案快照
 * </pre>
 *
 * <h2>这一个类里的东西全都不需要登录 —— 这就是它单独成类的原因</h2>
 *
 * <p>放在 {@link CPlanShareController} 里也能跑(共用一个 {@code @RequestMapping} 前缀
 * 是合法的),但那样"哪个方法公开"要靠逐行去读注解。拆开之后,
 * <b>类的边界就是安全边界</b>,与 {@code SecurityConfig} 里的路径前缀一一对应。
 *
 * <p>本类<b>没有</b> {@code @RequestBody}、<b>没有</b> {@code CurrentPrincipal} ——
 * 一个公开页拿不到也不该拿身份。
 *
 * <h2>为什么可以不登录</h2>
 *
 * <p>拿到链接的家人 / 设计师 / 门店店员没有账号,也不该为了看一眼效果图去注册。
 * 令牌本身就是凭据:{@code /api/v1/share/**} 不在 {@code SecurityConfig} 的受保护清单里,
 * 落在末尾的 {@code anyRequest().permitAll()} 上。
 *
 * <p>⚠️ 这是**巧合般的正确** —— 改动那份清单时没人会想起来"分享页靠的是别被加进去"。
 * 所以 {@code PlanShareIntegrationTest} 从两侧钉住:不带 Cookie 访问本接口必须是
 * <b>200</b>,而不带 Cookie 调两个写接口必须是 <b>401</b>。清单被改动时,这两条会红。
 *
 * <h2>隐私面(必须显式说出来的后果)</h2>
 *
 * <p>返回的是完整的 {@code PlanDetail}:<b>明文户型</b>、报价、效果图,以及令牌本身。
 * 也就是说,令牌是这条链路上<b>唯一</b>的机密 —— 拿到它就能看到全部。
 * 契约就是这么定的(响应 {@code data: PlanDetail}),这不是实现的选择;
 * 登记在 {@code openapi-verification.md} §43,连同"没有有效期、没有访问次数限制"一起。
 */
@RestController
@RequestMapping("/api/v1/share")
public class CShareController {

    private final PlanShareService shareService;

    public CShareController(PlanShareService shareService) {
        this.shareService = shareService;
    }

    /**
     * 凭令牌读方案快照。
     *
     * <p><b>响应带 {@code Cache-Control: no-store}。</b>这条接口的全部意义是"废除之后
     * 就打不开了" —— 而没有这个头的话,浏览器与中间缓存完全可以继续吐旧内容,
     * 于是**废除只是服务端的一句空话**。契约没有禁止额外的响应头,
     * 契约比对也不看响应头。
     *
     * <p>(没有顺手加 {@code Referrer-Policy}:它只对<b>文档</b>类型的响应有意义,
     * 而这里返回的是 JSON。分享页自己的 {@code Referrer-Policy}
     * 该由前端的那个页面来设 —— 那是 F-1.10 的事。)
     *
     * <p>取不到一律 30001,四种情形不区分,理由见 {@link PlanShareService#readShared}。
     */
    @GetMapping("/{token}")
    public ResponseEntity<ApiResponse<PlanDetailView>> read(@PathVariable String token) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(ApiResponse.ok(shareService.readShared(token)));
    }
}
