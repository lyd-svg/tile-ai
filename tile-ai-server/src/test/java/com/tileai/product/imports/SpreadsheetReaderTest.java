package com.tileai.product.imports;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import com.tileai.common.error.BusinessException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T-0.7:表格解析(架构 §8.7)。纯单元测试,不依赖 Spring 与数据库。
 *
 * <p>最要紧的一条是<b>模板往返</b>:下载的模板能被同一个解析器读回。
 * 这两端共用 {@link ImportColumn},但只有真的跑一遍才能证明 ——
 * 否则运营照模板填完却报"缺列",而两边看代码都"没问题"。
 *
 * <p><b>本文件不手写逗号分隔的行。</b>写测试时因为数错一个逗号,
 * 整行列错位了两次(268.00 落到别的列上),而症状是"某字段莫名等于 268.00",
 * 看着像解析器有 bug。改用 {@link #row(Map)} 按列枚举拼行,对齐由代码保证。
 */
class SpreadsheetReaderTest {

    private static final Charset GBK = Charset.forName("GBK");

    private final SpreadsheetReader reader = new SpreadsheetReader();
    private final ImportTemplateWriter writer = new ImportTemplateWriter();

    private SpreadsheetTable read(String fileName, byte[] content, String contentType) {
        return reader.read(new MockMultipartFile("file", fileName, contentType, content));
    }

    // ------------------------------------------------------------------
    // 模板往返
    // ------------------------------------------------------------------

    @Test
    @DisplayName("CSV 模板能被读回,且列全部识别")
    void csvTemplateRoundTrips() {
        SpreadsheetTable table = read("t.csv", writer.csv(), "text/csv");

        assertEquals(ImportColumn.values().length, table.columnIndexes().size(),
                "模板里的列应全部被识别: " + table.columnIndexes().keySet());
        assertEquals(1, table.rows().size(), "模板只含一行示例数据");

        SpreadsheetTable.Row sample = table.rows().get(0);
        assertEquals("TL-2043", sample.value(table.columnIndexes(), ImportColumn.SKU));
        assertEquals("雅灰石纹", sample.value(table.columnIndexes(), ImportColumn.NAME));
        assertEquals("268.00", sample.value(table.columnIndexes(), ImportColumn.PRICE_PER_SQM));
    }

    @Test
    @DisplayName("xlsx 模板能被读回,且列全部识别")
    void xlsxTemplateRoundTrips() {
        SpreadsheetTable table = read("t.xlsx", writer.xlsx(),
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

        assertEquals(ImportColumn.values().length, table.columnIndexes().size());
        assertEquals(1, table.rows().size());
        assertEquals("TL-2043", table.rows().get(0).value(table.columnIndexes(), ImportColumn.SKU));
        assertEquals("雅灰石纹",
                table.rows().get(0).value(table.columnIndexes(), ImportColumn.NAME));
    }

    @Test
    @DisplayName("CSV 模板带 UTF-8 BOM —— 否则 Excel 打开中文表头会乱码")
    void csvTemplateHasUtf8Bom() {
        byte[] csv = writer.csv();
        assertEquals((byte) 0xEF, csv[0]);
        assertEquals((byte) 0xBB, csv[1]);
        assertEquals((byte) 0xBF, csv[2]);
    }

    // ------------------------------------------------------------------
    // 编码
    // ------------------------------------------------------------------

    @Test
    @DisplayName("GBK 编码的 CSV 能正确读出中文(Excel 另存为 CSV 的默认编码)")
    void gbkCsvIsDecodedCorrectly() {
        String csv = csv(row(Map.of(ImportColumn.SKU, "TL-1",
                ImportColumn.NAME, "雅灰石纹",
                ImportColumn.PRICE_PER_SQM, "268.00")));

        SpreadsheetTable table = read("gbk.csv", csv.getBytes(GBK), "text/csv");

        assertEquals("雅灰石纹",
                table.rows().get(0).value(table.columnIndexes(), ImportColumn.NAME),
                "GBK 文件若按 UTF-8 硬读会得到乱码,而且不报错");
    }

    @Test
    @DisplayName("带 BOM 的 UTF-8 CSV 不会把 BOM 读进第一个表头")
    void utf8BomIsStripped() {
        String csv = csv(row(Map.of(ImportColumn.SKU, "TL-1",
                ImportColumn.NAME, "雅灰",
                ImportColumn.PRICE_PER_SQM, "268.00")));

        SpreadsheetTable table = read("bom.csv", withUtf8Bom(csv), "text/csv");

        assertTrue(table.columnIndexes().containsKey(ImportColumn.SKU),
                "BOM 若未剥离,第一个表头会变成 ﻿产品编号,导致整列识别不到。"
                        + "实际识别到的列: " + table.columnIndexes().keySet());
    }

    // ------------------------------------------------------------------
    // 解析细节
    // ------------------------------------------------------------------

    @Test
    @DisplayName("表头容忍全角括号与空格(中文输入法下很容易打成全角)")
    void headerToleratesFullWidthParensAndSpaces() {
        // 这里故意手写表头 —— 要验的就是"表头文字不同也能认出来"
        String csv = "产品编号,产品名称,单价（元/㎡）\r\nTL-1,雅灰,268\r\n";
        SpreadsheetTable table = read("fullwidth.csv", csv.getBytes(StandardCharsets.UTF_8),
                "text/csv");

        assertTrue(table.columnIndexes().containsKey(ImportColumn.PRICE_PER_SQM),
                "全角括号的表头应被识别: " + table.columnIndexes().keySet());
    }

    @Test
    @DisplayName("行号按原文件计(含表头),空行被跳过但不影响后续行号")
    void rowNumbersMatchOriginalFileAndBlankRowsAreSkipped() {
        String csv = csv(
                row(Map.of(ImportColumn.SKU, "TL-1", ImportColumn.NAME, "第一款",
                        ImportColumn.PRICE_PER_SQM, "100.00")),
                blankRow(),
                row(Map.of(ImportColumn.SKU, "TL-3", ImportColumn.NAME, "第三款",
                        ImportColumn.PRICE_PER_SQM, "300.00")));

        SpreadsheetTable table = read("rows.csv", csv.getBytes(StandardCharsets.UTF_8), "text/csv");

        assertEquals(2, table.rows().size(), "空行应被跳过");
        assertEquals(2, table.rows().get(0).rowNumber());
        assertEquals(4, table.rows().get(1).rowNumber(),
                "空行被跳过后,后续行号仍要是文件里的真实行号 —— 否则运营按报错行号找不到那一行");
    }

    @Test
    @DisplayName("引号包裹的字段(含逗号)不会被拆列")
    void quotedFieldsWithCommaAreNotSplit() {
        String csv = csv(row(Map.of(ImportColumn.SKU, "TL-1",
                ImportColumn.NAME, "\"雅灰,石纹\"",
                ImportColumn.PRICE_PER_SQM, "268.00")));

        SpreadsheetTable table = read("quoted.csv", csv.getBytes(StandardCharsets.UTF_8), "text/csv");

        assertEquals("雅灰,石纹",
                table.rows().get(0).value(table.columnIndexes(), ImportColumn.NAME));
    }

    @Test
    @DisplayName("按内容而非扩展名识别格式:扩展名写错也能读")
    void formatDetectedByContentNotExtension() {
        SpreadsheetTable table = read("wrong.csv", writer.xlsx(), "text/csv");
        assertEquals(ImportColumn.values().length, table.columnIndexes().size());
    }

    @Test
    @DisplayName("单值列取不到时返回 null 而不是空串")
    void missingCellReturnsNull() {
        String csv = csv(row(Map.of(ImportColumn.SKU, "TL-1",
                ImportColumn.NAME, "雅灰",
                ImportColumn.PRICE_PER_SQM, "268.00")));

        SpreadsheetTable table = read("nulls.csv", csv.getBytes(StandardCharsets.UTF_8), "text/csv");
        SpreadsheetTable.Row row = table.rows().get(0);

        assertNull(row.value(table.columnIndexes(), ImportColumn.SPEC));
        assertNotNull(row.value(table.columnIndexes(), ImportColumn.SKU));
    }

    @Test
    @DisplayName("多值分隔符是分号而非逗号 —— 逗号在 CSV 里会毁掉整行列对齐")
    void multiValueSeparatorIsSemicolon() {
        String csv = csv(row(Map.of(ImportColumn.SKU, "TL-1",
                ImportColumn.NAME, "雅灰",
                ImportColumn.PRICE_PER_SQM, "268.00",
                ImportColumn.APPLICABLE_SPACES, "bathroom;kitchen",
                ImportColumn.STYLE_TAGS, "modern;nordic")));

        SpreadsheetTable table = read("multi.csv", csv.getBytes(StandardCharsets.UTF_8), "text/csv");
        SpreadsheetTable.Row row = table.rows().get(0);

        assertEquals(List.of("bathroom", "kitchen"),
                List.of(row.value(table.columnIndexes(), ImportColumn.APPLICABLE_SPACES)
                        .split(ImportColumn.MULTI_VALUE_SEPARATOR)));
        assertEquals(List.of("modern", "nordic"),
                List.of(row.value(table.columnIndexes(), ImportColumn.STYLE_TAGS)
                        .split(ImportColumn.MULTI_VALUE_SEPARATOR)));
    }

    // ------------------------------------------------------------------
    // 拒绝
    // ------------------------------------------------------------------

    @Test
    @DisplayName("缺少必填列 → 10001,并指出缺的是哪一列")
    void missingRequiredColumnIsRejected() {
        String csv = "产品名称,单价(元/㎡)\r\n雅灰,268\r\n";
        BusinessException ex = assertThrows(BusinessException.class,
                () -> read("bad.csv", csv.getBytes(StandardCharsets.UTF_8), "text/csv"));
        assertTrue(ex.getMessage().contains("产品编号"), ex.getMessage());
    }

    @Test
    @DisplayName("空文件 → 10001")
    void emptyFileIsRejected() {
        assertThrows(BusinessException.class, () -> read("empty.csv", new byte[0], "text/csv"));
    }

    @Test
    @DisplayName("表头完全对不上 → 10001,提示可用表头")
    void unrecognizedHeaderIsRejected() {
        String csv = "aaa,bbb,ccc\r\n1,2,3\r\n";
        BusinessException ex = assertThrows(BusinessException.class,
                () -> read("unknown.csv", csv.getBytes(StandardCharsets.UTF_8), "text/csv"));
        assertTrue(ex.getMessage().contains("产品编号"), ex.getMessage());
    }

    // ------------------------------------------------------------------
    // 工具:按列拼行,不手写逗号
    // ------------------------------------------------------------------

    /** 表头 + 若干数据行,拼成 CSV 文本(无 BOM)。 */
    private static String csv(String[]... dataRows) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.join(",", ImportColumn.headers())).append("\r\n");
        for (String[] dataRow : dataRows) {
            sb.append(String.join(",", dataRow)).append("\r\n");
        }
        return sb.toString();
    }

    /** 按 {@link ImportColumn} 的声明顺序拼一行;未给的列留空。 */
    private static String[] row(Map<ImportColumn, String> values) {
        return Arrays.stream(ImportColumn.values())
                .map(column -> values.getOrDefault(column, ""))
                .toArray(String[]::new);
    }

    private static Map<ImportColumn, String> emptyRow() {
        return new LinkedHashMap<>();
    }

    private static String[] blankRow() {
        return row(emptyRow());
    }

    private static byte[] withUtf8Bom(String text) {
        byte[] body = text.getBytes(StandardCharsets.UTF_8);
        byte[] withBom = new byte[body.length + 3];
        withBom[0] = (byte) 0xEF;
        withBom[1] = (byte) 0xBB;
        withBom[2] = (byte) 0xBF;
        System.arraycopy(body, 0, withBom, 3, body.length);
        return withBom;
    }
}
