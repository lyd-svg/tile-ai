-- ============================================================================
-- V1 账户与鉴权 —— 架构 §8.1
--   t_user / t_sms_code / t_admin_user / t_operation_log
--
-- 字段严格取自 §8.1 的逐表清单(权威顺序:PRD > 架构 > 契约)。
-- 注意 §8.1 未给这几张表都列 updated_at(t_sms_code / t_admin_user /
-- t_operation_log 只列了 created_at),此处照办 —— 日志与一次性验证码
-- 本就无"最后修改时间"语义。
--
-- §8 未规定、由本迁移按惯例确定的口径:
--   主键   BIGINT UNSIGNED AUTO_INCREMENT
--   引擎   InnoDB(事务 + 行锁,Outbox 需与业务同事务)
--   字符集 utf8mb4 / utf8mb4_0900_ai_ci(MySQL 8 默认,支持中文与 emoji)
--   时间   DATETIME(避开 TIMESTAMP 的 2038 上限与时区隐式转换)
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 用户(PRD §5.1:手机号 + 验证码登录;注册必须设密码)
-- ---------------------------------------------------------------------------
CREATE TABLE t_user (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    phone         VARCHAR(20)     NOT NULL COMMENT '手机号,登录唯一标识',
    password_hash VARCHAR(100)    NOT NULL COMMENT 'BCrypt 密文(60 字符,留余量)',
    nickname      VARCHAR(50)     NULL,
    status        VARCHAR(20)     NOT NULL DEFAULT 'enabled' COMMENT 'enabled / disabled',
    last_login_at DATETIME        NULL,
    created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_phone (phone)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT '用户';

-- ---------------------------------------------------------------------------
-- 短信验证码(§8.1 注明"或改存 Redis";此处按主选方案建表)
-- ---------------------------------------------------------------------------
CREATE TABLE t_sms_code (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    phone      VARCHAR(20)     NOT NULL,
    code       VARCHAR(10)     NOT NULL,
    scene      VARCHAR(20)     NOT NULL COMMENT 'login / register',
    expires_at DATETIME        NOT NULL,
    used       TINYINT(1)      NOT NULL DEFAULT 0,
    created_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_sms_phone_scene (phone, scene, used)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT '短信验证码';

-- ---------------------------------------------------------------------------
-- 后台账号(角色见架构 §4.4:super_admin / operator / editor)
-- ---------------------------------------------------------------------------
CREATE TABLE t_admin_user (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    username      VARCHAR(50)     NOT NULL,
    password_hash VARCHAR(100)    NOT NULL,
    role          VARCHAR(30)     NOT NULL COMMENT 'super_admin / operator / editor',
    status        VARCHAR(20)     NOT NULL DEFAULT 'enabled',
    created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_admin_username (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT '后台账号';

-- ---------------------------------------------------------------------------
-- 操作日志(§4.4:所有写操作记入;契约 GET /admin/logs)
-- ---------------------------------------------------------------------------
CREATE TABLE t_operation_log (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    admin_id    BIGINT UNSIGNED NOT NULL,
    action      VARCHAR(64)     NOT NULL,
    target_type VARCHAR(64)     NULL,
    target_id   VARCHAR(64)     NULL,
    detail      JSON            NULL,
    ip          VARCHAR(45)     NULL COMMENT 'IPv6 最长 45 字符',
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_oplog_admin (admin_id),
    KEY idx_oplog_created (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT '操作日志';
