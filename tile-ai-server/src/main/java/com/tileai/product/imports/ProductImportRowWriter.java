package com.tileai.product.imports;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tileai.index.IndexSyncService;
import com.tileai.plan.PlanQuoteExpiryMarker;
import com.tileai.product.entity.Product;
import com.tileai.product.entity.ProductImage;
import com.tileai.product.mapper.ProductImageMapper;
import com.tileai.product.mapper.ProductMapper;

/**
 * 单行导入的落库(架构 §8.7)。
 *
 * <p>独立成组件而不是塞进 {@code ProductImportService} 的私有方法,是为了让
 * {@link Transactional} 真正生效 —— Spring 的事务是基于代理的,
 * <b>同一个类内部的自调用不会走代理</b>,注解会被静默忽略,
 * 结果是"以为有事务,其实没有"。这个坑不报错,只在出问题时才暴露。
 *
 * <p>{@code REQUIRES_NEW} 保证<b>一行一个事务</b>:某行落库失败只回滚该行,
 * 符合 §8.7「错误行不写入」,而不会把前面已成功的几百行一起回滚掉。
 */
@Component
public class ProductImportRowWriter {

    private final ProductMapper productMapper;
    private final ProductImageMapper productImageMapper;
    private final IndexSyncService indexSyncService;
    private final PlanQuoteExpiryMarker quoteExpiryMarker;

    public ProductImportRowWriter(ProductMapper productMapper,
                                  ProductImageMapper productImageMapper,
                                  IndexSyncService indexSyncService,
                                  PlanQuoteExpiryMarker quoteExpiryMarker) {
        this.productMapper = productMapper;
        this.productImageMapper = productImageMapper;
        this.indexSyncService = indexSyncService;
        this.quoteExpiryMarker = quoteExpiryMarker;
    }

    /**
     * 写入一行(新增或更新)。
     *
     * @param existingProductId 已存在的 SKU 主键;{@code null} 表示新增
     * @param previousPrice     该 SKU 变更前的单价;新增时为 {@code null}。
     *                          导入也会改价,所以这里是第三个「调价触发点」(T-1.15)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long apply(ProductImportData data, Long existingProductId, BigDecimal previousPrice) {
        Product product = new Product();
        product.setSkuCode(data.sku());
        product.setName(data.name());
        product.setSpec(data.spec());
        product.setMaterial(data.material());
        product.setSurface(data.surface());
        product.setColorPattern(data.colorPattern());
        product.setPricePerSqm(data.pricePerSqm());
        product.setApplicableSpaces(data.applicableSpaces());
        product.setStyleTags(data.styleTags());
        product.setCoverImage(data.coverImage());

        if (existingProductId == null) {
            // 新增一律落到下架态:批量导入的数据不该未经审核就对外可见,
            // 由运营核对后再挑着上架(§5.7 的上下架流程)。
            product.setStatus(Product.STATUS_OFF_SALE);
            productMapper.insert(product);
        } else {
            product.setId(existingProductId);
            // 刻意<b>不设</b> status:MyBatis-Plus 的 updateById 会跳过 null 字段,
            // 从而保留该 SKU 现有的上下架状态。
            // 若在这里写死状态,重新导入一次就会把所有已上架的 SKU 打回下架 ——
            // 那是一次静默的线上事故。
            productMapper.updateById(product);
        }

        // 覆盖导入把单价改了 → 引用了这个 SKU 的在用方案,报价就不再准了(T-1.15)。
        //
        // 必须留在本方法内(即 REQUIRES_NEW 事务内):若挪到调用方的循环之后批量标一次,
        // 进程在中间挂掉就会留下一批「价已改、方案却显示未过期」的记录 ——
        // 那正是这个功能要消灭的状态。代价是每改价一行多一条 UPDATE,接受。
        //
        // 按**变更前**的 skuCode 反查:方案里存的是生成那一刻的 SKU 快照。
        // 新增行(previousPrice == null 且 existingProductId == null)不可能有方案引用它,跳过。
        if (existingProductId != null
                && PlanQuoteExpiryMarker.priceChanged(previousPrice, data.pricePerSqm())) {
            quoteExpiryMarker.bySkuCode(data.sku());
        }

        replaceImages(product.getId(), data.imageUrls());

        // 导入的 SKU 同样要进索引 —— 否则批量导入的数据在 AI 咨询里搜不到。
        // 与写入在同一个 REQUIRES_NEW 事务内,满足 Outbox 的同事务要求(§6.4)。
        //
        // 注:一行一个事件。契约允许一个事件带多个 item(批量),大文件导入
        // (数千行)可改为按批投递以减少消息数 —— 但那是优化,当前写法更容易定位问题。
        indexSyncService.publishProductUpsert(product);
        return product.getId();
    }

    /** 整体替换多角度图,与 T-0.6 的编辑语义一致。 */
    private void replaceImages(long productId, List<String> imageUrls) {
        productImageMapper.delete(Wrappers.<ProductImage>lambdaQuery()
                .eq(ProductImage::getProductId, productId));
        if (imageUrls == null || imageUrls.isEmpty()) {
            return;
        }
        for (int i = 0; i < imageUrls.size(); i++) {
            ProductImage image = new ProductImage();
            image.setProductId(productId);
            image.setUrl(imageUrls.get(i));
            image.setAngle(i == 0 ? "default" : "angle" + i);
            image.setSort(i);
            productImageMapper.insert(image);
        }
    }
}
