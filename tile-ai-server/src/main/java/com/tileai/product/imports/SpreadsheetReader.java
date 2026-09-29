package com.tileai.product.imports;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.tileai.common.error.BusinessException;
import com.tileai.common.error.ErrorCode;

/**
 * 把上传的 xlsx / CSV 解析成 {@link SpreadsheetTable}(架构 §8.7)。
 *
 * <p>文件格式按<b>内容</b>而非扩展名判断:xlsx 是 ZIP(魔数 {@code PK}),
 * 其余按文本处理。运营把 {@code .csv} 存成 {@code .xlsx} 或反之都很常见,
 * 只看扩展名会给出"文件损坏"这类误导性的错误。
 */
@Component
public class SpreadsheetReader {

    private static final byte[] ZIP_MAGIC = {0x50, 0x4B};
    private static final Charset GBK = Charset.forName("GBK");

    /** 单个文件的行数上限,防止一次导入把内存或事务撑爆。 */
    private static final int MAX_ROWS = 20_000;

    public SpreadsheetTable read(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "导入文件不能为空");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "无法读取上传的文件");
        }

        SpreadsheetTable table = isZip(bytes) ? readXlsx(bytes) : readCsv(bytes);

        if (table.columnIndexes().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID,
                    "未识别到任何列,请使用模板中的表头:" + String.join("、", ImportColumn.headers()));
        }
        if (table.rows().size() > MAX_ROWS) {
            throw new BusinessException(ErrorCode.PARAM_INVALID,
                    "单次导入最多 " + MAX_ROWS + " 行,当前 " + table.rows().size() + " 行");
        }
        for (ImportColumn required : ImportColumn.values()) {
            if (required.required() && !table.columnIndexes().containsKey(required)) {
                throw new BusinessException(ErrorCode.PARAM_INVALID,
                        "缺少必填列:" + required.header());
            }
        }
        return table;
    }

    // ------------------------------------------------------------------

    private static boolean isZip(byte[] bytes) {
        return bytes.length >= 2 && bytes[0] == ZIP_MAGIC[0] && bytes[1] == ZIP_MAGIC[1];
    }

    private SpreadsheetTable readXlsx(byte[] bytes) {
        try (InputStream in = new ByteArrayInputStream(bytes);
             Workbook workbook = new XSSFWorkbook(in)) {

            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "Excel 中没有工作表");
            }

            // DataFormatter 按单元格的显示格式取值 —— 直接读 double 会把
            // "268" 变成 "268.0",把 "750×1500mm" 之类的文本数字搞乱。
            DataFormatter formatter = new DataFormatter();

            List<List<String>> raw = new ArrayList<>();
            int lastRow = sheet.getLastRowNum();
            for (int i = 0; i <= lastRow; i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    raw.add(List.of());
                    continue;
                }
                List<String> cells = new ArrayList<>();
                for (int c = 0; c < row.getLastCellNum(); c++) {
                    Cell cell = row.getCell(c);
                    cells.add(cell == null ? null : formatter.formatCellValue(cell));
                }
                raw.add(cells);
            }
            return toTable(raw);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "Excel 文件无法解析:" + e.getMessage());
        }
    }

    private SpreadsheetTable readCsv(byte[] bytes) {
        String text = decodeCsv(bytes);
        List<List<String>> raw = parseCsv(text);
        return toTable(raw);
    }

    /**
     * 解码 CSV 文本。
     *
     * <p>中文场景下 CSV 有两种常见编码:<b>Excel 另存为 CSV 默认是 GBK</b>
     * (不带 BOM),而程序导出的通常是 UTF-8(可能带 BOM)。
     * 先按 BOM 判定;没有 BOM 就严格按 UTF-8 试解,遇到非法字节再退回 GBK。
     * 直接用 UTF-8 硬读 GBK 文件会得到满屏乱码,而乱码不会报错 ——
     * 只会让所有 SKU 编号看起来"不像编号",排查起来很绕。
     */
    private String decodeCsv(byte[] bytes) {
        if (bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) {
            return new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
        }
        if (isValidUtf8(bytes)) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return new String(bytes, GBK);
    }

    private static boolean isValidUtf8(byte[] bytes) {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            decoder.decode(ByteBuffer.wrap(bytes));
            return true;
        } catch (CharacterCodingException e) {
            return false;
        }
    }

    /** 够用的 CSV 解析:支持双引号包裹、引号内逗号与换行、双写引号转义("")。 */
    private List<List<String>> parseCsv(String text) {
        List<List<String>> rows = new ArrayList<>();
        List<String> current = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    field.append(ch);
                }
            } else if (ch == '"') {
                inQuotes = true;
            } else if (ch == ',') {
                current.add(field.toString());
                field.setLength(0);
            } else if (ch == '\n' || ch == '\r') {
                if (ch == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                current.add(field.toString());
                field.setLength(0);
                rows.add(current);
                current = new ArrayList<>();
            } else {
                field.append(ch);
            }
        }
        if (field.length() > 0 || !current.isEmpty()) {
            current.add(field.toString());
            rows.add(current);
        }
        return rows;
    }

    // ------------------------------------------------------------------

    /** 原始行列 → 带行号的表格。空行被跳过,行号仍按原文件计。 */
    private SpreadsheetTable toTable(List<List<String>> raw) {
        if (raw.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "文件为空");
        }
        Map<ImportColumn, Integer> indexes = ImportColumn.indexOf(raw.get(0));

        List<SpreadsheetTable.Row> rows = new ArrayList<>();
        for (int i = 1; i < raw.size(); i++) {
            SpreadsheetTable.Row row = new SpreadsheetTable.Row(i + 1, raw.get(i));
            if (!row.isBlank()) {
                rows.add(row);
            }
        }
        return new SpreadsheetTable(new LinkedHashMap<>(indexes), rows);
    }
}
