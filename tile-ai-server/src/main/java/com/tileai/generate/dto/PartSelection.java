package com.tileai.generate.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 部位 → 瓷砖的选定(契约 {@code openapi-common.yaml#/components/schemas/PartSelection})。
 *
 * <p><b>一个类型用在三个地方</b>,不是偷懒 —— 这三处说的本来就是同一件事,
 * 分开定义就会出现三份可能漂移的字段表:
 * <ol>
 *   <li>请求 {@code GenerateRequest.parts}(契约 {@code required: [part, sku]},
 *       {@code area} / {@code source} 可省略);</li>
 *   <li>消息体 {@code GenerateTaskPayload.parts}(发给 Python);</li>
 *   <li>响应 {@code TaskProgress.parts}(契约:响应里 {@code source} <b>必填</b>)。</li>
 * </ol>
 * 校验注解只在第 1 处生效(控制器 {@code @Valid});在第 2、3 处它们只是无害的元数据。
 *
 * <p>{@code area} 可选是对的:请求里带着户型,服务端算得出来。这一点与报价的
 * {@code QuoteCalcRequest.PartSelection} 恰好相反 —— 那里请求里没有户型,
 * 所以面积必填(见那个类的说明)。
 */
public record PartSelection(

        @NotBlank(message = "部位不能为空")
        @Pattern(regexp = "floor|wall|local", message = "部位只能是 floor / wall / local")
        String part,

        @NotBlank(message = "瓷砖编号不能为空")
        @Size(max = 64, message = "瓷砖编号不能超过 64 字符")
        String sku,

        /** 该部位面积(㎡)。可省略 —— 省略时由服务端依户型推导。 */
        @DecimalMin(value = "0.00", message = "面积不能为负")
        BigDecimal area,

        /** 来源:用户选择 / AI 补齐(契约 {@code PartSource})。请求里可省略,默认 {@code user}。 */
        @Pattern(regexp = "user|ai", message = "来源只能是 user 或 ai")
        String source) {

    public static final String SOURCE_USER = "user";
    public static final String SOURCE_AI = "ai";

    /**
     * 把可省略的 {@code source} 归一成 {@code user}。
     *
     * <p>契约的 {@code PartSelection.source} 注明"响应中必填、请求中可省略(默认 user)"。
     * 归一放在这里,而不是在每个读取点各判一次 null —— 那必然有一处会漏。
     */
    public PartSelection withDefaultSource() {
        return source == null || source.isBlank() ? new PartSelection(part, sku, area, SOURCE_USER) : this;
    }
}
