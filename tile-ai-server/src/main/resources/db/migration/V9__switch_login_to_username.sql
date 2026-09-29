-- ============================================================================
-- V9 登录方式变更:t_user 改用 username,移除短信相关结构
--
-- 对应 PRD v0.7 / 架构 v1.7:登录由「手机号 + 短信验证码」改为「用户名 + 密码」。
--
--   t_user.phone(唯一)  →  t_user.username(唯一)
--   t_sms_code 表       →  删除(不再有任何验证码场景)
--
-- ⚠️ 本迁移假定 **C 端尚无任何用户数据** —— 这是成立的:注册登录接口(T-1.1)尚未实现,
--    库里不可能有 C 端用户。若在已有 C 端用户的环境执行,`ADD COLUMN username NOT NULL`
--    会因缺省值而失败 —— 那种情况下应先规划用户名迁移策略,而不是直接跑本文件。
--
-- ⚠️ 本文件是新增迁移,不得回头修改 V1–V8(校验和会失配)。
-- ============================================================================

-- 1) 新增用户名列。
--    先加为 NULL,回填后再收紧为 NOT NULL —— 这样即使表中有历史行也能安全过渡。
ALTER TABLE t_user
    ADD COLUMN username VARCHAR(50) NULL COMMENT '登录用户名,唯一' AFTER id;

-- 2) 移除外键依赖的手机号唯一约束与列。
--    明确写出索引名:MySQL 对 `DROP COLUMN` 隐含的索引删除行为不直观,
--    显式删索引再删列,阅读与排错都更清楚。
ALTER TABLE t_user DROP INDEX uk_user_phone;
ALTER TABLE t_user DROP COLUMN phone;

-- 3) 收紧为非空并加唯一约束(表为空,收紧必然成功)。
ALTER TABLE t_user
    MODIFY COLUMN username VARCHAR(50) NOT NULL COMMENT '登录用户名,唯一';
ALTER TABLE t_user
    ADD UNIQUE KEY uk_user_username (username);

-- 4) 删除短信验证码表。
--    登录改为账号密码后不再有任何验证码场景(架构 §15.6「短信通道 —— 已取消」)。
DROP TABLE IF EXISTS t_sms_code;
