package com.tileai.layout.model;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 户型中的单个空间。契约 {@code openapi-common.yaml#/components/schemas/LayoutSpace},
 * {@code required: [type, length, width, height]}(架构 §8.3 字段表,D-2 定稿)。
 *
 * <p>{@code type} 的取值由后台字典配置(§8.5 {@code t_dict};D-14:前端与契约不硬编码),
 * 因此这里<b>只校验非空与长度</b>,不枚举具体空间类型 —— 枚举化等于把字典写死进代码。
 * 取值合法性由 {@code GET /dicts} 的消费方负责,不在这里判。
 *
 * <p><b>{@code @JsonInclude(NON_NULL)} 不是可选项</b>:可选字段为 null 时必须<b>整个省略</b>,
 * 而不是序列化成 {@code "wallCount": null}。契约里这些字段声明为 {@code integer} /
 * {@code array} 且未标注 {@code nullable} —— 显式 null 会被 schema 判为类型不符。
 * 这个差异只有把落库 JSON 拿去做真实的 schema 校验才会暴露(见 {@code LayoutModelSchemaTest})。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LayoutSpace(
        @NotBlank(message = "空间类型不能为空")
        @Size(max = 50, message = "空间类型不能超过 50 字符")
        String type,

        @Size(max = 50, message = "空间名称不能超过 50 字符")
        String name,

        /**
         * 长(米)。**上限 30** —— 住宅空间的物理极限(最大开间十几米),留足余量。
         *
         * <p>⚠️ 上限必须有:面积按 {@code 长 × 宽} 派生,填个一万米就是天文数字,
         * 大到超过 {@code t_plan_part.gross_area} 的列宽时会**直接落库报错**。
         * 见架构 §8.3「尺寸上限的来历」。
         */
        @NotNull(message = "长不能为空")
        @DecimalMin(value = "0.01", message = "长必须大于 0")
        @DecimalMax(value = "30", message = "长不能超过 30 米")
        BigDecimal length,

        /** 宽(米)。上限同 {@code length}。 */
        @NotNull(message = "宽不能为空")
        @DecimalMin(value = "0.01", message = "宽必须大于 0")
        @DecimalMax(value = "30", message = "宽不能超过 30 米")
        BigDecimal width,

        /** 层高(米)。上限 **10** —— 挑高客厅到 6 米,10 米是极限。 */
        @NotNull(message = "层高不能为空")
        @DecimalMin(value = "0.01", message = "层高必须大于 0")
        @DecimalMax(value = "10", message = "层高不能超过 10 米")
        BigDecimal height,

        /** 墙面数;§8.3 字段表标注默认 4,可省略。不参与面积公式。 */
        @Min(value = 1, message = "墙面数至少为 1")
        @Max(value = 20, message = "墙面数最多 20")
        Integer wallCount,

        @Size(max = 20, message = "单个空间最多 20 个开口")
        List<@Valid Opening> openings,

        /** 各部位面积;<b>可省略</b> —— 省略时由服务端推导,见 {@code LayoutAreaCalculator}。 */
        @Valid
        LayoutAreas areas) {
}
