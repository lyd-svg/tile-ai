package com.tileai.plan;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tileai.admin.entity.AdminUser;
import com.tileai.admin.mapper.AdminUserMapper;
import com.tileai.common.security.JwtService;
import com.tileai.common.security.SubjectType;
import com.tileai.plan.entity.Plan;
import com.tileai.plan.entity.PlanPart;
import com.tileai.plan.mapper.PlanMapper;
import com.tileai.plan.mapper.PlanPartMapper;
import com.tileai.product.ProductService;
import com.tileai.product.dto.ProductUpsertRequest;
import com.tileai.product.entity.Product;
import com.tileai.product.imports.ImportBatch;
import com.tileai.product.imports.ImportColumn;
import com.tileai.product.mapper.ImportBatchMapper;
import com.tileai.product.mapper.ProductMapper;
import com.tileai.quote.QuoteParamService;
import com.tileai.quote.dto.QuoteParamUpsertRequest;
import com.tileai.quote.entity.QuoteParam;
import com.tileai.quote.mapper.QuoteParamMapper;

import jakarta.servlet.http.Cookie;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

/**
 * T-1.15 验收:改价那一刻把受影响的方案标成「报价已过期」。
 *
 * <p><b>需要 MySQL</b>({@code -Ddb.verify=true} 显式启用)。
 *
 * <h2>本类最重要的一条:标记不得抬 {@code updated_at}</h2>
 *
 * <p>{@code t_plan.updated_at} 带 {@code ON UPDATE CURRENT_TIMESTAMP},而「我的方案」
 * 列表按 {@code updated_at desc} 排序。标记过期确实改了那一行,MySQL 就会把时间戳抬到
 * 现在 —— 于是<b>后台改一次全店参数,所有用户的方案列表顺序会被整体打乱</b>,
 * 而用户什么都没做。这是本轮最隐蔽的一条:值确实变了、SQL 也确实写对了,
 * 只有本文件会红。
 *
 * <p>所以下面每条正向用例都同时断言 {@code updated_at} <b>逐值不变</b> ——
 * 而且先把时间戳往回拨 60 秒。不回拨的话"没变"这件事根本看不出来
 * (本来就差不多是现在),断言会变成恒真。
 *
 * <h2>为什么不用走一遍生成链路</h2>
 *
 * <p>标记只读 {@code t_plan}({@code is_deleted} / {@code quote_expired} /
 * {@code total_amount})与 {@code t_plan_part.sku_code} —— 户型、效果图、
 * {@code t_plan_quote} 一概用不到。直接造行能让每条用例只表达一件事,
 * 也让"哪些行**不该**被标"的边界(已删除 / 没有总额 / 已经过期)写得更利落。
 * 生成 → 标记 → 重算的完整闭环在 {@code PlanRecalcIntegrationTest} 里。
 *
 * <h2>只对自己造的数据下结论</h2>
 *
 * <p>用户号段 {@code 8_815_0001},SKU 前缀 {@code T15E-}。清理只按这两个维度删,
 * 不对全表条数做任何断言。报价参数是全局表,进出各拍一次快照原样还原。
 */
@EnabledIfSystemProperty(named = "db.verify", matches = "true",
        disabledReason = "需要 MySQL;用 -Ddb.verify=true 显式启用(T-1.15 验证)")
@SpringBootTest(properties = {
        "spring.flyway.enabled=false",
        "tile.auth.secret=0123456789abcdef0123456789abcdef",
        "tile.crypto.layout-key=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=",
        // 定时器一律关掉:本类验的是标记,不是投递与补偿
        "tile.generate.compensation-enabled=false",
        "tile.outbox.enabled=false"})
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PlanQuoteExpiryIntegrationTest {

    private static final long USER = 8_815_0001L;

    private static final String SKU_PREFIX = "T15E-";
    private static final String SKU_A = SKU_PREFIX + "A";
    private static final String SKU_B = SKU_PREFIX + "B";

    private static final String PRICE = "100.00";
    private static final String AUX_UNIT_PRICE = "20.00";
    private static final String LOSS_RATE = "0.06";

    private static final String IMPORT_BASE = "/api/v1/admin/products/import";
    private static final long IMPORT_TIMEOUT_MS = 15_000;

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
    private ProductMapper productMapper;

    @Autowired
    private QuoteParamMapper quoteParamMapper;

    @Autowired
    private ImportBatchMapper importBatchMapper;

    @Autowired
    private AdminUserMapper adminUserMapper;

    @Autowired
    private ProductService productService;

    @Autowired
    private QuoteParamService quoteParamService;

    @Autowired
    private PlanQuoteExpiryMarker marker;

    private Map<String, BigDecimal> originalParams = Map.of();
    private long adminId;
    private String adminToken;
    private final List<String> createdBatchIds = new ArrayList<>();

    @BeforeAll
    void captureGlobalState() {
        originalParams = paramsSnapshot();

        Integer existing = adminUserMapper.selectCount(Wrappers.<AdminUser>lambdaQuery()
                .eq(AdminUser::getUsername, "t15e_admin")).intValue();
        assertEquals(0, existing, "上一次运行残留了 t15e_admin,先手工清掉再跑");
        AdminUser admin = new AdminUser();
        admin.setUsername("t15e_admin");
        admin.setPasswordHash("$2a$10$placeholder-not-used-because-no-login-here");
        admin.setRole("super_admin");
        admin.setStatus(AdminUser.STATUS_ENABLED);
        adminUserMapper.insert(admin);
        adminId = admin.getId();
        adminToken = jwtService.issueAccess(adminId, SubjectType.ADMIN);
    }

    @AfterAll
    void restoreGlobalStateAndCleanUp() {
        quoteParamMapper.delete(Wrappers.<QuoteParam>lambdaQuery()
                .in(QuoteParam::getParamKey, List.of(
                        "aux_unit_price", "loss_rate", "loss_rate_min", "loss_rate_max")));
        originalParams.forEach(this::setParam);

        if (!createdBatchIds.isEmpty()) {
            importBatchMapper.delete(Wrappers.<ImportBatch>lambdaQuery()
                    .in(ImportBatch::getBatchId, createdBatchIds));
        }
        cleanup();
        adminUserMapper.deleteById(adminId);
    }

    @BeforeEach
    void resetState() {
        cleanup();
        seedProduct(SKU_A, "T15E-地面灰石纹", PRICE);
        seedProduct(SKU_B, "T15E-墙面米白", PRICE);
        setParam("aux_unit_price", AUX_UNIT_PRICE);
        setParam("loss_rate", LOSS_RATE);
        setParam("loss_rate_min", "0.05");
        setParam("loss_rate_max", "0.08");
    }

    // ===============================================================
    // 触发点一:后台改单价
    // ===============================================================

    @Test
    @DisplayName("改价 → 引用它的在用车方案标过期;总额不变、updated_at 一秒都不许动")
    void priceChangeMarksReferencingPlansWithoutTouchingUpdatedAt() {
        long mine = insertPlan(USER, "T15E-用 A 砖", "500.00", SKU_A, false, false);
        long other = insertPlan(USER, "T15E-用 B 砖", "800.00", SKU_B, false, false);
        LocalDateTime mineBefore = backdate(mine);
        LocalDateTime otherBefore = backdate(other);

        editPrice(SKU_A, "150.00");

        Plan after = planMapper.selectById(mine);
        assertTrue(Boolean.TRUE.equals(after.getQuoteExpired()),
                "改了 A 砖的价格,引用它的方案必须标成「报价已过期」");
        assertEquals(0, new BigDecimal("500.00").compareTo(after.getTotalAmount()),
                "标记**只动过期位** —— 总额不改。旧价的那个数原样留着,重算时才会被覆盖");
        assertEquals(mineBefore, after.getUpdatedAt(),
                "updated_at 被抬了 —— 方案列表按它倒序排,"
                        + "后台改一次价就会把所有用户的列表整体重排:" + mineBefore + " → " + after.getUpdatedAt());

        Plan untouched = planMapper.selectById(other);
        assertFalse(Boolean.TRUE.equals(untouched.getQuoteExpired()),
                "没动 B 砖的价,用 B 砖的方案不该被牵连");
        assertEquals(otherBefore, untouched.getUpdatedAt());
    }

    @Test
    @DisplayName("只改名字 / 图片,或把同一个价再存一遍 → 不标过期")
    void nonPriceEditDoesNotMark() {
        long planId = insertPlan(USER, "T15E-只改名", "500.00", SKU_A, false, false);
        LocalDateTime before = backdate(planId);

        editPrice(SKU_A, PRICE, "T15E-换了个名字");            // 价没变,名字变了
        assertFalse(Boolean.TRUE.equals(planMapper.selectById(planId).getQuoteExpired()),
                "只改名字影响不到金额,标成过期是在制造噪音");

        // 同一个数的另一种写法:BigDecimal.equals 会判为不等(100.00 vs 100.0),
        // 只有 compareTo 才认得出它们是一个价 —— 用错就会"每次编辑都标过期"
        editPrice(SKU_A, "100.0");
        assertFalse(Boolean.TRUE.equals(planMapper.selectById(planId).getQuoteExpired()),
                "100.00 与 100.0 是同一个价 —— 用 equals 比较 HERE 会误标");

        assertEquals(before, planMapper.selectById(planId).getUpdatedAt(),
                "两次都没改价,时间戳不该动");
    }

    @Test
    @DisplayName("新增产品 → 不影响任何已有方案(还没有方案可能引用它)")
    void creatingProductMarksNothing() {
        long planId = insertPlan(USER, "T15E-新增砖不影响我", "500.00", SKU_A, false, false);

        productService.create(new ProductUpsertRequest(SKU_PREFIX + "NEW", "T15E-新砖",
                null, null, null, null, new BigDecimal("999.00"), null, null, null, null, null));

        assertFalse(Boolean.TRUE.equals(planMapper.selectById(planId).getQuoteExpired()));
    }

    // ===============================================================
    // 边界:哪些方案**不该**被标记
    // ===============================================================

    @Test
    @DisplayName("已删除 / 没有总额 / 已经过期的方案:一个都不再动")
    void ineligiblePlansAreNotTouched() {
        long deleted = insertPlan(USER, "T15E-已删除", "500.00", SKU_A, false, true);
        long noQuote = insertPlan(USER, "T15E-没算过价", null, SKU_A, false, false);
        long already = insertPlan(USER, "T15E-早就过期了", "500.00", SKU_A, true, false);

        LocalDateTime deletedBefore = backdate(deleted);
        LocalDateTime noQuoteBefore = backdate(noQuote);
        LocalDateTime alreadyBefore = backdate(already);

        editPrice(SKU_A, "150.00");

        assertFalse(Boolean.TRUE.equals(planMapper.selectById(deleted).getQuoteExpired()),
                "逻辑删除的方案不该被标 —— 用户已经看不到它了");
        assertEquals(deletedBefore, planMapper.selectById(deleted).getUpdatedAt(),
                "已删除的方案连行都不该被碰到");

        assertNull(planMapper.selectById(noQuote).getTotalAmount());
        assertFalse(Boolean.TRUE.equals(planMapper.selectById(noQuote).getQuoteExpired()),
                "从没算出过金额的方案谈不上「报价过期」——"
                        + "标了会让用户在详情页看到一个「过期却没有金额」的矛盾状态");
        assertEquals(noQuoteBefore, planMapper.selectById(noQuote).getUpdatedAt());

        assertTrue(Boolean.TRUE.equals(planMapper.selectById(already).getQuoteExpired()));
        assertEquals(alreadyBefore, planMapper.selectById(already).getUpdatedAt(),
                "已经标过的方案不该被再写一次 —— WHERE 里的 quote_expired = 0 保证幂等");
    }

    @Test
    @DisplayName("重复改价:第二次返回 0 行,不再产生任何写入(幂等)")
    void markingIsIdempotent() {
        long planId = insertPlan(USER, "T15E-幂等", "500.00", SKU_A, false, false);

        assertEquals(1, marker.bySkuCode(SKU_A), "第一次应当标到 1 条");
        assertEquals(0, marker.bySkuCode(SKU_A),
                "第二次应当 0 条 —— 条件里带 quote_expired = 0,"
                        + "否则全局调价每条方案会被反复重写");

        LocalDateTime before = backdate(planId);
        editPrice(SKU_A, "150.00");
        assertEquals(before, planMapper.selectById(planId).getUpdatedAt(),
                "改价触发的标记同样不该抬时间戳");
    }

    // ===============================================================
    // 触发点二:批量导入改价
    // ===============================================================

    @Test
    @DisplayName("批量导入把价改了 → 同样标过期(且是在该行的 REQUIRES_NEW 事务里)")
    void importWithChangedPriceMarks() throws Exception {
        long planId = insertPlan(USER, "T15E-导入改价", "500.00", SKU_A, false, false);
        LocalDateTime before = backdate(planId);

        runImport(csv(row(SKU_A, "T15E-地面灰石纹", "150.00")), "upsert");

        assertTrue(Boolean.TRUE.equals(planMapper.selectById(planId).getQuoteExpired()),
                "导入也会改价 —— 不在这里标,运营批量调完价用户看到的还是旧数");
        assertEquals(before, planMapper.selectById(planId).getUpdatedAt(),
                "导入路径同样不该抬时间戳");
    }

    @Test
    @DisplayName("批量导入价没变(只改名字)→ 不标过期")
    void importWithSamePriceDoesNotMark() throws Exception {
        long planId = insertPlan(USER, "T15E-导入只改名", "500.00", SKU_A, false, false);

        runImport(csv(row(SKU_A, "T15E-改了个名字", PRICE)), "upsert");

        assertEquals("T15E-改了个名字", findProduct(SKU_A).getName(), "名字应当被覆盖成新的");
        assertFalse(Boolean.TRUE.equals(planMapper.selectById(planId).getQuoteExpired()),
                "价没变就不该标 —— 否则每次导入全库都会把所有人的方案标一遍");
    }

    // ===============================================================
    // 触发点三:全局报价参数
    // ===============================================================

    @Test
    @DisplayName("改辅料单价 → **所有**在用方案标过期(它乘在每一个金额上)")
    void auxUnitPriceChangeMarksEveryPlan() {
        long a = insertPlan(USER, "T15E-用 A 砖", "500.00", SKU_A, false, false);
        long b = insertPlan(USER, "T15E-用 B 砖", "800.00", SKU_B, false, false);
        LocalDateTime aBefore = backdate(a);
        LocalDateTime bBefore = backdate(b);

        quoteParamService.upsert(new QuoteParamUpsertRequest("aux_unit_price",
                new BigDecimal("25.00"), null));

        for (long id : new long[]{a, b}) {
            assertTrue(Boolean.TRUE.equals(planMapper.selectById(id).getQuoteExpired()),
                    "辅料单价乘在每一个金额上 —— 全店方案都会过期,方案 " + id);
        }
        assertEquals(aBefore, planMapper.selectById(a).getUpdatedAt(),
                "这一次命的行最多,把时间戳抬起来就是把所有用户的列表顺序一起打乱");
        assertEquals(bBefore, planMapper.selectById(b).getUpdatedAt());
    }

    @Test
    @DisplayName("改损耗率 → 同样全标;改 loss_rate_min(允许范围,不参与计算)→ 一条都不标")
    void lossRateMarksButItsRangeDoesNot() {
        long planId = insertPlan(USER, "T15E-参数边界", "500.00", SKU_A, false, false);

        // 允许范围只是校验与提示用的。把它当"参与计算"会白标全表 ——
        // 将来新增参数键时,affectsQuote() 是唯一要重新判断的地方。
        quoteParamService.upsert(new QuoteParamUpsertRequest("loss_rate_min",
                new BigDecimal("0.04"), null));
        assertFalse(Boolean.TRUE.equals(planMapper.selectById(planId).getQuoteExpired()),
                "loss_rate_min 不参与报价计算 —— 改它不该让任何方案过期");

        quoteParamService.upsert(new QuoteParamUpsertRequest("loss_rate",
                new BigDecimal("0.08"), null));
        assertTrue(Boolean.TRUE.equals(planMapper.selectById(planId).getQuoteExpired()),
                "loss_rate 是**唯一参与计算**的损耗率,改它必须全标");
    }

    @Test
    @DisplayName("把同一个参数值再存一遍 / 只改备注 → 不标过期")
    void resavingSameParamValueDoesNotMark() {
        long planId = insertPlan(USER, "T15E-参数幂等", "500.00", SKU_A, false, false);

        quoteParamService.upsert(new QuoteParamUpsertRequest("aux_unit_price",
                new BigDecimal(AUX_UNIT_PRICE), "T15E-只是补个备注"));

        assertFalse(Boolean.TRUE.equals(planMapper.selectById(planId).getQuoteExpired()),
                "值没变就不算调价 —— 运营补一句备注不该让全店方案集体报「已过期」");
    }

    // ===============================================================
    // 工具
    // ===============================================================

    /** 直接造一行「有报价」的方案 + 一条部位行。标记只看这两张表的几个列。 */
    private long insertPlan(long userId, String name, String totalAmount, String sku,
                            boolean quoteExpired, boolean deleted) {
        Plan plan = new Plan();
        plan.setUserId(userId);
        plan.setName(name);
        plan.setSpace("bathroom");
        plan.setTotalAmount(totalAmount == null ? null : new BigDecimal(totalAmount));
        plan.setQuoteExpired(quoteExpired);
        plan.setIsDeleted(deleted);
        planMapper.insert(plan);

        PlanPart part = new PlanPart();
        part.setPlanId(plan.getId());
        part.setPart("floor");
        part.setSkuCode(sku);
        part.setSource("user");
        part.setGrossArea(new BigDecimal("3.6000"));
        part.setArea(new BigDecimal("3.8160"));
        part.setPricePerSqm(new BigDecimal(PRICE));
        part.setLossRate(new BigDecimal(LOSS_RATE));
        part.setMaterialFee(new BigDecimal("381.60"));
        planPartMapper.insert(part);
        return plan.getId();
    }

    /**
     * 把 {@code updated_at} 往回拨 60 秒,并<b>读回库里实际存下的值</b>当基准。
     *
     * <p>不回拨的话,"时间戳没变"这条断言恒真 —— {@code DATETIME} 只到秒,
     * 同一秒内做的改动本来就看不出来。
     *
     * <p>基准必须来自库:Java 侧的 {@code LocalDateTime} 带纳秒,写进
     * {@code DATETIME} 会被截断,与读回来的值不相等。
     */
    private LocalDateTime backdate(long planId) {
        jdbcTemplate.update(
                "UPDATE t_plan SET updated_at = DATE_SUB(NOW(), INTERVAL 60 SECOND) WHERE id = ?",
                planId);
        LocalDateTime backdated = planMapper.selectById(planId).getUpdatedAt();
        assertTrue(backdated.isBefore(LocalDateTime.now().minusSeconds(30)),
                "回拨没生效(仍停在 " + backdated + "),后面的断言会全部变成恒真");
        return backdated;
    }

    private void editPrice(String sku, String newPrice) {
        editPrice(sku, newPrice, null);
    }

    /** 走 {@code ProductService.update} —— 改价触发点就在它里面。 */
    private void editPrice(String sku, String newPrice, String newName) {
        Product existing = findProduct(sku);
        assertNotNull(existing, "先 seed 产品:" + sku);
        productService.update(existing.getId(), new ProductUpsertRequest(
                sku,
                newName == null ? existing.getName() : newName,
                null, null, null, null,
                new BigDecimal(newPrice),
                null, null, null, null, null));
    }

    private JsonNode runImport(byte[] content, String mode) throws Exception {
        MvcResult accepted = mvc.perform(multipart(IMPORT_BASE)
                        .file(new MockMultipartFile("file", "products.csv", "text/csv", content))
                        .param("mode", mode)
                        .cookie(new Cookie("admin_access_token", adminToken)))
                .andReturn();
        assertEquals(200, accepted.getResponse().getStatus(),
                accepted.getResponse().getContentAsString());

        String batchId = objectMapper.readTree(accepted.getResponse().getContentAsString())
                .get("data").get("batchId").asText();
        createdBatchIds.add(batchId);
        return awaitDone(batchId);
    }

    /** 导入是异步落库的;不轮询到结束,后面的断言可能跑在写入之前。 */
    private JsonNode awaitDone(String batchId) throws Exception {
        long deadline = System.currentTimeMillis() + IMPORT_TIMEOUT_MS;
        JsonNode last = null;
        while (System.currentTimeMillis() < deadline) {
            MvcResult result = mvc.perform(get(IMPORT_BASE + "/" + batchId)
                    .cookie(new Cookie("admin_access_token", adminToken))).andReturn();
            last = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
            String status = last.get("status").asText();
            if (!"pending".equals(status) && !"running".equals(status)) {
                assertEquals("success", status, "批次应以 success 结束: " + last);
                assertEquals(0, last.get("failedCount").asInt(), "不该有失败行: " + last);
                return last;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("导入未在 " + IMPORT_TIMEOUT_MS + "ms 内完成,最后状态: " + last);
    }

    private byte[] csv(String[]... dataRows) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.join(",", ImportColumn.headers())).append("\r\n");
        for (String[] row : dataRows) {
            sb.append(String.join(",", row)).append("\r\n");
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String[] row(String sku, String name, String price) {
        Map<ImportColumn, String> values = new LinkedHashMap<>();
        values.put(ImportColumn.SKU, sku);
        values.put(ImportColumn.NAME, name);
        values.put(ImportColumn.PRICE_PER_SQM, price);
        return Arrays.stream(ImportColumn.values())
                .map(column -> values.getOrDefault(column, ""))
                .toArray(String[]::new);
    }

    private Product findProduct(String sku) {
        return productMapper.selectOne(Wrappers.<Product>lambdaQuery()
                .eq(Product::getSkuCode, sku));
    }

    private void seedProduct(String sku, String name, String price) {
        Product product = new Product();
        product.setSkuCode(sku);
        product.setName(name);
        product.setPricePerSqm(new BigDecimal(price));
        product.setStatus(Product.STATUS_OFF_SALE);
        productMapper.insert(product);
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

    /** 只删本类号段的数据 —— 不对全表条数下结论。 */
    private void cleanup() {
        for (Plan plan : planMapper.selectList(Wrappers.<Plan>lambdaQuery()
                .eq(Plan::getUserId, USER))) {
            planPartMapper.delete(Wrappers.<PlanPart>lambdaQuery()
                    .eq(PlanPart::getPlanId, plan.getId()));
            planMapper.deleteById(plan.getId());
        }
        productMapper.delete(Wrappers.<Product>lambdaQuery()
                .likeRight(Product::getSkuCode, SKU_PREFIX));
    }
}
