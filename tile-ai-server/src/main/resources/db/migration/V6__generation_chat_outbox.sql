-- ============================================================================
-- V6 生成任务、对话、发件箱 —— 架构 §8.6
--   t_generation_task / t_chat_session / t_chat_message / t_outbox
--
-- 状态机恒为 pending → running → success / failed(契约 T-10)。
-- t_outbox 是 Outbox 模式的落点(架构 §6.4):业务数据与发件箱记录在**同一事务**
-- 内写入,再由独立投递器扫描投递 —— MQ 消息不得与业务数据分两次提交。
-- 消息体见 asyncapi-internal.yaml(queue.generate / queue.index.sync)。
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 生成/解析任务(契约 GET /generate/{taskId};task_id 为对外唯一标识、幂等键)
-- ---------------------------------------------------------------------------
CREATE TABLE t_generation_task (
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    task_id          VARCHAR(64)     NOT NULL COMMENT '对外唯一;回调幂等去重键',
    user_id          BIGINT UNSIGNED NOT NULL,
    plan_id          BIGINT UNSIGNED NULL COMMENT '覆盖原方案时携带',
    type             VARCHAR(20)     NOT NULL COMMENT 'image / parse',
    status           VARCHAR(20)     NOT NULL DEFAULT 'pending' COMMENT 'pending / running / success / failed',
    progress         INT             NOT NULL DEFAULT 0 COMMENT '0-100,一期前端每 2s 轮询',
    payload          JSON            NULL COMMENT '入参快照',
    result_ref       VARCHAR(500)    NULL,
    error            VARCHAR(1000)   NULL,
    external_task_id VARCHAR(128)    NULL COMMENT '第三方模型侧任务号',
    created_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_task_task_id (task_id),
    KEY idx_task_user (user_id, created_at),
    KEY idx_task_status (status, updated_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT '生成与解析任务';

-- ---------------------------------------------------------------------------
-- 对话会话(路径 C;契约 GET /agent/chat/sessions)
-- ---------------------------------------------------------------------------
CREATE TABLE t_chat_session (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id    BIGINT UNSIGNED NOT NULL,
    title      VARCHAR(200)    NULL,
    created_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_chat_session_user (user_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT '对话会话';

-- ---------------------------------------------------------------------------
-- 对话消息(长期消息由 Java 落库;智能体侧只存 Redis 短期上下文,架构 §4.2)
-- refs 为引用的 SKU 列表,支持"一键采纳"
-- ---------------------------------------------------------------------------
CREATE TABLE t_chat_message (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    session_id BIGINT UNSIGNED NOT NULL,
    role       VARCHAR(20)     NOT NULL COMMENT 'user / assistant',
    content    TEXT            NULL,
    refs       JSON            NULL COMMENT '引用的 SKU 列表,支持一键采纳',
    created_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_chat_msg_session (session_id, id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT '对话消息';

-- ---------------------------------------------------------------------------
-- 发件箱(架构 §6.4 可靠性核心)
-- 投递器按 (status, next_retry_at) 扫描;失败按指数退避重试,超阈值进 DLQ
-- ---------------------------------------------------------------------------
CREATE TABLE t_outbox (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    aggregate_type VARCHAR(30)     NOT NULL COMMENT 'task / index_sync',
    aggregate_id   VARCHAR(64)     NOT NULL,
    queue          VARCHAR(100)    NOT NULL COMMENT '目标队列,如 queue.generate',
    payload        JSON            NOT NULL,
    status         VARCHAR(20)     NOT NULL DEFAULT 'pending' COMMENT 'pending / sent / failed',
    retry_count    INT             NOT NULL DEFAULT 0,
    next_retry_at  DATETIME        NULL,
    created_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at        DATETIME        NULL,
    PRIMARY KEY (id),
    KEY idx_outbox_dispatch (status, next_retry_at),
    KEY idx_outbox_aggregate (aggregate_type, aggregate_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT '发件箱(可靠投递)';
