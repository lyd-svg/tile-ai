-- ============================================================================
-- V11 T-1.15 报价过期标记所需索引(只加索引,不动任何列)
--
-- 背景:产品调价时要把"引用了这块砖的在用方案"标成 quote_expired = 1。
--   t_plan_part 此前只有 idx_plan_part_plan (plan_id, part) —— 按 sku_code
--   反查受影响方案没有任何索引可用,**每次调价都是全表扫描**。
--
-- idx_plan_part_sku   —— 单个产品改价 / 批量导入改价时按 SKU 反查。
-- idx_plan_expired    —— 全局报价参数(辅料单价、损耗率)变动时要扫全表所有在用方案;
--                        标记语句带 quote_expired = 0 条件,标过一次后命中的行极少,
--                        靠这个联合索引即可取完,而不是全表扫描。
--
-- ⚠️ 只加索引。SchemaMigrationTest.EXPECTED_SCHEMA 是**按列序**逐列比对的,加列会让那条红;
--    索引不属于架构 §8 的规定内容(§8 只规定字段),该测试不断言索引全集。
-- ⚠️ 唯一要同步改的是 SchemaMigrationTest.EXPECTED_MIGRATIONS —— 版本集合要求完全相等。
-- ⚠️ 本文件是新增迁移,不得回头修改 V1–V10(校验和会失配)。
-- ============================================================================

ALTER TABLE t_plan_part
    ADD KEY idx_plan_part_sku (sku_code);

ALTER TABLE t_plan
    ADD KEY idx_plan_expired (quote_expired, is_deleted);
