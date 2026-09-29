-- ============================================================================
-- V3 户型与模板 —— 架构 §8.3
--   t_unit_template / t_layout
--
-- 加密边界(CLAUDE.md「容易写错的约定」):
--   t_unit_template.layout_json 是**系统公共模板**,明文 JSON;
--   t_layout.layout_enc 是**随方案固化的快照**,AES-256-GCM 密文。
--   加密只作用于静态存储 —— 对外 API 一律返回解密后的明文(架构 §13.5),
--   对外 schema 中**不得出现 layout_enc**(契约 T-8)。
--
-- layout_enc 口径:VARBINARY(16384)。AES-GCM 密文为二进制(nonce 12 + 密文 + tag 16),
-- 16KB 上限对住宅户型快照极宽裕(单空间约数百字节;一期本就是单空间出图)。
-- 结构化户型模型字段定义见架构 §8.3 字段表(D-2 定稿)。
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 户型模板(PRD §5.3.1;契约 GET /layout/templates 支持按房间数/面积区间/结构筛选)
-- ---------------------------------------------------------------------------
CREATE TABLE t_unit_template (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    name        VARCHAR(200)    NOT NULL,
    room_count  INT             NULL COMMENT '居室数',
    area_range  VARCHAR(50)     NULL COMMENT '面积区间',
    structure   VARCHAR(50)     NULL COMMENT '户型结构',
    layout_json JSON            NOT NULL COMMENT '空间列表与尺寸,**明文**',
    status      VARCHAR(20)     NOT NULL DEFAULT 'enabled' COMMENT 'enabled / disabled',
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_template_filter (room_count, structure)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT '户型模板';

-- ---------------------------------------------------------------------------
-- 户型数据(绑定方案)。以加密快照随方案固化,避免模板变更污染历史方案(§8.3)
-- ---------------------------------------------------------------------------
CREATE TABLE t_layout (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    plan_id    BIGINT UNSIGNED NOT NULL,
    layout_enc VARBINARY(16384) NOT NULL COMMENT 'AES-256-GCM 密文,解密后为结构化户型模型快照',
    PRIMARY KEY (id),
    KEY idx_layout_plan (plan_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT '户型数据(密文快照)';
