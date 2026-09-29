package com.tileai.admin.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tileai.admin.entity.OperationLog;

/** {@code t_operation_log} 的数据访问。只增不改不删。 */
@Mapper
public interface OperationLogMapper extends BaseMapper<OperationLog> {
}
