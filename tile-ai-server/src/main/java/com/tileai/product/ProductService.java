package com.tileai.product;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tileai.combo.ComboService;
import com.tileai.common.api.PageData;
import com.tileai.common.error.BusinessException;
import com.tileai.common.error.ErrorCode;
import com.tileai.index.IndexSyncService;
import com.tileai.plan.PlanQuoteExpiryMarker;
import com.tileai.product.dto.ProductQuery;
import com.tileai.product.dto.ProductUpsertRequest;
import com.tileai.product.dto.ProductUpsertRequest.ImageItem;
import com.tileai.product.dto.SkuView;
import com.tileai.product.entity.Product;
import com.tileai.product.entity.ProductImage;
import com.tileai.product.mapper.ProductImageMapper;
import com.tileai.product.mapper.ProductMapper;

/**
 * 产品库 SKU 的领域逻辑(架构 §8.2、PRD §5.7)。
 *
 * <p>错误码的使用口径(§10.5):
 * <ul>
 *   <li><b>30001 资源不存在</b> —— ID 指向的 SKU 不存在;</li>
 *   <li><b>30002 状态不允许该操作</b> —— 编号重复、重复上下架、删上架中的 SKU。
 *       这些都是"当前状态不允许",而不是"参数写错了"。</li>
 * </ul>
 *
 * <p><b>删除时的两道检查</b>(契约给 DELETE 列了 30002 —— 状态不允许该操作):
 * <ol>
 *   <li><b>上架中的 SKU 不能删</b>,须先下架(状态类冲突,本任务 T-0.6 实现);</li>
 *   <li><b>被推荐搭配引用的 SKU 不能删</b>(T-0.8 补上)。搭配项存的是
 *       {@code product_id},硬删会留下指向不存在产品的悬挂引用 —— 前台展示时
 *       只能显示空白,属于静默的数据损坏。</li>
 * </ol>
 *
 * <p>注:方案({@code t_plan_part})引用 SKU 时<b>不受此限</b> —— 那儿的
 * {@code sku_code} / {@code product_name} / {@code price_per_sqm} 是<b>快照</b>,
 * 历史方案本就该保留当时的瓷砖信息,与产品是否还在售无关(§8.4)。
 */
@Service
public class ProductService {

    private final ProductMapper productMapper;
    private final ProductImageMapper productImageMapper;
    private final ComboService comboService;
    private final IndexSyncService indexSyncService;
    private final ObjectMapper objectMapper;
    private final PlanQuoteExpiryMarker quoteExpiryMarker;

    public ProductService(ProductMapper productMapper, ProductImageMapper productImageMapper,
                          ComboService comboService, IndexSyncService indexSyncService,
                          ObjectMapper objectMapper, PlanQuoteExpiryMarker quoteExpiryMarker) {
        this.productMapper = productMapper;
        this.productImageMapper = productImageMapper;
        this.comboService = comboService;
        this.indexSyncService = indexSyncService;
        this.objectMapper = objectMapper;
        this.quoteExpiryMarker = quoteExpiryMarker;
    }

    // ------------------------------------------------------------------
    // 查询
    // ------------------------------------------------------------------

    /**
     * 分页列表(契约 {@code GET /admin/products})。
     *
     * <p>{@code keyword} 同时匹配名称与编号;{@code status} 精确匹配。
     * 图片<b>批量取一次</b>而非逐条查 —— 否则一页 20 条就是 21 次查询。
     */
    @Transactional(readOnly = true)
    public PageData<SkuView> page(String keyword, String status, int page, int size) {
        LambdaQueryWrapper<Product> query = Wrappers.lambdaQuery();
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            query.and(w -> w.like(Product::getName, kw).or().like(Product::getSkuCode, kw));
        }
        if (StringUtils.hasText(status)) {
            query.eq(Product::getStatus, status.trim());
        }
        query.orderByDesc(Product::getId);

        Page<Product> result = productMapper.selectPage(new Page<>(page, size), query);

        List<Long> ids = result.getRecords().stream().map(Product::getId).toList();
        Map<Long, List<ProductImage>> images = imagesOf(ids);

        List<SkuView> list = result.getRecords().stream()
                .map(p -> SkuView.of(p, images.getOrDefault(p.getId(), List.of())))
                .toList();

        return PageData.of(list, result.getTotal(), page, size);
    }

    /** 详情(契约 {@code GET /admin/products/{id}})。 */
    @Transactional(readOnly = true)
    public SkuView get(long id) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "SKU 不存在: " + id);
        }
        return SkuView.of(product, imagesOf(List.of(id)).getOrDefault(id, List.of()));
    }

    // ------------------------------------------------------------------
    // C 端(路径 A,只读) —— 契约 openapi-c.yaml 的 product 分组
    // ------------------------------------------------------------------

    /**
     * C 端产品库筛选 / 搜索(契约 {@code GET /products})。
     *
     * <p><b>只返回在售 SKU</b> —— 下架产品不该出现在前台的浏览与搜索里。
     * 这一点与后台列表的区别是刻意的:后台要能看到并管理下架项。
     *
     * <p>{@code style} 与 {@code space} 过滤的是 <b>JSON 数组</b>
     * ({@code style_tags} / {@code applicable_spaces}),用 {@code JSON_CONTAINS}
     * 判成员。不用 {@code LIKE '%"modern"%'}:那是子串匹配,
     * {@code modern_lux} 这类值会被误命中。
     */
    @Transactional(readOnly = true)
    public PageData<SkuView> pageOnSale(ProductQuery query, int page, int size) {
        LambdaQueryWrapper<Product> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(Product::getStatus, Product.STATUS_ON_SALE);

        if (StringUtils.hasText(query.keyword())) {
            String kw = query.keyword().trim();
            wrapper.and(w -> w.like(Product::getName, kw).or().like(Product::getSkuCode, kw));
        }
        if (StringUtils.hasText(query.material())) {
            wrapper.eq(Product::getMaterial, query.material().trim());
        }
        if (StringUtils.hasText(query.colorPattern())) {
            wrapper.like(Product::getColorPattern, query.colorPattern().trim());
        }
        if (StringUtils.hasText(query.spec())) {
            wrapper.eq(Product::getSpec, query.spec().trim());
        }
        if (query.priceMin() != null) {
            wrapper.ge(Product::getPricePerSqm, query.priceMin());
        }
        if (query.priceMax() != null) {
            wrapper.le(Product::getPricePerSqm, query.priceMax());
        }
        if (StringUtils.hasText(query.style())) {
            wrapper.apply("JSON_CONTAINS(style_tags, {0})", jsonLiteral(query.style().trim()));
        }
        if (StringUtils.hasText(query.space())) {
            wrapper.apply("JSON_CONTAINS(applicable_spaces, {0})", jsonLiteral(query.space().trim()));
        }
        wrapper.orderByDesc(Product::getId);

        Page<Product> result = productMapper.selectPage(new Page<>(page, size), wrapper);
        List<Long> ids = result.getRecords().stream().map(Product::getId).toList();
        Map<Long, List<ProductImage>> images = imagesOf(ids);

        List<SkuView> list = result.getRecords().stream()
                .map(p -> SkuView.of(p, images.getOrDefault(p.getId(), List.of())))
                .toList();
        return PageData.of(list, result.getTotal(), page, size);
    }

    /**
     * C 端产品详情(契约 {@code GET /products/{sku}})。
     *
     * <p>按 <b>sku_code</b> 查(后台是按主键 id 查)—— 契约里 C 端路径参数名就是
     * {@code {sku}}。**下架产品一律 30001**,与"不存在"同样处理:
     * 若下架返回详情,用户就能拿着一个买不到的砖去下单。
     */
    @Transactional(readOnly = true)
    public SkuView getOnSale(String skuCode) {
        Product product = productMapper.selectOne(Wrappers.<Product>lambdaQuery()
                .eq(Product::getSkuCode, skuCode));
        if (product == null || !Product.STATUS_ON_SALE.equals(product.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "SKU 不存在或已下架: " + skuCode);
        }
        return SkuView.of(product, imagesOf(List.of(product.getId()))
                .getOrDefault(product.getId(), List.of()));
    }

    /**
     * 把字典键转成 JSON 字符串字面量(带双引号且正确转义),供 {@code JSON_CONTAINS} 使用。
     *
     * <p>不手工拼 {@code "\"" + key + "\""}:键里若含引号或反斜杠会拼出非法 JSON,
     * 轻则查不出结果、重则 SQL 报错。交给 JSON 序列化保证转义正确。
     */
    private String jsonLiteral(String value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("筛选值无法序列化为 JSON: " + value, e);
        }
    }

    // ------------------------------------------------------------------
    // 写入
    // ------------------------------------------------------------------

    /** 新增(契约 {@code POST /admin/products});返回新 SKU 的 id。 */
    @Transactional
    public long create(ProductUpsertRequest request) {
        if (findBySkuCode(request.sku()) != null) {
            throw new BusinessException(ErrorCode.STATE_NOT_ALLOWED,
                    "产品编号已存在: " + request.sku());
        }
        Product product = new Product();
        applyUpsert(product, request);
        // 契约里 status 非必填;不传时按"先建好、再上架"的常规流程落到下架态。
        product.setStatus(StringUtils.hasText(request.status())
                ? request.status() : Product.STATUS_OFF_SALE);

        productMapper.insert(product);
        replaceImages(product.getId(), request.images());

        // 与业务同事务登记索引同步事件(架构 §6.4 的 Outbox)
        indexSyncService.publishProductUpsert(product);
        return product.getId();
    }

    /** 编辑(契约 {@code PUT /admin/products/{id}})。 */
    @Transactional
    public void update(long id, ProductUpsertRequest request) {
        Product existing = productMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "SKU 不存在: " + id);
        }

        Product sameSku = findBySkuCode(request.sku());
        if (sameSku != null && !sameSku.getId().equals(id)) {
            throw new BusinessException(ErrorCode.STATE_NOT_ALLOWED,
                    "产品编号已被其它 SKU 占用: " + request.sku());
        }

        // 改价前先留底:applyUpsert 会把请求里的值写进 existing,晚一行就拿不到旧值。
        // SKU 也用**旧值** —— t_plan_part.sku_code 是生成那一刻的快照,用新值反查一条都匹配不上。
        BigDecimal oldPrice = existing.getPricePerSqm();
        String oldSkuCode = existing.getSkuCode();

        applyUpsert(existing, request);
        if (StringUtils.hasText(request.status())) {
            existing.setStatus(request.status());
        }
        productMapper.updateById(existing);

        // 单价变了 → 引用了这块砖的在用方案,报价就不再准了(T-1.15)。
        // 只改名字 / 图片 / 状态的编辑不标过期 —— 那些改动不影响金额。
        // 与价格写入同在 @Transactional 内:要么价和标记都生效,要么都不生效。
        if (PlanQuoteExpiryMarker.priceChanged(oldPrice, request.pricePerSqm())) {
            quoteExpiryMarker.bySkuCode(oldSkuCode);
        }

        // PUT 语义是整体替换:传了 images 就以传入的为准(传 null 表示不动,
        // 传空数组表示清空)。这一点对前端很重要,不能含糊。
        if (request.images() != null) {
            replaceImages(id, request.images());
        }

        indexSyncService.publishProductUpsert(existing);
    }

    /**
     * 删除(契约 {@code DELETE /admin/products/{id}})。
     *
     * <p><b>硬删除</b>:{@code t_product} 没有 {@code is_deleted}(§8.2 未列该字段,
     * 逻辑删除只用在 {@code t_plan} 上)。删除时一并清掉该 SKU 的多角度图,
     * 避免留下悬挂的 {@code t_product_image} 行。
     */
    @Transactional
    public void delete(long id) {
        Product existing = productMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "SKU 不存在: " + id);
        }
        if (Product.STATUS_ON_SALE.equals(existing.getStatus())) {
            throw new BusinessException(ErrorCode.STATE_NOT_ALLOWED,
                    "上架中的 SKU 不能删除,请先下架");
        }
        if (comboService.isProductReferenced(id)) {
            throw new BusinessException(ErrorCode.STATE_NOT_ALLOWED,
                    "该 SKU 已被推荐搭配引用,请先从搭配中移除再删除");
        }
        productImageMapper.delete(Wrappers.<ProductImage>lambdaQuery()
                .eq(ProductImage::getProductId, id));
        productMapper.deleteById(id);

        indexSyncService.publishProductDelete(existing.getSkuCode());
    }

    /**
     * 上架 / 下架(契约 {@code PATCH /admin/products/{id}/status})。
     *
     * <p>重复设置同一状态返回 30002 —— 契约把这种情形明确列为
     * "对同款砖重复上下架"(§10.5 对 30002 的举例)。
     */
    @Transactional
    public void changeStatus(long id, String status) {
        Product existing = productMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "SKU 不存在: " + id);
        }
        if (status.equals(existing.getStatus())) {
            throw new BusinessException(ErrorCode.STATE_NOT_ALLOWED,
                    "该 SKU 已处于 " + status + " 状态,无需重复操作");
        }

        // 只更新 status 一个字段:用局部对象而非整实体,
        // 避免"读出来—改一个字段—整体写回"把并发期间的其它改动覆盖掉。
        Product patch = new Product();
        patch.setId(id);
        patch.setStatus(status);
        productMapper.updateById(patch);

        // 上下架也要同步索引:§9.3 要求"下架产品在检索过滤中被排除",
        // 而 Python 是依据 payload 里的 status 做过滤的 —— 不同步就等于下架了还能搜到。
        existing.setStatus(status);
        indexSyncService.publishProductUpsert(existing);
    }

    // ------------------------------------------------------------------

    private Product findBySkuCode(String skuCode) {
        return productMapper.selectOne(Wrappers.<Product>lambdaQuery()
                .eq(Product::getSkuCode, skuCode));
    }

    private static void applyUpsert(Product target, ProductUpsertRequest request) {
        target.setSkuCode(request.sku());
        target.setName(request.name());
        target.setSpec(request.spec());
        target.setMaterial(request.material());
        target.setSurface(request.surface());
        target.setColorPattern(request.colorPattern());
        target.setPricePerSqm(request.pricePerSqm());
        target.setApplicableSpaces(request.applicableSpaces());
        target.setStyleTags(request.styleTags());
        target.setCoverImage(request.coverImage());
    }

    /** 整体替换多角度图。{@code sort} 缺省时按数组顺序补齐,保证展示顺序稳定。 */
    private void replaceImages(long productId, List<ImageItem> images) {
        productImageMapper.delete(Wrappers.<ProductImage>lambdaQuery()
                .eq(ProductImage::getProductId, productId));
        if (images == null || images.isEmpty()) {
            return;
        }
        int fallbackSort = 0;
        for (ImageItem item : images) {
            ProductImage image = new ProductImage();
            image.setProductId(productId);
            image.setUrl(item.url());
            image.setAngle(item.angle());
            image.setSort(item.sort() == null ? fallbackSort : item.sort());
            productImageMapper.insert(image);
            fallbackSort++;
        }
    }

    /** 批量取图片并按 productId 归组,避免逐条查询。 */
    private Map<Long, List<ProductImage>> imagesOf(List<Long> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        List<ProductImage> all = productImageMapper.selectList(Wrappers.<ProductImage>lambdaQuery()
                .in(ProductImage::getProductId, productIds)
                .orderByAsc(ProductImage::getProductId)
                .orderByAsc(ProductImage::getSort)
                .orderByAsc(ProductImage::getId));

        Map<Long, List<ProductImage>> grouped = new LinkedHashMap<>();
        for (ProductImage image : all) {
            grouped.computeIfAbsent(image.getProductId(), k -> new java.util.ArrayList<>())
                    .add(image);
        }
        return grouped;
    }
}
