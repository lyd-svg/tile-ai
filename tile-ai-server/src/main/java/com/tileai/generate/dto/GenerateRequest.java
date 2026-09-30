package com.tileai.generate.dto;

import java.util.List;

import com.tileai.layout.model.LayoutModel;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 提交生成任务请求(契约 {@code openapi-c.yaml#/components/schemas/GenerateRequest},
 * {@code required: [space, layout, parts]})。
 *
 * <p><b>{@code style} 不在必填里</b>,而消息体 {@code GenerateTaskPayload} 里它是必填 ——
 * 这处不一致由本类补齐:缺省时落空串,提示词组装退化为"不加风格修饰"。
 * 记在 {@code openapi-verification.md} §35.4。
 */
public record GenerateRequest(

        @NotBlank(message = "空间不能为空")
        @Size(max = 50, message = "空间 key 不能超过 50 字符")
        String space,

        /** 风格。可省略;省略时出图提示词里不带风格修饰(不报错、不阻断)。 */
        @Size(max = 50, message = "风格 key 不能超过 50 字符")
        String style,

        /**
         * 生成张数。默认 1;>1 为多角度,每个角度 1 张 —— <b>张数 = angles</b>。
         *
         * <p>上限 4 与已定义机位数一致(契约 {@code maximum: 4};架构 §7.3)。
         * 每个角度是一次真实出图(单张 ≤60s 且计费),超出机位数只会拿到重复机位。
         */
        @Min(value = 1, message = "角度数至少为 1")
        @Max(value = 4, message = "角度数最多为 4")
        Integer angles,

        /** 重新编辑时携带:非空表示覆盖原方案(架构 §11.4);为空则新建。 */
        Long planId,

        /**
         * 户型(必填)。{@code @Valid} <b>不会</b>顺带要求非空 —— 它只管"给了的话要合法",
         * 所以这里必须另加 {@code @NotNull},否则不传 layout 也能过校验,
         * 然后在服务端解引用时抛 90001。
         */
        @NotNull(message = "户型不能为空")
        @Valid
        LayoutModel layout,

        @NotEmpty(message = "至少需要一个部位")
        @Size(max = 20, message = "部位数量过多")
        List<@Valid PartSelection> parts) {

    /** 契约默认值 1。{@code null} 是"没传",不是"要 0 张"。 */
    public int anglesOrDefault() {
        return angles == null ? 1 : angles;
    }

    /** 缺省风格时用空串 —— 让提示词组装自然退化为"只有空间"而不是多一条判空分支。 */
    public String styleOrDefault() {
        return style == null ? "" : style;
    }
}
