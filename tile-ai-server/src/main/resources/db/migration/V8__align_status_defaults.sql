-- ============================================================================
-- V8 对齐两处状态列的默认值与注释 —— 使其与契约一致
--   t_unit_template.status    (架构 §8.3)
--   t_furnish_style.status    (架构 §8.5)
--
-- 背景:这两张表的 status 在**契约**里都声明为
--   `$ref: './openapi-common.yaml#/components/schemas/ProductStatus'`
-- 即取值只能是 `on_sale` / `off_sale`。
-- 而 V3 / V5 建表时架构 §8.3、§8.5 尚未规定取值,当时按惯例写成了
-- `enabled` / `disabled`,默认值因此与契约不符。
--
-- ⚠️ 列类型(VARCHAR(20))本来就能容纳两种取值,所以这不是"改结构",
--    只是把**默认值**和**注释**改成契约认可的口径。之所以仍要改默认值:
--    只要有一条 INSERT 没显式给 status,数据库就会写入 `enabled` ——
--    一个契约里不存在的状态值,而且**不报错**。
--
-- ⚠️ 本文件是新增迁移,不得回头修改 V1–V8 之前的任何文件(校验和会失配)。
-- ============================================================================

ALTER TABLE t_unit_template
    MODIFY COLUMN status VARCHAR(20) NOT NULL DEFAULT 'off_sale'
        COMMENT 'on_sale / off_sale —— 契约 UnitTemplate.status 引用 ProductStatus(架构 §8.3 未规定取值)';

ALTER TABLE t_furnish_style
    MODIFY COLUMN status VARCHAR(20) NOT NULL DEFAULT 'off_sale'
        COMMENT 'on_sale / off_sale —— 契约 FurnishStyleUpsert.status 引用 ProductStatus(架构 §8.5 未规定取值)';
