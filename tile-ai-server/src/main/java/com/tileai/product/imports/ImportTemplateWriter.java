package com.tileai.product.imports;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

/**
 * 生成批量导入模板(架构 §8.7)。
 *
 * <p>列定义来自 {@link ImportColumn} —— 与解析端同一份,不会出现
 * "导出的模板导不回去"。
 */
@Component
public class ImportTemplateWriter {

    /** 模板里的示例行,让运营一眼看懂多值列怎么填。 */
    private static final List<String> SAMPLE_ROW = List.of(
            "TL-2043",
            "雅灰石纹",
            "750×1500mm",
            "釉面砖",
            "柔光",
            "浅灰细石纹",
            "268.00",
            "bathroom;kitchen",
            "modern;nordic",
            "https://oss.example.com/cover.jpg",
            "https://oss.example.com/a.jpg;https://oss.example.com/b.jpg");

    public byte[] xlsx() {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("产品库导入模板");

            CellStyle headerStyle = workbook.createCellStyle();
            Font bold = workbook.createFont();
            bold.setBold(true);
            headerStyle.setFont(bold);

            Row header = sheet.createRow(0);
            List<String> headers = ImportColumn.headers();
            for (int i = 0; i < headers.size(); i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(headers.get(i));
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 22 * 256);
            }

            Row sample = sheet.createRow(1);
            for (int i = 0; i < SAMPLE_ROW.size(); i++) {
                sample.createCell(i).setCellValue(SAMPLE_ROW.get(i));
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("生成 Excel 模板失败", e);
        }
    }

    /**
     * 生成 CSV 模板。
     *
     * <p><b>带 UTF-8 BOM</b> —— 不带的话 Excel 会按系统本地编码(中文 Windows 是 GBK)
     * 打开,中文表头直接变乱码。运营看到乱码会以为模板坏了。
     */
    public byte[] csv() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.join(",", ImportColumn.headers())).append("\r\n");
        sb.append(String.join(",", SAMPLE_ROW)).append("\r\n");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0xEF);
        out.write(0xBB);
        out.write(0xBF);
        out.writeBytes(sb.toString().getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }
}
