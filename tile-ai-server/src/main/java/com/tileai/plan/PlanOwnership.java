package com.tileai.plan;

import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tileai.common.error.BusinessException;
import com.tileai.common.error.ErrorCode;
import com.tileai.plan.entity.Plan;
import com.tileai.plan.mapper.PlanMapper;

/**
 * 「这个方案是不是我的、还在不在」—— **全项目唯一的判定出口**。
 *
 * <p>条件三个,且合并成同一个结果:
 * <ol>
 *   <li>存在;</li>
 *   <li>没有逻辑删除({@code is_deleted = 0});</li>
 *   <li>属于当前用户。</li>
 * </ol>
 *
 * <p><b>刻意不区分。</b>若"存在但不属于你"单独回一个码,这个接口就成了一个可以
 * 拿来枚举别人方案 id 的探针 —— 参数化扫一遍就知道哪些 id 是真的。
 * 所以三者一律 {@code 30001},消息也统一。
 *
 * <p><b>为什么单独成类。</b>方案详情 / 重命名 / 删除 / 另存为 / 出图回调的
 * {@code assertOverwritable} 都要用它。这段 SQL 一旦被复制五份,迟早有一份会写成
 * 「先按 id 查出来,再在 Java 里比 user_id」—— 那一刻 30001 与 403 的分叉就漏出去了,
 * 而且不会有任何测试红。放在这里,条件只可能改一处。
 *
 * <p>判定放进 {@code WHERE} 而不是查出来再比,还有个实际好处:它天然是原子的 ——
 * 改名/删除时"查的时候还在、改的时候被删了"这种竞态根本不会发生。
 */
@Component
public class PlanOwnership {

    private final PlanMapper planMapper;

    public PlanOwnership(PlanMapper planMapper) {
        this.planMapper = planMapper;
    }

    /**
     * 取本人在用的方案,取不到返回 {@code null}。
     *
     * <p>返回 null 而不是抛异常,是给「取不到就要退回新建」的调用方留的
     * (出图期间方案被删,见 {@code PlanAutoSaveService.upsert})。要 30001 的
     * 调用方用 {@link #requireOwned}。
     */
    public Plan findOwned(long userId, Long planId) {
        if (planId == null) {
            return null;
        }
        return planMapper.selectOne(Wrappers.<Plan>lambdaQuery()
                .eq(Plan::getId, planId)
                .eq(Plan::getUserId, userId)
                .eq(Plan::getIsDeleted, false));
    }

    /**
     * 取本人在用的方案,取不到就 30001。
     *
     * @throws BusinessException 30001 —— 不存在、已删除或不属于本人(三者不可区分)
     */
    public Plan requireOwned(long userId, Long planId) {
        Plan plan = findOwned(userId, planId);
        if (plan == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "方案不存在");
        }
        return plan;
    }

    /**
     * 「改名 / 逻辑删除」这类<b>只动一两列</b>的写操作的 WHERE 条件(带归属 + 未删除)。
     *
     * <p>为什么不是"先 {@link #requireOwned} 再 update":判定放进 UPDATE 的 WHERE 里,
     * 这条写就是原子的 —— "查的时候还在、写的时候被删了"这种竞态根本不会发生,
     * 也不用把三条条件在第二个地方再抄一遍。代价是受影响行数为 0 时要自己抛 30001,
     * 用 {@link #requireAffected} 补上,让码与消息仍旧只出自本类。
     *
     * <p><b>{@code planId} 为 null 时 {@code eq} 会被跳过</b>(MyBatis-Plus 的
     * {@code eq} 在值为 null 时不拼这个条件)—— 那会让 UPDATE 命中<b>该用户的全部方案</b>。
     * 调用方拿到的是路径变量,不可能是 null,但写在这里免得以后有人拿它去做别的拼接。
     */
    public LambdaUpdateWrapper<Plan> ownedUpdate(long userId, Long planId) {
        if (planId == null) {
            throw new IllegalArgumentException("planId 不能为空:归属条件拼不出来");
        }
        return Wrappers.<Plan>lambdaUpdate()
                .eq(Plan::getId, planId)
                .eq(Plan::getUserId, userId)
                .eq(Plan::getIsDeleted, false);
    }

    /**
     * 写操作一行都没改到时抛 30001 —— 与 {@link #requireOwned} 完全同一套语义与措辞。
     *
     * @throws BusinessException 30001 —— 不存在、已删除或不属于本人(三者不可区分)
     */
    public void requireAffected(int affectedRows) {
        if (affectedRows == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "方案不存在");
        }
    }
}
