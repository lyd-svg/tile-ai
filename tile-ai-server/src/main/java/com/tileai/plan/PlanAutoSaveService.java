package com.tileai.plan;

import java.math.BigDecimal;
import java.util.List;

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
import com.tileai.common.error.BusinessException;
import com.tileai.common.error.ErrorCode;
import com.tileai.layout.model.LayoutAreas;
import com.tileai.layout.model.LayoutModel;
import com.tileai.layout.model.LayoutSpace;
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
 * <p><b>本服务不报价</b>:{@code t_plan.total_amount} / {@code t_plan_quote} /
 * {@code t_plan_part.material_fee} 全部留空,归 T-1.15(它卡片的验收标准就是
 * "生成时存 rulesSnapshot")。这里只落"这个方案铺了哪些砖、户型是什么、图在哪"。
 */
@Service
public class PlanAutoSaveService {

    private static final Logger log = LoggerFactory.getLogger(PlanAutoSaveService.class);

    /** {@code t_plan.name} 长度上限。 */
    private static final int NAME_MAX = 200;

    private final PlanMapper planMapper;
    private final PlanPartMapper planPartMapper;
    private final PlanImageMapper planImageMapper;
    private final AesGcmCipher cipher;
    private final DictService dictService;
    private final ObjectMapper objectMapper;

    public PlanAutoSaveService(PlanMapper planMapper, PlanPartMapper planPartMapper,
                              PlanImageMapper planImageMapper, AesGcmCipher cipher,
                              DictService dictService, ObjectMapper objectMapper) {
        this.planMapper = planMapper;
        this.planPartMapper = planPartMapper;
        this.planImageMapper = planImageMapper;
        this.cipher = cipher;
        this.dictService = dictService;
        this.objectMapper = objectMapper;
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
     * 让失败以返回值表达,这个问题根本不会出现。
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
        Plan existing = planId == null ? null : findOwned(userId, planId);

        long targetId;
        if (existing == null) {
            if (planId != null) {
                log.warn("目标方案 {} 在出图期间已不存在或不再属于用户 {},改为新建方案",
                        planId, userId);
            }
            targetId = createPlan(userId, space, style, layoutEnc);
        } else {
            targetId = overwritePlan(existing, space, style, layoutEnc);
        }

        replaceParts(targetId, space, layout, parts);
        replaceImages(targetId, images);
        return targetId;
    }

    // ------------------------------------------------------------------
    // 方案主体
    // ------------------------------------------------------------------

    private long createPlan(long userId, String space, String style, byte[] layoutEnc) {
        Plan plan = new Plan();
        plan.setUserId(userId);
        plan.setName(defaultName(space, style));
        plan.setSpace(space);
        plan.setStyle(style);
        plan.setLayoutEnc(layoutEnc);
        plan.setQuoteExpired(false);
        plan.setIsDeleted(false);
        planMapper.insert(plan);
        return plan.getId();
    }

    private long overwritePlan(Plan existing, String space, String style, byte[] layoutEnc) {
        existing.setSpace(space);
        existing.setStyle(style);
        existing.setLayoutEnc(layoutEnc);
        // **不动 name**:那是用户自己起的名字,重新生成不该把它冲回默认值。
        // 同理不动 created_at 与 share_token —— 覆盖要保住方案的对外身份(T-1.13)。
        planMapper.updateById(existing);
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
     * @param planId 为 null 表示新建,无需校验
     * @throws BusinessException 30001 —— 不存在、已删除或不属于本人
     */
    public void assertOverwritable(long userId, Long planId) {
        if (planId != null && findOwned(userId, planId) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "方案不存在或不可覆盖: " + planId);
        }
    }

    /**
     * 取本人在用的方案,取不到返回 {@code null}。
     *
     * <p>三个条件缺一不可,且合并成同一个结果:不存在、已逻辑删除、不是本人的。
     * 刻意不区分 —— 区分等于告诉调用方"这个 id 存在、只是不属于你",
     * 那就成了一个可以拿来枚举别人方案 id 的接口。
     *
     * <p><b>返回 null 而不是抛异常</b>:调用方有两处,一处要把"取不到"变成
     * 面向用户的 30001({@link #assertOverwritable}),一处要把"取不到"变成
     * 退回新建({@link #upsert})。抛异常的那版会让后者踩到 Spring 的
     * rollback-only 陷阱(见 {@link #upsert} 的说明)。
     */
    private Plan findOwned(long userId, Long planId) {
        return planMapper.selectOne(Wrappers.<Plan>lambdaQuery()
                .eq(Plan::getId, planId)
                .eq(Plan::getUserId, userId)
                .eq(Plan::getIsDeleted, false));
    }

    // ------------------------------------------------------------------
    // 部位
    // ------------------------------------------------------------------

    /**
     * 按 plan 删干净再插。
     *
     * <p>不做逐条 upsert:部位组合会变(这次铺地面+墙面、下次只铺地面),
     * 增量更新就得处理"多出来的那条删不删",而删干净重插天然没有这个问题。
     * 同一事务内,中途失败一起回滚。
     */
    private void replaceParts(long planId, String spaceKey, LayoutModel layout,
                              List<PlanPartRef> parts) {
        planPartMapper.delete(Wrappers.<PlanPart>lambdaQuery().eq(PlanPart::getPlanId, planId));

        LayoutSpace space = resolveSpace(layout, spaceKey);
        for (PlanPartRef part : parts) {
            PlanPart row = new PlanPart();
            row.setPlanId(planId);
            row.setPart(part.part());
            row.setSkuCode(part.sku());
            row.setGrossArea(grossAreaOf(space, part));
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
     */
    private String defaultName(String space, String style) {
        String name = label("space", space) + " · " + label("style", style);
        return name.length() > NAME_MAX ? name.substring(0, NAME_MAX) : name;
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
