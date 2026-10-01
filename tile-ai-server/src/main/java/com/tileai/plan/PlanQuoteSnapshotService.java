package com.tileai.plan;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tileai.common.model.Part;
import com.tileai.layout.model.LayoutSpace;
import com.tileai.plan.entity.PlanQuote;
import com.tileai.plan.mapper.PlanQuoteMapper;
import com.tileai.product.entity.Product;
import com.tileai.product.mapper.ProductMapper;
import com.tileai.quote.QuoteService;
import com.tileai.quote.dto.QuoteCalcRequest;
import com.tileai.quote.dto.QuoteResultView;

/**
 * 出图落方案时,把「这一单按生成时的价格算出来是多少」固化成快照
 * ({@code t_plan_quote} + {@code t_plan_part} 的费用列 + {@code t_plan.total_amount})。
 *
 * <p>本类<b>只做生成时那一次</b>。「按当前价重算」与「报价已过期」标记属 T-1.15。
 *
 * <h2>为什么这件事必须做,不能等到 T-1.15</h2>
 *
 * <p>契约 {@code PlanPart.materialFee} 是 <b>required</b>,而「我的方案」卡片的
 * 「报价总额」直接渲染 {@code PlanSummary.totalAmount}。不落这份快照,列表与详情
 * 就一天缺必填字段、卡片上一天没有数字。
 *
 * <h2>算不出就整单不落(本类最重要的一条口径)</h2>
 *
 * <p>{@link #build} 返回 {@code Optional.empty()} 时,调用方会把
 * {@code total_amount} / {@code t_plan_quote} / 五个费用列**一起留空**,
 * 而不是写 0、也不是"只落算得出来的那几行"。理由:
 *
 * <ul>
 *   <li>把推不出的面积当 0 会静默算出一个<b>偏低价</b> ——
 *       {@code PlanAutoSaveService.grossAreaOf} 对 {@code local} 部位和
 *       "匹配不到空间"返回的就是 null,那个 null 的含义是<b>「没定」</b>;</li>
 *   <li>只落部分行会得到一个"小一号的总额",而卡片上<b>没有任何字段</b>能说明
 *       这个总额是残缺的 —— 用户会当成真实报价。</li>
 * </ul>
 *
 * <p>所以要么整单有报价,要么整单没有。宁可空着,不可报一个自己都不信的数。
 *
 * <h2>算不出的四种情况</h2>
 *
 * <p>部位行为空 / 某行面积为 null / SKU 在 {@code t_product} 里查不到 /
 * {@code t_quote_param} 缺参数 —— 前三种在这里判,第四种由
 * {@link QuoteService#tryCalculate} 吞掉它的 30001 后返回空。四种都只记 WARN,
 * <b>绝不抛出</b>:方案必须存下来,报价只是它的附加物。
 */
@Service
public class PlanQuoteSnapshotService {

    private static final Logger log = LoggerFactory.getLogger(PlanQuoteSnapshotService.class);

    private final QuoteService quoteService;
    private final ProductMapper productMapper;
    private final PlanQuoteMapper planQuoteMapper;
    private final ObjectMapper objectMapper;

    public PlanQuoteSnapshotService(QuoteService quoteService, ProductMapper productMapper,
                                    PlanQuoteMapper planQuoteMapper, ObjectMapper objectMapper) {
        this.quoteService = quoteService;
        this.productMapper = productMapper;
        this.planQuoteMapper = planQuoteMapper;
        this.objectMapper = objectMapper;
    }

    // ------------------------------------------------------------------

    /**
     * 合并后的一个方案部位(V4 的 DDL 说 {@code gross_area} 是"同部位同砖合并后"的值)。
     *
     * @param grossArea 毛面积;{@code null} 表示"推不出",不是 0
     * @param source    契约 {@code PartSource}({@code user} / {@code ai}),调用方已归一为非空
     */
    public record MergedPart(String part, String sku, BigDecimal grossArea, String source) {

        public PartSku key() {
            return new PartSku(part, sku);
        }
    }

    /** 部位 + 瓷砖编号 —— 报价分项与方案部位行对得上的唯一钥匙。 */
    public record PartSku(String part, String sku) {
    }

    /** 一个部位行该落进 {@code t_plan_part} 的费用列(算不出时整条不存在)。 */
    public record PartFee(Long productId, String productName, BigDecimal pricePerSqm,
                          BigDecimal area, BigDecimal lossRate, BigDecimal materialFee) {
    }

    /** 一次算成、待落库的完整报价快照。 */
    public record Snapshot(BigDecimal totalAmount, BigDecimal materialTotal, BigDecimal auxTotal,
                           BigDecimal auxUnitPrice, BigDecimal auxArea, BigDecimal lossRate,
                           String rulesSnapshotJson, Map<PartSku, PartFee> fees) {
    }

    /**
     * 「算出来的那一份报价」+「据此整理的落库快照」—— 同一次计算的两种表述(T-1.15)。
     *
     * <p>重算({@code POST /plans/{id}/recalc})要**把报价原样返回给前端**,而
     * {@link #build} 只给得出整理过的快照。若重算自己再调一次 {@code QuoteService}
     * 拿返回值、同时又调 {@code build} 落库,就是**算了两次** —— 两次之间价格变了,
     * 用户看到的数与他点「重算」后存下来的数就会不一致。绑在一个 record 里,
     * 结构上就不允许出现这种分叉。
     *
     * @param snapshot 落库用的整理结果
     * @param quote    {@code QuoteService} 算出来的<b>原始</b>返回值,与
     *                 {@code /quote/calc} 的响应同源
     */
    public record SnapshotBundle(Snapshot snapshot, QuoteResultView quote) {
    }

    // ------------------------------------------------------------------

    /**
     * 算一份快照。**绝不抛异常**,算不出就返回空。
     *
     * <p>生成路径用这个;要同时拿到「报价原始返回值」的路径(重算)用
     * {@link #buildDetailed} —— 本方法就是它取 {@code snapshot} 那一半。
     *
     * @param space  plan 的生成空间。只作上下文 —— {@code QuoteService} 的公式里
     *               没有空间这一项(§8.8),面积一律由部位逐条给出
     * @param layout 该方案的户型快照,用来取墙面开口(开口只扣墙面,地面与局部不扣)
     * @param merged 已合并的部位行
     */
    public Optional<Snapshot> build(String space, LayoutSpace layout, List<MergedPart> merged) {
        return buildDetailed(space, layout, merged).map(SnapshotBundle::snapshot);
    }

    /**
     * 算一份快照,并把 {@code QuoteService} 的原始返回值一并带回(T-1.15 重算用)。
     *
     * <p>判定口径与 {@link #build} **完全同一份**(就是它本来的实现):
     * 部位行为空 / 某行面积为 null / SKU 查不到 / 参数缺失 / 序列化失败 →
     * {@code Optional.empty()},调用方据此决定"什么都不改"。
     */
    public Optional<SnapshotBundle> buildDetailed(String space, LayoutSpace layout,
                                                  List<MergedPart> merged) {
        if (merged == null || merged.isEmpty()) {
            log.warn("方案没有任何部位行,不落报价快照(空清单会算出 0 元,那是最坏的一种答案)");
            return Optional.empty();
        }
        List<String> undecided = merged.stream()
                .filter(row -> row.grossArea() == null)
                .map(row -> row.part() + "/" + row.sku())
                .toList();
        if (!undecided.isEmpty()) {
            log.warn("以下部位的面积推不出来({}),不落报价快照 —— 把它们当 0 会算出偏低价",
                    String.join("、", undecided));
            return Optional.empty();
        }

        Optional<QuoteResultView> result = quoteService.tryCalculate(toRequest(space, layout, merged));
        if (result.isEmpty()) {
            return Optional.empty();
        }
        QuoteResultView quote = result.get();

        Map<String, Product> products = productsOf(merged);
        if (products == null) {
            return Optional.empty();
        }

        Map<PartSku, PartFee> fees = new LinkedHashMap<>();
        BigDecimal auxArea = BigDecimal.ZERO;
        for (QuoteResultView.Item item : quote.items()) {
            Product product = products.get(item.sku());
            fees.put(new PartSku(item.part(), item.sku()), new PartFee(
                    product.getId(),
                    product.getName(),
                    item.pricePerSqm(),
                    item.effectiveArea(),
                    item.lossRate(),
                    item.materialFee()));
            auxArea = auxArea.add(item.effectiveArea());
        }

        String rulesJson;
        try {
            rulesJson = objectMapper.writeValueAsString(quote.rulesSnapshot());
        } catch (JsonProcessingException e) {
            // 两个 BigDecimal 序列化不了,说明 ObjectMapper 的配置坏了 —— 属编码问题。
            // 仍然不抛:方案必须存下来。但绝不能静默 —— 记 ERROR 并放弃这次报价。
            log.error("报价参数快照无法序列化,不落报价快照", e);
            return Optional.empty();
        }

        return Optional.of(new SnapshotBundle(new Snapshot(
                quote.total(),
                quote.materialTotal(),
                quote.auxTotal(),
                quote.rulesSnapshot().auxUnitPrice(),
                auxArea,
                quote.rulesSnapshot().lossRate(),
                rulesJson,
                fees), quote));
    }

    /**
     * 落库。**先删后插** —— V4 的 {@code t_plan_quote} 没有 {@code plan_id} 唯一键,
     * "一个方案至多一行"只能由写入方维持;覆盖保存时旧行必须被清掉,否则会堆起来。
     *
     * <p>本方法不标 {@code @Transactional}:它必须加入调用方({@code upsert})的事务 ——
     * 方案行与报价行要么一起成立、要么一起没有。
     */
    public void persist(long planId, Snapshot snapshot) {
        planQuoteMapper.delete(Wrappers.<PlanQuote>lambdaQuery().eq(PlanQuote::getPlanId, planId));

        PlanQuote row = new PlanQuote();
        row.setPlanId(planId);
        row.setMaterialTotal(snapshot.materialTotal());
        row.setAuxTotal(snapshot.auxTotal());
        row.setAuxUnitPrice(snapshot.auxUnitPrice());
        row.setAuxArea(snapshot.auxArea());
        row.setLossRate(snapshot.lossRate());
        row.setTotalAmount(snapshot.totalAmount());
        row.setRulesSnapshot(snapshot.rulesSnapshotJson());
        planQuoteMapper.insert(row);
    }

    // ------------------------------------------------------------------

    /**
     * 拼报价请求。
     *
     * <p>开口只挂在 {@code wall} 上:§8.8 里地面恒为 0("占地面部分"取决于墙厚,而户型模型
     * 没有墙厚字段)、局部按实际铺贴不扣 —— 这是 {@code QuoteService} 的既有口径,
     * 这里照抄不重算。墙面开口漏掉会让报价偏高,所以宁可把整组开口给过去,
     * 也不在这里按类型过滤。
     */
    private static QuoteCalcRequest toRequest(String space, LayoutSpace layout,
                                              List<MergedPart> merged) {
        List<QuoteCalcRequest.PartSelection> parts = merged.stream()
                .map(row -> new QuoteCalcRequest.PartSelection(
                        row.part(), row.sku(), row.grossArea(), row.source()))
                .toList();

        List<QuoteCalcRequest.PartOpenings> openings = new ArrayList<>();
        if (layout != null && layout.openings() != null && !layout.openings().isEmpty()) {
            openings.add(new QuoteCalcRequest.PartOpenings(Part.WALL.code(), layout.openings()));
        }

        return new QuoteCalcRequest(space, parts, openings);
    }

    /**
     * 按 SKU 取产品(要落 {@code product_id} 与 {@code product_name} 两个快照列)。
     *
     * <p>查不到返回 {@code null} 表示"这次不报价" —— <b>不是</b>抛异常。
     * 报价引擎自己也查一次产品(要单价),这里是第二次查,查的是**方案需要的**两列;
     * 两次查的判据一致(都在 {@code t_product} 里按 SKU 找),所以不会出现
     * "报价算出来了、方案却写不下名字"。
     */
    private Map<String, Product> productsOf(List<MergedPart> merged) {
        List<String> skus = merged.stream().map(MergedPart::sku).distinct().toList();
        Map<String, Product> bySku = productMapper.selectList(Wrappers.<Product>lambdaQuery()
                        .in(Product::getSkuCode, skus))
                .stream()
                .collect(Collectors.toMap(Product::getSkuCode, product -> product, (a, b) -> a));

        List<String> missing = skus.stream().filter(sku -> !bySku.containsKey(sku)).toList();
        if (!missing.isEmpty()) {
            log.warn("以下瓷砖编号在产品库里查不到({}),不落报价快照", String.join("、", missing));
            return null;
        }
        return bySku;
    }
}
