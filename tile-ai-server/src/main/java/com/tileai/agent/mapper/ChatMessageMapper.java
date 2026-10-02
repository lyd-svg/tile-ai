package com.tileai.agent.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tileai.agent.entity.ChatMessage;

/** {@code t_chat_message} 的数据访问。 */
@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {
}
