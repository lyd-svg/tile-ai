package com.tileai.plan;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

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
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tileai.common.error.BusinessException;
import com.tileai.common.error.ErrorCode;
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
import com.tileai.product.entity.Product;
import com.tileai.product.mapper.ProductMapper;
import com.tileai.quote.entity.QuoteParam;
import com.tileai.quote.mapper.QuoteParamMapper;

import jakarta.servlet.http.Cookie;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * T-1.13 验收:分享三条接口
 * (契约 {@code openapi-c.yaml} 的 share 分组;PRD §5.5、§10.2;架构 §13.5)。
 *
 * <p><b>需要 MySQL</b>({@code -Ddb.verify=true} 显式启用)。<b>不需要 Redis、
 * 不需要 RabbitMQ</b> —— 登录态由 {@link JwtService} 直接签一个 access 令牌充当。
 *
 * <h2>本类守着的东西,按重要性排</h2>
 *
 * <ol>
 *   <li><b>空令牌不能命中别人的方案。</b>查询条件是 {@code share_token = ?},
 *       而 MyBatis-Plus 的条件拼接在值为 null 时可能整个消失 —— 那样查询会退化成
 *       "取第一条未删除的方案",<b>任何人凭一个空令牌就能看到别人的方案,且不报错</b>。
 *       实现里有显式的空白检查({@link PlanShareService#readShared}),这里钉住它。</li>
 *   <li><b>废除之后链接当场失效</b>,不是"过一会儿失效" —— 同进程内废除完立刻访问,
 *       不做任何等待。</li>
 *   <li><b>公开页真的不需要登录,写接口真的需要</b> —— 这一条同时守着
 *       {@code SecurityConfig} 里 {@code /api/v1/share/**} 没被误登记进受保护清单。</li>
 *   <li><b>分享页与本人详情逐字段一致</b> —— 两边走的是同一个组装方法
 *       ({@link PlanDetailAssembler#assemble})。</li>
 *   <li><b>重复点「生成分享」不会让已发出的链接失效</b> —— 包括双击造成的并发。</li>
 * </ol>
 *
 * <h2>只对自己造的数据下结论</h2>
 *
 * <p>用户 id 用 {@code 8_813_00xx} 这一段固定大号,SKU 与产品名一律带 {@code T13-}
 * 前缀,清理只按这两个维度删 —— 不对全表条数做任何断言。
 */
@EnabledIfSystemProperty(named = "db.verify", matches = "true",
        disabledReason = "需要 MySQL;用 -Ddb.verify=true 显式启用(T-1.13 验证)")
@SpringBootTest(properties = {
        "spring.flyway.enabled=false",
        "tile.auth.secret=0123456789abcdef0123456789abcdef",
        // 32 字节(0x00…0x1f)的 Base64。测试自己给密钥,不依赖开发机上的 LAYOUT_ENC_KEY
        "tile.crypto.layout-key=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=",
        "tile.agent.service-token=t13-test-token",
        "tile.generate.compensation-enabled=false",
        // 关掉"受理前探测消费端":本机跑测试没有 Python 消费端,consumerCount 恒为 0,
        // 而本类要造方案 ⇒ 会走 POST /generate ⇒ 不关会整批 40002。
        "tile.generate.require-consumer=false",
        "tile.outbox.enabled=false",
        // 末尾**故意带一个斜杠** —— 顺带钉住"拼接时会把尾斜杠去掉",
        // 否则会拼出 https://share.test//share/xxx 这种看着像错的链接
        "tile.share.base-url=https://share.test/"})
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PlanShareIntegrationTest {

    private static final String PLANS = "/api/v1/plans";
    private static final String SHARE = "/api/v1/share";
    private static final String GENERATE = "/api/v1/generate";
    private static final String CALLBACK = "/internal/generate/callback";
    private static final String SERVICE_TOKEN_HEADER = "X-Service-Token";
    private static final String SERVICE_TOKEN = "t13-test-token";

    private static final String BASE_URL = "https://share.test";
    private static final String SHARE_PREFIX = BASE_URL + "/share/";

    private static final long USER = 8_813_0001L;
    private static final long OTHER_USER = 8_813_0002L;

    private static final String SKU_PREFIX = "T13-";
    private static final String SKU_FLOOR = SKU_PREFIX + "FLOOR";
    private static final String SKU_WALL = SKU_PREFIX + "WALL";

    private static final String DEFAULT_PARTS =
            "[{\"part\":\"floor\",\"sku\":\"" + SKU_FLOOR + "\"},"
                    + "{\"part\":\"wall\",\"sku\":\"" + SKU_WALL + "\"}]";

    /** 令牌的字母表:URL-safe Base64,**不含** {@code +} {@code /} {@code =}。 */
    private static final Pattern TOKEN_ALPHABET = Pattern.compile("^[A-Za-z0-9_-]+$");

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
    private PlanShareService shareService;

    /** 进本类之前 {@code t_quote_param} 里原本有什么 —— 跑完原样还回去。 */
    private Map<String, BigDecimal> originalParams = Map.of();

    /** 本类多数用例共用的那一份方案(每个用例重新生成,避免用例之间互相影响)。 */
    private long planId;

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
    void resetState() throws Exception {
        cleanup();
        seedProduct(SKU_FLOOR, "T13-地面灰石纹");
        seedProduct(SKU_WALL, "T13-墙面米白");
        seedParams();
        planId = generateAndSucceed(DEFAULT_PARTS);
    }

    // ===============================================================
    // 基本闭环
    // ===============================================================

    @Test
    @DisplayName("生成分享:返回令牌与完整链接,链接指向前端页面且不带双斜杠")
    void shareReturnsTokenAndUrl() throws Exception {
        JsonNode data = dataOf(call(post(PLANS + "/" + planId + "/share"), USER));

        String token = data.get("shareToken").asText();
        assertFalse(token.isBlank(), "令牌不能为空");
        assertEquals(SHARE_PREFIX + token, data.get("shareUrl").asText(),
                "链接应当是 配置的前端地址 + /share/ + 令牌");
        assertFalse(data.has("qrCodeUrl"),
                "二维码由前端画,后端不经手 —— 这个字段应当整键不出现: " + data);

        // 库里真的写进去了,不是只回了个字符串
        assertEquals(token, planMapper.selectById(planId).getShareToken());
    }

    /**
     * 分享页发的与本人详情接口发的<b>逐字段一致</b>。
     *
     * <p>两边是同一个 schema({@code PlanDetail}),走的是同一个组装方法。这里直接
     * 比整份 JSON —— 一旦有人在其中一条路上多加/少加一个字段,或者报价、部位、
     * 户型快照的处理分了叉,这条会红。而"两条路各自的字段测试"是抓不到分叉的。
     */
    @Test
    @DisplayName("分享页与本人详情**整份 JSON 相同** —— 同一个 schema,同一个组装方法")
    void sharedPageIsIdenticalToOwnerDetail() throws Exception {
        String token = shareOf(planId);

        JsonNode mine = dataOf(call(get(PLANS + "/" + planId), USER));
        JsonNode shared = dataOf(readShared(token));

        assertEquals(mine.toString(), shared.toString(),
                "分享页与详情页发的东西必须一字不差 —— 两条路各抄一份组装逻辑,"
                        + "分叉了也不会有测试红(所以才有 PlanDetailAssembler)");
    }

    @Test
    @DisplayName("分享页:户型是明文;报价与过期标记照常出现(T-8)")
    void sharedPageCarriesPlaintextLayoutAndQuote() throws Exception {
        String token = shareOf(planId);

        MvcResult result = readShared(token);
        String body = result.getResponse().getContentAsString();
        JsonNode data = dataOf(result);

        assertFalse(body.contains("layout_enc"), "响应里出现了密文列名: " + body);
        assertFalse(body.contains("layoutEnc"), "响应里出现了密文列名: " + body);
        assertEquals("bathroom",
                data.get("layout").get("spaces").get(0).get("type").asText(),
                "分享页看到的是解密后的明文户型");

        // 分享页不是另造的"瘦 schema":报价与过期标记都在
        assertTrue(data.has("quote"), "分享页应当带上完整报价: " + data);
        assertTrue(data.has("totalAmount"));
        assertTrue(data.has("quoteExpired"));
        assertFalse(data.get("images").isEmpty(), "分享页应当带上效果图");
    }

    // ===============================================================
    // 令牌本身
    // ===============================================================

    @Test
    @DisplayName("令牌 ≥128 bit、URL-safe 字母表、放得进 VARCHAR(64)")
    void tokenIsStrongAndUrlSafe() throws Exception {
        String token = shareOf(planId);

        assertTrue(TOKEN_ALPHABET.matcher(token).matches(),
                "令牌只能用 URL-safe Base64 字母表 —— 它要进 URL 路径,"
                        + "标准 Base64 的 + / = 会被转义甚至截断: " + token);
        assertTrue(token.length() <= 64,
                "令牌要放得进 t_plan.share_token 的 VARCHAR(64),实际 " + token.length() + " 字符");

        byte[] raw;
        try {
            raw = Base64.getUrlDecoder().decode(token);
        } catch (IllegalArgumentException e) {
            throw new AssertionError("令牌不是合法的 URL-safe Base64: " + token, e);
        }
        assertTrue(raw.length * 8 >= 128,
                "架构 §13.5 要求令牌 ≥128 bit。实际 " + (raw.length * 8) + " bit(" + raw.length + " 字节)"
                        + " —— 这一条也是不用 UUID v4 的原因:它只有 122 个随机位");
    }

    @Test
    @DisplayName("两个方案各自生成,令牌互不相同")
    void twoPlansGetDifferentTokens() throws Exception {
        long other = generateAndSucceed(DEFAULT_PARTS);

        String first = shareOf(planId);
        String second = shareOf(other);

        assertNotEquals(first, second,
                "每个方案各自一个令牌 —— 相同意味着生成逻辑退化了(比如取了固定值)");
    }

    /**
     * 重复点「生成分享」:拿回<b>同一个</b>令牌,而且**旧链接仍然能打开**。
     *
     * <p>这条钉的是一个很容易写错、后果又很难解释的行为:如果第二次点击重新生成一个
     * 新令牌,那么家人手里那条已经发出去的链接会<b>当场作废</b> —— 而用户完全不知道
     * 自己做错了什么。
     */
    @Test
    @DisplayName("重复生成:返回同一个令牌,且已发出的旧链接仍然可用")
    void sharingAgainKeepsTheSameToken() throws Exception {
        String first = shareOf(planId);
        String second = shareOf(planId);

        assertEquals(first, second, "重复生成不该换一个令牌 —— 已发出的链接会当场作废");
        assertEquals(0, codeOf(readShared(first)), "旧链接必须仍然能打开");
    }

    /**
     * <b>并发</b>生成:两个请求同时进来,只能有一个令牌被写进去,且两次响应给出同一个。
     *
     * <p>这才是真正压到 {@code isNull} 那个条件语句的用例 —— 顺序调用两次的话,
     * 第二次在 {@code requireOwned} 那一步就看到令牌了,根本走不到写入分支。
     *
     * <p>用开始门闩把 8 个线程同时放出去。<b>注意这条测试的性质</b>:
     * 它证明不了"竞态一定发生过"(线程调度不确定),但它的断言是绝对的 ——
     * <b>无论有没有真的撞上,所有响应都必须是同一个令牌</b>。
     * 去掉那个条件语句后,这条大概率会红。
     */
    @Test
    @DisplayName("并发生成:8 个请求同时打,只有一个令牌被写下,所有响应都返回它")
    void concurrentSharesAgreeOnOneToken() throws Exception {
        int threads = 8;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<String>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit((Callable<String>) () -> {
                    start.await(10, TimeUnit.SECONDS);
                    return dataOf(call(post(PLANS + "/" + planId + "/share"), USER))
                            .get("shareToken").asText();
                }));
            }
            start.countDown();

            List<String> tokens = new ArrayList<>();
            for (Future<String> future : futures) {
                tokens.add(future.get(30, TimeUnit.SECONDS));
            }

            String first = tokens.get(0);
            for (String token : tokens) {
                assertEquals(first, token,
                        "并发下的响应必须是同一个令牌 —— 否则用户手里的链接会在"
                                + "另一条响应返回的瞬间失效");
            }
            assertEquals(first, planMapper.selectById(planId).getShareToken(),
                    "库里存下的应当就是大家拿到的那个");
            assertEquals(0, codeOf(readShared(first)), "这个链接必须真的能用");
        } finally {
            pool.shutdownNow();
        }
    }

    // ===============================================================
    // 权限:公开的那条真的公开,写的那条真的要登录
    // ===============================================================

    @Test
    @DisplayName("公开页不带 Cookie 就是 200;响应带 Cache-Control: no-store")
    void publicPageNeedsNoLogin() throws Exception {
        String token = shareOf(planId);

        MvcResult result = mvc.perform(get(SHARE + "/" + token)).andReturn();

        assertEquals(200, result.getResponse().getStatus(),
                "分享页必须匿名可访问 —— 家人/设计师没有账号,也不该为了看一眼去注册");
        assertEquals("no-store", result.getResponse().getHeader(HttpHeaders.CACHE_CONTROL),
                "没有 no-store 的话,浏览器与中间缓存会继续吐旧内容 —— "
                        + "「废除之后打不开」就只是服务端的一句空话");
        assertEquals(0, codeOf(result), "匿名访问应当成功: "
                + result.getResponse().getContentAsString());
    }

    // ===============================================================
    // 失效
    // ===============================================================

    @Test
    @DisplayName("废除:旧链接**当场**失效,库里 share_token 真的是 null")
    void revokeKillsTheLinkImmediately() throws Exception {
        String token = shareOf(planId);
        assertEquals(0, codeOf(readShared(token)), "废除之前应当能用");

        assertEquals(0, codeOf(call(delete(PLANS + "/" + planId + "/share"), USER)));

        // 不等待、不重试 —— 同进程内立刻访问
        assertEquals(30001, codeOf(readShared(token)), "废除之后旧链接应当当场 30001");

        assertNull(planMapper.selectById(planId).getShareToken(),
                "「置空令牌」必须真的写进库里。用 updateById 的话它会**跳过 null 字段** —— "
                        + "接口照样返回成功,而链接还能打开,没有任何报错");
    }

    @Test
    @DisplayName("废除是幂等的:连废两次都成功,从没分享过的方案废除也成功")
    void revokeIsIdempotent() throws Exception {
        shareOf(planId);

        assertEquals(0, codeOf(call(delete(PLANS + "/" + planId + "/share"), USER)));
        assertEquals(0, codeOf(call(delete(PLANS + "/" + planId + "/share"), USER)),
                "第二次废除应当成功 —— 用户点两次不该看到报错。"
                        + "注意这条不能靠「受影响行数」判断:第二次没有任何列变化,"
                        + "驱动报 0 行还是 1 行取决于 JDBC 的 useAffectedRows 开关");

        long neverShared = generateAndSucceed(DEFAULT_PARTS);
        assertEquals(0, codeOf(call(delete(PLANS + "/" + neverShared + "/share"), USER)),
                "从没分享过的方案,废除也应当成功(而不是 30001)");
    }

    @Test
    @DisplayName("废除之后重新生成:新令牌 ≠ 旧令牌,旧链接仍是 30001,新链接可用")
    void reshareAfterRevokeIssuesANewToken() throws Exception {
        String before = shareOf(planId);
        assertEquals(0, codeOf(call(delete(PLANS + "/" + planId + "/share"), USER)));

        String after = shareOf(planId);

        assertNotEquals(before, after,
                "废除之后重新生成应当是一个**新**令牌 —— 复用旧的那就等于没废除");
        assertEquals(30001, codeOf(readShared(before)), "旧链接必须仍然是死的");
        assertEquals(0, codeOf(readShared(after)), "新链接必须能用");
    }

    @Test
    @DisplayName("方案被逻辑删除后,分享出去的链接解释成 30001(而不是还能看)")
    void deletedPlanItsTokenStopsWorking() throws Exception {
        String token = shareOf(planId);
        assertEquals(0, codeOf(call(delete(PLANS + "/" + planId), USER)));

        assertEquals(30001, codeOf(readShared(token)),
                "方案已删除 —— 分享链接要能解释成「这个方案不存在了」;"
                        + "而 share_token 本身仍留在库里(删除是标记,不碰它)");
        assertNotNull(planMapper.selectById(planId).getShareToken(),
                "逻辑删除不该顺手清掉令牌 —— 那会让误删之后无法解释这条链接");
    }

    // ===============================================================
    // 取不到的情形:一律 30001,措辞相同
    // ===============================================================

    @Test
    @DisplayName("无效令牌一律 30001,措辞与「方案不存在」完全相同")
    void badTokensAreAll30001WithTheSameMessage() throws Exception {
        String missing = messageOf(readShared("T13-根本没有这个令牌"));
        assertEquals(PlanOwnership.NOT_FOUND_MESSAGE, missing,
                "措辞必须与归属判定的那一句一字不差 —— 接收方看到的说法不该因为走哪条路而不同");

        // 超长令牌:不是 10001(参数长度不是这条接口的校验维度),也不该 500
        assertEquals(30001, codeOf(readShared("T13-" + "x".repeat(200))));
        assertEquals(missing, messageOf(readShared("T13-" + "x".repeat(200))));

        // 随手编的、长度合法的令牌
        assertEquals(30001, codeOf(readShared("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")));
    }

    /**
     * 空白令牌一律 30001,与"不存在"同一码同一措辞。
     *
     * <p><b>这是契约级断言,不是"守卫测试"—— 别把它当成后者的证明。</b>
     * 2026-10-01 本卡实现时做过故障注入:把 {@link PlanShareService#readShared}
     * 里那行空白检查整个删掉,<b>这条用例照样通过</b>。原因是
     * {@code eq(列, null)} 拼出来的是 {@code share_token = NULL}(SQL 里永远不成立),
     * 空白串也匹配不到任何行 —— 结果一样是 30001。
     *
     * <p>所以它钉的是<b>对外行为</b>(空白令牌必须报 30001、措辞与归属判定一字不差),
     * 而"查询不会退化成取第一条"这件事,靠的是实现里那行判断 ——
     * 那行的理由写在 {@code readShared} 的注释里,不在这个断言里。
     *
     * <p>直接调服务而不走 HTTP:{@code GET /api/v1/share/}(真空白)匹配不到
     * {@code {token}} 这个模板,压根落不到这个方法上,测的就不是同一件事了。
     */
    @Test
    @DisplayName("空白令牌一律 30001,措辞与「方案不存在」相同")
    void blankTokenIs30001() {
        for (String blank : new String[] {null, "", "   ", "\t\n"}) {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> shareService.readShared(blank),
                    "空白令牌必须报 30001,不能返回任何方案: " + blank);
            assertEquals(ErrorCode.NOT_FOUND, ex.errorCode(),
                    "空白令牌应当是 30001(与不存在同一码),实际: " + ex.errorCode());
            assertEquals(PlanOwnership.NOT_FOUND_MESSAGE, ex.getMessage());
        }
    }

    /**
     * <b>已知取舍(挂在明处的债):令牌比较是大小写<u>不</u>敏感的表列排序规则。</b>
     *
     * <p>{@code t_plan.share_token} 建表时(V4)沿用了库的默认排序规则
     * {@code utf8mb4_0900_ai_ci} —— {@code ai_ci} 就是"重音不敏感、大小写不敏感"。
     * 而令牌是 Base64URL 编码的,<b>大小写是有意义的</b>:理论上 {@code AbC} 与
     * {@code abc} 是两个不同的令牌,数据库却判它们相等。
     *
     * <p>熵的损失是有限的(可行等价类少了约 43 个二进制位,256 bit 变成约 213 bit ——
     * 仍然不可穷举),所以本轮<b>不</b>为它单独开一个迁移:现在是"先跑通"的阶段,
     * 改列排序规则要 ALTER 一张有唯一键的表,风险与收益不成比例。
     *
     * <p><b>但它是债,不是"没关系"。</b>这条断言写的是**当前事实**:
     * 用小写去读一个大写令牌,今天<b>能读到</b>。等哪天把列改成
     * {@code COLLATE utf8mb4_bin}(一行 {@code ALTER},配合新版本号迁移),
     * 这条会红 —— 那时把期望值从 0 改成 30001 才算修完。
     * 与 {@code contract-impl-gaps.yaml} 的"挂账已过期即判红"是同一个手法:
     * <b>让"还欠着"这件事无法悄悄消失。</b>
     */
    @Test
    @DisplayName("已知取舍:同一串不同大小写也能读到 —— 修好(改 utf8mb4_bin)时这条会红")
    void tokenComparisonIsCaseInsensitiveToday() throws Exception {
        // 用生成出来的那份方案(它有户型快照)—— 直接造的行没有 layout_enc,
        // 组装时会走 90001,那就测不到"读没读到"这件事了。
        jdbcTemplate.update("UPDATE t_plan SET share_token = ? WHERE id = ?",
                "T13-CaseProbe-AbC", planId);

        assertEquals(0, codeOf(readShared("T13-CaseProbe-AbC")), "原样的令牌当然能读到");
        assertEquals(0, codeOf(readShared("t13-caseprobe-abc")),
                "今天大小写不敏感,所以小写也能读到。看到这条红,说明列已经改成了 "
                        + "utf8mb4_bin —— 把这里的 0 改成 30001,债就还清了");
    }

    @Test
    @DisplayName("别人的方案 / 不存在的方案:生成与废除都是 30001,措辞相同")
    void foreignOrMissingPlanIs30001() throws Exception {
        long theirs = insertPlan(OTHER_USER, "T13-别人的方案");

        String missing = messageOf(call(get(PLANS + "/999999999"), USER));

        for (long bad : new long[] {999_999_999L, theirs}) {
            assertEquals(30001, codeOf(call(post(PLANS + "/" + bad + "/share"), USER)),
                    "生成分享应当 30001: planId=" + bad);
            assertEquals(30001, codeOf(call(delete(PLANS + "/" + bad + "/share"), USER)),
                    "废除分享应当 30001: planId=" + bad);
        }

        assertEquals(missing, messageOf(call(post(PLANS + "/" + theirs + "/share"), USER)),
                "「不是你的」与「不存在」的措辞必须一致 —— 区分开来就是一个"
                        + "可以枚举别人方案 id 的探针");
    }

    // ===============================================================
    // 不打扰用户的列表
    // ===============================================================

    /**
     * 分享与废除都<b>不得抬 {@code updated_at}</b>。
     *
     * <p>分享状态不在方案卡片上显示,用户没有"改过这个方案";而「我的方案」列表按
     * {@code updated_at desc} 排序 —— 抬时间戳会让用户什么都没做、列表顺序却变了。
     *
     * <p>{@code t_plan.updated_at} 带 {@code ON UPDATE CURRENT_TIMESTAMP},
     * 所以<b>光靠"不写它"是不够的</b>,必须显式压掉。这条就是那个决定的判据:
     * 后人把它改回 {@code updated_at = NOW()} 时,这里会红。
     */
    @Test
    @DisplayName("分享 / 废除都不抬 updated_at —— 列表顺序不该因为分享而变")
    void sharingDoesNotTouchUpdatedAt() throws Exception {
        // 往回拨 60 秒:不拨的话"前进"根本看不出来(本来就差不多是现在)
        jdbcTemplate.update(
                "UPDATE t_plan SET updated_at = DATE_SUB(NOW(), INTERVAL 60 SECOND) WHERE id = ?",
                planId);
        LocalDateTime baseline = planMapper.selectById(planId).getUpdatedAt();
        assertTrue(baseline.isBefore(LocalDateTime.now().minusSeconds(30)),
                "回拨没生效(仍停在 " + baseline + "),后面的断言就没意义了");

        shareOf(planId);
        assertEquals(baseline, planMapper.selectById(planId).getUpdatedAt(),
                "生成分享动了 updated_at —— 方案列表(按 updated_at desc 排序)会"
                        + "无理由地重排,而没有任何别的测试会红");

        assertEquals(0, codeOf(call(delete(PLANS + "/" + planId + "/share"), USER)));
        assertEquals(baseline, planMapper.selectById(planId).getUpdatedAt(),
                "废除分享同样不该动 updated_at");
    }

    // ===============================================================
    // 工具
    // ===============================================================

    /** 生成一次分享,返回令牌。 */
    private String shareOf(long id) throws Exception {
        return dataOf(call(post(PLANS + "/" + id + "/share"), USER)).get("shareToken").asText();
    }

    private MvcResult readShared(String token) throws Exception {
        return mvc.perform(get(SHARE + "/" + token)).andReturn();
    }

    /** 直接签一个 access 令牌当 Cookie —— 登录要 Redis,而本测试验的不是登录。 */
    private Cookie cookieFor(long userId) {
        return new Cookie("access_token", jwtService.issueAccess(userId, SubjectType.USER));
    }

    private MvcResult call(MockHttpServletRequestBuilder request, long userId) throws Exception {
        return mvc.perform(request.cookie(cookieFor(userId))).andReturn();
    }

    /** 受理一次生成 + 发一条 success 回调,返回落库的方案 id。 */
    private long generateAndSucceed(String partsJson) throws Exception {
        String taskId = dataOf(call(post(GENERATE)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(generateBody(partsJson)), USER)).get("taskId").asText();

        MvcResult callback = mvc.perform(post(CALLBACK)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(successBody(taskId))
                        .header(SERVICE_TOKEN_HEADER, SERVICE_TOKEN))
                .andReturn();
        assertEquals(0, codeOf(callback),
                "出图回调应成功: " + callback.getResponse().getContentAsString());

        GenerationTask task = taskMapper.selectOne(Wrappers.<GenerationTask>lambdaQuery()
                .eq(GenerationTask::getTaskId, taskId));
        Long saved = task.getPlanId();
        assertNotNull(saved, "回调成功却没落方案 —— 后面的断言全都无从谈起");
        return saved;
    }

    /** 受理请求体:2m × 1.8m × 2.4m 的卫生间。 */
    private String generateBody(String partsJson) {
        return "{\"space\":\"bathroom\",\"style\":\"modern\","
                + "\"layout\":{\"spaces\":[{\"type\":\"bathroom\",\"name\":\"卫生间\","
                + "\"length\":2,\"width\":1.8,\"height\":2.4,\"wallCount\":4}]},"
                + "\"parts\":" + partsJson + "}";
    }

    /** success 回调体。地址指向 Java —— Python 只在内网,前端够不到它的地址(§3.3)。 */
    private String successBody(String taskId) {
        return """
                {"taskId":"%s","status":"success","progress":100,"imageUrls":[
                  {"url":"http://java.test/static/placeholder.svg?task=%s","view":"default",
                   "isCover":true}]}""".formatted(taskId, taskId);
    }

    /** 直接造一行方案 —— 不需要户型,用于归属判定。 */
    private long insertPlan(long userId, String name) {
        Plan plan = new Plan();
        plan.setUserId(userId);
        plan.setName(name);
        plan.setQuoteExpired(false);
        plan.setIsDeleted(false);
        planMapper.insert(plan);
        return plan.getId();
    }

    // ------------------------------------------------------------------

    /** 产品的最小非空集(与 {@code QuoteEngineRegressionTest} 同一个口径)。 */
    private void seedProduct(String sku, String name) {
        Product product = new Product();
        product.setSkuCode(sku);
        product.setName(name);
        product.setPricePerSqm(new BigDecimal("100.00"));
        product.setStatus(Product.STATUS_OFF_SALE);
        productMapper.insert(product);
    }

    private void seedParams() {
        setParam("aux_unit_price", "20.00");
        setParam("loss_rate", "0.06");
        setParam("loss_rate_min", "0.05");
        setParam("loss_rate_max", "0.08");
    }

    private static List<String> managedParamKeys() {
        return new ArrayList<>(List.of(
                "aux_unit_price", "loss_rate", "loss_rate_min", "loss_rate_max"));
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
        Map<String, BigDecimal> snapshot = new java.util.LinkedHashMap<>();
        for (QuoteParam param : quoteParamMapper.selectList(Wrappers.<QuoteParam>lambdaQuery())) {
            snapshot.put(param.getParamKey(), param.getParamValue());
        }
        return snapshot;
    }

    // ------------------------------------------------------------------

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

    // ------------------------------------------------------------------

    /**
     * 清理本测试造的数据。
     *
     * <p>只按<b>自己的用户号段</b>与<b>自己的 SKU 前缀</b>删 —— 绝不清空整张表,
     * 也不对全表条数下结论(库里还有别的测试类、别的用例的数据)。
     */
    private void cleanup() {
        for (long userId : new long[] {USER, OTHER_USER}) {
            for (GenerationTask task : taskMapper.selectList(Wrappers.<GenerationTask>lambdaQuery()
                    .eq(GenerationTask::getUserId, userId))) {
                outboxMapper.delete(Wrappers.<OutboxMessage>lambdaQuery()
                        .eq(OutboxMessage::getAggregateId, task.getTaskId()));
                taskMapper.deleteById(task.getId());
            }
            for (Plan plan : planMapper.selectList(Wrappers.<Plan>lambdaQuery()
                    .eq(Plan::getUserId, userId))) {
                long id = plan.getId();
                planPartMapper.delete(Wrappers.<PlanPart>lambdaQuery().eq(PlanPart::getPlanId, id));
                planImageMapper.delete(Wrappers.<PlanImage>lambdaQuery().eq(PlanImage::getPlanId, id));
                planQuoteMapper.delete(Wrappers.<PlanQuote>lambdaQuery().eq(PlanQuote::getPlanId, id));
                planMapper.deleteById(id);
            }
        }
        productMapper.delete(Wrappers.<Product>lambdaQuery()
                .likeRight(Product::getSkuCode, SKU_PREFIX));
    }
}
