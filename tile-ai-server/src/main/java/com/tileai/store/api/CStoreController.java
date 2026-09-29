package com.tileai.store.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tileai.common.api.ApiResponse;
import com.tileai.store.StoreService;
import com.tileai.store.dto.StoreView;

/**
 * C 端门店信息(契约 {@code openapi-c.yaml} 的 store 分组;PRD §5.6 引导线下、§7.6)。
 *
 * <pre>
 *   GET /api/v1/store  单店名称 / 地址 / 电话 / 地图链接(公开)
 * </pre>
 *
 * <p><b>一期边界(PRD §10)</b>:线下为**单店**,本接口**仅展示**门店信息用于引导到店,
 * **不做线上下单/支付** —— 因此这里没有、也不应该有任何下单或支付相关的入口。
 *
 * <p>门店尚未配置时返回 30001(而非空对象)—— 空对象会让前端画出一个没有内容的卡片,
 * 不如明确告诉它"还没配"。
 */
@RestController
@RequestMapping("/api/v1/store")
public class CStoreController {

    private final StoreService storeService;

    public CStoreController(StoreService storeService) {
        this.storeService = storeService;
    }

    @GetMapping
    public ApiResponse<StoreView> get() {
        return ApiResponse.ok(storeService.get());
    }
}
