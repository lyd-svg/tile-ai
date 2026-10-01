package com.tileai.plan;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tileai.common.api.PageData;
import com.tileai.common.crypto.AesGcmCipher;
import com.tileai.common.error.BusinessException;
import com.tileai.common.error.ErrorCode;
import com.tileai.layout.model.LayoutModel;
import com.tileai.plan.dto.PlanDetailView;
import com.tileai.plan.dto.PlanImageView;
import com.tileai.plan.dto.PlanPartView;
import com.tileai.plan.dto.PlanSummaryView;
import com.tileai.plan.entity.Plan;
import com.tileai.plan.entity.PlanImage;
import com.tileai.plan.entity.PlanPart;
import com.tileai.plan.entity.PlanQuote;
import com.tileai.plan.mapper.PlanImageMapper;
import com.tileai.plan.mapper.PlanMapper;
import com.tileai.plan.mapper.PlanPartMapper;
import com.tileai.plan.mapper.PlanQuoteMapper;
import com.tileai.quote.dto.QuoteResultView;

/**
 * 「我的方案」的读与管(契约 {@code openapi-c.yaml} 的 plan 分组;PRD §5.6、§11.4)。
 *
 * <pre>
 *   GET    /api/v1/plans           列表
 *   GET    /api/v1/plans/{id}      详情
 *   PATCH  /api/v1/plans/{id}      重命名
 *   DELETE /api/v1/plans/{id}      删除(逻辑删除)
 *   POST   /api/v1/plans/{id}/copy 另存为
 * </pre>
 *
 * <p>方案的<b>写</b>不在这里 —— 它是出图成功那一刻由 {@code PlanAutoSaveService}
 * 自动落下的。这里只有"用户主动对自己方案做的事"。
 *
 * <h2>四条贯穿全类的口径</h2>
 *
 * <ol>
 *   <li><b>归属判定只有一个出口</b>({@link PlanOwnership})。不存在、已删除、不属于本人
 *       三者一律 30001 且措辞相同 —— 区分开来就等于给了一个可以枚举别人方案 id 的探针。</li>
 *   <li><b>列表查询三条件缺一不可</b>:{@code user_id = 我} + {@code is_deleted = false}
 *       + {@code order by updated_at desc, id desc}。全项目没配 {@code @TableLogic}
 *       (逻辑删除得手写),而 {@code id desc} 是同一秒内多条的稳定兜底 ——
 *       少了它,{@code updated_at} 相同的两条在翻页时会重复出现或整条漏掉。
 *       这个排序正好命中 V4 建的 {@code idx_plan_user(user_id, is_deleted, updated_at)}。</li>
 *   <li><b>子表一律整页批量取,不逐行查</b>({@code in (planIds)} 一次拉完)。
 *       一页 20 个方案逐个查部位和图片就是 41 条 SQL。</li>
 *   <li><b>没有的东西就省略,不编。</b>没封面、没算出价、没分享令牌 —— 相应字段整个不出现在
 *       JSON 里(靠 VO 上的 {@code @JsonInclude(NON_NULL)}),而不是输出空串或 0。</li>
 * </ol>
 */
@Service
public class PlanService {

    private static final Logger log = LoggerFactory.getLogger(PlanService.class);

    private final PlanMapper planMapper;
    private final PlanPartMapper planPartMapper;
    private final PlanImageMapper planImageMapper;
    private final PlanQuoteMapper planQuoteMapper;
    private final PlanOwnership ownership;
    private final AesGcmCipher cipher;
    private final ObjectMapper objectMapper;

    public PlanService(PlanMapper planMapper, PlanPartMapper planPartMapper,
                       PlanImageMapper planImageMapper, PlanQuoteMapper planQuoteMapper,
                       PlanOwnership ownership, AesGcmCipher cipher, ObjectMapper objectMapper) {
        this.planMapper = planMapper;
        this.planPartMapper = planPartMapper;
        this.planImageMapper = planImageMapper;
        this.planQuoteMapper = planQuoteMapper;
        this.ownership = ownership;
        this.cipher = cipher;
        this.objectMapper = objectMapper;
    }

    // ------------------------------------------------------------------
    // 列表
    // ------------------------------------------------------------------

    /**
     * 我的方案列表,按最后修改时间倒序。
     *
     * <p><b>越界的页码不是错误。</b>第 5 页没有数据就返回空列表 + 正确的 total
     * (契约 {@code PageData} 的 list 本就可以是空数组)。把"翻过头了"报成 10001
     * 会让前端的"下一页"按钮在最后一页之后变成一个报错弹窗。
     * 真正的参数错误只有 {@code page < 1} 和 {@code size > 100},由控制器上的
     * {@code @Min}/{@code @Max} 在进方法之前拦掉。
     */
    @Transactional(readOnly = true)
    public PageData<PlanSummaryView> page(long userId, int page, int size) {
        Page<Plan> result = planMapper.selectPage(new Page<>(page, size),
                Wrappers.<Plan>lambdaQuery()
                        .eq(Plan::getUserId, userId)
                        .eq(Plan::getIsDeleted, false)
                        .orderByDesc(Plan::getUpdatedAt)
                        .orderByDesc(Plan::getId));

        List<Long> ids = result.getRecords().stream().map(Plan::getId).toList();
        Map<Long, List<PlanPartView>> parts = partsOf(ids);
        Map<Long, String> covers = coversOf(ids);

        List<PlanSummaryView> list = result.getRecords().stream()
                .map(plan -> new PlanSummaryView(
                        plan.getId(),
                        plan.getName(),
                        plan.getSpace(),
                        plan.getStyle(),
                        covers.get(plan.getId()),
                        plan.getTotalAmount(),
                        Boolean.TRUE.equals(plan.getQuoteExpired()),
                        parts.get(plan.getId()),
                        plan.getCreatedAt(),
                        plan.getUpdatedAt()))
                .toList();

        return PageData.of(list, result.getTotal(), page, size);
    }

    // ------------------------------------------------------------------
    // 详情
    // ------------------------------------------------------------------

    /**
     * 方案详情。
     *
     * <p><b>户型解密失败走 90001,不走 30001。</b>密文坏了有两种可能 —— 密钥换了、
     * 或者这行被改过 —— 但无论哪种,<b>方案是存在的</b>。回 30001 会让用户以为
     * 方案被删了,然后去重建一份;而真正该被叫醒的是运维。真实原因只进日志,
     * 不进响应体(和 {@code AesGcmCipher} 的既有口径一致)。
     *
     * @throws BusinessException 30001 —— 不存在、已删除或不属于本人;90001 —— 密文解不开
     */
    @Transactional(readOnly = true)
    public PlanDetailView detail(long userId, long planId) {
        Plan plan = ownership.requireOwned(userId, planId);
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
     * <p><b>包级可见</b>(T-1.15):重算({@link PlanRecalcService})也要读同一份户型快照。
     * 解密逻辑**只留这一份** —— 复制一份到重算里,两条路迟早会在"密文损坏该怎么办"
     * 这种边界上分叉。两边给的错误码是同一个 90001(见 {@link ErrorCode#INTERNAL_ERROR}),
     * 因为"方案在,坏的是系统"。
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
    // 重命名 / 删除
    // ------------------------------------------------------------------

    /**
     * 重命名。
     *
     * <p><b>只改 {@code name} 一列</b>,不是整行 {@code updateById} —— 后者会把实体里
     * 所有非空字段一起写回去,把并发写入的其它列(比如用户同时触发的"重新生成")覆盖掉。
     *
     * <p><b>超长直接 10001,不截断。</b>把用户的输入悄悄截短,比报错更糟:他以为改成功了,
     * 而库里是另一个名字。长度按<b>码点</b>判(见 {@link PlanNames})—— 和
     * {@code VARCHAR(200)} 和"另存为"的截断用的是同一把尺子。
     *
     * <p>{@code updated_at} 显式写 {@code NOW()}:改名成同一个名字时没有列的值发生变化,
     * V4 的 {@code ON UPDATE CURRENT_TIMESTAMP} 就不会触发,而列表按 {@code updated_at}
     * 倒序 —— 时间戳不前进,用户刚改过名的方案会沉在列表底部。
     *
     * @throws BusinessException 10001 名字为空或超长;30001 方案不可用
     */
    @Transactional
    public void rename(long userId, long planId, String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "方案名不能为空");
        }
        if (PlanNames.isTooLong(trimmed)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID,
                    "方案名最长 " + PlanNames.NAME_MAX + " 字");
        }

        int affected = planMapper.update(null, ownership.ownedUpdate(userId, planId)
                .set(Plan::getName, trimmed)
                .setSql("updated_at = NOW()"));
        ownership.requireAffected(affected);
    }

    /**
     * 删除方案 —— <b>逻辑删除</b>,只把 {@code is_deleted} 置 1。
     *
     * <p><b>不删子表,也不碰 {@code share_token}。</b>分享出去的链接(T-1.13)要能
     * 解释成"这个方案已被删除",而不是变成一个说不清是什么的 404;误删也还有
     * 从库里恢复的余地。真删行还会把上面两条一起毁掉。
     *
     * <p><b>二次删除同样报 30001</b>(刻意):{@code is_deleted} 已经在 WHERE 里,
     * 第二次 UPDATE 命中 0 行 —— 对用户来说"这个方案已经不在我的列表里了",
     * 再删一次不是成功也不是异常,报 30001 最准确地描述了他看到的事实。
     *
     * <p>这里不用显式抬 {@code updated_at}:{@code is_deleted} 的值确实变了,
     * V4 的 {@code ON UPDATE} 会自己触发。
     *
     * @throws BusinessException 30001 —— 不存在、已删除或不属于本人
     */
    @Transactional
    public void delete(long userId, long planId) {
        int affected = planMapper.update(null, ownership.ownedUpdate(userId, planId)
                .set(Plan::getIsDeleted, true));
        ownership.requireAffected(affected);
    }

    // ------------------------------------------------------------------
    // 另存为
    // ------------------------------------------------------------------

    /**
     * 另存为:整份方案(方案 + 部位 + 图 + 报价)复制成一份新方案。
     *
     * <p><b>{@code t_generation_task} / {@code t_outbox} 不复制。</b>任务与出图是原方案的
     * 历史,副本还没有生成过任何东西 —— 拷过来的话,副本会带着一条"已经成功"的任务,
     * 前端一看就以为图也拷好了。副本要走自己的那一次生成。
     *
     * <p><b>四张表在同一个事务里</b>:副本不能出现"有方案没明细"或"有明细没总额"。
     *
     * <p><b>户型密文直接拷字节,不解密再加密。</b>两者都正确,但直接拷多两个好处:
     * 少一次解密(源密文坏了的话,连"另存为"都会失败 —— 而另存为本该是用户
     * 抢救一份方案的最后一招),以及不引入"密文变了"这个无意义的差异。
     *
     * <p><b>副本没有 {@code share_token}</b>(置 null):{@code uk_plan_share_token}
     * 是唯一键,拷过来必然撞;而且一个分享链接指向两个方案本身就是错的 ——
     * 分享的是哪一份,得用户在新方案上重新决定。
     *
     * <p><b>不动原方案任何一个字节</b>,包括 {@code updated_at}。用户在列表里
     * 看到的时间、排序位置都不该因为"我另存了一份"而变化。
     *
     * @return 新方案的 id
     * @throws BusinessException 30001 —— 原方案不可用
     */
    @Transactional
    public long copy(long userId, long planId) {
        Plan source = ownership.requireOwned(userId, planId);

        Plan target = new Plan();
        target.setUserId(userId);
        target.setName(PlanNames.copyName(source.getName()));
        target.setSpace(source.getSpace());
        target.setStyle(source.getStyle());
        target.setLayoutEnc(source.getLayoutEnc());
        target.setTotalAmount(source.getTotalAmount());
        // 报价快照原样带过来,所以"过不过期"的判断也一样 —— 过期标记不是时间函数,
        // 而是"报价所依据的价格还是不是当前价",两份方案用的是同一份报价。
        target.setQuoteExpired(source.getQuoteExpired());
        // 刻意置 null,理由见方法说明
        target.setShareToken(null);
        target.setIsDeleted(false);
        // created_at / updated_at 留给 DB 默认值:副本是一个新方案,有自己的创建时间,
        // 而不是"原方案那一刻被创建的"。
        planMapper.insert(target);

        long newId = target.getId();
        copyParts(planId, newId);
        copyImages(planId, newId);
        copyQuote(planId, newId);
        log.info("方案 {} 另存为新方案 {} (用户 {})", planId, newId, userId);
        return newId;
    }

    private void copyParts(long sourceId, long targetId) {
        for (PlanPart source : planPartMapper.selectList(Wrappers.<PlanPart>lambdaQuery()
                .eq(PlanPart::getPlanId, sourceId)
                .orderByAsc(PlanPart::getId))) {
            PlanPart copy = new PlanPart();
            copy.setPlanId(targetId);
            copy.setPart(source.getPart());
            copy.setProductId(source.getProductId());
            copy.setSkuCode(source.getSkuCode());
            copy.setProductName(source.getProductName());
            copy.setPricePerSqm(source.getPricePerSqm());
            copy.setGrossArea(source.getGrossArea());
            copy.setArea(source.getArea());
            copy.setLossRate(source.getLossRate());
            copy.setMaterialFee(source.getMaterialFee());
            copy.setSource(source.getSource());
            planPartMapper.insert(copy);
        }
    }

    private void copyImages(long sourceId, long targetId) {
        for (PlanImage source : planImageMapper.selectList(Wrappers.<PlanImage>lambdaQuery()
                .eq(PlanImage::getPlanId, sourceId)
                .orderByAsc(PlanImage::getId))) {
            PlanImage copy = new PlanImage();
            copy.setPlanId(targetId);
            copy.setUrl(source.getUrl());
            copy.setView(source.getView());
            copy.setIsCover(source.getIsCover());
            // created_at 留给 DB 默认值 —— 副本的图是刚拷出来的
            planImageMapper.insert(copy);
        }
    }

    private void copyQuote(long sourceId, long targetId) {
        List<PlanQuote> rows = planQuoteMapper.selectList(Wrappers.<PlanQuote>lambdaQuery()
                .eq(PlanQuote::getPlanId, sourceId));
        if (rows.isEmpty()) {
            // 源方案本来就没算出价 —— 副本同样没有,这不算失败
            return;
        }
        if (rows.size() > 1) {
            log.warn("源方案 {} 有多于一行 t_plan_quote({} 行),副本只带第一行", sourceId, rows.size());
        }
        PlanQuote source = rows.get(0);

        PlanQuote copy = new PlanQuote();
        copy.setPlanId(targetId);
        copy.setMaterialTotal(source.getMaterialTotal());
        copy.setAuxTotal(source.getAuxTotal());
        copy.setAuxUnitPrice(source.getAuxUnitPrice());
        copy.setAuxArea(source.getAuxArea());
        copy.setLossRate(source.getLossRate());
        copy.setTotalAmount(source.getTotalAmount());
        copy.setRulesSnapshot(source.getRulesSnapshot());
        planQuoteMapper.insert(copy);
    }

    // ------------------------------------------------------------------
    // 子表批量读取
    // ------------------------------------------------------------------

    /** 一页方案的部位行,按 planId 分组;**顺序即插入序**(部位展示顺序)。 */
    private Map<Long, List<PlanPartView>> partsOf(List<Long> planIds) {
        if (planIds.isEmpty()) {
            return Map.of();
        }
        return planPartMapper.selectList(Wrappers.<PlanPart>lambdaQuery()
                        .in(PlanPart::getPlanId, planIds)
                        .orderByAsc(PlanPart::getId))
                .stream()
                .collect(Collectors.groupingBy(PlanPart::getPlanId,
                        LinkedHashMap::new,
                        Collectors.mapping(PlanService::toPartView, Collectors.toList())));
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

    /**
     * 一页方案各自的封面 url(只查 {@code is_cover = 1} 那几行,不为封面拉回整张表)。
     *
     * <p>同一方案出现多行封面时取 id 最小的第一个 —— 写入方
     * ({@code GenerateCallbackService.toPlanImages})已经保证只有一个,
     * 这里的取舍只是不让数据库返回顺序决定卡片封面。
     */
    private Map<Long, String> coversOf(List<Long> planIds) {
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
