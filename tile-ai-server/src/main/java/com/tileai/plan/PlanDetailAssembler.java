package com.tileai.plan;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tileai.common.crypto.AesGcmCipher;
import com.tileai.common.error.BusinessException;
import com.tileai.common.error.ErrorCode;
import com.tileai.layout.model.LayoutModel;
import com.tileai.plan.dto.PlanDetailView;
import com.tileai.plan.dto.PlanImageView;
import com.tileai.plan.dto.PlanPartView;
import com.tileai.plan.entity.Plan;
import com.tileai.plan.entity.PlanImage;
import com.tileai.plan.entity.PlanPart;
import com.tileai.plan.entity.PlanQuote;
import com.tileai.plan.mapper.PlanImageMapper;
import com.tileai.plan.mapper.PlanPartMapper;
import com.tileai.plan.mapper.PlanQuoteMapper;
import com.tileai.quote.dto.QuoteResultView;

/**
 * 「一行 {@code t_plan} → 一份对外可发的方案详情」—— **唯一的组装点**(T-1.13)。
 *
 * <h2>为什么单独成类</h2>
 *
 * <p>要做同一件事的有<b>两个</b>入口:
 * <ol>
 *   <li>本人看自己的方案 —— {@code GET /api/v1/plans/{id}}
 *       ({@link PlanService#detail});</li>
 *   <li>拿到分享链接的任何人看只读快照 —— {@code GET /api/v1/share/{token}}
 *       ({@link PlanShareService#readShared})。</li>
 * </ol>
 *
 * <p>两条路的响应体在契约里<b>是同一个 schema</b>({@code PlanDetail})。要是各抄一份
 * 组装逻辑,迟早会在某个字段上分叉 —— 而<b>两边各自的测试都可能全绿</b>,
 * 因为各自都在验"我这条路的字段对不对",没人验"两条路是否一致"。
 * T-1.15 已经在"生成与重算的口径"上付过一次学费(见 {@code openapi-verification.md} §42.6),
 * 这里用同样的解法:<b>抽出来,让两条路静态调用同一个方法</b>。
 *
 * <p>顺带解决一个隐性约束:如果把 {@code assemble} 做成 {@link PlanService} 的
 * <b>包级方法</b>再被 {@link PlanShareService} 跨 bean 调用,它能工作只是因为两者
 * 恰好同包(Spring 的 CGLIB 子类能覆写包级方法)。一个独立组件没有这层约束,
 * 也能脱离 Spring 直接单测。
 *
 * <h2>组装出来的是什么</h2>
 *
 * <p>与 {@code openapi-common.yaml#/components/schemas/PlanDetail} 逐字段对齐 ——
 * 字段集由 {@code PlanContractTest} 绑死。两条容易踩的口径:
 * <ul>
 *   <li><b>户型是明文。</b>库里存的是 AES-256-GCM 密文({@code t_plan.layout_enc}),
 *       但那是<b>静态存储</b>的事;对外 schema 里不得出现 {@code layout_enc}
 *       (契约校验表 T-8、CLAUDE.md 约定)。</li>
 *   <li><b>没有的东西就省略,不编。</b>没算出价 → 没有 {@code quote};没分享 → 没有
 *       {@code shareToken};没封面 → 没有 {@code coverImage}(靠 VO 上的
 *       {@code @JsonInclude(NON_NULL)}),而不是输出空串或 0 元。</li>
 * </ul>
 */
@Component
public class PlanDetailAssembler {

    private static final Logger log = LoggerFactory.getLogger(PlanDetailAssembler.class);

    private final PlanPartMapper planPartMapper;
    private final PlanImageMapper planImageMapper;
    private final PlanQuoteMapper planQuoteMapper;
    private final AesGcmCipher cipher;
    private final ObjectMapper objectMapper;

    public PlanDetailAssembler(PlanPartMapper planPartMapper, PlanImageMapper planImageMapper,
                               PlanQuoteMapper planQuoteMapper, AesGcmCipher cipher,
                               ObjectMapper objectMapper) {
        this.planPartMapper = planPartMapper;
        this.planImageMapper = planImageMapper;
        this.planQuoteMapper = planQuoteMapper;
        this.cipher = cipher;
        this.objectMapper = objectMapper;
    }

    // ------------------------------------------------------------------
    // 组装
    // ------------------------------------------------------------------

    /**
     * 一行已经<b>确认可读</b>的方案 → 详情响应体。
     *
     * <p>本方法<b>不做任何归属判定</b>:调用方负责决定"这一行为什么可以被读"。
     * 反过来,归属判定也<b>不该</b>在这里再抄一遍 —— 它只有一个出口
     * ({@link PlanOwnership}),而分享页那条路的判据压根不是归属(是令牌),
     * 在这里加一层"必须是本人的"会直接让分享页失效。
     *
     * @throws BusinessException 90001 —— 没有户型快照或密文解不开(见 {@link #readLayout})
     */
    public PlanDetailView assemble(Plan plan) {
        long planId = plan.getId();
        List<PlanPartView> parts = partsOf(List.of(planId)).getOrDefault(planId, List.of());
        List<PlanImageView> images = imagesOf(List.of(planId)).getOrDefault(planId, List.of());

        return new PlanDetailView(
                plan.getId(),
                plan.getName(),
                plan.getSpace(),
                plan.getStyle(),
                coverOf(images),
                plan.getTotalAmount(),
                Boolean.TRUE.equals(plan.getQuoteExpired()),
                parts,
                plan.getCreatedAt(),
                plan.getUpdatedAt(),
                readLayout(plan),
                quoteOf(planId, parts),
                images,
                plan.getShareToken());
    }

    /**
     * 密文 → 明文户型。
     *
     * <p>对外 API 只传明文:密文是**静态存储**的形态,不越过 service 边界往外渗
     * (契约校验表 T-8、CLAUDE.md 约定)。
     *
     * <p><b>解密逻辑只留这一份</b>:详情接口、分享页、重算
     * ({@link PlanRecalcService}) 都要读同一份户型快照。复制一份出去,三条路迟早会在
     * "密文损坏该怎么办"这种边界上分叉。三边给的错误码是同一个 90001
     * (见 {@link ErrorCode#INTERNAL_ERROR}),因为"方案在,坏的是系统"。
     *
     * <p>这也是唯一一处 90001 会从<b>公开</b>的分享页漏出去的地方:理论上成了一个很弱的
     * 探针(有效令牌 + 数据恰好损坏 → 90001,而无效令牌 → 30001)。刻意接受,理由见
     * {@code openapi-verification.md} §43:要利用它得先猜中一个 256 bit 的令牌,
     * 而把 90001 折成 30001 会让接收方以为"链接被废除了",去找分享者重发 —— 而重发治不好
     * 损坏的密文。
     *
     * @throws BusinessException 90001 —— 没有户型快照、密文损坏、或密钥不匹配
     */
    LayoutModel readLayout(Plan plan) {
        byte[] layoutEnc = plan.getLayoutEnc();
        if (layoutEnc == null || layoutEnc.length == 0) {
            log.error("方案 {} 没有户型快照(layout_enc 为空),读不出布局", plan.getId());
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
        try {
            return objectMapper.readValue(cipher.decryptToString(layoutEnc), LayoutModel.class);
        } catch (JsonProcessingException | IllegalStateException e) {
            log.error("方案 {} 的户型快照解不开(密钥不匹配、密文损坏或快照不是合法 JSON)",
                    plan.getId(), e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }

    /**
     * 汇总 + 分项 → 详情里的 {@code quote}。
     *
     * <p><b>没算过价就返回 null</b>(整个字段省略),而不是造一个五项全 0 的报价单 ——
     * 那会让用户看到一张"¥0"的单子,并以为这就是最终价格。
     *
     * <p>{@code rulesSnapshot} 反序列化自库里的 JSON 原文,不重算:它记的是
     * "当时按什么参数算的",重查参数表只会得到"现在是什么参数",那就不是快照了。
     */
    private QuoteResultView quoteOf(long planId, List<PlanPartView> parts) {
        List<PlanQuote> rows = planQuoteMapper.selectList(Wrappers.<PlanQuote>lambdaQuery()
                .eq(PlanQuote::getPlanId, planId));
        if (rows.isEmpty()) {
            return null;
        }
        if (rows.size() > 1) {
            // 写入方是先删后插(V4 没有 plan_id 唯一键),正常不可能超过一行
            log.warn("方案 {} 有多于一行 t_plan_quote({} 行),这里只取第一行", planId, rows.size());
        }
        PlanQuote row = rows.get(0);

        List<QuoteResultView.Item> items = new ArrayList<>(parts.size());
        for (PlanPartView part : parts) {
            if (part.materialFee() == null) {
                continue;
            }
            items.add(new QuoteResultView.Item(part.part(), part.sku(), part.grossArea(),
                    part.area(), part.lossRate(), part.pricePerSqm(), part.materialFee()));
        }
        if (items.size() != parts.size()) {
            log.warn("方案 {} 有汇总行但 {} 个部位缺费用列,明细会少几行(总价仍按库里存的返回)",
                    planId, parts.size() - items.size());
        }

        return new QuoteResultView(row.getMaterialTotal(), row.getAuxTotal(),
                row.getTotalAmount(), items, rulesSnapshotOf(planId, row.getRulesSnapshot()));
    }

    private QuoteResultView.RulesSnapshot rulesSnapshotOf(long planId, String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, QuoteResultView.RulesSnapshot.class);
        } catch (JsonProcessingException e) {
            log.warn("方案 {} 的 rules_snapshot 解析失败,详情里省略 rulesSnapshot: {}",
                    planId, e.getOriginalMessage());
            return null;
        }
    }

    // ------------------------------------------------------------------
    // 子表批量读取(列表与详情共用)
    // ------------------------------------------------------------------

    /** 一页方案的部位行,按 planId 分组;**顺序即插入序**(部位展示顺序)。 */
    Map<Long, List<PlanPartView>> partsOf(List<Long> planIds) {
        if (planIds.isEmpty()) {
            return Map.of();
        }
        return planPartMapper.selectList(Wrappers.<PlanPart>lambdaQuery()
                        .in(PlanPart::getPlanId, planIds)
                        .orderByAsc(PlanPart::getId))
                .stream()
                .collect(Collectors.groupingBy(PlanPart::getPlanId,
                        LinkedHashMap::new,
                        Collectors.mapping(PlanDetailAssembler::toPartView, Collectors.toList())));
    }

    /**
     * 一页方案各自的封面 url(只查 {@code is_cover = 1} 那几行,不为封面拉回整张表)。
     *
     * <p>同一方案出现多行封面时取 id 最小的第一个 —— 写入方
     * ({@code GenerateCallbackService.toPlanImages})已经保证只有一个,
     * 这里的取舍只是不让数据库返回顺序决定卡片封面。
     */
    Map<Long, String> coversOf(List<Long> planIds) {
        if (planIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> covers = new HashMap<>();
        for (PlanImage image : planImageMapper.selectList(Wrappers.<PlanImage>lambdaQuery()
                .in(PlanImage::getPlanId, planIds)
                .eq(PlanImage::getIsCover, true)
                .orderByAsc(PlanImage::getId))) {
            covers.putIfAbsent(image.getPlanId(), image.getUrl());
        }
        return covers;
    }

    /** 一页方案的效果图,按 planId 分组。顺序即插入序,与出图时的视角顺序一致。 */
    private Map<Long, List<PlanImageView>> imagesOf(List<Long> planIds) {
        if (planIds.isEmpty()) {
            return Map.of();
        }
        return planImageMapper.selectList(Wrappers.<PlanImage>lambdaQuery()
                        .in(PlanImage::getPlanId, planIds)
                        .orderByAsc(PlanImage::getId))
                .stream()
                .collect(Collectors.groupingBy(PlanImage::getPlanId,
                        LinkedHashMap::new,
                        Collectors.mapping(
                                row -> new PlanImageView(row.getUrl(), row.getView(),
                                        row.getIsCover()),
                                Collectors.toList())));
    }

    private static PlanPartView toPartView(PlanPart row) {
        return new PlanPartView(row.getPart(), row.getSkuCode(), row.getProductName(),
                row.getPricePerSqm(), row.getGrossArea(), row.getArea(), row.getLossRate(),
                row.getMaterialFee(), row.getSource());
    }

    /** 一组图里的封面 url;一个都没有时返回 null(调用方据此省略该字段)。 */
    private static String coverOf(List<PlanImageView> images) {
        return images.stream()
                .filter(image -> Boolean.TRUE.equals(image.isCover()))
                .map(PlanImageView::url)
                .findFirst()
                .orElse(null);
    }
}
