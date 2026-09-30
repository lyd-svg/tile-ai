package com.tileai.generate.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 任务进度 / 结果(契约 {@code openapi-c.yaml#/components/schemas/TaskProgress},
 * {@code required: [taskId, status]})。
 *
 * <p>一次响应同时承载三件事:进度(pending/running 时)、结果(success 时)、
 * 失败原因(failed 时)。前端 2s 轮询的就是它,所以<b>字段随状态逐步填充</b>,
 * 未到那一步的字段为 null。
 *
 * <p>{@code @JsonInclude(NON_NULL)} 让尚未产生的字段整个省略,而不是输出
 * {@code "images": null} —— 契约把 {@code images} 声明为 {@code array} 且未标
 * {@code nullable},显式 null 会被 schema 判为类型不符(与 {@code LayoutSpace}
 * 同一个坑)。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TaskProgressView(
        String taskId,
        String status,
        /** 0–100。二期 SSE 复用同字段(契约注明)。 */
        Integer progress,
        /** 成功后方案 id。新建方案时由这里回传(契约 {@code GenerateRequest.planId} 的说明)。 */
        Long planId,
        /** 成功后可用。 */
        List<PlanImageView> images,
        /** 完整部位列表。一期无 AI 补齐,故 {@code source} 恒为 {@code user}。 */
        List<PartSelection> parts) {

    /** 方案效果图(契约 {@code common.PlanImage})。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PlanImageView(String url, String view, Boolean isCover) {
    }
}
