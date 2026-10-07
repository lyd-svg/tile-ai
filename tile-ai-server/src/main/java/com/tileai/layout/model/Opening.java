package com.tileai.layout.model;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * 门窗开口。契约 {@code openapi-common.yaml#/components/schemas/Opening},
 * {@code required: [type, w, h]}(架构 §8.3 字段表)。
 *
 * <p>尺寸用 {@link BigDecimal} 而非 double:面积由这些数乘出来,而面积最终决定报价金额,
 * 二进制浮点的误差会在乘法里被放大。
 *
 * <h2>门窗位置三字段(2026-10-07 新增,<b>全部可选</b>)</h2>
 *
 * <p>出效果图时,"房间长什么样"原先**全靠模型编** —— 同一个户型点两次得到两间不同的房。
 * 解决办法是**按户型尺寸画一张"白模"当参考图**(实验见 {@code openapi-verification.md} §77),
 * 而画白模**必须有门窗位置**,原先只有宽高,表达不了"门在这面墙的哪一段"。
 *
 * <p><b>为什么可选而不是必填</b>(这是刻意的,不是漏写):
 * <ol>
 *   <li><b>文字描述那条路解析不出位置</b> —— 若必填,LLM 只能编一个位置出来,
 *       与"确定性"的初衷背道而驰;</li>
 *   <li><b>存量数据没有位置</b>({@code t_unit_template.layout_json}、
 *       {@code t_plan.layout_enc} 里已有的开口);</li>
 *   <li><b>报价不需要位置</b> —— 扣减只按 {@code w × h}(架构 §8.8);</li>
 *   <li>本类型被 <b>{@code /quote/calc} 复用</b>({@code openapi-c.yaml}),
 *       必填会连带收紧报价接口。</li>
 * </ol>
 *
 * <p><b>缺位置时</b>:白模**照画房间、不画这个洞**并记 WARN —— 降级不阻断(架构 §13.3)。
 *
 * <p>⚠️ {@code @JsonInclude(NON_NULL)} <b>必须留着</b>:这三个字段可空,而
 * {@code LayoutModelSchemaTest} 是拿**序列化出来的 JSON** 去跑契约的真 schema 校验的 ——
 * 少了它就会序列化出 {@code "wall": null},被判类型不符而红。
 *
 * @param type   开口类型:{@code door} / {@code window}
 * @param w      开口宽(米)
 * @param h      开口高(米)
 * @param wall   在哪面墙:{@code back} / {@code left} / {@code right}。见契约 {@code OpeningWall}
 * @param offset 洞的**水平中心**距该墙「0 点」的距离(米)。与 {@code wall} 成对出现
 * @param sill   洞底离地高度(米);可省略 —— 门缺省 0、窗缺省 0.9
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Opening(
        @NotNull(message = "开口类型不能为空")
        @Pattern(regexp = "door|window", message = "开口类型只能是 door 或 window")
        String type,

        @NotNull(message = "开口宽不能为空")
        @DecimalMin(value = "0.01", message = "开口宽必须大于 0")
        @DecimalMax(value = "30", message = "开口宽不能超过 30 米")
        BigDecimal w,

        @NotNull(message = "开口高不能为空")
        @DecimalMin(value = "0.01", message = "开口高必须大于 0")
        @DecimalMax(value = "10", message = "开口高不能超过 10 米")
        BigDecimal h,

        /**
         * 在哪面墙。
         *
         * <p>⚠️ {@code @Pattern} 对 {@code null} **放行**(Bean Validation 的约定),
         * 正好符合"可选"的要求 —— 不需要额外的分组校验。
         */
        @Pattern(regexp = "back|left|right", message = "墙面只能是 back、left 或 right")
        String wall,

        /** 洞的水平中心距该墙「0 点」的距离(米)。与 {@code wall} 成对出现。 */
        @DecimalMin(value = "0.00", message = "位置偏移不能为负")
        @DecimalMax(value = "30", message = "位置偏移不能超过 30 米")
        BigDecimal offset,

        /** 洞底离地高度(米)。{@code DecimalMin} 同样对 {@code null} 放行。 */
        @DecimalMin(value = "0.00", message = "离地高度不能为负")
        @DecimalMax(value = "10", message = "离地高度不能超过 10 米")
        BigDecimal sill) {
}
