package com.tileai.product.imports;

import java.util.Arrays;

/**
 * 导入模式(架构 §8.7:「以 {@code sku_code} 为业务主键,支持'新增或更新(upsert)'
 * 与'仅新增'两种模式,避免重复导入产生脏数据」)。
 */
public enum ImportMode {

    /**
     * 新增或更新。已存在的编号<b>更新</b>,不存在的新建。
     *
     * <p>这是幂等的关键:同一份文件重复导入,结果与导入一次相同(§8.7 的验证判据)。
     */
    UPSERT("upsert"),

    /**
     * 仅新增。已存在的编号<b>记为错误行</b>,不覆盖既有数据。
     *
     * <p>适合"这批是全新 SKU,不想误改线上已有数据"的场景。
     */
    INSERT_ONLY("insert_only");

    private final String code;

    ImportMode(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    /** 未知值返回 {@code null};调用方须按参数非法处理,不得默默取默认值。 */
    public static ImportMode of(String code) {
        return code == null ? null
                : Arrays.stream(values()).filter(m -> m.code.equals(code)).findFirst().orElse(null);
    }
}
