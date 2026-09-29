package com.tileai.product.imports;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 批量导入模板的列定义(架构 §8.7)。
 *
 * <p><b>模板生成与文件解析共用这一份定义</b> —— 否则"导出的模板"与"能读回的格式"
 * 会各写一套,迟早对不上,而症状是运营照模板填完却报"缺列",极难自查。
 */
public enum ImportColumn {

    SKU("产品编号", true),
    NAME("产品名称", true),
    SPEC("规格", false),
    MATERIAL("材质", false),
    SURFACE("光泽度", false),
    COLOR_PATTERN("花色纹理", false),
    PRICE_PER_SQM("单价(元/㎡)", true),
    /** 多值,用 {@link #MULTI_VALUE_SEPARATOR} 分隔。 */
    APPLICABLE_SPACES("适用空间", false),
    /** 多值,用 {@link #MULTI_VALUE_SEPARATOR} 分隔。 */
    STYLE_TAGS("风格标签", false),
    COVER_IMAGE("封面图URL", false),
    /** 多值,元素为图片 URL,用 {@link #MULTI_VALUE_SEPARATOR} 分隔。 */
    IMAGE_URLS("多角度图URL", false);

    /**
     * 多值单元格的分隔符。
     *
     * <p>刻意<b>不用逗号</b>:CSV 本身就以逗号分列,单元格里再放逗号必须加引号,
     * 运营手工编辑时极易漏引号导致整行列错位。
     */
    public static final String MULTI_VALUE_SEPARATOR = ";";

    private final String header;
    private final boolean required;

    ImportColumn(String header, boolean required) {
        this.header = header;
        this.required = required;
    }

    /** 模板首行的中文表头。 */
    public String header() {
        return header;
    }

    /** 是否必填 —— 与契约 {@code ProductUpsert} 的 {@code required} 保持一致。 */
    public boolean required() {
        return required;
    }

    /** 全部表头,按枚举声明顺序 —— 模板列顺序即由此决定。 */
    public static List<String> headers() {
        return Arrays.stream(values()).map(ImportColumn::header).toList();
    }

    /**
     * 按表头文字反查列。
     *
     * <p>比对前去掉首尾空白,并容忍全角括号 —— 运营用中文输入法时
     * 常把 {@code (元/㎡)} 打成 {@code (元/㎡)},直接字符串相等会认不出来。
     */
    public static ImportColumn ofHeader(String header) {
        if (header == null) {
            return null;
        }
        String normalized = normalizeHeader(header);
        for (ImportColumn column : values()) {
            if (normalizeHeader(column.header).equals(normalized)) {
                return column;
            }
        }
        return null;
    }

    /** 表头 → 列下标。未识别的表头被忽略(而不是报错),以便模板日后新增列时旧解析仍可用。 */
    public static Map<ImportColumn, Integer> indexOf(List<String> headerRow) {
        Map<ImportColumn, Integer> indexes = new LinkedHashMap<>();
        for (int i = 0; i < headerRow.size(); i++) {
            ImportColumn column = ofHeader(headerRow.get(i));
            if (column != null) {
                indexes.putIfAbsent(column, i);
            }
        }
        return indexes;
    }

    private static String normalizeHeader(String value) {
        return value.trim().replace('（', '(').replace('）', ')').replace(" ", "");
    }
}
