package com.tileai.agent.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tileai.agent.entity.ChatSession;

/** {@code t_chat_session} 的数据访问。 */
@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSession> {
}
