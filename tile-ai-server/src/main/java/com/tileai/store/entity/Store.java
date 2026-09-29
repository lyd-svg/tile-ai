package com.tileai.store.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 门店信息(架构 §8.5 {@code t_store};PRD §7.6 —— <b>线下为单店</b>)。
 *
 * <p>"单店单条"意味着这张表在正常运行时<b>只有一行</b>:没有"新增门店""删除门店",
 * 只有对那一行的读取与整体更新。这也是 §8.5 只给它列了 {@code updated_at}
 * 而没有 {@code created_at} 的原因 —— 配置项关心的是"何时被改"。
 */
@TableName("t_store")
public class Store {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String name;

    private String address;

    private String phone;

    /** 地图 / 导航链接;§8.5 未标必填,契约中亦为 nullable。 */
    private String mapUrl;

    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getMapUrl() {
        return mapUrl;
    }

    public void setMapUrl(String mapUrl) {
        this.mapUrl = mapUrl;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
