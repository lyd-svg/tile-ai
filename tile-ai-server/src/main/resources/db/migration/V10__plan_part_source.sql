-- ============================================================================
-- V10 t_plan_part 补 source 列 —— 契约 openapi-common.yaml#/components/schemas/PlanPart
--
--   PlanPart.source (契约 PartSource) = user(用户显式选择) / ai(AI 自动补齐)
--
-- 上游依据:PRD §5.2.4「AI 自动补齐……补齐结果随方案自动保存」+ §5.5「AI 补齐的砖
-- 一并按同规则计入材料费」。契约早已声明该字段,但架构 §8.4 的 t_plan_part 字段清单
-- 漏了它 —— 本迁移与架构 §8.4 同步补上,属**补漏**,不是反向改文档。
--
-- 为什么现在才补:T-1.11 存方案时把它丢掉了,理由是「那是生成口径,不是方案口径」
-- (见 GenerateCallbackService#toPlanParts 的注释)。该判断被 T-2.1 的契约↔实现比对
-- 量了出来 —— 契约声明了、实现没有。按既定口径(§36.8:分叉一律改实现、不动契约),
-- 本次改实现。原始判断保留在 openapi-verification.md 的 T-1.12 一节里。
--
-- ⚠️ 列必须**追加在最后**。`SchemaMigrationTest.EXPECTED_SCHEMA` 是**按列序**逐列
--    比对的(它 last 断言字段名列表完全相等),插在中间会让那条测试红。
-- ⚠️ 本文件是新增迁移,不得回头修改 V1–V9(校验和会失配)。
-- ============================================================================

ALTER TABLE t_plan_part
    ADD COLUMN source VARCHAR(10) NULL
        COMMENT '部位瓷砖来源:user 用户选择 / ai AI 补齐(契约 PartSource)'
        AFTER material_fee;
