package com.tileai.product.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tileai.product.entity.Product;

/** {@code t_product} 的数据访问。 */
@Mapper
public interface ProductMapper extends BaseMapper<Product> {
}
