package com.tileai.combo.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tileai.combo.entity.RecommendComboItem;

/**
 * {@code t_recommend_combo_item} 的数据访问。
 *
 * <p>产品库删除 SKU 时也用它做"是否被搭配引用"的检查(T-0.8 的验证判据)。
 */
@Mapper
public interface RecommendComboItemMapper extends BaseMapper<RecommendComboItem> {
}
