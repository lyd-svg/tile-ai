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
 * @param part   {@code floor} / {@code wall} / {@code local}
 * @param sku    瓷砖编号
 * @param area   请求里显式给的面积;为 null 表示"用户型推导"
 * @param source 部位瓷砖来源(契约 {@code PartSource}):{@code user} 用户显式选择 /
 *               {@code ai} AI 自动补齐。**调用方必须先归一成非空** ——
 *               契约里请求可省略、但落库与对外返回都必填,归一放在
 *               {@code PartSelection.withDefaultSource()},不在这里兜底
 *               (每个读取点各判一次 null,必然有一处会漏)。
 */
public record PlanPartRef(String part, String sku, BigDecimal area, String source) {
}
