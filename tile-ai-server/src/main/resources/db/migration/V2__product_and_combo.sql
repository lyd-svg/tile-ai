-- ============================================================================
-- V2 产品库与推荐搭配 —— 架构 §8.2
--   t_product / t_product_image / t_recommend_combo / t_recommend_combo_item
--
-- 量纲(CLAUDE.md「容易写错的约定」):
--   price_per_sqm 元/㎡  -> DECIMAL(10,2)
--   ref_price_min/max 元 -> DECIMAL(12,2)
-- 图片一律存 URL,不存二进制(§8 命名约定)。
-- ============================================================================

-- ---------------------------------------------------------------------------
-- SKU(PRD §7.1 产品库核心字段)
-- ---------------------------------------------------------------------------
CREATE TABLE t_product (
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    sku_code         VARCHAR(64)     NOT NULL COMMENT '业务主键,批量导入按它 upsert',
    name             VARCHAR(200)    NOT NULL,
    spec             VARCHAR(100)    NULL COMMENT '规格,如 750×1500mm',
    material         VARCHAR(50)     NULL COMMENT '抛光砖 / 釉面砖 / 仿古砖 / 岩板…',
    surface          VARCHAR(50)     NULL COMMENT '光泽度',
    color_pattern    VARCHAR(200)    NULL COMMENT '花色/纹理描述',
    price_per_sqm    DECIMAL(10,2)   NOT NULL COMMENT '元/㎡',
    applicable_spaces JSON           NULL COMMENT '数组,元素为空间字典 key',
    style_tags       JSON            NULL COMMENT '数组,元素为风格字典 key',
    status           VARCHAR(20)     NOT NULL DEFAULT 'off_sale' COMMENT 'on_sale / off_sale',
    cover_image      VARCHAR(500)    NULL COMMENT 'URL',
    created_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_sku_code (sku_code),
    KEY idx_product_status (status),
    KEY idx_product_material (material)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT '产品库 SKU';

-- ---------------------------------------------------------------------------
-- 产品多角度图(§8.2 未给本表列时间戳,照办)
-- ---------------------------------------------------------------------------
CREATE TABLE t_product_image (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    product_id BIGINT UNSIGNED NOT NULL,
    url        VARCHAR(500)    NOT NULL,
    angle      VARCHAR(50)     NULL,
    sort       INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_pimg_product (product_id, sort)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT '产品多角度图';

-- ---------------------------------------------------------------------------
-- 推荐搭配(PRD §5.2.2 路径 B:纯人工维护,不做自动组合)
-- ---------------------------------------------------------------------------
CREATE TABLE t_recommend_combo (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    name          VARCHAR(200)    NOT NULL,
    space         VARCHAR(50)     NULL COMMENT '空间字典 key',
    style         VARCHAR(50)     NULL COMMENT '风格字典 key',
    ref_price_min DECIMAL(12,2)   NULL COMMENT '元',
    ref_price_max DECIMAL(12,2)   NULL COMMENT '元',
    cover_image   VARCHAR(500)    NULL,
    status        VARCHAR(20)     NOT NULL DEFAULT 'off_sale' COMMENT 'on_sale / off_sale',
    sort          INT             NOT NULL DEFAULT 0,
    created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_combo_space_style (space, style),
    KEY idx_combo_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT '推荐搭配';

-- ---------------------------------------------------------------------------
-- 搭配所含 SKU(§8.2 未给本表列时间戳,照办)
-- part 恒为 floor / wall / local —— 一期不含美缝(契约 T-9)
-- ---------------------------------------------------------------------------
CREATE TABLE t_recommend_combo_item (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    combo_id   BIGINT UNSIGNED NOT NULL,
    product_id BIGINT UNSIGNED NOT NULL,
    part       VARCHAR(10)     NOT NULL COMMENT 'floor / wall / local',
    note       VARCHAR(255)    NULL,
    sort       INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_combo_item_combo (combo_id, sort),
    KEY idx_combo_item_product (product_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT '搭配所含 SKU';
