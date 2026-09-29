package com.tileai.product.imports;

import java.math.BigDecimal;
import java.util.List;

/**
 * 一行通过校验的导入数据 —— 已经与文件格式(xlsx / CSV)无关。
 *
 * <p>解析、校验、落库三段刻意分开:校验产出本对象,落库只认本对象。
 * 这样校验规则只写一份,不会出现"CSV 校验了、Excel 没校验"只在某种格式下
 * 才暴露的漏洞。
 */
public record ProductImportData(
        String sku,
        String name,
        String spec,
        String material,
        String surface,
        String colorPattern,
        BigDecimal pricePerSqm,
        List<String> applicableSpaces,
        List<String> styleTags,
        String coverImage,
        List<String> imageUrls) {
}
