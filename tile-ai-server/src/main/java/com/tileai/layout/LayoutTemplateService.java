package com.tileai.layout;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tileai.common.api.PageData;
import com.tileai.common.error.BusinessException;
import com.tileai.common.error.ErrorCode;
import com.tileai.layout.dto.UnitTemplateUpsertRequest;
import com.tileai.layout.dto.UnitTemplateView;
import com.tileai.layout.entity.UnitTemplate;
import com.tileai.layout.mapper.UnitTemplateMapper;

/**
 * 户型模板维护(架构 §8.3 {@code t_unit_template};PRD §5.3.1)。
 *
 * <p><b>面积在写入前补齐</b>:模板存的是"标准户型",面积按架构 §8.3 的公式推导,
 * 若模板维护者已手工核算过则原样采用。这样下游(户型录入、报价)拿到模板时
 * 面积一定是齐的,不必各自再算一遍。
 *
 * <p><b>删除无需引用检查</b>:§8.3 明确"户型以<b>加密快照</b>随方案固化,
 * 避免模板变更污染历史方案" —— 方案里存的是自己的快照,与模板再无关联,
 * 所以模板可以随意删除/修改,不会影响任何历史方案。
 */
@Service
public class LayoutTemplateService {

    private final UnitTemplateMapper templateMapper;

    public LayoutTemplateService(UnitTemplateMapper templateMapper) {
        this.templateMapper = templateMapper;
    }

    // ------------------------------------------------------------------

    /**
     * 分页列表(契约 {@code GET /admin/templates})。
     *
     * <p>三个筛选项来自 PRD §5.3.1「按房间数 + 面积区间 + 户型结构筛选」,
     * 均为<b>精确匹配</b> —— 它们是后台录入时选定的分类值,不是自由文本,
     * 用模糊匹配会让"80-100㎡"这种值互相误命中。
     */
    @Transactional(readOnly = true)
    public PageData<UnitTemplateView> page(Integer roomCount, String areaRange, String structure,
                                           int page, int size) {
        LambdaQueryWrapper<UnitTemplate> query = Wrappers.lambdaQuery();
        if (roomCount != null) {
            query.eq(UnitTemplate::getRoomCount, roomCount);
        }
        if (StringUtils.hasText(areaRange)) {
            query.eq(UnitTemplate::getAreaRange, areaRange.trim());
        }
        if (StringUtils.hasText(structure)) {
            query.eq(UnitTemplate::getStructure, structure.trim());
        }
        query.orderByDesc(UnitTemplate::getId);

        Page<UnitTemplate> result = templateMapper.selectPage(new Page<>(page, size), query);
        List<UnitTemplateView> list = result.getRecords().stream()
                .map(UnitTemplateView::of)
                .toList();
        return PageData.of(list, result.getTotal(), page, size);
    }

    /** 详情(契约 {@code GET /admin/templates/{id}};失败码 30001)。 */
    @Transactional(readOnly = true)
    public UnitTemplateView get(long id) {
        UnitTemplate template = templateMapper.selectById(id);
        if (template == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "户型模板不存在: " + id);
        }
        return UnitTemplateView.of(template);
    }

    // ------------------------------------------------------------------

    /** 新增(契约 {@code POST /admin/templates});返回新模板 id。 */
    @Transactional
    public long create(UnitTemplateUpsertRequest request) {
        UnitTemplate template = new UnitTemplate();
        apply(template, request);
        // 契约里 status 非必填;不传时按"先建好、再上架"的常规流程落到下架态
        template.setStatus(StringUtils.hasText(request.status())
                ? request.status() : UnitTemplate.STATUS_OFF_SALE);

        templateMapper.insert(template);
        return template.getId();
    }

    /** 编辑(契约 {@code PUT /admin/templates/{id}})。layout 为整体替换。 */
    @Transactional
    public void update(long id, UnitTemplateUpsertRequest request) {
        UnitTemplate existing = templateMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "户型模板不存在: " + id);
        }
        apply(existing, request);
        if (StringUtils.hasText(request.status())) {
            existing.setStatus(request.status());
        }
        templateMapper.updateById(existing);
    }

    /** 删除(契约 {@code DELETE /admin/templates/{id}})。 */
    @Transactional
    public void delete(long id) {
        if (templateMapper.selectById(id) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "户型模板不存在: " + id);
        }
        templateMapper.deleteById(id);
    }

    // ------------------------------------------------------------------

    private static void apply(UnitTemplate template, UnitTemplateUpsertRequest request) {
        template.setName(request.name());
        template.setRoomCount(request.roomCount());
        template.setAreaRange(request.areaRange());
        template.setStructure(request.structure());
        // 面积推导:只补不覆盖(架构 §8.3 v1.5)
        template.setLayout(LayoutAreaCalculator.withDerivedAreas(request.layout()));
    }
}
