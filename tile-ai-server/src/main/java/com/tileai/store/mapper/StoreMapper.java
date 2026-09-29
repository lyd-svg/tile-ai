package com.tileai.store.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tileai.store.entity.Store;

/** {@code t_store} 的数据访问。单店单条。 */
@Mapper
public interface StoreMapper extends BaseMapper<Store> {
}
