package com.tileai.common.outbox;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/** {@code t_outbox} 的数据访问(架构 §6.4)。 */
@Mapper
public interface OutboxMapper extends BaseMapper<OutboxMessage> {
}
