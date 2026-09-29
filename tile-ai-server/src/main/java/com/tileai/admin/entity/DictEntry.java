package com.tileai.admin.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 数据字典项(架构 §8.5 {@code t_dict})。
 *
 * <p>建立它的是 T-0.7 的批量导入 —— §8.7 要求校验"枚举值",
 * 而空间类型 / 风格等枚举的<b>权威来源就是这张表</b>(D-14 定稿:前端与契约不硬编码)。
 * 字典的后台增删改查属 <b>T-0.11</b>,届时直接复用本类与其 Mapper。
 */
@TableName("t_dict")
public class DictEntry {

    public static final String STATUS_ENABLED = "enabled";

    /** 字典类型:空间。产品库的 {@code applicableSpaces} 引用此类型的 key。 */
    public static final String TYPE_SPACE = "space";

    /** 字典类型:风格。产品库的 {@code styleTags} 引用此类型的 key。 */
    public static final String TYPE_STYLE = "style";

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String dictType;

    private String dictKey;

    private String dictLabel;

    private Integer sort;

    private String status;

    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDictType() {
        return dictType;
    }

    public void setDictType(String dictType) {
        this.dictType = dictType;
    }

    public String getDictKey() {
        return dictKey;
    }

    public void setDictKey(String dictKey) {
        this.dictKey = dictKey;
    }

    public String getDictLabel() {
        return dictLabel;
    }

    public void setDictLabel(String dictLabel) {
        this.dictLabel = dictLabel;
    }

    public Integer getSort() {
        return sort;
    }

    public void setSort(Integer sort) {
        this.sort = sort;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
