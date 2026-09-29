package com.tileai.combo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tileai.combo.dto.ComboUpsertRequest;
import com.tileai.combo.dto.ComboView;
import com.tileai.combo.entity.RecommendCombo;
import com.tileai.combo.entity.RecommendComboItem;
import com.tileai.combo.mapper.RecommendComboItemMapper;
import com.tileai.combo.mapper.RecommendComboMapper;
import com.tileai.common.api.PageData;
import com.tileai.common.error.BusinessException;
import com.tileai.common.error.ErrorCode;
import com.tileai.index.IndexSyncService;
import com.tileai.product.entity.Product;
import com.tileai.product.mapper.ProductMapper;

/**
 * 推荐搭配的领域逻辑(架构 §8.2;PRD §5.2.2 路径 B —— <b>人工维护,不做自动组合</b>)。
 *
 * <p>两处映射差异是本类的核心职责:
 * <ul>
 *   <li>表里存 {@code product_id},契约对外用 {@code sku} —— 写入时按编号解析,
 *       读取时反查编号;</li>
 *   <li>表里<b>没有 name 列</b>,契约 {@code ComboItem.name}(冗余展示名)
 *       是读取时从产品表带出的。</li>
 * </ul>
 */
@Service
public class ComboService {

    private final RecommendComboMapper comboMapper;
    private final RecommendComboItemMapper comboItemMapper;
    private final ProductMapper productMapper;
    private final IndexSyncService indexSyncService;

    public ComboService(RecommendComboMapper comboMapper,
                        RecommendComboItemMapper comboItemMapper,
                        ProductMapper productMapper,
                        IndexSyncService indexSyncService) {
        this.comboMapper = comboMapper;
        this.comboItemMapper = comboItemMapper;
        this.productMapper = productMapper;
        this.indexSyncService = indexSyncService;
    }

    // ------------------------------------------------------------------
    // 查询
    // ------------------------------------------------------------------

    /** 分页列表(契约 {@code GET /admin/combos});{@code space} / {@code style} 可选过滤。 */
    @Transactional(readOnly = true)
    public PageData<ComboView> page(String space, String style, int page, int size) {
        LambdaQueryWrapper<RecommendCombo> query = Wrappers.lambdaQuery();
        if (StringUtils.hasText(space)) {
            query.eq(RecommendCombo::getSpace, space.trim());
        }
        if (StringUtils.hasText(style)) {
            query.eq(RecommendCombo::getStyle, style.trim());
        }
        query.orderByDesc(RecommendCombo::getSort).orderByDesc(RecommendCombo::getId);

        Page<RecommendCombo> result = comboMapper.selectPage(new Page<>(page, size), query);
        Map<Long, List<ComboView.Item>> items = itemsOf(
                result.getRecords().stream().map(RecommendCombo::getId).toList());

        List<ComboView> list = result.getRecords().stream()
                .map(combo -> toView(combo, items.getOrDefault(combo.getId(), List.of())))
                .toList();
        return PageData.of(list, result.getTotal(), page, size);
    }

    /** 详情(契约 {@code GET /admin/combos/{id}};失败码 30001)。 */
    @Transactional(readOnly = true)
    public ComboView get(long id) {
        RecommendCombo combo = comboMapper.selectById(id);
        if (combo == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "搭配不存在: " + id);
        }
        return toView(combo, itemsOf(List.of(id)).getOrDefault(id, List.of()));
    }

    // ------------------------------------------------------------------
    // C 端(路径 B,只读) —— 契约 openapi-c.yaml 的 combo 分组
    // ------------------------------------------------------------------

    /**
     * C 端推荐搭配列表(契约 {@code GET /combos})。
     *
     * <p><b>只返回上架搭配</b> —— 下架的不该出现在前台。
     * 与后台列表的区别是刻意的:后台要能看到并管理下架项。
     */
    @Transactional(readOnly = true)
    public PageData<ComboView> pageOnSale(String space, String style, int page, int size) {
        LambdaQueryWrapper<RecommendCombo> query = Wrappers.lambdaQuery();
        query.eq(RecommendCombo::getStatus, RecommendCombo.STATUS_ON_SALE);
        if (StringUtils.hasText(space)) {
            query.eq(RecommendCombo::getSpace, space.trim());
        }
        if (StringUtils.hasText(style)) {
            query.eq(RecommendCombo::getStyle, style.trim());
        }
        query.orderByDesc(RecommendCombo::getSort).orderByDesc(RecommendCombo::getId);

        Page<RecommendCombo> result = comboMapper.selectPage(new Page<>(page, size), query);
        Map<Long, List<ComboView.Item>> items = itemsOf(
                result.getRecords().stream().map(RecommendCombo::getId).toList());

        List<ComboView> list = result.getRecords().stream()
                .map(combo -> toView(combo, items.getOrDefault(combo.getId(), List.of())))
                .toList();
        return PageData.of(list, result.getTotal(), page, size);
    }

    /**
     * C 端搭配详情(契约 {@code GET /combos/{id}})。
     *
     * <p><b>下架搭配一律 30001</b>,与"不存在"同样处理 —— 否则用户能拿着一个
     * 前台已撤下的搭配继续往下走。
     */
    @Transactional(readOnly = true)
    public ComboView getOnSale(long id) {
        RecommendCombo combo = comboMapper.selectById(id);
        if (combo == null || !RecommendCombo.STATUS_ON_SALE.equals(combo.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "搭配不存在或已下架: " + id);
        }
        return toView(combo, itemsOf(List.of(id)).getOrDefault(id, List.of()));
    }

    /**
     * 某产品是否被任何搭配引用。
     *
     * <p>供产品库删除 SKU 时做前置检查(T-0.8 验证判据:被引用的 SKU 不可删)。
     * 产品库与搭配是两个模块,这里以方法而非表依赖的形式暴露,避免产品模块
     * 直接去查搭配的表。
     */
    @Transactional(readOnly = true)
    public boolean isProductReferenced(long productId) {
        return comboItemMapper.selectCount(Wrappers.<RecommendComboItem>lambdaQuery()
                .eq(RecommendComboItem::getProductId, productId)) > 0;
    }

    // ------------------------------------------------------------------
    // 写入
    // ------------------------------------------------------------------

    /** 新增(契约 {@code POST /admin/combos});返回新搭配 id。 */
    @Transactional
    public long create(ComboUpsertRequest request) {
        Map<String, Product> products = resolveProducts(request.items());

        RecommendCombo combo = new RecommendCombo();
        apply(combo, request);
        combo.setStatus(StringUtils.hasText(request.status())
                ? request.status() : RecommendCombo.STATUS_OFF_SALE);
        comboMapper.insert(combo);

        replaceItems(combo.getId(), request.items(), products);

        // 与业务同事务登记索引同步事件(架构 §6.4)
        indexSyncService.publishComboUpsert(combo);
        return combo.getId();
    }

    /** 编辑(契约 {@code PUT /admin/combos/{id}})。items 为整体替换。 */
    @Transactional
    public void update(long id, ComboUpsertRequest request) {
        RecommendCombo existing = comboMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "搭配不存在: " + id);
        }
        Map<String, Product> products = resolveProducts(request.items());

        apply(existing, request);
        if (StringUtils.hasText(request.status())) {
            existing.setStatus(request.status());
        }
        comboMapper.updateById(existing);

        replaceItems(id, request.items(), products);

        indexSyncService.publishComboUpsert(existing);
    }

    /** 删除(契约 {@code DELETE /admin/combos/{id}});一并清掉关联项,不留悬挂行。 */
    @Transactional
    public void delete(long id) {
        RecommendCombo existing = comboMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "搭配不存在: " + id);
        }
        comboItemMapper.delete(Wrappers.<RecommendComboItem>lambdaQuery()
                .eq(RecommendComboItem::getComboId, id));
        comboMapper.deleteById(id);

        indexSyncService.publishComboDelete(id);
    }

    // ------------------------------------------------------------------

    private static void apply(RecommendCombo combo, ComboUpsertRequest request) {
        combo.setName(request.name());
        combo.setSpace(request.space());
        combo.setStyle(request.style());
        combo.setRefPriceMin(request.refPriceMin());
        combo.setRefPriceMax(request.refPriceMax());
        combo.setCoverImage(request.coverImage());
        combo.setSort(request.sort() == null ? 0 : request.sort());
    }

    /**
     * 把请求里的 sku 解析成产品。
     *
     * <p>引用不存在的 SKU 直接拒绝 —— 否则搭配详情里会出现一款"查不到的砖",
     * 而前台展示时只能显示空白,属于静默的数据损坏。
     */
    private Map<String, Product> resolveProducts(List<ComboUpsertRequest.Item> items) {
        Set<String> skus = items.stream()
                .map(ComboUpsertRequest.Item::sku)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<String, Product> bySku = productMapper.selectList(Wrappers.<Product>lambdaQuery()
                        .in(Product::getSkuCode, skus))
                .stream()
                .collect(Collectors.toMap(Product::getSkuCode, p -> p, (a, b) -> a));

        List<String> missing = skus.stream().filter(sku -> !bySku.containsKey(sku)).toList();
        if (!missing.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID,
                    "引用的瓷砖编号不存在:" + String.join("、", missing));
        }
        return bySku;
    }

    /** 整体替换搭配项。 */
    private void replaceItems(long comboId, List<ComboUpsertRequest.Item> items,
                              Map<String, Product> products) {
        comboItemMapper.delete(Wrappers.<RecommendComboItem>lambdaQuery()
                .eq(RecommendComboItem::getComboId, comboId));

        int fallbackSort = 0;
        for (ComboUpsertRequest.Item item : items) {
            RecommendComboItem entity = new RecommendComboItem();
            entity.setComboId(comboId);
            entity.setProductId(products.get(item.sku()).getId());
            entity.setPart(item.part());
            entity.setNote(item.note());
            entity.setSort(item.sort() == null ? fallbackSort : item.sort());
            comboItemMapper.insert(entity);
            fallbackSort++;
        }
    }

    /** 批量取搭配项并转成视图(含从产品表带出的 sku 与展示名),避免逐条查询。 */
    private Map<Long, List<ComboView.Item>> itemsOf(List<Long> comboIds) {
        if (comboIds.isEmpty()) {
            return Map.of();
        }
        List<RecommendComboItem> items = comboItemMapper.selectList(
                Wrappers.<RecommendComboItem>lambdaQuery()
                        .in(RecommendComboItem::getComboId, comboIds)
                        .orderByAsc(RecommendComboItem::getComboId)
                        .orderByAsc(RecommendComboItem::getSort)
                        .orderByAsc(RecommendComboItem::getId));
        if (items.isEmpty()) {
            return Map.of();
        }

        Set<Long> productIds = items.stream().map(RecommendComboItem::getProductId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, Product> products = productMapper.selectBatchIds(productIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p, (a, b) -> a));

        Map<Long, List<ComboView.Item>> grouped = new LinkedHashMap<>();
        for (RecommendComboItem item : items) {
            Product product = products.get(item.getProductId());
            grouped.computeIfAbsent(item.getComboId(), k -> new ArrayList<>())
                    .add(new ComboView.Item(
                            product == null ? null : product.getSkuCode(),
                            product == null ? null : product.getName(),
                            item.getPart(),
                            item.getNote(),
                            item.getSort()));
        }
        return grouped;
    }

    private static ComboView toView(RecommendCombo combo, List<ComboView.Item> items) {
        return new ComboView(combo.getId(), combo.getName(), combo.getSpace(), combo.getStyle(),
                combo.getRefPriceMin(), combo.getRefPriceMax(), combo.getCoverImage(),
                combo.getStatus(), combo.getSort(), items);
    }
}
