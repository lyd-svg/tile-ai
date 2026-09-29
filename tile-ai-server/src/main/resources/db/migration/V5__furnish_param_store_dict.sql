-- ============================================================================
-- V5 陈设、参数、门店、字典 —— 架构 §8.5
--   t_furnish_style / t_quote_param / t_store / t_dict
--
-- §8.5 这几张表都只列了 updated_at(无 created_at),照办 —— 它们都是
-- "配置项",关心的是"何时被改",不是"何时被创建"。
--
-- t_dict 是"枚举不硬编码"的落点(D-14 定稿):空间类型、风格等由后台维护,
-- 前端与契约不硬编码;契约中的 enum(living_room/bedroom/…、modern/…)仅是内置默认值。
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 陈设风格配置(空间 × 风格 矩阵;PRD §5.4 陈设随风格自动变化)
-- (space, style) 唯一 —— 契约 PUT /admin/furnish 为 upsert(T-0.10)
-- ---------------------------------------------------------------------------
CREATE TABLE t_furnish_style (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    space       VARCHAR(50)     NOT NULL COMMENT '空间字典 key',
    style       VARCHAR(50)     NOT NULL COMMENT '风格字典 key',
    prompt      TEXT            NULL COMMENT '陈设提示词,供 Python 组装出图提示词',
    cover_image VARCHAR(500)    NULL,
    status      VARCHAR(20)     NOT NULL DEFAULT 'enabled' COMMENT 'enabled / disabled',
    updated_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_furnish_space_style (space, style)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT '陈设风格配置';

-- ---------------------------------------------------------------------------
-- 报价参数(PRD §5.5:辅料单价按行业标准、损耗率默认 6% 范围 5%~8%,后台可配置)
-- param_key ∈ {aux_unit_price, loss_rate_min, loss_rate_max}
-- param_value 用 DECIMAL(10,4) 同时容纳两种量纲:
--   辅料单价 元/㎡(如 20.0000)与损耗率小数(如 0.0600)
-- ---------------------------------------------------------------------------
CREATE TABLE t_quote_param (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    param_key   VARCHAR(50)     NOT NULL COMMENT 'aux_unit_price / loss_rate_min / loss_rate_max',
    param_value DECIMAL(10,4)   NULL COMMENT '量纲随 param_key:元/㎡ 或 小数',
    remark      VARCHAR(255)    NULL,
    updated_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_quote_param_key (param_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT '报价参数';

-- ---------------------------------------------------------------------------
-- 门店(PRD §7.6:**单店**,故无"新增/删除",只有一条的读取与整体更新)
-- ---------------------------------------------------------------------------
CREATE TABLE t_store (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    name       VARCHAR(200)    NOT NULL,
    address    VARCHAR(500)    NULL,
    phone      VARCHAR(50)     NULL,
    map_url    VARCHAR(500)    NULL COMMENT '地图/导航链接',
    updated_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT '门店(单店单条)';

-- ---------------------------------------------------------------------------
-- 数据字典(D-14:枚举后台可配置;契约 GET /dicts 与 /admin/dicts)
-- 删除被引用项须返回 30002 而非静默删除(T-0.11 验证项)
-- ---------------------------------------------------------------------------
CREATE TABLE t_dict (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    dict_type  VARCHAR(30)     NOT NULL COMMENT 'space / style 等',
    dict_key   VARCHAR(50)     NOT NULL,
    dict_label VARCHAR(100)    NOT NULL,
    sort       INT             NOT NULL DEFAULT 0,
    status     VARCHAR(20)     NOT NULL DEFAULT 'enabled' COMMENT 'enabled / disabled',
    updated_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_dict_type_key (dict_type, dict_key),
    KEY idx_dict_type_sort (dict_type, status, sort)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT '数据字典';
