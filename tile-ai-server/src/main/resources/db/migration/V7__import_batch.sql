-- ============================================================================
-- V7 导入批次 —— 架构 §8.7(闭合缺口 G-2)
--   t_import_batch
--
-- 契约有 POST /admin/products/import 与 GET /admin/products/import/{batchId}
-- 查询批次进度与结果,但架构 §8 原先只描述了导入流程、**未定义承载表**。
-- 架构 v1.3 已在 §8.7 补上定义,本迁移据此建表。
--
-- ⚠️⚠️ 本文件是**新增迁移**,任何情况下都不得回头修改 V1–V6。
-- Flyway 会为每个迁移文件记录校验和;改动一个已应用的迁移会让后续所有启动
-- 校验失败(checksum mismatch),应用直接起不来。新表一律用**新版本号追加**。
--
-- 字段与 openapi-admin.yaml#/components/schemas/ImportStatus 一一对应:
--   batchId        ↔ batch_id            status         ↔ status
--   total          ↔ total_count          successCount   ↔ success_count
--   failedCount    ↔ failed_count         errors[]       ↔ errors
--   errorReportUrl ↔ error_report_url
-- 对外 camelCase、DB snake_case,映射在 ORM 层完成(架构 §10.1)。
-- ============================================================================

CREATE TABLE t_import_batch (
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    batch_id         VARCHAR(64)     NOT NULL COMMENT '对外唯一,即契约中的 batchId',
    biz_type         VARCHAR(30)     NOT NULL COMMENT 'product / combo / template / furnish / quote_param',
    mode             VARCHAR(20)     NOT NULL COMMENT 'upsert / insert_only',
    status           VARCHAR(20)     NOT NULL DEFAULT 'pending' COMMENT 'pending / running / success / failed',
    total_count      INT             NOT NULL DEFAULT 0 COMMENT '总行数',
    success_count    INT             NOT NULL DEFAULT 0,
    failed_count     INT             NOT NULL DEFAULT 0,
    errors           JSON            NULL COMMENT '错误行明细,元素 { row, reason }',
    error_report_url VARCHAR(500)    NULL COMMENT '错误报告下载地址',
    created_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_import_batch_id (batch_id),
    KEY idx_import_batch_biz (biz_type, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT '导入批次';
