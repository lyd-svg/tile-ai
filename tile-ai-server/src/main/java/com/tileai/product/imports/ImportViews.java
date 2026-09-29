package com.tileai.product.imports;

import java.util.List;

/**
 * 批量导入相关的对外视图。
 *
 * <p>与实体 {@link ImportBatch} 分开,是因为<b>对外字段名与库内字段名不同</b>:
 * 库里是 {@code total_count}(架构 §8.7),契约声明的是 {@code total}。
 * 直接用实体序列化就会漏出 {@code totalCount},与契约对不上 ——
 * 这正是 T-0.6 那次 {@code success} 字段外泄的同一类问题。
 */
public final class ImportViews {

    private ImportViews() {
    }

    /** 契约 {@code ImportAccepted}:受理结果只返回批次号。 */
    public record ImportAccepted(String batchId) {
    }

    /** 契约 {@code ImportStatus}:进度与结果。 */
    public record ImportStatus(
            String batchId,
            String status,
            int total,
            int successCount,
            int failedCount,
            List<ImportRowError> errors,
            String errorReportUrl) {

        public static ImportStatus of(ImportBatch batch) {
            return new ImportStatus(
                    batch.getBatchId(),
                    batch.getStatus(),
                    nullToZero(batch.getTotalCount()),
                    nullToZero(batch.getSuccessCount()),
                    nullToZero(batch.getFailedCount()),
                    batch.getErrors() == null ? List.of() : batch.getErrors(),
                    batch.getErrorReportUrl());
        }

        private static int nullToZero(Integer value) {
            return value == null ? 0 : value;
        }
    }
}
