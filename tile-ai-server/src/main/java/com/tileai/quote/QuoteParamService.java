package com.tileai.quote;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tileai.quote.dto.QuoteParamUpsertRequest;
import com.tileai.quote.dto.QuoteParamView;
import com.tileai.quote.entity.QuoteParam;
import com.tileai.quote.entity.QuoteParamKey;
import com.tileai.quote.mapper.QuoteParamMapper;

/**
 * 报价参数维护(架构 §8.5 {@code t_quote_param};PRD §5.5、§5.7)。
 *
 * <p><b>为什么校验这么重</b>:这两个参数(辅料单价、损耗率)直接参与每一个报价金额的计算。
 * 填错一个数量级,所有报价同步偏 100 倍,而接口、数据库、页面都不会报错。
 * 所以量纲校验做在写入侧,宁可拒得严一点。
 *
 * <p>该接口的权限点仅 super_admin(架构 §4.4)—— 由 {@code @RequiresPermission} 保证。
 */
@Service
public class QuoteParamService {

    private final QuoteParamMapper quoteParamMapper;

    public QuoteParamService(QuoteParamMapper quoteParamMapper) {
        this.quoteParamMapper = quoteParamMapper;
    }

    /**
     * 列表(契约 {@code GET /admin/quote-params})。
     *
     * <p>契约声明的是 {@code data: { list }} 而非分页 —— 参数就三条,分页没有意义。
     */
    @Transactional(readOnly = true)
    public List<QuoteParamView> list() {
        return quoteParamMapper.selectList(Wrappers.<QuoteParam>lambdaQuery()
                        .orderByAsc(QuoteParam::getId))
                .stream()
                .map(QuoteParamView::of)
                .toList();
    }

    /**
     * 新增或更新(契约 {@code PUT /admin/quote-params})。
     *
     * <p>以 {@code param_key} 为唯一键 upsert —— 参数表是"键值对"结构,
     * 同一个键存在多条会让人不知道哪条生效。
     */
    @Transactional
    public void upsert(QuoteParamUpsertRequest request) {
        QuoteParamKey key = QuoteParamKey.of(request.paramKey());
        // 量纲校验(D-19 / D-15):元/㎡ 与小数两种量纲,填错不会报错只会算错
        key.validate(request.paramValue());

        QuoteParam existing = quoteParamMapper.selectOne(Wrappers.<QuoteParam>lambdaQuery()
                .eq(QuoteParam::getParamKey, request.paramKey()));

        if (existing == null) {
            QuoteParam created = new QuoteParam();
            created.setParamKey(request.paramKey());
            created.setParamValue(request.paramValue());
            created.setRemark(request.remark());
            quoteParamMapper.insert(created);
            return;
        }

        existing.setParamValue(request.paramValue());
        existing.setRemark(request.remark());
        quoteParamMapper.updateById(existing);
    }
}
