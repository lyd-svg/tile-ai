package com.tileai.admin.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tileai.admin.entity.AdminUser;

/** {@code t_admin_user} 的数据访问。 */
@Mapper
public interface AdminUserMapper extends BaseMapper<AdminUser> {
}
