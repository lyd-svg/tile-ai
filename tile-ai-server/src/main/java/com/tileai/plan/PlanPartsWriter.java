package com.tileai.plan;

import java.util.List;

import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tileai.plan.PlanQuoteSnapshotService.MergedPart;
import com.tileai.plan.PlanQuoteSnapshotService.PartFee;
import com.tileai.plan.PlanQuoteSnapshotService.Snapshot;
import com.tileai.plan.entity.PlanPart;
import com.tileai.plan.mapper.PlanPartMapper;

/**
 * {@code t_plan_part} 的落库 —— 部位行 + 分项费用列(T-1.15)。
 *
 * <p>从 {@code PlanAutoSaveService.replaceParts} 原样搬出来,由<b>生成</b>与<b>重算</b>
 * 两条路径共用同一份。重算若自己再写一遍"按 (部位, SKU) 取费用"的循环,
 * 两条路迟早会在某个边界上分叉(比如快照缺失时一行写 0、另一行留空),
 * 而两边各自的测试都可能是绿的。
 *
 * <p>独立成组件而不是静态工具,是因为它要写库:静态方法里捞 mapper 只能靠
 * 参数传递或持有状态,前者容易漏传、后者在并发下不安全。
 */
@Component
public class PlanPartsWriter {

    private final PlanPartMapper planPartMapper;

    public PlanPartsWriter(PlanPartMapper planPartMapper) {
        this.planPartMapper = planPartMapper;
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
    public void replaceParts(long planId, List<MergedPart> merged, Snapshot snapshot) {
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
}
