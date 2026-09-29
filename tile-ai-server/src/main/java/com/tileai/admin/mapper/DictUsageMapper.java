package com.tileai.admin.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 字典项的<b>引用计数</b> —— 删除字典项前必须确认没有任何地方在用它
 * (契约 {@code DELETE /admin/dicts/{id}}:「删除前应校验无产品/搭配/方案引用,
 * 被引用则返回状态不允许 30002」)。
 *
 * <p><b>为什么用原生 SQL 而不是各模块的 Mapper</b>:这些表分别属于产品、搭配、
 * 陈设、方案四个模块,而那三个模块的实体类此项目前还不存在(T-1.x 才建)。
 * 为了一个引用检查去把它们的实体提前造出来,会把"模块边界"变成摆设。
 * 计数是纯粹的运维查询,用只读 SQL 表达最直接,也不引入跨模块的类型耦合。
 */
@Mapper
public interface DictUsageMapper {

    /**
     * 产品库中引用了该键的数量。
     *
     * <p>{@code applicable_spaces} / {@code style_tags} 是 JSON 数组,用
     * {@code JSON_CONTAINS} 判断成员;<b>候选值必须带双引号</b>
     * (JSON 字符串字面量),由调用方用 JSON 序列化保证转义正确。
     *
     * <p>列值为 NULL 时 {@code JSON_CONTAINS} 返回 NULL,整个条件不为真 ——
     * 未填这两列的产品不会被误判为"引用了"。
     */
    @Select("""
            SELECT COUNT(*) FROM t_product
             WHERE JSON_CONTAINS(applicable_spaces, #{candidate})
                OR JSON_CONTAINS(style_tags, #{candidate})
            """)
    long countProducts(@Param("candidate") String candidate);

    @Select("SELECT COUNT(*) FROM t_recommend_combo WHERE space = #{key} OR style = #{key}")
    long countCombos(@Param("key") String key);

    @Select("SELECT COUNT(*) FROM t_furnish_style WHERE space = #{key} OR style = #{key}")
    long countFurnishStyles(@Param("key") String key);

    @Select("SELECT COUNT(*) FROM t_plan WHERE space = #{key} OR style = #{key}")
    long countPlans(@Param("key") String key);
}
