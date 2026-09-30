package com.tileai.internal;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tileai.common.api.ApiResponse;
import com.tileai.furnish.FurnishStyleService;
import com.tileai.furnish.dto.FurnishStyleView;
import com.tileai.furnish.entity.FurnishStyle;
import com.tileai.internal.dto.FurnishPromptView;
import com.tileai.product.ProductService;
import com.tileai.product.dto.SkuView;

/**
 * Python 取权威数据的两条接口(契约 {@code openapi-internal.yaml};
 * 架构 §4.3「数据服务」、§5 关键约定)。
 *
 * <pre>
 *   GET /internal/products/{sku}      权威 SKU(含**照片**)
 *   GET /internal/furnish/prompts     按「空间 × 风格」取陈设提示词
 * </pre>
 *
 * <p><b>为什么必须由 Java 提供</b>:Python 不持有业务数据副本、也不直连 MySQL
 * (不变量 4)。所以"某块砖的规格与照片""这个空间配什么陈设"这两件事,
 * Python 只能问 Java。
 *
 * <p><b>这两个端点只是转发,没有任何新查询逻辑</b> —— 底层就是
 * {@link ProductService#getOnSale(String)} 与 {@link FurnishStyleService#list}。
 * 刻意不在这里补业务:一旦这里出现第二条查询路径,权限、缓存、字典口径
 * 就会多出一份可能不同的实现。
 *
 * <p><b>照片是出图的输入</b>(§15.3 多参考图条件生成):{@link SkuView} 里的
 * {@code coverImage} / {@code images[]} 就是 Python 要拿去当参考图的东西,
 * 它们随本接口一起给到,不需要再加一个"取照片"的接口。
 */
@RestController
@RequestMapping("/internal")
public class InternalDataController {

    private final ProductService productService;
    private final FurnishStyleService furnishStyleService;

    public InternalDataController(ProductService productService,
                                  FurnishStyleService furnishStyleService) {
        this.productService = productService;
        this.furnishStyleService = furnishStyleService;
    }

    /**
     * 取权威 SKU。
     *
     * <p>复用 C 端详情那条路({@code getOnSale}):<b>下架与不存在同样返 30001</b>。
     * 这不是偷懒 —— 用户选中某块砖之后它被下架,继续拿它的照片出一张买不到的图
     * 才是更坏的结果。Python 侧按"取不到"降级(T-1.10 已定义:保留部位、
     * 材质短语为空、记 WARN),生成照常。
     *
     * <p>响应里包含 {@code coverImage} 与 {@code images[]} —— <b>这就是参考图</b>。
     */
    @GetMapping("/products/{sku}")
    public ApiResponse<SkuView> product(@PathVariable String sku) {
        return ApiResponse.ok(productService.getOnSale(sku));
    }

    /**
     * 取陈设提示词。
     *
     * <p><b>未配置时降级,不报错</b>(架构 §13.3):返回
     * {@code prompt:""} + {@code furnishConfigured:false},生成照常进行,
     * Python 侧记 WARN(它的 {@code build_prompts} 已经这么做了)。
     *
     * <p>为什么不返 30001:字典里「空间 × 风格」有 20 个组合,后台很可能配不全;
     * 让未配置变成"生成失败",用户点下去直接看不到图,还会以为是自己操作错了。
     * 陈设缺失是<b>画质问题</b>,该暴露给监控与后台,不该让用户承担。
     *
     * <p><b>只认 {@code on_sale}</b>:后台把一条配置置为 {@code off_sale} 的意图
     * 就是"这条别用"。若不管状态照样返回,那个开关对出图就是个摆设。
     * 注意 upsert 在未传 status 时默认落 {@code off_sale} ——
     * 即"建了但没明确启用"视同未生效,这个保守默认是对的。
     */
    @GetMapping("/furnish/prompts")
    public ApiResponse<FurnishPromptView> furnishPrompts(@RequestParam String space,
                                                         @RequestParam String style) {
        List<FurnishStyleView> configs = furnishStyleService.list(space, style);
        return ApiResponse.ok(configs.stream()
                .filter(config -> FurnishStyle.STATUS_ON_SALE.equals(config.status()))
                // 唯一键就是 (space, style),命中至多一条;取第一条是为了确定,
                // 而不是"随便挑一个"
                .findFirst()
                .map(FurnishPromptView::configured)
                .orElseGet(FurnishPromptView::missing));
    }
}
