package com.tileai.internal.dto;

import com.tileai.furnish.dto.FurnishStyleView;

/**
 * 陈设提示词的内部视图(契约 {@code openapi-internal.yaml} 的
 * {@code GET /internal/furnish/prompts} 响应 {@code data})。
 *
 * <p>与后台的 {@link FurnishStyleView} <b>刻意不同形</b>:后台要看 id 与 status
 * (要能编辑),而 Python 出图只需要"拼进去的那句话",外加一个"到底配没配"的布尔量。
 * 把 id/status 也发过去只会给 Python 多几个它不该依赖的字段 ——
 * 而它一旦依赖,以后改表结构就得先问 Python 同不同意。
 *
 * <p>{@code furnishConfigured = false} 时 {@code prompt} 恒为空串
 * (契约写的就是"未配置时为空串"),调用方不必先判假再读 prompt。
 */
public record FurnishPromptView(String prompt, String coverImage, boolean furnishConfigured) {

    /** 未配置时的降级值 —— 降级不阻断主流程(架构 §13.3)。 */
    public static FurnishPromptView missing() {
        return new FurnishPromptView("", null, false);
    }

    public static FurnishPromptView configured(FurnishStyleView source) {
        return new FurnishPromptView(source.prompt(), source.coverImage(), true);
    }
}
