-- ============================================================================
-- V12 放宽效果图地址的列宽(500 → 2048)
--
-- 背景(2026-10-02):接入硅基流动 Qwen-Image-Edit-2509 后实测,**它返回的是带签名的
--   临时地址**(s3.siliconflow.cn + 一长串 JWT 安全令牌),长度远超 500 字符。
--   而回调的 DTO 与这两列原来都卡在 500 ⇒ **成功回调被 10001 拒掉,图进不了系统**。
--   实测证据:600 字符的地址即返回
--   `{"code":10001,"errors":[{"field":"imageUrls[0].url","reason":"图片 url 不能超过 500 字符"}]}`。
--
-- 为什么**两列**都要放宽(缺一不可):
--   t_plan_image.url               —— 方案里的效果图地址;
--   t_generation_task.result_ref   —— 任务上的封面地址(进度接口返回的就是它)。
--   正常情况下 §53 的转存会把长地址下载后换成本机的短地址(约 60 字符)再入库,
--   所以**通常存进去是短的**;但**转存失败时会降级为保留原地址** ——
--   那种情况下长地址会直接写进来,列宽不放宽就会在写库时报错,
--   把一次本可降级的小事变成故障。
--
-- 为什么是 2048:签名地址里主要是那串 JWT,实测量级在一千多字符;
--   2048 留了余量,两个 varchar(2048) 在 utf8mb4 下最多各占 8KB,不至于撑爆行内长度。
--
-- ⚠️ 与 V11 同类:本文件是**新增迁移**,不得回头修改 V1–V11(校验和会失配)。
-- ⚠️ 列宽属于 SchemaMigrationTest.EXPECTED_SCHEMA 的断言内容 —— 已同步改那里,
--    并把 "12" 加进 EXPECTED_MIGRATIONS。
-- ⚠️ 契约**不需要改**:openapi-internal.yaml 的 imageUrls[].url 与
--    openapi-common.yaml 的 PlanImage.url 都只有 `type: string`,没有 maxLength ——
--    500 是**实现自己加的**约束,本次是放宽实现。
-- ============================================================================

ALTER TABLE t_plan_image
    MODIFY COLUMN url VARCHAR(2048) NOT NULL;

ALTER TABLE t_generation_task
    MODIFY COLUMN result_ref VARCHAR(2048) NULL;
