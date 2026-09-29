package com.tileai.layout.dto;

import com.tileai.layout.entity.UnitTemplate;
import com.tileai.layout.model.LayoutModel;

/**
 * 户型模板的对外视图。
 *
 * <p>契约:{@code openapi-common.yaml#/components/schemas/UnitTemplate},
 * {@code required: [id, name]}。
 *
 * <p>对外字段是 {@code layout}(明文),**不是**库里的 {@code layout_json} ——
 * 契约校验表 T-8 要求对外 schema 中不得出现密文/内部字段名。
 */
public record UnitTemplateView(
        Long id,
        String name,
        Integer roomCount,
        String areaRange,
        String structure,
        LayoutModel layout,
        String status) {

    public static UnitTemplateView of(UnitTemplate template) {
        return new UnitTemplateView(template.getId(), template.getName(), template.getRoomCount(),
                template.getAreaRange(), template.getStructure(), template.getLayout(),
                template.getStatus());
    }
}
