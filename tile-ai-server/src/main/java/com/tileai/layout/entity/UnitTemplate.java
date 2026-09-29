package com.tileai.layout.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.tileai.layout.model.LayoutModel;

/**
 * 户型模板(架构 §8.3 {@code t_unit_template};PRD §5.3.1)。
 *
 * <p><b>明文存储</b>:§8.3 明确 {@code layout_json} 是"系统公共模板,明文" ——
 * 加密只作用于随方案固化的户型快照({@code t_layout.layout_enc} / {@code t_plan.layout_enc})。
 *
 * <p>状态取值与契约一致({@code on_sale} / {@code off_sale})——
 * 契约的 {@code UnitTemplate.status} 引用 {@code ProductStatus}。
 * 注:V3 建表时架构尚未规定取值,当时写的默认值是 {@code enabled},已由
 * 迁移 {@code V8__align_status_defaults.sql} 修正。
 */
@TableName(value = "t_unit_template", autoResultMap = true)
public class UnitTemplate {

    public static final String STATUS_ON_SALE = "on_sale";
    public static final String STATUS_OFF_SALE = "off_sale";

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String name;

    /** 居室数。 */
    private Integer roomCount;

    /** 面积区间,如 {@code 80-100㎡}。 */
    private String areaRange;

    /** 户型结构,如 {@code 三室两厅}。 */
    private String structure;

    /** 空间列表与尺寸,<b>明文</b>。 */
    @TableField(value = "layout_json", typeHandler = JacksonTypeHandler.class)
    private LayoutModel layout;

    private String status;

    private LocalDateTime createdAt;

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

    public Integer getRoomCount() {
        return roomCount;
    }

    public void setRoomCount(Integer roomCount) {
        this.roomCount = roomCount;
    }

    public String getAreaRange() {
        return areaRange;
    }

    public void setAreaRange(String areaRange) {
        this.areaRange = areaRange;
    }

    public String getStructure() {
        return structure;
    }

    public void setStructure(String structure) {
        this.structure = structure;
    }

    public LayoutModel getLayout() {
        return layout;
    }

    public void setLayout(LayoutModel layout) {
        this.layout = layout;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
