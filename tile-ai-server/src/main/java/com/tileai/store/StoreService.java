package com.tileai.store;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tileai.common.error.BusinessException;
import com.tileai.common.error.ErrorCode;
import com.tileai.store.dto.StoreUpsertRequest;
import com.tileai.store.dto.StoreView;
import com.tileai.store.entity.Store;
import com.tileai.store.mapper.StoreMapper;

/**
 * 门店信息维护(架构 §8.5 {@code t_store};PRD §7.6 —— <b>单店</b>)。
 *
 * <p>一期线下只有一家店,所以这张表是<b>配置项而不是集合</b>:
 * 只有读取与整体更新,没有新增与删除(契约也只声明了 GET 与 PUT)。
 */
@Service
public class StoreService {

    private final StoreMapper storeMapper;

    public StoreService(StoreMapper storeMapper) {
        this.storeMapper = storeMapper;
    }

    /** 读取门店(契约 {@code GET /admin/store});尚未配置时 30001。 */
    @Transactional(readOnly = true)
    public StoreView get() {
        return StoreView.of(requireStore());
    }

    /**
     * 整体更新门店(契约 {@code PUT /admin/store})。
     *
     * <p><b>「整体更新而非插入」的落实方式</b>:无论调用多少次,库里始终<b>恰好一行</b> ——
     * 已存在就覆盖那一行,不存在才建。不能理解为"库里没有就不让配":
     * 表里没有种子数据,那样会导致首次配置无从进行。
     *
     * <p>不用 {@code @Transactional} 之外的额外手段防并发插入:单店后台的
     * 门店配置是极低频操作,由运营一人维护,并发双击的代价(多一行)远小于
     * 引入分布式锁的复杂度。回收空闲行由 T-2.x 的运维手段覆盖。
     */
    @Transactional
    public void update(StoreUpsertRequest request) {
        List<Store> all = storeMapper.selectList(Wrappers.<Store>lambdaQuery()
                .orderByAsc(Store::getId));

        Store target;
        if (all.isEmpty()) {
            target = new Store();
        } else {
            target = all.get(0);
        }

        target.setName(request.name());
        target.setAddress(request.address());
        target.setPhone(request.phone());
        target.setMapUrl(request.mapUrl());

        if (target.getId() == null) {
            storeMapper.insert(target);
        } else {
            storeMapper.updateById(target);
        }
    }

    // ------------------------------------------------------------------

    /** 取唯一那条门店记录;不存在即 30001。 */
    private Store requireStore() {
        Store store = storeMapper.selectOne(Wrappers.<Store>lambdaQuery()
                .orderByAsc(Store::getId).last("LIMIT 1"));
        if (store == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "门店信息尚未配置");
        }
        return store;
    }
}
