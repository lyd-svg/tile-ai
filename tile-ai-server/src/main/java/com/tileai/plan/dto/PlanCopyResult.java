package com.tileai.plan.dto;

/**
 * 「另存为」的结果(契约 {@code openapi-c.yaml} 的 {@code POST /plans/{id}/copy},
 * 内联 schema {@code {planId}})。
 *
 * <p>只回一个 id,不回整个方案 —— 前端拿到它之后要么直接跳详情、要么带着它去重新
 * 生成(§11.4 的"另存"分支)。真把 14 个字段的方案塞回来,前端也只会用这一个。
 *
 * <p>用 {@code long} 而不是 {@code Long}:这个字段一定算得出来(新方案刚插完),
 * 没有"可能没有"这一说。空值在这里只意味着实现写错了,那该炸而不是静默输出 null。
 */
public record PlanCopyResult(long planId) {
}
