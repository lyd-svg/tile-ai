package com.tileai.quote;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tileai.common.error.BusinessException;
import com.tileai.common.error.ErrorCode;
import com.tileai.common.model.Part;
import com.tileai.layout.model.Opening;
import com.tileai.product.entity.Product;
import com.tileai.product.mapper.ProductMapper;
import com.tileai.quote.dto.QuoteCalcRequest;
import com.tileai.quote.dto.QuoteCalcRequest.PartSelection;
import com.tileai.quote.dto.QuoteResultView;
import com.tileai.quote.entity.QuoteParam;
import com.tileai.quote.entity.QuoteParamKey;
import com.tileai.quote.mapper.QuoteParamMapper;

/**
 * 报价规则引擎(架构 §8.8;PRD §5.5)。
 *
 * <h2>公式</h2>
 * <pre>
 *   有效面积   = 部位毛面积 − Σ(该部位开口投影面积)
 *   材料费     = 有效面积 × (1 + 损耗率) × 该砖单价
 *   辅料费     = Σ各部位有效面积 × 辅料单价
 *   单空间总价 = Σ各部位材料费 + 辅料费
 * </pre>
 *
 * <h2>报价只归本模块(CLAUDE.md 架构不变量 1)</h2>
 * <p>Python 不参与报价,契约中**不存在** {@code /internal/quote/*}。
 * 触发点只有两处:本接口(C 端预览)与生成流程的 Java 内 in-process 调用。
 * 对话智能体问价一律 {@code 50001} 拒答(一致性评审 A1)。
 *
 * <h2>为什么这个类是"纯函数式"的</h2>
 * <p>除读参数与读单价外不做任何 I/O,也不依赖请求上下文 —— 这样它才能被
 * §10.6⑤ 的示例数值直接钉住,也能被 T-1.15(按当前价重算)原样复用。
 * 重算之所以安全,正是因为公式只有这一份。
 */
@Service
public class QuoteService {

    private static final Logger log = LoggerFactory.getLogger(QuoteService.class);

    /** 面积小数位,与 §8.4 的 {@code DECIMAL(10,4)} 一致。 */
    private static final int AREA_SCALE = 4;

    /** 金额小数位:报价精确到分。 */
    private static final int MONEY_SCALE = 2;

    private static final BigDecimal ONE = BigDecimal.ONE;

    private final QuoteParamMapper quoteParamMapper;
    private final ProductMapper productMapper;

    public QuoteService(QuoteParamMapper quoteParamMapper, ProductMapper productMapper) {
        this.quoteParamMapper = quoteParamMapper;
        this.productMapper = productMapper;
    }

    // ------------------------------------------------------------------

    /**
     * 计算单空间报价(契约 {@code POST /quote/calc})。
     *
     * <p>{@code request.space()} 不参与计算 —— 面积已由请求逐部位给出。
     * 它只作上下文(契约把它标为必填),不要为了"用上它"而引入面积推导:
     * 那会让报价依赖空间类型,而 §8.8 的公式里没有这一项。
     */
    @Transactional(readOnly = true)
    public QuoteResultView calculate(QuoteCalcRequest request) {
        return calculateCore(request);
    }

    /**
     * 「算得出来就算,算不出来就算了」—— 给<b>落方案快照</b>用,绝不把异常抛给调用方。
     *
     * <p>用于 T-1.12 的出图回调:方案必须存下来,而报价只是它的一个附加物。
     * 产品刚好下架、报价参数没配、某个部位的面积推不出来 —— 这些都不该让一次
     * 已经成功的生成回滚成失败(用户看到的是"图出来了但保存失败")。
     *
     * <p><b>本方法刻意不标任何事务注解。</b>这不是省事,是必须:
     * {@link #calculate} 标着 {@code @Transactional(readOnly = true)},从外层事务里
     * 调它、再用 {@code try/catch} 吞掉异常,Spring 会把**共享事务标成 rollback-only**
     * —— 外层正常返回后在提交时抛 {@code UnexpectedRollbackException},症状是
     * "方法明明没报错,数据却没写进去",现场极难查。让异常在**没有事务代理**的
     * {@link #calculateCore} 上抛出并就地吞掉,这个问题根本不会出现。
     *
     * <p>失败只记 WARN(带原因),调用方拿到的 {@code Optional.empty()} 就是
     * 「这次不落报价」的唯一信号。
     */
    public Optional<QuoteResultView> tryCalculate(QuoteCalcRequest request) {
        try {
            return Optional.of(calculateCore(request));
        } catch (RuntimeException e) {
            log.warn("报价快照计算失败,本次不落报价(方案照常保存): {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 真正的计算主体。
     *
     * <p>刻意<b>不</b>标事务注解,也不做任何 I/O 之外的事 —— 除了读参数与读单价、
     * 它只做乘法与加法。所以它既能被 {@link #calculate}(C 端预览,只读事务)复用,
     * 也能被 {@link #tryCalculate}(落方案快照,加入调用方事务)复用,两条路径
     * 算出<b>同一份</b>结果。
     */
    private QuoteResultView calculateCore(QuoteCalcRequest request) {
        QuoteParams params = loadParams();
        Map<String, Product> products = resolveProducts(request.parts());
        Map<String, BigDecimal> deductions = openingDeductions(request.openings());

        // 同部位同砖先合并面积(§8.8:避免分别计算后取整产生误差)
        Map<PartSku, BigDecimal> merged = mergeParts(request.parts());

        // 按部位聚合:开口扣减是**部位级**的,而计价的粒度是 (部位, 砖)
        Map<String, BigDecimal> grossByPart = new LinkedHashMap<>();
        for (Map.Entry<PartSku, BigDecimal> entry : merged.entrySet()) {
            grossByPart.merge(entry.getKey().part(), entry.getValue(), BigDecimal::add);
        }

        List<QuoteResultView.Item> items = new ArrayList<>();
        BigDecimal materialTotal = BigDecimal.ZERO;
        BigDecimal totalEffectiveArea = BigDecimal.ZERO;

        for (Map.Entry<PartSku, BigDecimal> entry : merged.entrySet()) {
            PartSku key = entry.getKey();
            BigDecimal grossArea = entry.getValue();

            BigDecimal partGross = grossByPart.get(key.part());
            BigDecimal partDeduction = deductions.getOrDefault(key.part(), BigDecimal.ZERO);
            BigDecimal partEffective = partGross.subtract(partDeduction).max(BigDecimal.ZERO);

            // 一个部位用了多款砖时,开口扣减按**毛面积占比**分摊到各款砖上。
            // §8.8 只说"扣减是部位级的、计价是按 SKU 的",没说多 SKU 时怎么分 ——
            // 按面积占比是唯一不偏袒某一款砖的分法,也保证各分项有效面积之和
            // 恰好等于该部位的有效面积。
            BigDecimal effectiveArea = partGross.signum() == 0
                    ? BigDecimal.ZERO
                    : grossArea.multiply(partEffective)
                            .divide(partGross, AREA_SCALE, RoundingMode.HALF_UP);

            Product product = products.get(key.sku());
            BigDecimal price = product.getPricePerSqm();
            BigDecimal materialFee = effectiveArea
                    .multiply(ONE.add(params.lossRate()))
                    .multiply(price)
                    .setScale(MONEY_SCALE, RoundingMode.HALF_UP);

            items.add(new QuoteResultView.Item(
                    key.part(),
                    key.sku(),
                    grossArea.setScale(AREA_SCALE, RoundingMode.HALF_UP),
                    effectiveArea,
                    params.lossRate(),
                    price,
                    materialFee));

            // 合计**累加已四舍五入的分项**,而不是先加总分再取整 ——
            // 这样"明细逐条加起来"与"合计"永远对得上,对账时不会差一分钱。
            materialTotal = materialTotal.add(materialFee);
            totalEffectiveArea = totalEffectiveArea.add(effectiveArea);
        }

        items.sort((a, b) -> {
            int byPart = partOrder(a.part()) - partOrder(b.part());
            return byPart != 0 ? byPart : a.sku().compareTo(b.sku());
        });

        BigDecimal auxTotal = totalEffectiveArea.multiply(params.auxUnitPrice())
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal total = materialTotal.add(auxTotal);

        return new QuoteResultView(materialTotal, auxTotal, total, items,
                new QuoteResultView.RulesSnapshot(params.auxUnitPrice(), params.lossRate()));
    }

    // ------------------------------------------------------------------
    // 参数与单价
    // ------------------------------------------------------------------

    /** 报价参数。 */
    private record QuoteParams(BigDecimal auxUnitPrice, BigDecimal lossRate) {
    }

    /**
     * 读报价参数。
     *
     * <p>缺参数时**直接失败**,不给默认值兜底:一组"悄悄用了内置默认值"的报价
     * 比一条明确的配置错误危险得多 —— 运营以为价格按自己配的算,实际不是。
     */
    private QuoteParams loadParams() {
        Map<String, BigDecimal> values = quoteParamMapper.selectList(
                        Wrappers.<QuoteParam>lambdaQuery())
                .stream()
                .filter(param -> param.getParamValue() != null)
                .collect(Collectors.toMap(QuoteParam::getParamKey, QuoteParam::getParamValue,
                        (a, b) -> a));

        BigDecimal auxUnitPrice = values.get(QuoteParamKey.AUX_UNIT_PRICE.code());
        BigDecimal lossRate = values.get(QuoteParamKey.LOSS_RATE.code());

        List<String> missing = new ArrayList<>();
        if (auxUnitPrice == null) {
            missing.add(QuoteParamKey.AUX_UNIT_PRICE.code());
        }
        if (lossRate == null) {
            missing.add(QuoteParamKey.LOSS_RATE.code());
        }
        if (!missing.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND,
                    "报价参数未配置:" + String.join("、", missing)
                            + "(请先在后台「报价参数」中设置)");
        }
        return new QuoteParams(auxUnitPrice, lossRate);
    }

    /**
     * 按 sku 取产品(要它的单价)。
     *
     * <p>编号不存在直接 10001 —— 报价单上出现一个查不到的砖,等于给出一个
     * 无法成交的价格。
     */
    private Map<String, Product> resolveProducts(List<PartSelection> parts) {
        Set<String> skus = parts.stream().map(PartSelection::sku)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<String, Product> bySku = productMapper.selectList(Wrappers.<Product>lambdaQuery()
                        .in(Product::getSkuCode, skus))
                .stream()
                .collect(Collectors.toMap(Product::getSkuCode, product -> product, (a, b) -> a));

        List<String> missing = skus.stream().filter(sku -> !bySku.containsKey(sku)).toList();
        if (!missing.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID,
                    "瓷砖编号不存在:" + String.join("、", missing));
        }
        return bySku;
    }

    // ------------------------------------------------------------------
    // 面积
    // ------------------------------------------------------------------

    /** 同部位同砖的合并键。 */
    private record PartSku(String part, String sku) {
    }

    /** 同部位同砖**先合并面积**(§8.8)。用 LinkedHashMap 保持请求顺序,便于排查。 */
    private static Map<PartSku, BigDecimal> mergeParts(List<PartSelection> parts) {
        Map<PartSku, BigDecimal> merged = new LinkedHashMap<>();
        for (PartSelection selection : parts) {
            merged.merge(new PartSku(selection.part(), selection.sku()), selection.area(),
                    BigDecimal::add);
        }
        return merged;
    }

    /**
     * 各部位的开口扣减面积(§8.8)。
     *
     * <pre>
     *   地面 floor = 0
     *   墙面 wall  = Σ门洞面积 + Σ窗洞面积
     *   局部 local = 0(腰线/花砖按实际铺贴,不扣开口)
     * </pre>
     *
     * <p><b>地面为什么是 0</b>:§8.8 写「地面部位开口 = 门洞面积(**占地面部分**)」,
     * 而"占地面部分"取决于墙体厚度 —— 户型模型(§8.3)里**没有墙厚字段**,
     * 因此该项恒为 0。这与 §10.6⑤ 的示例一致:示例中地面存在门洞,
     * 而 {@code floor.grossArea = effectiveArea = 3.6},未做任何扣减。
     */
    private static Map<String, BigDecimal> openingDeductions(
            List<QuoteCalcRequest.PartOpenings> openings) {
        Map<String, BigDecimal> deductions = new LinkedHashMap<>();
        if (openings == null) {
            return deductions;
        }
        for (QuoteCalcRequest.PartOpenings group : openings) {
            if (!Part.WALL.code().equals(group.part()) || group.openings() == null) {
                // 地面与局部不扣开口;显式记 0 便于阅读时确认"不是漏算了"
                deductions.putIfAbsent(group.part(), BigDecimal.ZERO);
                continue;
            }
            BigDecimal sum = BigDecimal.ZERO;
            for (Opening opening : group.openings()) {
                // 墙面部位开口 = 门洞面积 + 窗洞面积(§8.8 未区分门窗算法,统一按宽×高)
                sum = sum.add(opening.w().multiply(opening.h()));
            }
            deductions.merge(group.part(), sum, BigDecimal::add);
        }
        return deductions;
    }

    /** 输出顺序:地面 → 墙面 → 局部,与 §8.8 的公式书写顺序一致。 */
    private static int partOrder(String part) {
        if (Part.FLOOR.code().equals(part)) {
            return 0;
        }
        if (Part.WALL.code().equals(part)) {
            return 1;
        }
        return 2;
    }
}
