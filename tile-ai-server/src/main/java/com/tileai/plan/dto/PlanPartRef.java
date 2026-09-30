package com.tileai.plan.dto;

import java.math.BigDecimal;

/**
 * 一个要落到方案里的「部位 → 瓷砖」映射。
 *
 * <p>与 {@link PlanImageRef} 同一个理由:单独定义,让 {@code plan} 包
 * <b>不依赖</b> {@code generate} 包。否则 {@code generate → plan}(保存方案)与
 * {@code plan → generate}(复用它的 DTO)会绕成一个包级环,以后想把方案模块
 * 单独拆出去时才发现拆不动。
 *
 * @param area 请求里显式给的面积;为 null 表示"用户型推导"
 */
public record PlanPartRef(String part, String sku, BigDecimal area) {
}
