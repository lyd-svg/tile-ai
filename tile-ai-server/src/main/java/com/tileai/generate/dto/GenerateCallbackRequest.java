package com.tileai.generate.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 生成结果回传(契约 {@code openapi-internal.yaml} 的
 * {@code POST /internal/generate/callback},{@code required: [taskId, status]})。
 *
 * <p>Python → Java 方向。一次回调承载三种事件:开工({@code running} + 0%)、
 * 进度({@code running} + 百分比)、终局({@code success} / {@code failed})。
 * <b>同一个 taskId 会被回调多次</b>,这是设计如此 —— 所以服务端所有状态写入
 * 都必须带条件(见 {@link com.tileai.generate.GenerateCallbackService})。
 *
 * <p>{@code @JsonIgnoreProperties(ignoreUnknown = true)}:Python 侧日后给回调
 * 加一个诊断字段(比如耗时),不该把 Java 的回调打成 10001。契约也没有声明
 * {@code additionalProperties: false}。反过来,Java 需要的字段缺了就报错 ——
 * 少字段是接口对不上,多字段只是版本差。
 *
 * <p>字段名是 <b>camelCase</b>,与 {@code asyncapi-internal.yaml} 的消息体一致
 * (索引同步那条走的是 snake_case,两者不要互相"统一")。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GenerateCallbackRequest(

        @NotBlank(message = "taskId 不能为空")
        @Size(max = 64, message = "taskId 不能超过 64 字符")
        String taskId,

        @NotBlank(message = "status 不能为空")
        @Pattern(regexp = "pending|running|success|failed",
                message = "status 只能是 pending / running / success / failed")
        String status,

        /** {@code status=success} 时必填(契约注明)。 */
        @Valid
        List<ImageRef> imageUrls,

        @Min(value = 0, message = "进度不能为负")
        @Max(value = 100, message = "进度不能超过 100")
        Integer progress,

        /** {@code status=failed} 时携带。 */
        @Valid
        ErrorInfo error,

        String traceId) {

    /**
     * 一张出图结果。
     *
     * <p>{@code view} 的命名约定见契约 {@code common.PlanImage.view}(D-5):
     * N 张 = {@code default} + {@code angle1}…{@code angleN-1}。
     */
    public record ImageRef(
            @NotBlank(message = "图片 url 不能为空")
            @Size(max = 500, message = "图片 url 不能超过 500 字符")
            String url,

            @NotBlank(message = "视角不能为空")
            @Size(max = 50, message = "视角名不能超过 50 字符")
            String view,

            /** 省略时按视角推断({@code default} 即封面)。 */
            Boolean isCover) {
    }

    /** 失败信息。{@code code} 取自契约 {@code ErrorCode},Java 侧不改写它。 */
    public record ErrorInfo(Integer code, String message) {
    }

    /** 进度缺省 0 —— 只报"开工了"不报百分比时,进度条应停在起点而不是倒退。 */
    public int progressOrDefault() {
        return progress == null ? 0 : progress;
    }
}
