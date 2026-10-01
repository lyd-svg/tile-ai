package com.tileai.plan.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 「我的方案」卡片(契约 {@code openapi-common.yaml#/components/schemas/PlanSummary};
 * PRD §5.6)。
 *
 * <p>契约 {@code required: [id, name, createdAt]} —— 这三个一定在。其余可省略。
 *
 * <p><b>{@code quoteExpired} 用基本类型 {@code boolean},不是 {@code Boolean}。</b>
 * 契约声明的是 {@code type: boolean}(非 nullable),而库列 {@code TINYINT(1)}
 * 在老数据上可能是 NULL。用包装类型会随 {@code NON_NULL} 被整条省略;
 * 用基本类型则必然输出 true/false,null 由服务层折算成 false —— 语义也正好对:
 * "没标记过过期"就是"没过期"。
 *
 * <p><b>{@code coverImage} 可能没有值。</b>本期封面图不做对象存储,取的是方案里
 * {@code is_cover = 1} 那张图的 url(决策①,2026-10-01);万一一条都没有(存量数据),
 * 就整个省略 —— <b>不伪造、也不留空串</b>。前端对缺封面的卡片画自己的占位。
 *
 * <p><b>{@code parts} 是可省略的。</b>契约把它描述成"所选瓷砖摘要",但方案部位的
 * 权威列表在详情接口;列表接口带上它是为了让卡片不必再发一轮请求。
 *
 * @param id           方案 id
 * @param name         方案名(用户可改;自动生成时形如"卫生间 · 现代")
 * @param space        生成空间,空间字典 key
 * @param style        风格字典 key
 * @param coverImage   封面效果图 URL
 * @param totalAmount  生成时的报价总额。<b>null 表示"这一次没算出价"</b>,不是 0
 * @param quoteExpired 是否因产品调价而过期(归 T-1.15,当前恒为 false)
 * @param parts        部位—瓷砖摘要
 * @param createdAt    创建时间
 * @param updatedAt    最后修改时间(列表按它倒序)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PlanSummaryView(
        Long id,
        String name,
        String space,
        String style,
        String coverImage,
        BigDecimal totalAmount,
        boolean quoteExpired,
        List<PlanPartView> parts,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
