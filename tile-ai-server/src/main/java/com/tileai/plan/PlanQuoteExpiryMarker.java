package com.tileai.plan;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tileai.plan.entity.Plan;
import com.tileai.plan.mapper.PlanMapper;

/**
 * 「这些方案的报价已经不准了」—— 报价过期标记的**唯一写入点**(T-1.15)。
 *
 * <p>报价是生成那天算的。瓷砖单价改了、或者全店的辅料单价/损耗率改了,
 * 库里的金额不会自己变,但用户看到的数字已经不对了。本类负责把受影响的方案
 * 标成 {@code quote_expired = 1},让"过期"这件事在界面上**可见**;
 * 用户点「按当前价格重算」({@code PlanRecalcService})后置回 0。
 *
 * <h2>为什么只依赖 {@code PlanMapper}</h2>
 *
 * <p>三个触发点分属三个模块 —— {@code product} 改价、{@code product} 批量导入改价、
 * {@code quote} 改全局参数 —— 都要调本类。若本类再回头去注入它们的任何一个 bean,
 * 立刻形成 Spring 的 bean 环(Boot 2.6+ 默认禁止),应用起不来。
 * <b>本类只认 {@code t_plan},不认识产品与报价参数</b>;是哪块砖变了、哪个参数变了,
 * 由调用方判断后告诉它。
 *
 * <p>项目既有先例就是直接注入对方的 Service(如 {@code ProductService} 注入
 * {@code ComboService}),这里不用 Spring 事件:监听器被误删时**不会红**,
 * 正是本项目最忌讳的静默陷阱。
 *
 * <h2>⚠️ 必须压制 {@code updated_at} 的自动更新</h2>
 *
 * <p>{@code t_plan.updated_at} 带 {@code ON UPDATE CURRENT_TIMESTAMP},而「我的方案」
 * 列表是按 {@code updated_at desc} 排序的。标记过期确实改了这一行,MySQL 就会把时间戳
 * 抬到现在 —— 于是<b>后台改一次全局参数,所有用户的方案列表顺序会被整体打乱</b>。
 * 用户没有做任何操作,自己的列表却变了样;而现有测试一条都不会红。
 *
 * <p>所以两条 UPDATE 都显式写了 {@code updated_at = updated_at}。MySQL 的语义是:
 * 把自动更新列显式赋成它自己的当前值,即可抑制自动更新。用户真正做了操作的路径
 * (改名、重算)才该抬时间戳,那里写的是 {@code updated_at = NOW()}。
 *
 * <h2>为什么条件里要带 {@code quote_expired = 0}</h2>
 *
 * <p>三个好处,缺一不可:
 * <ol>
 *   <li><b>幂等</b> —— 重复标记不会产生任何写入;</li>
 *   <li><b>压住写入量</b> —— 全局参数变动那条会命中所有方案,但标过一次之后再改参数,
 *       命中的只剩"新生成 + 刚重算过"的那几条,靠 {@code idx_plan_expired} 走索引即取完;</li>
 *   <li><b>不制造无意义的时间戳抖动</b>。</li>
 * </ol>
 *
 * <p>另带 {@code total_amount IS NOT NULL}:没有报价的方案谈不上"报价过期" ——
 * 它从来就没算出过金额(§41.2「算不出就整单不落」),标成"已过期"只会让用户在详情页
 * 看到一个"过期了、却没有金额"的矛盾状态。这条同时把全局调价的写入面缩小了。
 */
@Component
public class PlanQuoteExpiryMarker {

    private final PlanMapper planMapper;

    public PlanQuoteExpiryMarker(PlanMapper planMapper) {
        this.planMapper = planMapper;
    }

    /**
     * 某块砖的单价变了 → 把引用了它的在用方案标为过期。
     *
     * <p>按 {@code sku_code} 反查,不按 {@code product_id}:批量导入是按 SKU 匹配产品的,
     * 而 {@code t_plan_part} 里存的是**生成那一刻**的 SKU 快照。两者口径一致。
     *
     * @param skuCode 变更前的 SKU;产品同时改了 SKU 时也应传**旧值** ——
     *                方案里存的是旧 sku_code,用新值反查会一条都匹配不上
     * @return 实际被标记的方案数(已经是过期状态的不计)
     */
    public int bySkuCode(String skuCode) {
        if (skuCode == null || skuCode.isBlank()) {
            throw new IllegalArgumentException("skuCode 不能为空:反查条件拼不出来");
        }
        return planMapper.update(null, expirable()
                .apply("id IN (SELECT plan_id FROM t_plan_part WHERE sku_code = {0})", skuCode));
    }

    /**
     * 全局报价参数(辅料单价 / 损耗率)变了 → 把所有在用方案标为过期。
     *
     * <p>这两个参数乘在**每一个**报价金额上,所以变一次就是所有方案一起过期。
     * 这是事实,不是过度反应 —— 用户看到"已过期"提示时的依据是准的。
     *
     * @return 实际被标记的方案数
     */
    public int all() {
        return planMapper.update(null, expirable());
    }

    /**
     * 两次单价算不算「调价」—— 三个触发点共用这一个判断。
     *
     * <p>分散到三处各写一遍 {@code compareTo},迟早有一处改成 {@code equals} 或漏掉
     * null 判断,于是"只有部分改价路径会标过期"——这种分叉不会有任何测试红。
     *
     * <p>用 {@code compareTo} 而<b>不是</b>{@code equals}:{@code BigDecimal} 的
     * {@code equals} 带 scale,{@code 100.00} 与 {@code 100.0} 会判为不等 ——
     * 那样每次编辑(哪怕没动价)都会被当成调价。
     *
     * @return 单价确实变了(含"一边有值一边没有")
     */
    public static boolean priceChanged(BigDecimal before, BigDecimal after) {
        if (before == null || after == null) {
            return before != after;
        }
        return before.compareTo(after) != 0;
    }

    /** 两条 UPDATE 共用的条件与赋值。 */
    private static LambdaUpdateWrapper<Plan> expirable() {
        return Wrappers.<Plan>lambdaUpdate()
                .set(Plan::getQuoteExpired, true)
                .eq(Plan::getIsDeleted, false)
                .eq(Plan::getQuoteExpired, false)
                .isNotNull(Plan::getTotalAmount)
                // 见类注释:不写这一句,MySQL 会把 updated_at 抬到现在,列表顺序被打乱
                .setSql("updated_at = updated_at");
    }
}
