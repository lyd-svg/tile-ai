package com.tileai.product.imports;

import java.util.List;
import java.util.Map;

/**
 * 解析出来的表格 —— 不含任何业务语义,只是"第几行第几列是什么字符串"。
 *
 * <p>解析与校验刻意分开:解析层只管把 xlsx / CSV 变成行列,
 * 校验层才判断"这个单价能不能用"。这样两种文件格式共用同一套校验规则,
 * 不会出现"CSV 校验了、Excel 没校验"这类只在某种格式下才有的漏洞。
 *
 * @param columnIndexes 列 → 下标;未在文件中出现的列为空
 * @param rows          数据行(<b>不含表头</b>)
 */
public record SpreadsheetTable(Map<ImportColumn, Integer> columnIndexes, List<Row> rows) {

    /**
     * 一行数据。
     *
     * @param rowNumber 该行在<b>原始文件中的行号</b>(从 1 开始,含表头)。
     *                  错误报告里给运营看的就是这个号 —— 直接用数据行序号
     *                  会与他们在 Excel 里看到的行号差一个表头,徒增困惑。
     * @param cells     原始单元格文本
     */
    public record Row(int rowNumber, List<String> cells) {

        /** 取某列的值;列不存在或为空时返回 {@code null}(而不是空串,便于 {@code == null} 判空)。 */
        public String value(Map<ImportColumn, Integer> columnIndexes, ImportColumn column) {
            Integer index = columnIndexes.get(column);
            if (index == null || index >= cells.size()) {
                return null;
            }
            String value = cells.get(index);
            if (value == null) {
                return null;
            }
            String trimmed = value.trim();
            return trimmed.isEmpty() ? null : trimmed;
        }

        /** 该行是否完全为空(全空行应被跳过,而不是报"缺必填项")。 */
        public boolean isBlank() {
            return cells.stream().allMatch(cell -> cell == null || cell.isBlank());
        }
    }
}
