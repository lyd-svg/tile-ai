package com.tileai.product.imports;

/**
 * 导入过程中的一行错误。
 *
 * <p>契约 {@code openapi-admin.yaml#/components/schemas/ImportStatus.errors[]}
 * 声明为 {@code { row: integer, reason: string }}。<b>行号是给人看的</b> ——
 * 运营拿着它在原始文件里定位,所以取的是<b>文件里的真实行号</b>(含表头),
 * 不是数据行的序号。
 */
public record ImportRowError(int row, String reason) {
}
