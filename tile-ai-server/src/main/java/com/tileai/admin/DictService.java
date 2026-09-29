package com.tileai.admin;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tileai.admin.dto.DictUpsertRequest;
import com.tileai.admin.dto.DictView;
import com.tileai.admin.entity.DictEntry;
import com.tileai.admin.mapper.DictMapper;
import com.tileai.admin.mapper.DictUsageMapper;
import com.tileai.common.error.BusinessException;
import com.tileai.common.error.ErrorCode;

/**
 * 空间 / 风格字典维护(架构 §8.5 {@code t_dict};D-14 定稿)。
 *
 * <p><b>字典为什么值得单独存在</b>:空间类型与装修风格是<b>跨模块引用的全局枚举</b> ——
 * 产品库的适用空间、搭配、陈设矩阵、方案、户型都用它。硬编码进前端或契约
 * (D-14)会让"新增一个房间类型"变成一次发版。所以它由后台维护、前端只读。
 *
 * <p>也正因为它是全局的,<b>删除必须做引用检查</b>:静默删掉一个正在被产品引用的键,
 * 会留下一堆指向不存在空间的悬空引用(前台只能显示空白)。
 */
@Service
public class DictService {

    private final DictMapper dictMapper;
    private final DictUsageMapper dictUsageMapper;
    private final ObjectMapper objectMapper;

    public DictService(DictMapper dictMapper, DictUsageMapper dictUsageMapper,
                       ObjectMapper objectMapper) {
        this.dictMapper = dictMapper;
        this.dictUsageMapper = dictUsageMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 列表(契约 {@code GET /admin/dicts})。
     *
     * <p>管理端<b>返回含 status=disabled 的项</b>(契约明确)—— 运营要能看到被停用的项,
     * 否则无从恢复。C 端只读接口(T-1.4)才过滤 enabled。
     */
    @Transactional(readOnly = true)
    public List<DictView> list(String type) {
        LambdaQueryWrapper<DictEntry> query = Wrappers.lambdaQuery();
        if (StringUtils.hasText(type)) {
            query.eq(DictEntry::getDictType, type.trim());
        }
        query.orderByAsc(DictEntry::getDictType).orderByAsc(DictEntry::getSort)
                .orderByAsc(DictEntry::getId);

        return dictMapper.selectList(query).stream().map(DictView::of).toList();
    }

    /**
     * C 端字典查询(契约 {@code GET /dicts})。
     *
     * <p>与后台列表的唯一区别:<b>只返回 {@code status=enabled} 的项</b>,
     * 因为停用项对前台是不可见的。排序同样按 {@code sort} 升序 —— 前端下拉的顺序
     * 由运营在后台决定(D-14:枚举不硬编码)。
     */
    @Transactional(readOnly = true)
    public List<DictView> listEnabled(String type) {
        LambdaQueryWrapper<DictEntry> query = Wrappers.lambdaQuery();
        query.eq(DictEntry::getStatus, DictEntry.STATUS_ENABLED);
        if (StringUtils.hasText(type)) {
            query.eq(DictEntry::getDictType, type.trim());
        }
        query.orderByAsc(DictEntry::getDictType).orderByAsc(DictEntry::getSort)
                .orderByAsc(DictEntry::getId);

        return dictMapper.selectList(query).stream().map(DictView::of).toList();
    }

    /** 新增(契约 {@code POST /admin/dicts})。 */
    @Transactional
    public long create(DictUpsertRequest request) {
        if (findByTypeAndKey(request.type(), request.key()) != null) {
            throw new BusinessException(ErrorCode.STATE_NOT_ALLOWED,
                    "同类型下已存在该存储键: " + request.type() + " / " + request.key());
        }

        DictEntry entry = new DictEntry();
        entry.setDictType(request.type());
        entry.setDictKey(request.key());
        entry.setDictLabel(request.label());
        entry.setSort(request.sort() == null ? 0 : request.sort());
        entry.setStatus(StringUtils.hasText(request.status())
                ? request.status() : DictEntry.STATUS_ENABLED);
        dictMapper.insert(entry);
        return entry.getId();
    }

    /** 更新(契约 {@code PUT /admin/dicts/{id}})。 */
    @Transactional
    public void update(long id, DictUpsertRequest request) {
        DictEntry existing = dictMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "字典项不存在: " + id);
        }

        DictEntry sameKey = findByTypeAndKey(request.type(), request.key());
        if (sameKey != null && !sameKey.getId().equals(id)) {
            throw new BusinessException(ErrorCode.STATE_NOT_ALLOWED,
                    "同类型下已存在该存储键: " + request.type() + " / " + request.key());
        }

        existing.setDictType(request.type());
        existing.setDictKey(request.key());
        existing.setDictLabel(request.label());
        if (request.sort() != null) {
            existing.setSort(request.sort());
        }
        if (StringUtils.hasText(request.status())) {
            existing.setStatus(request.status());
        }
        dictMapper.updateById(existing);
    }

    /**
     * 删除(契约 {@code DELETE /admin/dicts/{id}})。
     *
     * <p><b>被引用则拒绝(30002),而不是静默删除</b> —— 契约对该接口的 description
     * 写明了这一点。悬空引用不会报错,只会在前台显示成空白,属于最难排查的那类问题。
     */
    @Transactional
    public void delete(long id) {
        DictEntry existing = dictMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "字典项不存在: " + id);
        }

        String usage = describeUsage(existing);
        if (usage != null) {
            throw new BusinessException(ErrorCode.STATE_NOT_ALLOWED,
                    "该字典项已被" + usage + "引用,请先解除引用再删除");
        }

        dictMapper.deleteById(id);
    }

    /** 供批量导入等服务复用:某类型下已启用的键集合。 */
    @Transactional(readOnly = true)
    public List<DictEntry> enabledOfType(String dictType) {
        return dictMapper.selectList(Wrappers.<DictEntry>lambdaQuery()
                .eq(DictEntry::getDictType, dictType)
                .eq(DictEntry::getStatus, DictEntry.STATUS_ENABLED));
    }

    // ------------------------------------------------------------------

    private DictEntry findByTypeAndKey(String type, String key) {
        return dictMapper.selectOne(Wrappers.<DictEntry>lambdaQuery()
                .eq(DictEntry::getDictType, type)
                .eq(DictEntry::getDictKey, key));
    }

    /**
     * 汇总引用情况;无人引用返回 {@code null},否则返回可读的来源描述。
     *
     * <p>把各来源的计数一并列出(而不是碰到第一个就返回),是为了让运营一次就知道
     * 需要去哪些地方解除引用,而不是删一次、报一次、改一处、再删。
     */
    private String describeUsage(DictEntry entry) {
        String key = entry.getDictKey();
        long products = dictUsageMapper.countProducts(toJsonString(key));
        long combos = dictUsageMapper.countCombos(key);
        long furnishes = dictUsageMapper.countFurnishStyles(key);
        long plans = dictUsageMapper.countPlans(key);

        StringBuilder usage = new StringBuilder();
        append(usage, products, "个产品");
        append(usage, combos, "个推荐搭配");
        append(usage, furnishes, "条陈设配置");
        append(usage, plans, "个方案");

        return usage.isEmpty() ? null : usage.toString();
    }

    private static void append(StringBuilder usage, long count, String label) {
        if (count <= 0) {
            return;
        }
        if (!usage.isEmpty()) {
            usage.append("、");
        }
        usage.append(count).append(label);
    }

    /**
     * 把键转成 JSON 字符串字面量(带双引号且正确转义),供 {@code JSON_CONTAINS} 使用。
     *
     * <p>不用手工拼 {@code "\"" + key + "\""}:键里若含引号或反斜杠会拼出非法 JSON,
     * 轻则查不出引用、重则 SQL 报错。交给 JSON 序列化保证转义正确。
     */
    private String toJsonString(String key) {
        try {
            return objectMapper.writeValueAsString(key);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("字典键无法序列化为 JSON: " + key, e);
        }
    }
}
