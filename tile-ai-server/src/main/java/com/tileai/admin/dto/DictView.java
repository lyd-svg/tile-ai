package com.tileai.admin.dto;

import com.tileai.admin.entity.DictEntry;

/**
 * 数据字典项的对外视图。
 *
 * <p>契约:{@code openapi-common.yaml#/components/schemas/Dict},
 * {@code required: [type, key, label]}。
 *
 * <p>注意对外字段名与库内不同:库内是 {@code dict_type} / {@code dict_key} / {@code dict_label},
 * 对外是 {@code type} / {@code key} / {@code label} —— 映射在这一层完成(CLAUDE.md 命名约定)。
 */
public record DictView(Long id, String type, String key, String label, Integer sort,
                       String status) {

    public static DictView of(DictEntry entity) {
        return new DictView(entity.getId(), entity.getDictType(), entity.getDictKey(),
                entity.getDictLabel(), entity.getSort(), entity.getStatus());
    }
}
