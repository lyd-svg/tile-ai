/**
 * 产品库模块(架构 §4.1)。
 *
 * <p>SKU 的增删改查、上下架、筛选(花色/材质/规格/风格/价格区间)、
 * 按名称/编号搜索、多角度图。
 *
 * <p>对应 PRD §5.2.1、§7.1;后台接口见 {@code openapi-admin.yaml} 的 products 分组,
 * C 端只读见 {@code openapi-c.yaml} 的 product 分组。落地见 T-0.6 / T-1.2。
 */
package com.tileai.product;
