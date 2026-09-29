package com.tileai.furnish.dto;

import com.tileai.furnish.entity.FurnishStyle;

/**
 * 陈设风格配置的对外视图。
 *
 * <p>契约:{@code openapi-common.yaml#/components/schemas/FurnishStyle},
 * {@code required: [id, space, style, prompt]}。
 */
public record FurnishStyleView(Long id, String space, String style, String prompt,
                               String coverImage, String status) {

    public static FurnishStyleView of(FurnishStyle entity) {
        return new FurnishStyleView(entity.getId(), entity.getSpace(), entity.getStyle(),
                entity.getPrompt(), entity.getCoverImage(), entity.getStatus());
    }
}
