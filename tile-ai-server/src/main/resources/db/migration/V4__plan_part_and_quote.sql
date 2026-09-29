-- ============================================================================
-- V4 方案、部位与报价 —— 架构 §8.4
--   t_plan / t_plan_part / t_plan_quote / t_plan_image
--
-- 报价分两处落地:分项在 t_plan_part,汇总在 t_plan_quote,与 PRD §5.5 公式一一对应。
-- 量纲:金额 DECIMAL(12,2) 元;面积 DECIMAL(10,4) ㎡;损耗率 DECIMAL(5,4) 小数
--   —— 损耗率以**小数**存储,0.0600 表示 6%(D-15)。
--
-- 注意:列名 `view` 是 MySQL 保留字,DDL 与后续 SQL/ORM 均需反引号。
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 方案(PRD §5.6;§8.4 明确含 is_deleted —— 删除为逻辑删除)
-- ---------------------------------------------------------------------------
CREATE TABLE t_plan (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id       BIGINT UNSIGNED NOT NULL,
    name          VARCHAR(200)    NULL COMMENT '可重命名',
    space         VARCHAR(50)     NULL COMMENT '生成空间,空间字典 key',
    style         VARCHAR(50)     NULL COMMENT '风格字典 key',
    layout_enc    VARBINARY(16384) NULL COMMENT '户型快照,AES-256-GCM 密文',
    total_amount  DECIMAL(12,2)   NULL COMMENT '生成时总价;最终以门店实测为准',
    share_token   VARCHAR(64)     NULL COMMENT '分享令牌,≥128bit;废除后置空',
    quote_expired TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '产品调价后置 1',
    is_deleted    TINYINT(1)      NOT NULL DEFAULT 0,
    created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_plan_share_token (share_token),
    KEY idx_plan_user (user_id, is_deleted, updated_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT '方案';

-- ---------------------------------------------------------------------------
-- 方案部位—瓷砖映射 + 报价分项(§8.4)
-- sku_code / product_name / price_per_sqm 是**快照**,不随产品库改动而变
-- ---------------------------------------------------------------------------
CREATE TABLE t_plan_part (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    plan_id       BIGINT UNSIGNED NOT NULL,
    part          VARCHAR(10)     NOT NULL COMMENT 'floor / wall / local',
    product_id    BIGINT UNSIGNED NULL,
    sku_code      VARCHAR(64)     NULL,
    product_name  VARCHAR(200)    NULL COMMENT '快照',
    price_per_sqm DECIMAL(10,2)   NULL COMMENT '快照,元/㎡',
    gross_area    DECIMAL(10,4)   NULL COMMENT '毛面积,同部位同砖合并后,㎡',
    area          DECIMAL(10,4)   NULL COMMENT '有效面积(已扣开口),㎡',
    loss_rate     DECIMAL(5,4)    NULL COMMENT '小数,0.0600 = 6%',
    material_fee  DECIMAL(12,2)   NULL COMMENT '元',
    PRIMARY KEY (id),
    KEY idx_plan_part_plan (plan_id, part)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT '方案部位与报价分项';

-- ---------------------------------------------------------------------------
-- 方案报价汇总(§8.4)
-- rules_snapshot 记录生成时参数(辅料单价/损耗率/各砖单价),供追溯与"按当前价格重算"
-- ---------------------------------------------------------------------------
CREATE TABLE t_plan_quote (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    plan_id        BIGINT UNSIGNED NOT NULL,
    material_total DECIMAL(12,2)   NULL COMMENT '材料费合计,元',
    aux_total      DECIMAL(12,2)   NULL COMMENT '辅料费,元',
    aux_unit_price DECIMAL(10,2)   NULL COMMENT '辅料单价快照,元/㎡',
    aux_area       DECIMAL(10,4)   NULL COMMENT '计辅料的面积合计,㎡',
    loss_rate      DECIMAL(5,4)    NULL COMMENT '小数',
    total_amount   DECIMAL(12,2)   NULL COMMENT '元',
    rules_snapshot JSON            NULL COMMENT '生成时参数快照',
    PRIMARY KEY (id),
    KEY idx_plan_quote_plan (plan_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT '方案报价汇总';

-- ---------------------------------------------------------------------------
-- 方案效果图(§8.4 仅列 created_at,照办)
-- ---------------------------------------------------------------------------
CREATE TABLE t_plan_image (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    plan_id    BIGINT UNSIGNED NOT NULL,
    url        VARCHAR(500)    NOT NULL,
    `view`     VARCHAR(30)     NULL COMMENT 'default / angle1 …(保留字,需反引号)',
    is_cover   TINYINT(1)      NOT NULL DEFAULT 0,
    created_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_plan_image_plan (plan_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT '方案效果图';
