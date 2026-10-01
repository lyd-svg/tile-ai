package com.tileai.plan.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 重命名方案的请求体(契约 {@code openapi-c.yaml} 的 {@code PATCH /plans/{id}},
 * 内联 schema {@code required: [name]})。
 *
 * <p><b>长度上限为什么不写 {@code @Size(max = 200)}:</b> 注解数的是 UTF-16 单元,
 * 而 {@code t_plan.name} 的 {@code VARCHAR(200)} 数码点 —— 一个 emoji 在注解眼里是 2、
 * 在 MySQL 眼里是 1。挂了 {@code @Size(200)} 就会把"100 个 emoji 的名字"拒掉,
 * 而它在库里完全是合法的;更要命的是同一个上限在两处用了两把不同的尺子。
 *
 * <p>所以 200 这个数<b>只在 {@code PlanNames} 里判一次</b>(按码点),
 * 由 {@code PlanService.rename} 调用,超长报 10001。
 * {@code @NotBlank} 留着 —— 空/空白是格式问题,和长度不是一回事。
 */
public record PlanRenameRequest(
        @NotBlank(message = "方案名不能为空")
        String name) {
}
