package com.tileai.admin.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tileai.admin.entity.DictEntry;

/** {@code t_dict} 的数据访问。 */
@Mapper
public interface DictMapper extends BaseMapper<DictEntry> {
}
