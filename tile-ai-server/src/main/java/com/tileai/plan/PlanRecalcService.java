package com.tileai.plan;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tileai.common.error.BusinessException;
import com.tileai.common.error.ErrorCode;
import com.tileai.layout.model.LayoutModel;
import com.tileai.layout.model.LayoutSpace;
import com.tileai.plan.PlanQuoteSnapshotService.MergedPart;
import com.tileai.plan.PlanQuoteSnapshotService.SnapshotBundle;
import com.tileai.plan.dto.PlanPartRef;
import com.tileai.plan.entity.Plan;
import com.tileai.plan.entity.PlanPart;
import com.tileai.plan.mapper.PlanMapper;
import com.tileai.plan.mapper.PlanPartMapper;
import com.tileai.quote.dto.QuoteResultView;

/**
 * 「按当前价格重算」(契约 {@code POST /plans/{id}/recalc};架构 §15.7;T-1.15)。
 *
 * <p>瓷砖涨价、或后台改了全店的辅料单价 / 损耗率之后,方案里那个金额就是<b>旧的</b>。
 * 本服务让用户一键把它算成当前价 —— 只覆盖报价相关的部分,方案本身(户型、图、名字、
 * 分享链接)一个字不动。
 *
 * <h2>无请求体 —— 这是不变量,不是风格</h2>
 *
 * <p>部位、面积、开口<b>一律取自方案快照</b>({@code t_plan_part} + {@code layout_enc})。
 * 一旦让它收前端传参,{@code /quote/calc} 那一套参数校验就被绕过去了:前端可以直接把
 * 面积填成 1 ㎡ 去探价(CLAUDE.md 架构不变量 6)。所以控制器上连 {@code @RequestBody}
 * 都不存在,反射测试钉着这一条。
 *
 * <h2>为什么读存储值、而不是重新推导</h2>
 *
 * <p>面积读的是 {@code t_plan_part.gross_area}(生成那一刻固化的快照),**不在重算时重推**。
 * 重推会引入第二个口径:同样的户型、同样的部位,推导规则哪天改了(比如补齐 {@code local}
 * 部位的默认值),重算得到的数就会与当初生成的数不一样,而用户什么都没改。
 * 定空间的规则、合并部位的规则也都是<b>调用生成路径的同一个方法</b>,不是复制一份。
 *
 * <h2>算不出来就什么都不改</h2>
 *
 * <p>失败一律 {@code 90001}(不是 30001)—— 沿袭 §41.4:「方案在,坏的是系统」。
 * 户型为空 / 密文损坏 / 没有部位行 / 某行面积为空 / SKU 已从产品库删掉 / 报价参数没配,
 * 这几种用户都无法自修,报 30001 会让他以为方案没了。而且<b>这几种情况下数据库一个字节
 * 都不动</b> —— 覆盖到一半失败、留下一个新总额配一份旧明细,比不算更糟。
 */
@Service
public class PlanRecalcService {

    private static final Logger log = LoggerFactory.getLogger(PlanRecalcService.class);

    private final PlanOwnership ownership;
    private final PlanMapper planMapper;
    private final PlanPartMapper planPartMapper;
    private final PlanService planService;
    private final PlanQuoteSnapshotService quoteSnapshotService;
    private final PlanPartsWriter partsWriter;

    public PlanRecalcService(PlanOwnership ownership, PlanMapper planMapper,
                            PlanPartMapper planPartMapper, PlanService planService,
                            PlanQuoteSnapshotService quoteSnapshotService,
                            PlanPartsWriter partsWriter) {
        this.ownership = ownership;
        this.planMapper = planMapper;
        this.planPartMapper = planPartMapper;
        this.planService = planService;
        this.quoteSnapshotService = quoteSnapshotService;
        this.partsWriter = partsWriter;
    }

    /**
     * 按当前价格重算该方案的报价,并覆盖存储。
     *
     * @return {@code QuoteService} 算出来的那份报价 —— 与 {@code /quote/calc} 同源,
     *         也就是刚刚落库的那一份。**同一个对象**返回给前端,
     *         结构上不可能出现"看到的数"与"存下来的数"不一致
     * @throws BusinessException 30001 —— 方案不存在、已删除或不属于本人;90001 —— 见类注释
     */
    @Transactional
    public QuoteResultView recalc(long userId, long planId) {
        Plan plan = ownership.requireOwned(userId, planId);

        // 读的部分全部先做完(解密 + 校验 + 算价),一个字节都不写。
        // 下面任何一步抛异常,事务里都还没有脏东西。
        LayoutModel layout = planService.readLayout(plan);
        List<PlanPart> rows = planPartMapper.selectList(Wrappers.<PlanPart>lambdaQuery()
                .eq(PlanPart::getPlanId, planId)
                .orderByAsc(PlanPart::getId));

        LayoutSpace resolved = PlanSpaces.resolve(layout, plan.getSpace());
        List<MergedPart> merged = PlanAutoSaveService.mergeParts(resolved, toRefs(rows, planId));

        Optional<SnapshotBundle> built =
                quoteSnapshotService.buildDetailed(plan.getSpace(), resolved, merged);
        if (built.isEmpty()) {
            log.warn("方案 {} 重算不出报价,本次不做任何修改(原因见上一条 WARN)", planId);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
        SnapshotBundle bundle = built.get();

        // 写:部位费用列 + 报价明细 + 方案总额。同一事务,中途失败一起回滚。
        partsWriter.replaceParts(planId, merged, bundle.snapshot());
        quoteSnapshotService.persist(planId, bundle.snapshot());

        // 归属条件放进 UPDATE 的 WHERE(PlanOwnership.ownedUpdate),而不是"先查后写":
        // 这样"查的时候还在、写的时候被删了"的竞态根本不会发生。
        // 行数为 0 时由 requireAffected 补 30001 —— 30001 的语义只出自 PlanOwnership。
        //
        // quote_expired 置回 false:总额刚刚按当前价算过,定义上不可能是过期的。
        //
        // updated_at 显式写 NOW():用户确实点了一次,方案列表按 updated_at desc 排序,
        // 刚重算的理应排到前面。与 PlanQuoteExpiryMarker 的「标记不得抬时间戳」是
        // **相反**的两件事 —— 那边是系统在后台改标记(用户没操作),这边是用户的操作。
        int affected = planMapper.update(null, ownership.ownedUpdate(userId, planId)
                .set(Plan::getTotalAmount, bundle.snapshot().totalAmount())
                .set(Plan::getQuoteExpired, false)
                .setSql("updated_at = NOW()"));
        ownership.requireAffected(affected);

        return bundle.quote();
    }

    /**
     * {@code t_plan_part} 行 → 生成路径用的部位引用。
     *
     * <p>{@code area} 传的是<b>存下来的</b> {@code gross_area} —— 只要它不是 null,
     * {@code PlanAutoSaveService.grossAreaOf} 就会直接采用,不会再去推导
     * (那条优先级是"请求里显式给了就用它")。这正是重算与生成口径一致的机制。
     */
    private static List<PlanPartRef> toRefs(List<PlanPart> rows, long planId) {
        if (rows.isEmpty()) {
            log.warn("方案 {} 没有任何部位行,无从重算(空清单会算出 0 元)", planId);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
        for (PlanPart row : rows) {
            if (row.getGrossArea() == null) {
                // 面积没定 → 当 0 算会得出偏低价。这一条本来也会被 buildDetailed 拦下,
                // 在这里先拦是为了把日志指到具体是哪一行。
                log.warn("方案 {} 的部位 {}/{} 面积未定,不重算", planId, row.getPart(), row.getSkuCode());
                throw new BusinessException(ErrorCode.INTERNAL_ERROR);
            }
        }
        return rows.stream()
                .map(row -> new PlanPartRef(row.getPart(), row.getSkuCode(),
                        row.getGrossArea(), row.getSource()))
                .toList();
    }
}
