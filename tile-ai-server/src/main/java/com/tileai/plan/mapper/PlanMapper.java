package com.tileai.plan.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tileai.plan.entity.Plan;

/** {@code t_plan} 的数据访问。 */
@Mapper
public interface PlanMapper extends BaseMapper<Plan> {
}
