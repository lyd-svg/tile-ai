package com.tileai.plan.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.tileai.layout.model.LayoutModel;
import com.tileai.quote.dto.QuoteResultView;

/**
 * 方案详情(契约 {@code openapi-common.yaml#/components/schemas/PlanDetail})。
 *
 * <p>契约是 {@code allOf: [PlanSummary, {...4 个字段}]} —— 共 14 个属性。
 *
 * <h2>为什么"摊平写"而不是内嵌一个 {@code summary}</h2>
 *
 * <p>Java 的 record <b>不能继承</b>,所以合并在类型层面做不到。剩下的两条路是
 * "内嵌 {@code PlanSummary} 字段"或"把 10 个字段抄一遍"。前者看着更 DRY,
 * 但它会<b>直接改变响应形状</b>:{@code allOf} 的语义是"把这些属性摊在同一层",
 * 而不是"多一个嵌套对象"。前端按契约写的 {@code data.name} 会变成
 * {@code data.summary.name} —— 而且这种错不会在编译期或契约比对里露出来
 * (比对的判据里没有响应体嵌套形状这一维)。
 *
 * <p>抄一遍字段是有代价的:两处要保持一致。这一点由测试兜住 ——
 * {@code PlanContractTest} 会把本类的字段集与 {@code PlanSummaryView} 的
 * <b>逐一对齐</b>,少抄一个字段就红。
 *
 * <h2>户型的密文不出现在这里</h2>
 *
 * <p>{@code layout} 是<b>解密后的明文</b>{@link LayoutModel}。库里存的是 AES-256-GCM
 * 密文({@code t_plan.layout_enc}),但那是<b>静态存储</b>的事 —— 对外 schema 里
 * 不得出现 {@code layout_enc}(契约校验表 T-8、CLAUDE.md 约定)。密文只在
 * Spring 的 service 边界内存在,出了这一层就只剩明文。
 *
 * @param id           方案 id
 * @param name         方案名
 * @param space        生成空间
 * @param style        风格
 * @param coverImage   封面效果图 URL
 * @param totalAmount  生成时的报价总额;null = 这一次没算出价
 * @param quoteExpired 是否过期(T-1.15)
 * @param parts        部位—瓷砖 + 报价分项
 * @param createdAt    创建时间
 * @param updatedAt    最后修改时间
 * @param layout       户型快照,<b>明文</b>
 * @param quote        报价汇总;与 {@code parts} 里的分项同源。没算出价时为 null,整个省略
 * @param images       效果图
 * @param shareToken   分享令牌;未生成分享时为 null,整个省略(T-1.13)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PlanDetailView(
        Long id,
        String name,
        String space,
        String style,
        String coverImage,
        BigDecimal totalAmount,
        boolean quoteExpired,
        List<PlanPartView> parts,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LayoutModel layout,
        QuoteResultView quote,
        List<PlanImageView> images,
        String shareToken) {
}
