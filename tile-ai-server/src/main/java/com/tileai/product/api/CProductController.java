package com.tileai.product.api;

import java.math.BigDecimal;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tileai.common.api.ApiResponse;
import com.tileai.common.api.PageData;
import com.tileai.product.ProductService;
import com.tileai.product.dto.ProductQuery;
import com.tileai.product.dto.SkuView;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * C 端产品库浏览(契约 {@code openapi-c.yaml} 的 product 分组;PRD §5.2.1 路径 A「直接选砖」)。
 *
 * <pre>
 *   GET /api/v1/products       产品库筛选 / 搜索(公开)
 *   GET /api/v1/products/{sku} 产品详情(公开)
 * </pre>
 *
 * <p><b>业务结果一律 HTTP 200,业务错误码 10002/10003 用真实状态码</b> ——
 * 不过本接口全部公开,不涉及鉴权。
 *
 * <p>与后台 {@code /admin/products} 的三处关键差异(不是疏漏,是刻意的):
 * <ol>
 *   <li>路径参数是 <b>{@code {sku}}</b>(编号)而非 {@code {id}}(主键)—— 前端的
 *       "选砖"流程里流传的是编号;</li>
 *   <li><b>只返回在售 SKU</b> —— 下架的不该出现在前台浏览与搜索里;</li>
 *   <li>可通过 <b>style / space</b> 按字典键筛选,后台列表没有这两个维度。</li>
 * </ol>
 */
@RestController
@RequestMapping("/api/v1/products")
@Validated
public class CProductController {

    private static final String DEFAULT_PAGE = "1";
    private static final String DEFAULT_SIZE = "20";

    private final ProductService productService;

    public CProductController(ProductService productService) {
        this.productService = productService;
    }

    /** 筛选参数名按 D-8 裁决,见 {@link ProductQuery}。 */
    @GetMapping
    public ApiResponse<PageData<SkuView>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String material,
            @RequestParam(required = false) String colorPattern,
            @RequestParam(required = false) String spec,
            @RequestParam(required = false) String style,
            @RequestParam(required = false) String space,
            @RequestParam(required = false) BigDecimal priceMin,
            @RequestParam(required = false) BigDecimal priceMax,
            @RequestParam(defaultValue = DEFAULT_PAGE) @Min(value = 1, message = "页码从 1 开始") int page,
            @RequestParam(defaultValue = DEFAULT_SIZE) @Min(value = 1, message = "每页条数至少为 1")
            @Max(value = 100, message = "每页条数最多 100") int size) {

        ProductQuery query = new ProductQuery(keyword, material, colorPattern, spec,
                style, space, priceMin, priceMax);
        return ApiResponse.ok(productService.pageOnSale(query, page, size));
    }

    /** 详情;不存在**或已下架**都返回 30001。 */
    @GetMapping("/{sku}")
    public ApiResponse<SkuView> detail(@PathVariable String sku) {
        return ApiResponse.ok(productService.getOnSale(sku));
    }
}
