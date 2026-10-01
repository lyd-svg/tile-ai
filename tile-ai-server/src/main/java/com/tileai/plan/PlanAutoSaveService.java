package com.tileai.plan;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tileai.admin.DictService;
import com.tileai.common.crypto.AesGcmCipher;
import com.tileai.layout.model.LayoutAreas;
import com.tileai.layout.model.LayoutModel;
import com.tileai.layout.model.LayoutSpace;
import com.tileai.plan.PlanQuoteSnapshotService.MergedPart;
import com.tileai.plan.PlanQuoteSnapshotService.PartFee;
import com.tileai.plan.PlanQuoteSnapshotService.PartSku;
import com.tileai.plan.PlanQuoteSnapshotService.Snapshot;
import com.tileai.plan.dto.PlanImageRef;
import com.tileai.plan.dto.PlanPartRef;
import com.tileai.plan.entity.Plan;
import com.tileai.plan.entity.PlanImage;
import com.tileai.plan.entity.PlanPart;
import com.tileai.plan.mapper.PlanImageMapper;
import com.tileai.plan.mapper.PlanMapper;
import com.tileai.plan.mapper.PlanPartMapper;

/**
 * 出图完成后的<b>方案自动保存</b>(契约 {@code openapi-c.yaml} 的
 * {@code GenerateRequest.planId} 说明;架构 §11.2、§11.4)。
 *
 * <p>用户点"生成"时并没有先建方案 —— 方案是出图成功那一刻自动落下的:
 * 不带 {@code planId} 就是<b>新建</b>,带了就是<b>覆盖原方案</b>。
 * 这样"没出成图却多出一个空方案"不会发生。
 *
 * <h2>三个不出声的坑</h2>
 *
 * <ol>
 *   <li><b>{@code t_plan_image.view} 是 MySQL 保留字</b> —— 见
 *       {@link PlanImage} 的 {@code @TableField("`view`")}。</li>
 *   <li><b>{@code t_plan_image} 没有 {@code updated_at}</b>,所以覆盖只能
 *       <b>先删后插</b>。不这么做的话,这次出 2 张、上次 4 张,就会剩两张上一次的
 *       图留在方案里 —— 用户点开看到两个版本的图混在一起。</li>
 *   <li><b>户型是加密存储的</b>({@code layout_enc})。写进去的是
 *       {@link AesGcmCipher} 的密文,且是<b>补过面积</b>的那一份 —— 面积派生全项目
 *       只在服务端做一次({@code LayoutAreaCalculator}),存快照时把它固化下来,
 *       T-1.15 报价时就不必再推一遍、也不会推出不一样的结果。</li>
 * </ol>
 *
 * <h2>报价快照(T-1.12 起)</h2>
 *
 * <p>本服务现在还会落一份<b>生成时</b>的报价:总价进 {@code t_plan.total_amount},
 * 明细进 {@code t_plan_quote},分项金额回填 {@code t_plan_part} 的五个费用列。
 * 口径见 {@link PlanQuoteSnapshotService} —— <b>算不出就整单不落</b>,绝不把
 * "推不出的面积"当 0 算出个偏低价。"按当前价重算"与"报价已过期"标记仍归 T-1.15。
 *
 * <p>整份快照与方案行在<b>同一个事务</b>里落 —— 中途失败一起回滚,不会留下
 * "方案有总额、明细却没写"的半截状态。
 */
@Service
public class PlanAutoSaveService {

    private static final Logger log = LoggerFactory.getLogger(PlanAutoSaveService.class);

    /** 契约 {@code PartSource} 的默认值(请求里可省略)。 */
    private static final String SOURCE_USER = "user";

    private final PlanMapper planMapper;
    private final PlanPartMapper planPartMapper;
    private final PlanImageMapper planImageMapper;
    private final AesGcmCipher cipher;
    private final DictService dictService;
    private final ObjectMapper objectMapper;
    private final PlanOwnership ownership;
    private final PlanQuoteSnapshotService quoteSnapshotService;

    public PlanAutoSaveService(PlanMapper planMapper, PlanPartMapper planPartMapper,
                              PlanImageMapper planImageMapper, AesGcmCipher cipher,
                              DictService dictService, ObjectMapper objectMapper,
                              PlanOwnership ownership,
                              PlanQuoteSnapshotService quoteSnapshotService) {
        this.planMapper = planMapper;
        this.planPartMapper = planPartMapper;
        this.planImageMapper = planImageMapper;
        this.cipher = cipher;
        this.dictService = dictService;
        this.objectMapper = objectMapper;
        this.ownership = ownership;
        this.quoteSnapshotService = quoteSnapshotService;
    }

    /**
     * 新建或覆盖方案。
     *
     * <p><b>必须与回调的状态更新在同一事务里</b>(调用方是
     * {@code GenerateCallbackService.handle},那个方法已是 {@code @Transactional})。
     * 本方法标 {@code @Transactional}(传播默认 {@code REQUIRED})正是为了加入
     * 调用方的事务 —— 方案保存失败时状态更新一起回滚,任务退回可重试,
     * 而不是留下"任务说 success、方案却没存"这种对不上账的状态。
     *
     * <p><b>覆盖目标不在了就退回新建,不抛异常。</b>用户在点生成到出图完成之间
     * 删掉了那个方案,是完全可能的。此时图已经出好、模型调用也计过费了 ——
     * 因为"没地方存"把它丢掉是最坏的选择;而户型、风格、部位都在任务快照里,
     * 新建一个方案什么都不缺。名字会退回默认值(用户原来自起的名字随方案一起
     * 没了),这一点记在 {@code openapi-verification.md} §35。
     *
     * <p><b>为什么是"返回 null 再判",而不是 {@code try/catch} 异常</b>:
     * 本方法标了 {@code @Transactional},从<b>方法内部</b>抛出的异常一旦穿过
     * Spring 的事务代理,整条共享事务就会被标成 rollback-only —— 外层再想
     * 捕获它继续提交,只会拿到 {@code UnexpectedRollbackException}。
     * 让失败以返回值表达,这个问题根本不会出现。报价快照那条路走的也是同一套
     * 思路({@code QuoteService.tryCalculate} 返回 {@code Optional} 而不是抛)。
     *
     * <p>面向用户的 30001 由受理阶段的 {@link #assertOverwritable} 负责:
     * 那时还没花掉模型调用,拒绝得越早越好。
     *
     * @param planId 非空表示覆盖
     * @return 方案 id(新建的是新 id,覆盖的仍是原 id)
     */
    @Transactional
    public long upsert(long userId, Long planId, String space, String style,
                       LayoutModel layout, List<PlanPartRef> parts,
                       List<PlanImageRef> images) {
        byte[] layoutEnc = cipher.encryptString(toJson(layout));

        // 面积快照先定下来:部位合并与报价都要用它。
        LayoutSpace resolved = resolveSpace(layout, space);
        List<MergedPart> merged = mergeParts(resolved, parts);

        // 算不出就整单不落(Optional.empty)。**这一步在任何写库之前** ——
        // 报价永远不该成为"方案存不下来"的原因。
        Optional<Snapshot> snapshot = quoteSnapshotService.build(space, resolved, merged);
        if (snapshot.isEmpty()) {
            log.warn("方案(用户 {}、覆盖 {} )本次不落报价快照,费用列与总价留空",
                    userId, planId == null ? "否" : planId);
        }

        Plan existing = planId == null ? null : ownership.findOwned(userId, planId);

        long targetId;
        if (existing == null) {
            if (planId != null) {
                log.warn("目标方案 {} 在出图期间已不存在或不再属于用户 {},改为新建方案",
                        planId, userId);
            }
            targetId = createPlan(userId, space, style, layoutEnc, snapshot);
        } else {
            targetId = overwritePlan(existing, space, style, layoutEnc, snapshot);
        }

        replaceParts(targetId, merged, snapshot.orElse(null));
        replaceImages(targetId, images);
        snapshot.ifPresent(fresh -> quoteSnapshotService.persist(targetId, fresh));
        return targetId;
    }

    // ------------------------------------------------------------------
    // 方案主体
    // ------------------------------------------------------------------

    private long createPlan(long userId, String space, String style, byte[] layoutEnc,
                            Optional<Snapshot> snapshot) {
        Plan plan = new Plan();
        plan.setUserId(userId);
        plan.setName(defaultName(space, style));
        plan.setSpace(space);
        plan.setStyle(style);
        plan.setLayoutEnc(layoutEnc);
        plan.setTotalAmount(snapshot.map(Snapshot::totalAmount).orElse(null));
        plan.setQuoteExpired(false);
        plan.setIsDeleted(false);
        planMapper.insert(plan);
        return plan.getId();
    }

    /**
     * 覆盖原方案。
     *
     * <p><b>不动 {@code name}、{@code created_at}、{@code share_token}</b> ——
     * 用户自己起的名字不该被重新生成冲回默认值,方案的对外身份(T-1.13 的分享链接)
     * 也不该因为重出一张图就换掉。
     *
     * <p><b>用定向 UPDATE 而不是 {@code updateById}</b>,有两个原因:
     *
     * <ol>
     *   <li>{@code updateById} 会把实体里所有非空字段整行写回,把并发写入的其它列
     *       一起覆盖掉(比如用户刚好在这两分钟里改了名)。</li>
     *   <li>{@code total_amount} 要能<b>被清成 null</b>:上一次算得出价、这一次算不出,
     *       留着旧总额就是在报一个已经不对的数。{@code set(...)} 是无条件写,
     *       正好要这个语义。</li>
     * </ol>
     *
     * <p><b>{@code updated_at} 显式写 {@code NOW()}。</b>不能指望 V4 的
     * {@code ON UPDATE CURRENT_TIMESTAMP}:它只在至少一列的值真的变了时才触发,
     * 而 {@code updateById} 在"值没变"时不会抬时间戳 —— 方案列表按
     * {@code updated_at desc} 排序,时间戳不前进就等于用户刚重出的图排到了列表最下面。
     * 写 {@code NOW()}(数据库时钟)而不是 Java 的 {@code now()},是为了和
     * {@code created_at} 用同一个时钟,不引入两台机器的时区偏差。
     *
     * <p>{@code quote_expired} 一并清 0:总额刚刚按当前价算过,定义上就不可能是过期的。
     */
    private long overwritePlan(Plan existing, String space, String style, byte[] layoutEnc,
                               Optional<Snapshot> snapshot) {
        planMapper.update(null, Wrappers.<Plan>lambdaUpdate()
                .eq(Plan::getId, existing.getId())
                .set(Plan::getSpace, space)
                .set(Plan::getStyle, style)
                .set(Plan::getLayoutEnc, layoutEnc)
                .set(Plan::getTotalAmount, snapshot.map(Snapshot::totalAmount).orElse(null))
                .set(Plan::getQuoteExpired, false)
                .setSql("updated_at = NOW()"));
        return existing.getId();
    }

    /**
     * 受理阶段的前置校验:"这个 planId 待会儿覆盖得动吗"。
     *
     * <p>理由是一次生成要花一次真实模型调用:若 planId 根本不属于本人,等到出图完成、
     * 回调来存方案时才失败,用户等了两分钟拿到一个报错,而模型调用已经烧掉了;
     * 更糟的是任务会卡在 {@code running} 直到补偿扫描重投,再烧一次。
     * 这类"注定失败"的请求应当<b>在受理时就拒掉</b>,而不是让它跑完全程。
     *
     * <p>判定委托给 {@link PlanOwnership} —— 和详情/改名/删除/另存为用的是
     * 同一个出口,30001 的语义只可能有一份。
     *
     * @param planId 为 null 表示新建,无需校验
     * @throws com.tileai.common.error.BusinessException 30001 —— 不存在、已删除或不属于本人
     */
    public void assertOverwritable(long userId, Long planId) {
        if (planId != null) {
            ownership.requireOwned(userId, planId);
        }
    }

    // ------------------------------------------------------------------
    // 部位
    // ------------------------------------------------------------------

    /**
     * 请求里的部位行 → 落库行:**同部位同砖先合并**。
     *
     * <p>V4 的 DDL 注释本来就写着 {@code gross_area} 是"同部位同砖合并后"的值,
     * 而 T-1.11 没有合并。不合并会有两个后果:同一块砖在同一个部位出现两行,
     * 而 {@code Σ material_fee} 与 {@code material_total} 对不上 —— 报价引擎内部
     * 是先合并再算的。
     *
     * <p>合并不改变<b>顺序</b>(LinkedHashMap 保留首次出现的位置),所以"插入序即
     * 期望展示序"这条对效果图成立的规则,对部位行同样成立。
     *
     * <p><b>面积合并的红线:任一行是 null,合并结果就是 null。</b>null 的含义是
     * "这块砖铺多少还没定"({@code grossAreaOf} 对 {@code local} 部位和匹配不到空间
     * 都返回 null),`null + 3㎡` 推不出任何值 —— 把 null 当 0 加出来的是个偏小的数,
     * 然后会被当成真实面积算进报价。宁可整条留在"未定"。
     *
     * <p><b>来源合并取"用户优先"</b>:只要有一行是用户显式选的,这一行就记
     * {@code user}。AI 只在用户没给的地方补齐(PRD §5.2.4),用户给了的地方就该
     * 按用户算 —— 标成 {@code ai} 会让用户在详情页看到"这块是 AI 帮你选的",
     * 而实际上是他自己选的。
     */
    private static List<MergedPart> mergeParts(LayoutSpace resolved, List<PlanPartRef> parts) {
        Map<PartSku, MergedPart> merged = new LinkedHashMap<>();
        for (PlanPartRef part : parts) {
            BigDecimal area = grossAreaOf(resolved, part);
            PartSku key = new PartSku(part.part(), part.sku());
            String source = sourceOf(part.source());

            MergedPart previous = merged.get(key);
            if (previous == null) {
                merged.put(key, new MergedPart(part.part(), part.sku(), area, source));
            } else {
                merged.put(key, new MergedPart(
                        previous.part(),
                        previous.sku(),
                        addNullable(previous.grossArea(), area),
                        SOURCE_USER.equals(previous.source()) || SOURCE_USER.equals(source)
                                ? SOURCE_USER
                                : source));
            }
        }
        return List.copyOf(merged.values());
    }

    /** 两段面积相加;**只要有一段是 null,结果就是 null**(null = 未定,不是 0)。 */
    private static BigDecimal addNullable(BigDecimal left, BigDecimal right) {
        return (left == null || right == null) ? null : left.add(right);
    }

    /**
     * 归一来源。
     *
     * <p>边界已经归一过({@code PartSelection.withDefaultSource()}),这里是
     * "绝不能把 null/空写进库"的兜底:{@code t_plan_part.source} 一旦写空,
     * 详情接口就会返回一个违约的值,而契约里 {@code PartSource} 是必填枚举。
     * 真的走到这里说明调用方漏了归一,所以要出声。
     */
    private static String sourceOf(String source) {
        if (source == null || source.isBlank()) {
            log.warn("部位来源为空,按契约默认值 {} 落库(调用方应已归一)", SOURCE_USER);
            return SOURCE_USER;
        }
        return source;
    }

    /**
     * 按 plan 删干净再插。
     *
     * <p>不做逐条 upsert:部位组合会变(这次铺地面+墙面、下次只铺地面),
     * 增量更新就得处理"多出来的那条删不删",而删干净重插天然没有这个问题。
     * 同一事务内,中途失败一起回滚。
     *
     * <p>费用列从报价快照里按 {@code (部位, SKU)} 取。快照为空时整片留空
     * (不写 0);快照非空时每一行都取得到 —— 报价分项就是按这同一份合并结果算的。
     */
    private void replaceParts(long planId, List<MergedPart> merged, Snapshot snapshot) {
        planPartMapper.delete(Wrappers.<PlanPart>lambdaQuery().eq(PlanPart::getPlanId, planId));

        for (MergedPart part : merged) {
            PartFee fee = snapshot == null ? null : snapshot.fees().get(part.key());

            PlanPart row = new PlanPart();
            row.setPlanId(planId);
            row.setPart(part.part());
            row.setSkuCode(part.sku());
            row.setGrossArea(part.grossArea());
            row.setSource(part.source());
            if (fee != null) {
                row.setProductId(fee.productId());
                row.setProductName(fee.productName());
                row.setPricePerSqm(fee.pricePerSqm());
                row.setArea(fee.area());
                row.setLossRate(fee.lossRate());
                row.setMaterialFee(fee.materialFee());
            }
            planPartMapper.insert(row);
        }
    }

    /**
     * 该部位的毛面积。
     *
     * <p>优先级:请求里显式给了就用它(用户录户型时可能已人工核过),否则用派生值。
     *
     * <p><b>局部部位派生出来是 null 而不是 0</b>:面积公式对它的定义就是
     * "按实际铺贴、无默认值"(见 {@code LayoutAreaCalculator}),那个 0 的含义是
     * "这里推不出值"。存成 0 会被后来人当成"这块砖用了 0 ㎡";null 说的是"没定" ——
     * 报价时遇到 null 会停下来要,遇到 0 就直接乘出 0 元。
     */
    private static BigDecimal grossAreaOf(LayoutSpace space, PlanPartRef part) {
        if (part.area() != null) {
            return part.area();
        }
        if (space == null || space.areas() == null) {
            return null;
        }
        LayoutAreas areas = space.areas();
        return switch (part.part()) {
            case "floor" -> areas.floor();
            case "wall" -> areas.wall();
            default -> null;
        };
    }

    /**
     * 定出"这次生成的是哪个空间"(面积快照取它的)。
     *
     * <p>规则上游没写,由本任务定,记在 {@code openapi-verification.md} §35.8:
     * <ol>
     *   <li>取户型里 {@code type} 与请求 {@code space} 相同的第一个空间;</li>
     *   <li>没有匹配的、且户型只有一个空间时就用它 —— 用户录了个卫生间却把空间
     *       选成"其他",这种不一致不值得让整个方案保存失败;</li>
     *   <li>都没有则取第一个并记 WARN。<b>不报错</b>:面积定不准是报价问题,
     *       而出图已经成功了 —— 不该因为定不出面积就把方案整个丢掉。</li>
     * </ol>
     */
    private static LayoutSpace resolveSpace(LayoutModel layout, String spaceKey) {
        if (layout == null || layout.spaces() == null || layout.spaces().isEmpty()) {
            return null;
        }
        if (layout.spaces().size() == 1) {
            return layout.spaces().get(0);
        }
        for (LayoutSpace space : layout.spaces()) {
            if (space.type() != null && space.type().equals(spaceKey)) {
                return space;
            }
        }
        log.warn("户型含 {} 个空间,其中没有 type={} 的,无法唯一确定生成空间"
                        + "(一期为单空间出图),已取第一个空间用于面积快照",
                layout.spaces().size(), spaceKey);
        return layout.spaces().get(0);
    }

    // ------------------------------------------------------------------
    // 效果图
    // ------------------------------------------------------------------

    private void replaceImages(long planId, List<PlanImageRef> images) {
        planImageMapper.delete(Wrappers.<PlanImage>lambdaQuery().eq(PlanImage::getPlanId, planId));

        for (PlanImageRef image : images) {
            PlanImage row = new PlanImage();
            row.setPlanId(planId);
            row.setUrl(image.url());
            row.setView(image.view());
            row.setIsCover(image.isCover());
            planImageMapper.insert(row);
        }
    }

    // ------------------------------------------------------------------

    /**
     * 默认方案名,形如 <code>卫生间 · 现代</code>。
     *
     * <p>名字从<b>字典</b>取(D-14:空间/风格不硬编码),查不到就退回键本身 ——
     * 后台新增一个空间时这里会显示成 {@code attic · modern},不好看但不会炸,
     * 而且用户本来就能重命名(T-1.12)。
     *
     * <p>为什么不留空:{@code PlanSummary.name} 在契约里是 <b>required</b>,
     * 方案列表(T-1.12)直接渲染它,留空就是一张没有标题的卡片。
     *
     * <p>截断走 {@link PlanNames#fit}(按<b>码点</b>),不自己写
     * {@code substring} —— 后者会把 emoji 的代理对劈成两半。
     */
    private String defaultName(String space, String style) {
        return PlanNames.fit(label("space", space) + " · " + label("style", style),
                PlanNames.NAME_MAX);
    }

    private String label(String dictType, String key) {
        if (!StringUtils.hasText(key)) {
            return "";
        }
        return dictService.enabledOfType(dictType).stream()
                .filter(entry -> key.equals(entry.getDictKey()))
                .findFirst()
                .map(entry -> entry.getDictLabel())
                .orElse(key);
    }

    private String toJson(LayoutModel layout) {
        try {
            return objectMapper.writeValueAsString(layout);
        } catch (JsonProcessingException e) {
            // 序列化失败属编码问题,不能被静默吞掉 —— 抛出去让整个事务回滚,
            // 否则会存下一份空的/坏的户型快照,而任务在页面上显示成功。
            throw new IllegalStateException("户型快照无法序列化", e);
        }
    }
}
