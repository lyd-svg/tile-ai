package com.tileai.auth.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tileai.auth.entity.User;

/** {@code t_user} 的数据访问。 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
