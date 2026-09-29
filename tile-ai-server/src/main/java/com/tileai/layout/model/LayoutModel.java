package com.tileai.layout.model;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/**
 * 结构化户型数据模型(空间列表 + 尺寸 + 门窗)。
 *
 * <p>契约:{@code openapi-common.yaml#/components/schemas/LayoutModel},
 * {@code required: [spaces]},{@code spaces} 的 {@code minItems: 1}。
 * 字段定义以架构 §8.3「结构化户型模型字段定义」表为准(D-2 定稿)。
 *
 * <p><b>加密边界</b>:模板里的这份模型是<b>明文</b>(§8.3:系统公共模板);
 * 只有随方案固化的户型快照({@code t_layout.layout_enc} / {@code t_plan.layout_enc})
 * 才做 AES-256-GCM 加密。对外 API 一律传明文,对外 schema 中不得出现 {@code layout_enc}(契约 T-8)。
 */
public record LayoutModel(
        @NotEmpty(message = "户型至少要有一个空间")
        @Size(max = 50, message = "单个户型最多 50 个空间")
        List<@Valid LayoutSpace> spaces) {
}
