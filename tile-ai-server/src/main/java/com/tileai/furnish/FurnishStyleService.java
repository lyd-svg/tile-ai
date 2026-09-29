package com.tileai.furnish;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tileai.common.error.BusinessException;
import com.tileai.common.error.ErrorCode;
import com.tileai.furnish.dto.FurnishStyleUpsertRequest;
import com.tileai.furnish.dto.FurnishStyleView;
import com.tileai.furnish.entity.FurnishStyle;
import com.tileai.furnish.mapper.FurnishStyleMapper;

/**
 * 陈设风格配置(架构 §8.5 {@code t_furnish_style};PRD §5.4、§5.7)。
 *
 * <p>这是一张 <b>空间 × 风格</b> 矩阵。写入走 <b>upsert</b>:以
 * {@code (space, style)} 为唯一键,重复提交是<b>更新</b>而不是新增 ——
 * 这是契约 {@code PUT /admin/furnish} 的定义,也是 T-0.10 的验证判据。
 */
@Service
public class FurnishStyleService {

    private final FurnishStyleMapper furnishStyleMapper;

    public FurnishStyleService(FurnishStyleMapper furnishStyleMapper) {
        this.furnishStyleMapper = furnishStyleMapper;
    }

    /**
     * 列表(契约 {@code GET /admin/furnish})。
     *
     * <p>契约对该接口声明的是 <b>{@code data: { list }} 而非分页</b> ——
     * 空间×风格的组合数量可控,前端需要的是完整矩阵。
     */
    @Transactional(readOnly = true)
    public List<FurnishStyleView> list(String space, String style) {
        LambdaQueryWrapper<FurnishStyle> query = Wrappers.lambdaQuery();
        if (StringUtils.hasText(space)) {
            query.eq(FurnishStyle::getSpace, space.trim());
        }
        if (StringUtils.hasText(style)) {
            query.eq(FurnishStyle::getStyle, style.trim());
        }
        query.orderByAsc(FurnishStyle::getSpace).orderByAsc(FurnishStyle::getStyle)
                .orderByAsc(FurnishStyle::getId);

        return furnishStyleMapper.selectList(query).stream()
                .map(FurnishStyleView::of)
                .toList();
    }

    /**
     * 新增或更新(契约 {@code PUT /admin/furnish})。
     *
     * <p>以 {@code (space, style)} 为唯一键 upsert。之所以不返回 id,
     * 是因为契约对该接口的响应只声明了 {@code ApiResponse} 而不含 data。
     */
    @Transactional
    public void upsert(FurnishStyleUpsertRequest request) {
        FurnishStyle existing = findBySpaceAndStyle(request.space(), request.style());

        if (existing == null) {
            FurnishStyle created = new FurnishStyle();
            created.setSpace(request.space());
            created.setStyle(request.style());
            apply(created, request);
            created.setStatus(StringUtils.hasText(request.status())
                    ? request.status() : FurnishStyle.STATUS_OFF_SALE);
            furnishStyleMapper.insert(created);
            return;
        }

        apply(existing, request);
        // 未传 status 时不改动既有状态 —— 否则"只改提示词"会把已生效的配置打回下架
        if (StringUtils.hasText(request.status())) {
            existing.setStatus(request.status());
        }
        furnishStyleMapper.updateById(existing);
    }

    /** 删除(契约 {@code DELETE /admin/furnish/{id}};失败码 30001)。 */
    @Transactional
    public void delete(long id) {
        if (furnishStyleMapper.selectById(id) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "陈设配置不存在: " + id);
        }
        furnishStyleMapper.deleteById(id);
    }

    // ------------------------------------------------------------------

    private FurnishStyle findBySpaceAndStyle(String space, String style) {
        return furnishStyleMapper.selectOne(Wrappers.<FurnishStyle>lambdaQuery()
                .eq(FurnishStyle::getSpace, space)
                .eq(FurnishStyle::getStyle, style));
    }

    private static void apply(FurnishStyle target, FurnishStyleUpsertRequest request) {
        target.setSpace(request.space());
        target.setStyle(request.style());
        target.setPrompt(request.prompt());
        target.setCoverImage(request.coverImage());
    }
}
