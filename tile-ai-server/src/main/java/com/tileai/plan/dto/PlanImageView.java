package com.tileai.plan.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 方案效果图(契约 {@code openapi-common.yaml#/components/schemas/PlanImage})。
 *
 * <p>契约<b>没有</b> {@code required},三个属性都可选。
 *
 * <p>{@code view} 的命名口径(D-5,2026-09-30 修订):{@code angles = N} 即出 <b>N 张</b>,
 * 第 1 张 {@code default}、第 k 张({@code k ≥ 2}){@code angle{k-1}} ——
 * 张数必须等于角度数。本类只是搬运 {@code t_plan_image.view},重算命名是出图环节的事。
 *
 * @param url     图片地址(本期是替身占位图 URL,未接真实模型)
 * @param view    视图名:{@code default} / {@code angle1} …
 * @param isCover 是否封面。<b>存量数据里可能</b>一张封面都没有(见 {@code PlanSummaryView} 的说明)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PlanImageView(String url, String view, Boolean isCover) {
}
