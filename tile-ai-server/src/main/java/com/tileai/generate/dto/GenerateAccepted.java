package com.tileai.generate.dto;

/**
 * 生成任务受理结果(契约 {@code openapi-c.yaml#/components/schemas/GenerateAccepted},
 * {@code required: [taskId, status]})。
 *
 * <p>受理与出图是<b>分离</b>的:这里只回一个任务号,图稍后由前端按 2s 轮询
 * {@code GET /generate/{taskId}} 拿(架构 §6.3)。所以 {@code status} 恒为
 * {@code pending} —— 它描述的是"受理那一刻的状态",不是"任务的结果"。
 */
public record GenerateAccepted(String taskId, String status) {
}
