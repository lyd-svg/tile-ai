package com.tileai.plan;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tileai.common.outbox.OutboxMapper;
import com.tileai.common.outbox.OutboxMessage;
import com.tileai.common.security.JwtService;
import com.tileai.common.security.SubjectType;
import com.tileai.generate.entity.GenerationTask;
import com.tileai.generate.mapper.GenerationTaskMapper;
import com.tileai.plan.entity.Plan;
import com.tileai.plan.entity.PlanImage;
import com.tileai.plan.entity.PlanPart;
import com.tileai.plan.entity.PlanQuote;
import com.tileai.plan.mapper.PlanImageMapper;
import com.tileai.plan.mapper.PlanMapper;
import com.tileai.plan.mapper.PlanPartMapper;
import com.tileai.plan.mapper.PlanQuoteMapper;
import com.tileai.product.ProductService;
import com.tileai.product.dto.ProductUpsertRequest;
import com.tileai.product.entity.Product;
import com.tileai.product.mapper.ProductMapper;
import com.tileai.quote.entity.QuoteParam;
import com.tileai.quote.mapper.QuoteParamMapper;

import jakarta.servlet.http.Cookie;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * T-1.15 验收:{@code POST /plans/{id}/recalc} —— 按当前价格重算报价。
 *
 * <p><b>需要 MySQL</b>({@code -Ddb.verify=true} 显式启用)。
 *
 * <h2>本类要证明的三件事</h2>
 *
 * <ol>
 *   <li><b>算对了</b> —— 期望值是<b>手算</b>的,不是调一次 {@code QuoteService}
 *       再比(拿被测代码算期望值,断言就成了"它等于它自己");</li>
 *   <li><b>口径与生成一致</b> —— 多空间户型生成一次、不改任何价、直接重算,
 *       两次总额**逐分相等**。这比"代码看起来调的是同一个方法"强得多;</li>
 *   <li><b>算不出来就什么都不改</b> —— 五种失败各一条,逐列断言库没动。</li>
 * </ol>
 *
 * <h2>为什么失败一律 90001 而不是 30001</h2>
 *
 * <p>沿袭 §41.4:密文解不开走 90001,因为"方案在,坏的是系统"。重算失败的原因
 * (户型为空、SKU 已从产品库删掉、报价参数没配、某行面积未定)用户一个都修不了 ——
 * 报 30001 会让他以为方案没了,进而去删掉一个其实完好无损的方案。
 *
 * <h2>只对自己造的数据下结论</h2>
 *
 * <p>用户号段 {@code 8_814_0001} 起,SKU 前缀 {@code T15R-}(与
 * {@code PlanQuoteExpiryIntegrationTest} 的 {@code T15E-} 分开 —— 两个类各自的清理
 * 按前缀删,共用一个前缀会让先跑的类把后跑的类的数据删掉)。报价参数是全局表,
 * 进出各拍一次快照原样还原。
 */
@EnabledIfSystemProperty(named = "db.verify", matches = "true",
        disabledReason = "需要 MySQL;用 -Ddb.verify=true 显式启用(T-1.15 验证)")
@SpringBootTest(properties = {
        "spring.flyway.enabled=false",
        "tile.auth.secret=0123456789abcdef0123456789abcdef",
        "tile.crypto.layout-key=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=",
        "tile.agent.service-token=t15-test-token",
        "tile.generate.compensation-enabled=false",
        // 关掉"受理前探测消费端":本机跑测试没有 Python 消费端,consumerCount 恒为 0,
        // 而本类要造方案 ⇒ 会走 POST /generate ⇒ 不关会整批 40002。
        "tile.generate.require-consumer=false",
        "tile.outbox.enabled=false"})
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PlanRecalcIntegrationTest {

    private static final String PLANS = "/api/v1/plans";
    private static final String RECALC = "/recalc";
    private static final String GENERATE = "/api/v1/generate";
    private static final String CALLBACK = "/internal/generate/callback";
    private static final String SERVICE_TOKEN_HEADER = "X-Service-Token";
    private static final String SERVICE_TOKEN = "t15-test-token";

    private static final long USER = 8_814_0001L;
    private static final long OTHER_USER = 8_814_0002L;

    private static final String SKU_PREFIX = "T15R-";
    private static final String SKU_FLOOR = SKU_PREFIX + "FLOOR";
    private static final String SKU_WALL = SKU_PREFIX + "WALL";

    private static final String PRICE = "100.00";
    private static final String AUX_UNIT_PRICE = "20.00";
    private static final String LOSS_RATE = "0.06";

    private static final String DEFAULT_PARTS =
            "[{\"part\":\"floor\",\"sku\":\"" + SKU_FLOOR + "\"},"
                    + "{\"part\":\"wall\",\"sku\":\"" + SKU_WALL + "\"}]";

    /**
     * 户型 {@code 2m × 1.8m × 2.4m}、两个 SKU 同价 100.00、辅料 20.00、损耗 6% 时,
     * §8.8 的公式手算结果:
     * <pre>
     *   地面 毛面积 3.6   × 1.06 × 100 =  381.60
     *   墙面 毛面积 18.24 × 1.06 × 100 = 1933.44
     *   材料合计                         2315.04
     *   辅料 (3.6 + 18.24) × 20.00   =   436.80
     *   生成时总价                       2751.84
     * </pre>
     *
     * <p>把单价从 100.00 改成 200.00 之后(材料费翻倍、辅料不变):
     * <pre>
     *   材料合计 4630.08 + 辅料 436.80 =  5066.88
     * </pre>
     */
    private static final BigDecimal EXPECTED_TOTAL_BEFORE = new BigDecimal("2751.84");
    private static final BigDecimal EXPECTED_MATERIAL_AFTER = new BigDecimal("4630.08");
    private static final BigDecimal EXPECTED_TOTAL_AFTER = new BigDecimal("5066.88");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlanMapper planMapper;

    @Autowired
    private PlanPartMapper planPartMapper;

    @Autowired
    private PlanImageMapper planImageMapper;

    @Autowired
    private PlanQuoteMapper planQuoteMapper;

    @Autowired
    private GenerationTaskMapper taskMapper;

    @Autowired
    private OutboxMapper outboxMapper;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private QuoteParamMapper quoteParamMapper;

    @Autowired
    private ProductService productService;

    private Map<String, BigDecimal> originalParams = Map.of();

    @BeforeAll
    void captureOriginalParams() {
        originalParams = paramsSnapshot();
    }

    @AfterAll
    void restoreOriginalParamsAndCleanUp() {
        quoteParamMapper.delete(Wrappers.<QuoteParam>lambdaQuery()
                .in(QuoteParam::getParamKey, managedParamKeys()));
        originalParams.forEach(this::setParam);
        cleanup();
    }

    @BeforeEach
    void resetState() {
        cleanup();
        seedProduct(SKU_FLOOR, "T15R-地面灰石纹");
        seedProduct(SKU_WALL, "T15R-墙面米白");
        seedParams();
    }

    // ===============================================================
    // 算对了
    // ===============================================================

    @Test
    @DisplayName("改价后重算:总额、明细、方案行三处一致,且等于**手算**的新价")
    void recalcProducesTheHandComputedTotal() throws Exception {
        long planId = generateAndSucceed(null, DEFAULT_PARTS);
        assertEquals(0, EXPECTED_TOTAL_BEFORE.compareTo(planMapper.selectById(planId).getTotalAmount()),
                "前置条件不成立:生成时应当落下 2751.84");

        setProductPrice(SKU_FLOOR, "200.00");
        setProductPrice(SKU_WALL, "200.00");

        JsonNode quote = dataOf(call(post(PLANS + "/" + planId + RECALC), USER));

        // ① 响应就是重算后的报价
        assertEquals(0, EXPECTED_TOTAL_AFTER.compareTo(quote.get("total").decimalValue()),
                "响应总额与手算值不符: " + quote);
        assertEquals(0, EXPECTED_MATERIAL_AFTER.compareTo(quote.get("materialTotal").decimalValue()));

        // ② Σ items[].materialFee 与 materialsTotal 对得上(否则详情页永远差几分钱)
        BigDecimal sum = BigDecimal.ZERO;
        for (JsonNode item : quote.get("items")) {
            sum = sum.add(item.get("materialFee").decimalValue());
        }
        assertEquals(0, quote.get("materialTotal").decimalValue().compareTo(sum),
                "分项之和与材料合计对不上");

        // ③ 三处存储同值:方案行、报价汇总、响应
        Plan plan = planMapper.selectById(planId);
        assertEquals(0, EXPECTED_TOTAL_AFTER.compareTo(plan.getTotalAmount()),
                "t_plan.total_amount 没被重算覆盖");
        List<PlanQuote> quotes = quotesOf(planId);
        assertEquals(1, quotes.size(), "重算是**覆盖**不是追加 —— 一个方案至多一行报价汇总");
        assertEquals(0, EXPECTED_TOTAL_AFTER.compareTo(quotes.get(0).getTotalAmount()));
        assertEquals(0, plan.getTotalAmount().compareTo(quotes.get(0).getTotalAmount()),
                "同一个价的两种存法对不上 —— 列表卡片读前者、详情读后者");

        // ④ 部位行的费用列也跟着更新了(明细与合计不能各说各话)
        for (PlanPart part : partsOf(planId)) {
            assertEquals(0, new BigDecimal("200.00").compareTo(part.getPricePerSqm()),
                    "部位行的单价快照没更新: " + part.getPart());
            assertNotNull(part.getMaterialFee());
        }

        // ⑤ rules_snapshot 刷新成**这次**用的参数
        Map<String, BigDecimal> rules = new LinkedHashMap<>();
        JsonNode rulesJson = objectMapper.readTree(quotes.get(0).getRulesSnapshot());
        rules.put("auxUnitPrice", rulesJson.get("auxUnitPrice").decimalValue());
        rules.put("lossRate", rulesJson.get("lossRate").decimalValue());
        assertEquals(0, new BigDecimal(AUX_UNIT_PRICE).compareTo(rules.get("auxUnitPrice")));
        assertEquals(0, new BigDecimal(LOSS_RATE).compareTo(rules.get("lossRate")));
    }

    @Test
    @DisplayName("重算不动方案本身:名字 / 户型 / 效果图 / share_token 一字不改,updated_at 抬起来")
    void recalcOnlyTouchesQuoteRelatedColumns() throws Exception {
        long planId = generateAndSucceed(null, DEFAULT_PARTS);
        String token = "t15r-share-" + System.nanoTime();
        jdbcTemplate.update("UPDATE t_plan SET share_token = ? WHERE id = ?", token, planId);

        Plan before = planMapper.selectById(planId);
        List<String> imagesBefore = descriptionsOfImages(planId);
        String layoutBefore = Arrays.toString(before.getLayoutEnc());
        LocalDateTime updatedBefore = beforedate(planId);

        call(post(PLANS + "/" + planId + RECALC), USER);

        Plan after = planMapper.selectById(planId);
        assertEquals(before.getName(), after.getName(), "重算不该动方案名");
        assertEquals(layoutBefore, Arrays.toString(after.getLayoutEnc()),
                "重算读的是户型快照,不该把它重新序列化写回去(密文会变、字节也不同)");
        assertEquals(token, after.getShareToken(), "分享令牌与报价无关");
        assertEquals(imagesBefore, descriptionsOfImages(planId), "效果图与价格无关");
        assertEquals(USER, after.getUserId());
        assertFalse(Boolean.TRUE.equals(after.getIsDeleted()));

        assertTrue(after.getUpdatedAt().isAfter(updatedBefore),
                "重算是**用户做的一次操作**,方案列表按 updated_at desc 排序,"
                        + "刚重算过的方案应当排到前面 —— 这与「标记过期不得抬时间戳」"
                        + "是相反的两件事:" + updatedBefore + " → " + after.getUpdatedAt());
    }

    // ===============================================================
    // 口径与生成一致 —— 本类最值钱的一条
    // ===============================================================

    /**
     * 「生成时算的」与「重算算的」必须是同一个口径。
     *
     * <p>做法:用**多空间**户型生成一次,一个价都不改,直接重算,断言两次总额逐分相等。
     *
     * <p>为什么挑多空间户型:一期是单空间出图,而生成与重算都要从多个空间里挑一个来取
     * 面积({@code PlanSpaces.resolve})。若两边各写一份挑选规则,或者重算重新推导面积
     * 而不是读 {@code t_plan_part.gross_area},差别只会在这个边界上冒出来 ——
     * 而两边各自的测试都可能是绿的。
     *
     * <p>顺带给出一个事实:{@code openapi-verification.md} §41.2 曾声称多空间方案会落进
     * 「保守不报价」分支,实际不会 —— {@code PlanSpaces.resolve} 只要户型里至少有一个空间
     * 就一定会返回一个。这条用例的输出就是那个事实的依据(见 §42 的订正)。
     */
    @Test
    @DisplayName("多空间户型:不改任何价直接重算,两次总额逐分相等(口径一致的可执行证明)")
    void recalcOnMultiSpaceLayoutMatchesGenerationToTheCent() throws Exception {
        long planId = generateAndSucceedMultiSpace();

        BigDecimal generated = planMapper.selectById(planId).getTotalAmount();
        assertNotNull(generated,
                "多空间户型应当照常算出报价(§41.2 声称会落进「保守不报价」分支 —— 事实不是)");
        assertEquals(0, EXPECTED_TOTAL_BEFORE.compareTo(generated),
                "多空间户型里定到的应当是 type=bathroom 那个空间(2m × 1.8m × 2.4m)");

        JsonNode quote = dataOf(call(post(PLANS + "/" + planId + RECALC), USER));

        assertEquals(0, generated.compareTo(quote.get("total").decimalValue()),
                "生成与重算算出了不同的数 —— 两条路的口径分叉了: "
                        + generated + " vs " + quote.get("total").decimalValue());
        assertEquals(0, generated.compareTo(planMapper.selectById(planId).getTotalAmount()),
                "重算后落库的总额也必须是同一个数");
    }

    // ===============================================================
    // 过期标记与重算的闭环
    // ===============================================================

    @Test
    @DisplayName("改价标过期 → 重算 → 过期位回到 false,且总额是新价的那个数")
    void recalcClearsTheExpiredFlag() throws Exception {
        long planId = generateAndSucceed(null, DEFAULT_PARTS);

        // 走**真实触发点**(ProductService.update),不用 setProductPrice ——
        // 这一步要验的正是"后台改价 → 标过期 → 重算清掉"这条闭环,
        // 绕开标记就等于把闭环的头掐掉了,后面的断言会变成恒真。
        changePriceViaService(SKU_FLOOR, "200.00");
        changePriceViaService(SKU_WALL, "200.00");
        assertTrue(Boolean.TRUE.equals(planMapper.selectById(planId).getQuoteExpired()),
                "改了价,方案应当已经被标成过期(标记部分见 PlanQuoteExpiryIntegrationTest)");

        JsonNode quote = dataOf(call(post(PLANS + "/" + planId + RECALC), USER));

        assertFalse(Boolean.TRUE.equals(planMapper.selectById(planId).getQuoteExpired()),
                "总额刚刚按当前价算过,定义上不可能是过期的 —— 不清回去,"
                        + "用户会看到一个「已过期」的提示配着一个刚算出来的新价");
        assertEquals(0, EXPECTED_TOTAL_AFTER.compareTo(quote.get("total").decimalValue()));

        // 详情页读到的也是同一个事实
        JsonNode detail = dataOf(call(get(PLANS + "/" + planId), USER));
        assertFalse(detail.get("quoteExpired").asBoolean(), "详情里的过期位没更新");
    }

    @Test
    @DisplayName("连算两次:结果相同、报价汇总仍只有一行(幂等,不是追加)")
    void recalcIsIdempotent() throws Exception {
        long planId = generateAndSucceed(null, DEFAULT_PARTS);
        setProductPrice(SKU_FLOOR, "200.00");
        setProductPrice(SKU_WALL, "200.00");

        BigDecimal first = dataOf(call(post(PLANS + "/" + planId + RECALC), USER))
                .get("total").decimalValue();
        BigDecimal second = dataOf(call(post(PLANS + "/" + planId + RECALC), USER))
                .get("total").decimalValue();

        assertEquals(0, first.compareTo(second), "两次重算算出不同的数: " + first + " vs " + second);
        assertEquals(1, quotesOf(planId).size(),
                "第二次重算没有先删旧行 —— t_plan_quote 会一张张堆起来,详情读到哪一行全看运气");
        assertEquals(2, partsOf(planId).size(),
                "部位行也不该被追加成 4 行(重算走的是先删后插)");
    }

    // ===============================================================
    // 算不出来就什么都不改
    // ===============================================================

    @Test
    @DisplayName("SKU 已从产品库删掉 → 90001,且库里的总额 / 报价 / 部位费用列一个字节都没变")
    void missingProductFailsWithoutChangingAnything() throws Exception {
        long planId = generateAndSucceed(null, DEFAULT_PARTS);

        productMapper.delete(Wrappers.<Product>lambdaQuery().eq(Product::getSkuCode, SKU_WALL));
        String fingerprintBefore = fingerprintOfQuoteState(planId);

        assertEquals(90001, codeOf(call(post(PLANS + "/" + planId + RECALC), USER)),
                "算不出来应当 90001(方案在,坏的是系统),而不是 30001");
        assertEquals(fingerprintBefore, fingerprintOfQuoteState(planId),
                "重算失败却改了库 —— 留下一个新总额配一份旧明细,比不算更糟");

        seedProduct(SKU_WALL, "T15R-墙面米白");
    }

    @Test
    @DisplayName("报价参数没配 → 90001,库不变")
    void missingQuoteParamsFailWithoutChangingAnything() throws Exception {
        long planId = generateAndSucceed(null, DEFAULT_PARTS);

        quoteParamMapper.delete(Wrappers.<QuoteParam>lambdaQuery()
                .in(QuoteParam::getParamKey, List.of("aux_unit_price", "loss_rate")));
        try {
            String fingerprintBefore = fingerprintOfQuoteState(planId);

            assertEquals(90001, codeOf(call(post(PLANS + "/" + planId + RECALC), USER)),
                    "缺参数时报价引擎返回空,重算应当以 90001 结束");
            assertEquals(fingerprintBefore, fingerprintOfQuoteState(planId));
        } finally {
            quoteParamMapper.delete(Wrappers.<QuoteParam>lambdaQuery()
                    .in(QuoteParam::getParamKey, managedParamKeys()));
            originalParams.forEach(this::setParam);
        }
    }

    @Test
    @DisplayName("户型的密文被破坏 → 90001,库不变(与详情的口径一致)")
    void brokenLayoutCipherFailsWithoutChangingAnything() throws Exception {
        long planId = generateAndSucceed(null, DEFAULT_PARTS);

        jdbcTemplate.update("UPDATE t_plan SET layout_enc = ? WHERE id = ?",
                new byte[] {9, 9, 9, 9}, planId);
        String fingerprintBefore = fingerprintOfQuoteState(planId);

        assertEquals(90001, codeOf(call(post(PLANS + "/" + planId + RECALC), USER)),
                "密文解不开走 90001 而不是 30001 —— §41.4 的沿袭:方案在,坏的是系统");
        assertEquals(fingerprintBefore, fingerprintOfQuoteState(planId));
    }

    @Test
    @DisplayName("方案没有户型快照 → 90001,库不变")
    void absentLayoutFailsWithoutChangingAnything() throws Exception {
        long planId = generateAndSucceed(null, DEFAULT_PARTS);

        jdbcTemplate.update("UPDATE t_plan SET layout_enc = NULL WHERE id = ?", planId);
        String fingerprintBefore = fingerprintOfQuoteState(planId);

        assertEquals(90001, codeOf(call(post(PLANS + "/" + planId + RECALC), USER)));
        assertEquals(fingerprintBefore, fingerprintOfQuoteState(planId));
    }

    @Test
    @DisplayName("部位行的面积未定(gross_area 为 null)→ 90001,库不变")
    void undecidedAreaFailsWithoutChangingAnything() throws Exception {
        long planId = generateAndSucceed(null, DEFAULT_PARTS);

        jdbcTemplate.update("UPDATE t_plan_part SET gross_area = NULL WHERE plan_id = ?", planId);
        String fingerprintBefore = fingerprintOfQuoteState(planId);

        assertEquals(90001, codeOf(call(post(PLANS + "/" + planId + RECALC), USER)),
                "面积未定时当 0 算会得出偏低价 —— 必须停下来");
        assertEquals(fingerprintBefore, fingerprintOfQuoteState(planId));
    }

    @Test
    @DisplayName("没有部位行 → 90001,库不变(空清单会算出 0 元)")
    void noPartsFailsWithoutChangingAnything() throws Exception {
        long planId = generateAndSucceed(null, DEFAULT_PARTS);

        planPartMapper.delete(Wrappers.<PlanPart>lambdaQuery().eq(PlanPart::getPlanId, planId));
        String fingerprintBefore = fingerprintOfQuoteState(planId);

        assertEquals(90001, codeOf(call(post(PLANS + "/" + planId + RECALC), USER)),
                "一个部位都没有时算出来的是 0 元 —— 那是最坏的一种答案");
        assertEquals(fingerprintBefore, fingerprintOfQuoteState(planId));
    }

    // ===============================================================
    // 归属与登录
    // ===============================================================

    @Test
    @DisplayName("归属:不存在 / 别人的 / 已删除 —— 一律 30001,措辞与详情一致;未登录 401")
    void ownershipAndAuth() throws Exception {
        long theirs = insertPlan(OTHER_USER, "T15R-别人的方案");
        long deleted = insertPlan(USER, "T15R-待删除");
        assertEquals(0, codeOf(call(delete(PLANS + "/" + deleted), USER)));

        for (long bad : new long[]{999_999_999L, theirs, deleted}) {
            assertEquals(30001, codeOf(call(post(PLANS + "/" + bad + RECALC), USER)),
                    "重算应当 30001: planId=" + bad);
        }
        // 三者措辞必须一致 —— 区分开来等于送出一个能枚举别人方案 id 的探针
        assertEquals(messageOf(call(post(PLANS + "/999999999" + RECALC), USER)),
                messageOf(call(post(PLANS + "/" + theirs + RECALC), USER)));
        assertEquals(messageOf(call(post(PLANS + "/999999999" + RECALC), USER)),
                messageOf(call(post(PLANS + "/" + deleted + RECALC), USER)));

        // 未登录:401 走真实状态码(项目约定:只有 401/403 不用 200)
        assertEquals(401, mvc.perform(post(PLANS + "/" + theirs + RECALC))
                .andReturn().getResponse().getStatus());
    }

    // ===============================================================
    // 工具
    // ===============================================================

    private Cookie cookieFor(long userId) {
        return new Cookie("access_token", jwtService.issueAccess(userId, SubjectType.USER));
    }

    private MvcResult call(MockHttpServletRequestBuilder request, long userId) throws Exception {
        return mvc.perform(request.cookie(cookieFor(userId))).andReturn();
    }

    private long generateAndSucceed(Long planId, String partsJson) throws Exception {
        return generateAndSucceed(planId, partsJson, singleSpaceLayout());
    }

    /** 多空间:前两个都不是 bathroom,第三个才是 —— 逼着定空间规则真的起作用。 */
    private long generateAndSucceedMultiSpace() throws Exception {
        return generateAndSucceed(null, DEFAULT_PARTS, """
                {"spaces":[
                  {"type":"living_room","name":"客厅","length":5,"width":4,"height":2.8,"wallCount":4},
                  {"type":"kitchen","name":"厨房","length":3,"width":2,"height":2.7,"wallCount":4},
                  {"type":"bathroom","name":"卫生间","length":2,"width":1.8,"height":2.4,"wallCount":4}]}
                """);
    }

    private long generateAndSucceed(Long planId, String partsJson, String layoutJson) throws Exception {
        String taskId = dataOf(call(post(GENERATE).contentType(MediaType.APPLICATION_JSON)
                .content(generateBody(planId, partsJson, layoutJson)), USER)).get("taskId").asText();

        MvcResult callback = mvc.perform(post(CALLBACK).contentType(MediaType.APPLICATION_JSON)
                        .content(successBody(taskId)).header(SERVICE_TOKEN_HEADER, SERVICE_TOKEN))
                .andReturn();
        assertEquals(0, codeOf(callback),
                "出图回调应成功: " + callback.getResponse().getContentAsString());

        GenerationTask task = taskMapper.selectOne(Wrappers.<GenerationTask>lambdaQuery()
                .eq(GenerationTask::getTaskId, taskId));
        Long saved = task.getPlanId();
        assertNotNull(saved, "回调成功却没落方案 —— 后面的断言全都无从谈起");
        return saved;
    }

    private static String singleSpaceLayout() {
        return """
                {"spaces":[{"type":"bathroom","name":"卫生间",
                  "length":2,"width":1.8,"height":2.4,"wallCount":4}]}
                """;
    }

    private String generateBody(Long planId, String partsJson, String layoutJson) {
        StringBuilder sb = new StringBuilder("{\"space\":\"bathroom\",\"style\":\"modern\"");
        if (planId != null) {
            sb.append(",\"planId\":").append(planId);
        }
        sb.append(",\"layout\":").append(layoutJson.replaceAll("\\s+", ""))
                .append(",\"parts\":").append(partsJson)
                .append('}');
        return sb.toString();
    }

    private String successBody(String taskId) {
        return """
                {"taskId":"%s","status":"success","progress":100,"imageUrls":[
                  {"url":"http://java.test/static/placeholder.svg?task=%s","view":"default",
                   "isCover":true}]}""".formatted(taskId, taskId);
    }

    private long insertPlan(long userId, String name) {
        Plan plan = new Plan();
        plan.setUserId(userId);
        plan.setName(name);
        plan.setQuoteExpired(false);
        plan.setIsDeleted(false);
        planMapper.insert(plan);
        return plan.getId();
    }

    /**
     * 报价相关状态的<b>逐列</b>指纹 —— 用来证明"失败了就一个字节都没改"。
     *
     * <p>故意<b>不</b>包含 {@code updated_at}:它不是报价状态,把它算进来只会让
     * 断言在无关的时序差异上变红。
     */
    private String fingerprintOfQuoteState(long planId) {
        Plan plan = planMapper.selectById(planId);
        List<String> rows = new ArrayList<>();
        rows.add("plan|" + plan.getTotalAmount() + "|" + plan.getQuoteExpired());
        for (PlanPart part : partsOf(planId)) {
            rows.add("part|" + part.getPart() + "|" + part.getSkuCode() + "|"
                    + part.getGrossArea() + "|" + part.getArea() + "|" + part.getLossRate()
                    + "|" + part.getMaterialFee() + "|" + part.getPricePerSqm()
                    + "|" + part.getProductName());
        }
        for (PlanQuote quote : quotesOf(planId)) {
            rows.add("quote|" + quote.getMaterialTotal() + "|" + quote.getAuxTotal()
                    + "|" + quote.getTotalAmount() + "|" + quote.getRulesSnapshot());
        }
        return String.join("\n", rows);
    }

    private List<PlanPart> partsOf(long planId) {
        return planPartMapper.selectList(Wrappers.<PlanPart>lambdaQuery()
                .eq(PlanPart::getPlanId, planId).orderByAsc(PlanPart::getId));
    }

    private List<PlanQuote> quotesOf(long planId) {
        return planQuoteMapper.selectList(Wrappers.<PlanQuote>lambdaQuery()
                .eq(PlanQuote::getPlanId, planId));
    }

    private List<String> descriptionsOfImages(long planId) {
        List<String> rows = new ArrayList<>();
        for (PlanImage image : planImageMapper.selectList(Wrappers.<PlanImage>lambdaQuery()
                .eq(PlanImage::getPlanId, planId).orderByAsc(PlanImage::getId))) {
            rows.add(String.join("|", image.getUrl(), image.getView(),
                    String.valueOf(image.getIsCover())));
        }
        return rows;
    }

    /**
     * 把 {@code updated_at} 往回拨 60 秒,并返回<b>库里实际存下的值</b>当基准。
     *
     * <p>不回拨的话,"重算把时间戳抬起来了"这条断言会变成恒真 —— {@code DATETIME}
     * 只到秒,同一秒内的先后根本看不出来。拨完顺手断言它确实退回去了,
     * 否则回拨本身失效(比如表结构改了)也不会有人知道。
     */
    private LocalDateTime beforedate(long planId) {
        jdbcTemplate.update(
                "UPDATE t_plan SET updated_at = DATE_SUB(NOW(), INTERVAL 60 SECOND) WHERE id = ?",
                planId);
        LocalDateTime backdated = planMapper.selectById(planId).getUpdatedAt();
        assertTrue(backdated.isBefore(LocalDateTime.now().minusSeconds(30)),
                "回拨没生效(仍停在 " + backdated + "),后面的断言就没意义了");
        return backdated;
    }

    private void seedProduct(String sku, String name) {
        Product product = new Product();
        product.setSkuCode(sku);
        product.setName(name);
        product.setPricePerSqm(new BigDecimal(PRICE));
        product.setStatus(Product.STATUS_OFF_SALE);
        productMapper.insert(product);
    }

    /**
     * 走 {@code ProductService.update} 改单价 —— 顺带触发「标记过期」。
     * 只验重算本身的用例不该用这个(它们要的是"价变了但过期位不变")。
     */
    private void changePriceViaService(String sku, String price) {
        Product existing = productMapper.selectOne(Wrappers.<Product>lambdaQuery()
                .eq(Product::getSkuCode, sku));
        assertNotNull(existing, "先 seed 产品:" + sku);
        productService.update(existing.getId(), new ProductUpsertRequest(
                sku, existing.getName(), null, null, null, null,
                new BigDecimal(price), null, null, null, null, null));
    }

    /** 直接改单价 —— 走 mapper 而不是 {@code ProductService.update},免得顺带触发标记。 */
    private void setProductPrice(String sku, String price) {
        Product product = productMapper.selectOne(Wrappers.<Product>lambdaQuery()
                .eq(Product::getSkuCode, sku));
        assertNotNull(product, "先 seed 产品:" + sku);
        product.setPricePerSqm(new BigDecimal(price));
        productMapper.updateById(product);
    }

    private void seedParams() {
        setParam("aux_unit_price", AUX_UNIT_PRICE);
        setParam("loss_rate", LOSS_RATE);
        setParam("loss_rate_min", "0.05");
        setParam("loss_rate_max", "0.08");
    }

    private static List<String> managedParamKeys() {
        return List.of("aux_unit_price", "loss_rate", "loss_rate_min", "loss_rate_max");
    }

    private void setParam(String key, String value) {
        setParam(key, new BigDecimal(value));
    }

    private void setParam(String key, BigDecimal value) {
        quoteParamMapper.delete(Wrappers.<QuoteParam>lambdaQuery().eq(QuoteParam::getParamKey, key));
        QuoteParam param = new QuoteParam();
        param.setParamKey(key);
        param.setParamValue(value);
        quoteParamMapper.insert(param);
    }

    private Map<String, BigDecimal> paramsSnapshot() {
        Map<String, BigDecimal> snapshot = new LinkedHashMap<>();
        for (QuoteParam param : quoteParamMapper.selectList(Wrappers.<QuoteParam>lambdaQuery())) {
            snapshot.put(param.getParamKey(), param.getParamValue());
        }
        return snapshot;
    }

    private JsonNode dataOf(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString();
        JsonNode root = objectMapper.readTree(body);
        assertEquals(0, root.path("code").asInt(), "应当成功: " + body);
        return root.get("data");
    }

    private static int codeOf(MvcResult result) throws Exception {
        return new ObjectMapper().readTree(result.getResponse().getContentAsString())
                .path("code").asInt();
    }

    private static String messageOf(MvcResult result) throws Exception {
        return new ObjectMapper().readTree(result.getResponse().getContentAsString())
                .path("message").asText();
    }

    private void cleanup() {
        for (long userId : new long[]{USER, OTHER_USER}) {
            for (GenerationTask task : taskMapper.selectList(Wrappers.<GenerationTask>lambdaQuery()
                    .eq(GenerationTask::getUserId, userId))) {
                outboxMapper.delete(Wrappers.<OutboxMessage>lambdaQuery()
                        .eq(OutboxMessage::getAggregateId, task.getTaskId()));
                taskMapper.deleteById(task.getId());
            }
            for (Plan plan : planMapper.selectList(Wrappers.<Plan>lambdaQuery()
                    .eq(Plan::getUserId, userId))) {
                long planId = plan.getId();
                planPartMapper.delete(Wrappers.<PlanPart>lambdaQuery()
                        .eq(PlanPart::getPlanId, planId));
                planImageMapper.delete(Wrappers.<PlanImage>lambdaQuery()
                        .eq(PlanImage::getPlanId, planId));
                planQuoteMapper.delete(Wrappers.<PlanQuote>lambdaQuery()
                        .eq(PlanQuote::getPlanId, planId));
                planMapper.deleteById(planId);
            }
        }
        productMapper.delete(Wrappers.<Product>lambdaQuery()
                .likeRight(Product::getSkuCode, SKU_PREFIX));
    }
}
