package com.tileai.product.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tileai.product.imports.ImportBatch;

/** {@code t_import_batch} 的数据访问(架构 §8.7)。 */
@Mapper
public interface ImportBatchMapper extends BaseMapper<ImportBatch> {
}
