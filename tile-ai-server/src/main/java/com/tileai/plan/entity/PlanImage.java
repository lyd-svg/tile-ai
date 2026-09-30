package com.tileai.plan.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 方案效果图(架构 §8.4 {@code t_plan_image})。
 *
 * <p><b>两个坑,都不会报错:</b>
 *
 * <ol>
 *   <li>{@code view} 是 <b>MySQL 保留字</b>,SQL 里必须带反引号。
 *       MyBatis-Plus 不会自动加 —— 所以这里显式写 {@code @TableField("`view`")}。
 *       少了它,生成的 SQL 会语法错(这个还算好,至少会报)。</li>
 *   <li>本表<b>只有 {@code created_at},没有 {@code updated_at}</b>(§8.4 照办)。
 *       所以"重新生成覆盖原方案"时不能指望靠时间戳判断新旧,也不能 UPDATE 后就完事
 *       —— 覆盖的策略是<b>先按 plan_id 删干净再插</b>,见 {@code PlanAutoSaveService}。</li>
 * </ol>
 */
@TableName("t_plan_image")
public class PlanImage {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long planId;

    private String url;

    /** 视图名:{@code default} / {@code angle1}…(D-5;保留字,需反引号)。 */
    @TableField("`view`")
    private String view;

    /** 仅 {@code default} 那张为 true(封面)。 */
    @TableField("is_cover")
    private Boolean isCover;

    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getPlanId() {
        return planId;
    }

    public void setPlanId(Long planId) {
        this.planId = planId;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getView() {
        return view;
    }

    public void setView(String view) {
        this.view = view;
    }

    public Boolean getIsCover() {
        return isCover;
    }

    public void setIsCover(Boolean isCover) {
        this.isCover = isCover;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
