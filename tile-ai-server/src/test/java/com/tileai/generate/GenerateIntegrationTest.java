package com.tileai.generate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tileai.common.crypto.AesGcmCipher;
import com.tileai.common.mq.RabbitTopologyConfig;
import com.tileai.common.outbox.OutboxMapper;
import com.tileai.common.outbox.OutboxMessage;
import com.tileai.common.security.JwtService;
import com.tileai.common.security.SubjectType;
import com.tileai.common.trace.TraceContext;
import com.tileai.generate.dto.GenerateCallbackRequest;
import com.tileai.generate.dto.GenerateTaskPayload;
import com.tileai.generate.entity.GenerationTask;
import com.tileai.generate.mapper.GenerationTaskMapper;
import com.tileai.layout.model.LayoutModel;
import com.tileai.plan.entity.Plan;
import com.tileai.plan.entity.PlanImage;
import com.tileai.plan.entity.PlanPart;
import com.tileai.plan.mapper.PlanImageMapper;
import com.tileai.plan.mapper.PlanMapper;
import com.tileai.plan.mapper.PlanPartMapper;

import jakarta.servlet.http.Cookie;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * T-1.11 验收:生成编排(架构 §6.2、§6.4;契约 {@code openapi-c.yaml} 的 generate 分组
 * 与 {@code openapi-internal.yaml} 的 callback)。
 *
 * <p><b>需要 MySQL + RabbitMQ</b>({@code -Drabbit.verify=true -Ddb.verify=true} 显式启用)。
 *
 * <p>验的是整条链路,而不是各自孤立的接口:
 * <pre>
 *   受理(任务 + 发件箱,同一事务)→ 投递到 queue.generate → 回调 running/success
 *   → 方案自动保存(加密户型)→ 轮询拿到图
 * </pre>
 *
 * <p>五条判据对应关系:
 * <ol>
 *   <li>状态机 —— {@code running}/{@code success}/{@code failed} 三条路径各自的落库结果;</li>
 *   <li>回调幂等 —— 重复回调与<b>并发</b>回调都只产生一份方案;</li>
 *   <li>Outbox —— 消息真的到了队列,且与库里存的是同一份;</li>
 *   <li>补偿扫描 —— 见 {@link GenerateCompensationIntegrationTest}(不依赖 MQ);</li>
 *   <li>故障注入 —— 重复回调(见 ②)、fail 分支、未知 taskId、success 不带图。</li>
 * </ol>
 *
 * <p><b>刻意不验"图画得像不像那块砖"</b>:本期图像是替身(§29),出图那一步在 Python 侧。
 * Java 这一侧能负责的是"把照片信息传下去"与"把回调结果落对"。
 */
@EnabledIfSystemProperty(named = "rabbit.verify", matches = "true",
        disabledReason = "需要 RabbitMQ 与 MySQL;用 -Drabbit.verify=true -Ddb.verify=true 显式启用")
@SpringBootTest(properties = {
        "spring.flyway.enabled=false",
        "tile.auth.secret=0123456789abcdef0123456789abcdef",
        // 32 字节(0x00…0x1f)的 Base64。测试自己给密钥,不依赖开发机上的环境变量 ——
        // 否则"忘了导出 LAYOUT_ENC_KEY"会让一整类用例静默跳过。
        "tile.crypto.layout-key=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=",
        "tile.agent.service-token=t11-test-token",
        // 关掉补偿扫描的定时器:它 60 秒一次,会在断言"任务仍是 pending"的用例里
        // 把任务挪走。扫描本身由 GenerateCompensationIntegrationTest 显式驱动。
        "tile.generate.compensation-enabled=false",
        // 关掉"受理前探测消费端":跑测试的机器上没有 Python 消费端,consumerCount
        // 恒为 0,不关的话本类凡是要 POST /generate 的用例会整批 40002。
        // 探测本身的验收在 GenerateConsumerProbeTest / GenerateConsumerRequiredIntegrationTest。
        "tile.generate.require-consumer=false",
        // 关掉效果图转存:本测试的回调地址是 http://java.test/... (解析不了),
        // 开着会每张图都等一次 DNS 失败再降级,白白拖慢用例。转存自身的验收
        // 由专门的用例覆盖(它用一个可放行环回的 UrlPolicy 打本地桩服务)。
        "tile.storage.enabled=false"})
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GenerateIntegrationTest {

    private static final String GENERATE = "/api/v1/generate";
    private static final String CALLBACK = "/internal/generate/callback";
    private static final String SERVICE_TOKEN_HEADER = "X-Service-Token";
    private static final String SERVICE_TOKEN = "t11-test-token";

    /** 本测试类的 C 端用户。用固定的大号 id,避免撞上库里真实的用户。 */
    private static final long USER = 8_811_0001L;
    /** 第二个用户:专门用来验"别人的 planId 覆盖不动"。 */
    private static final long OTHER_USER = 8_811_0002L;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private AesGcmCipher cipher;

    @Autowired
    private GenerationTaskMapper taskMapper;

    @Autowired
    private GenerateCallbackService callbackService;

    @Autowired
    private PlanMapper planMapper;

    @Autowired
    private PlanPartMapper planPartMapper;

    @Autowired
    private PlanImageMapper planImageMapper;

    @Autowired
    private OutboxMapper outboxMapper;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    /**
     * 每个用例从空状态开始。
     *
     * <p>JUnit 不保证执行顺序,而本类多个用例都靠"表里只有我这几行"来做计数断言
     * (比如"重复回调后行数不变")。不清就会互相干扰 —— 本项目为此踩过多次。
     */
    @BeforeEach
    void resetState() {
        cleanup();
    }

    @AfterAll
    void cleanupAfterAll() {
        cleanup();
    }

    // ===============================================================
    // ① 受理
    // ===============================================================

    @Test
    @DisplayName("受理:返回 pending + taskId,任务与发件箱两行同事务落库")
    void acceptCreatesPendingTaskAndOutboxRow() throws Exception {
        JsonNode data = dataOf(postGenerate(generateBody(null, 1)));

        String taskId = data.get("taskId").asText();
        assertEquals(GenerationTask.STATUS_PENDING, data.get("status").asText(),
                "此刻消息还没投出去,状态只能是 pending(§35.2)");
        assertTrue(taskId.startsWith("g_"), "任务号形状应对齐契约示例 g_1027f3: " + taskId);

        GenerationTask task = taskOf(taskId);
        assertNotNull(task, "任务行没落库");
        assertEquals(USER, task.getUserId());
        assertEquals(GenerationTask.STATUS_PENDING, task.getStatus());
        assertEquals(0, task.getProgress());
        assertEquals(GenerationTask.TYPE_IMAGE, task.getType());

        // 发件箱那条:没有它,"落库成功但消息丢了"就是一个永远 pending 的孤儿任务
        OutboxMessage outbox = outboxOf(taskId);
        assertNotNull(outbox, "发件箱行没落库 —— 消息永远不会被投递");
        assertEquals(OutboxMessage.AGGREGATE_TASK, outbox.getAggregateType());
        assertEquals(RabbitTopologyConfig.QUEUE_GENERATE, outbox.getQueue());
        assertEquals(OutboxMessage.STATUS_PENDING, outbox.getStatus());

        // 落库的 payload 与投递的 payload 必须是同一份(序列化只做一次)
        assertEquals(objectMapper.readTree(task.getPayload()), objectMapper.readTree(outbox.getPayload()),
                "任务快照与发件箱消息体不应当是两份各自构造的数据");
    }

    @Test
    @DisplayName("受理:angles 省略时按契约默认 1")
    void anglesDefaultsToOne() throws Exception {
        String taskId = taskIdOf(postGenerate(generateBody(null, null)));

        assertEquals(1, payloadOf(taskId).get("angles").asInt(),
                "契约 GenerateRequest.angles 的默认值是 1,null 是「没传」不是「0 张」");
    }

    @Test
    @DisplayName("受理:户型面积由服务端派生后再进消息体")
    void areasAreDerivedBeforeDispatch() throws Exception {
        String taskId = taskIdOf(postGenerate(generateBody(null, 1)));

        JsonNode areas = payloadOf(taskId).get("layout").get("spaces").get(0).get("areas");
        // 2 × 1.8 = 3.6;2 × (2 + 1.8) × 2.4 = 18.24
        assertEquals(0, new BigDecimal("3.6").compareTo(areas.get("floor").decimalValue()));
        assertEquals(0, new BigDecimal("18.24").compareTo(areas.get("wall").decimalValue()));
    }

    @Test
    @DisplayName("受理:两个部位都选了 → 不触发 AI 补齐,source 恒为 user")
    void partsCarryUserSourceAndNoAiCompletion() throws Exception {
        // ⚠️ 本用例的两个部位(floor+wall)都是请求里给的,所以**不会**触发 T-1.9 的补齐
        // —— 那一步只在"有未选的地面/墙面"时才发。补齐本身的验收在
        // GeneratePartCompletionIntegrationTest。
        String taskId = taskIdOf(postGenerate(generateBody(null, 1)));

        JsonNode parts = payloadOf(taskId).get("parts");
        assertEquals(2, parts.size(), "请求里给了几个部位就传几个");
        for (JsonNode part : parts) {
            assertEquals("user", part.get("source").asText(),
                    "用户自己选的部位不该被标成 ai");
        }
    }

    @Test
    @DisplayName("受理:请求的 traceId 透传进消息体,便于跨进程串联")
    void traceIdIsPropagated() throws Exception {
        String traceId = "t11-trace-abc123";
        String taskId = dataOf(mvc.perform(postGenerate(generateBody(null, 1))
                        .header(TraceContext.HEADER, traceId)).andReturn()).get("taskId").asText();

        assertEquals(traceId, payloadOf(taskId).get("traceId").asText(),
                "消息体里的 traceId 必须与受理请求一致 —— 否则出了图找不到是哪次请求");
    }

    @Test
    @DisplayName("受理:至少一个部位,少了就是 10001")
    void emptyPartsIsParamInvalid() throws Exception {
        String body = """
                {"space":"bathroom","angles":1,"layout":{"spaces":[
                  {"type":"bathroom","length":2,"width":1.8,"height":2.4}]},"parts":[]}
                """;
        assertEquals(10001, codeOf(mvc.perform(postGenerate(body)).andReturn()));
    }

    @Test
    @DisplayName("受理:angles 超过 4 被拒(与已定义机位数一致)")
    void tooManyAnglesIsParamInvalid() throws Exception {
        assertEquals(10001, codeOf(mvc.perform(postGenerate(generateBody(null, 5))).andReturn()));
    }

    @Test
    @DisplayName("受理:覆盖别人的方案在**受理时**就被拒(30001),不烧模型调用")
    void acceptWithForeignPlanIdIsRejectedEarly() throws Exception {
        long foreignPlanId = insertPlan(OTHER_USER, "别人的方案");

        MvcResult result = mvc.perform(postGenerate(generateBody(foreignPlanId, 1))).andReturn();

        assertEquals(30001, codeOf(result),
                "等到出图完成才失败的话,用户等了两分钟、模型调用也烧掉了");
        assertEquals(0, taskMapper.selectCount(Wrappers.<GenerationTask>lambdaQuery()
                        .eq(GenerationTask::getUserId, USER)),
                "被拒的请求不该留下任务行");
    }

    // ===============================================================
    // ③ 消息真的投出去了
    // ===============================================================

    @Test
    @DisplayName("投递:消息真的进了 queue.generate,必填字段齐备(§asyncapi)")
    void acceptedMessageReachesQueue() throws Exception {
        String taskId = taskIdOf(postGenerate(generateBody(null, 2)));

        JsonNode message = awaitMessage(taskId);

        // 契约 GenerateTaskPayload 的 required 全集。少任何一个 Python 都组装不出提示词。
        for (String field : new String[]{"taskId", "type", "space", "style", "angles",
                "parts", "layout", "traceId", "enqueuedAt"}) {
            assertTrue(message.hasNonNull(field), "消息体缺必填字段 " + field + ": " + message);
        }
        assertEquals("image", message.get("type").asText());
        assertEquals(2, message.get("angles").asInt());
        assertEquals(GenerateTaskPayload.INITIAL_ATTEMPT, message.get("attempt").asInt(),
                "首次投递 attempt 必须是 1(contract minimum: 1)");
        assertEquals(USER, message.get("userId").asLong());
    }

    @Test
    @DisplayName("投递:队列里收到的消息与发件箱里存的**逐字段一致**(不在投递时重新构造)")
    void dispatchedMessageEqualsStoredPayload() throws Exception {
        String taskId = taskIdOf(postGenerate(generateBody(null, 1)));

        JsonNode received = awaitMessage(taskId);
        JsonNode stored = objectMapper.readTree(outboxOf(taskId).getPayload());

        assertEquals(stored, received,
                "若投递时重新构造消息,库里的记录就不再是「当时要发出去什么」的凭据");
    }

    // ===============================================================
    // ④ 状态机:running
    // ===============================================================

    @Test
    @DisplayName("状态机:回调 running 把任务从 pending 翻到 running,进度只前进不回退")
    void runningCallbackAdvancesProgress() throws Exception {
        String taskId = taskIdOf(postGenerate(generateBody(null, 1)));

        assertEquals(0, codeOf(callback(callbackBody(taskId, "running", 0, null))));
        assertEquals(GenerationTask.STATUS_RUNNING, taskOf(taskId).getStatus());
        assertEquals(0, taskOf(taskId).getProgress());

        callback(callbackBody(taskId, "running", 50, null));
        assertEquals(50, taskOf(taskId).getProgress());

        // 乱序到达的旧进度:第 1 张的回调晚于第 2 张
        callback(callbackBody(taskId, "running", 25, null));
        assertEquals(50, taskOf(taskId).getProgress(),
                "进度条倒退比不动更让人怀疑系统坏了(GREATEST)");
    }

    @Test
    @DisplayName("轮询:pending 时就能查到任务与部位(前端不必等出图)")
    void progressIsQueryableWhilePending() throws Exception {
        String taskId = taskIdOf(postGenerate(generateBody(null, 1)));

        JsonNode data = dataOf(progressOf(taskId));

        assertEquals(taskId, data.get("taskId").asText());
        assertEquals(GenerationTask.STATUS_PENDING, data.get("status").asText());
        assertEquals(2, data.get("parts").size());
        assertEquals("user", data.get("parts").get(0).get("source").asText(),
                "契约规定响应里 source 必填");
        assertFalse(data.has("images"), "还没出图就不该出现 images 字段(不是输出 null)");
    }

    @Test
    @DisplayName("轮询:任务不存在是 40003;别人的任务同样是 40003(不区分)")
    void unknownOrForeignTaskIsNotFound() throws Exception {
        assertEquals(40003, codeOf(mvc.perform(get(GENERATE + "/g_no_such_task")
                .cookie(cookieFor(USER + 1))).andReturn()));

        String taskId = taskIdOf(postGenerate(generateBody(null, 1)));
        assertEquals(40003, codeOf(mvc.perform(get(GENERATE + "/" + taskId)
                        .cookie(cookieFor(OTHER_USER))).andReturn()),
                "区分「不存在」与「不是你的」等于送出一个枚举别人 taskId 的接口");
    }

    // ===============================================================
    // ④ 状态机:success + 方案自动保存
    // ===============================================================

    @Test
    @DisplayName("状态机:success 回调落方案 —— 户型加密、部位带毛面积、图按视角顺序、封面唯一")
    void successCallbackSavesPlan() throws Exception {
        String taskId = taskIdOf(postGenerate(generateBody(null, 2)));

        MvcResult accepted = callback(successBody(taskId, "default", "angle1"));
        assertEquals(0, codeOf(accepted));

        GenerationTask task = taskOf(taskId);
        assertEquals(GenerationTask.STATUS_SUCCESS, task.getStatus());
        assertEquals(100, task.getProgress());
        assertNotNull(task.getPlanId(), "新建方案时 planId 由回调产生并回填任务");
        assertNotNull(task.getResultRef(), "封面图 url 应作为结果引用留下");

        Plan plan = planMapper.selectById(task.getPlanId());
        assertNotNull(plan);
        assertEquals(USER, plan.getUserId());
        assertEquals("bathroom", plan.getSpace());
        assertEquals("modern", plan.getStyle());
        assertNotNull(plan.getName(), "契约 PlanSummary.name 是必填 —— 方案卡片要有标题");
        assertTrue(plan.getName().contains("·"), "默认名形如「卫生间 · 现代」: " + plan.getName());

        // 户型是密文,且能解回原模型
        assertNotNull(plan.getLayoutEnc());
        String decrypted = cipher.decryptToString(plan.getLayoutEnc());
        assertTrue(decrypted.contains("spaces"), "解密后应能读回 LayoutModel: " + decrypted);
        assertFalse(new String(plan.getLayoutEnc(), StandardCharsets.ISO_8859_1).contains("spaces"),
                "密文里不该出现明文片段 —— 那说明根本没加密");

        List<PlanPart> parts = planPartMapper.selectList(Wrappers.<PlanPart>lambdaQuery()
                .eq(PlanPart::getPlanId, task.getPlanId()).orderByAsc(PlanPart::getId));
        assertEquals(2, parts.size());
        assertEquals("floor", parts.get(0).getPart());
        assertEquals(0, new BigDecimal("3.6").compareTo(parts.get(0).getGrossArea()),
                "部位面积应取自派生后的户型快照");
        assertEquals("wall", parts.get(1).getPart());
        assertEquals(0, new BigDecimal("18.24").compareTo(parts.get(1).getGrossArea()));

        List<PlanImage> images = planImageMapper.selectList(Wrappers.<PlanImage>lambdaQuery()
                .eq(PlanImage::getPlanId, task.getPlanId()).orderByAsc(PlanImage::getId));
        assertEquals(2, images.size(), "angles=2 ⇒ 恰好 2 张");
        assertEquals("default", images.get(0).getView());
        assertEquals("angle1", images.get(1).getView());
        assertTrue(images.get(0).getIsCover(), "封面是 default 那张(D-5 定它为基准机位)");
        assertFalse(images.get(1).getIsCover(), "封面只允许一张");
    }

    @Test
    @DisplayName("状态机:success 后轮询能拿到 planId 与图,前端据此跳转方案详情")
    void progressReturnsImagesAfterSuccess() throws Exception {
        String taskId = taskIdOf(postGenerate(generateBody(null, 2)));
        callback(successBody(taskId, "default", "angle1"));

        JsonNode data = dataOf(progressOf(taskId));

        assertEquals(GenerationTask.STATUS_SUCCESS, data.get("status").asText());
        assertEquals(100, data.get("progress").asInt());
        assertTrue(data.get("planId").asLong() > 0);
        assertEquals(2, data.get("images").size());
        assertEquals("default", data.get("images").get(0).get("view").asText());
        assertTrue(data.get("images").get(0).get("isCover").asBoolean());
    }

    @Test
    @DisplayName("方案:带 planId 重新生成 = 覆盖同一个方案,行数不翻倍(先删后插)")
    void regenerateOverwritesSamePlan() throws Exception {
        long planId = planIdOf(taskIdAfterSuccess(null, 2));

        // 第二次:带 planId 覆盖,且这次只出 1 张
        String secondTaskId = taskIdOf(postGenerate(generateBody(planId, 1)));
        callback(successBody(secondTaskId, "default"));

        assertEquals(planId, taskOf(secondTaskId).getPlanId(), "覆盖应沿用原方案 id");
        assertEquals(1, planImageMapper.selectCount(Wrappers.<PlanImage>lambdaQuery()
                        .eq(PlanImage::getPlanId, planId)),
                "上一次的 2 张必须被清掉 —— t_plan_image 没有 updated_at,覆盖只能先删后插;"
                        + "否则用户点开会看到两个版本的图混在一起");
        assertEquals(1, planMapper.selectCount(Wrappers.<Plan>lambdaQuery()
                .eq(Plan::getUserId, USER)));
    }

    // ===============================================================
    // ② 幂等
    // ===============================================================

    @Test
    @DisplayName("幂等:同一条 success 回调发两次,第二次返回成功但一行都不改")
    void duplicateSuccessCallbackChangesNothing() throws Exception {
        String taskId = taskIdOf(postGenerate(generateBody(null, 2)));
        String body = successBody(taskId, "default", "angle1");

        assertEquals(0, codeOf(callback(body)));
        long planId = taskOf(taskId).getPlanId();
        long plans = planMapper.selectCount(null);
        long parts = planPartMapper.selectCount(null);
        long images = planImageMapper.selectCount(null);

        MvcResult again = callback(body);
        assertEquals(0, codeOf(again),
                "重复回传要返回成功 —— 回错会让 Python 以为没送到而重试,放大一次重复");

        assertEquals(planId, taskOf(taskId).getPlanId());
        assertEquals(plans, planMapper.selectCount(null), "方案表不该多出一行");
        assertEquals(parts, planPartMapper.selectCount(null), "部位不该重复插入");
        assertEquals(images, planImageMapper.selectCount(null), "图不该重复插入");
    }

    @Test
    @DisplayName("幂等(并发):两个线程同时发同一条 success,只产生一份方案")
    void concurrentSuccessCallbacksProduceExactlyOnePlan() throws Exception {
        String taskId = taskIdOf(postGenerate(generateBody(null, 2)));
        GenerateCallbackRequest request = objectMapper.readValue(
                successBody(taskId, "default", "angle1"), GenerateCallbackRequest.class);

        // 直接打服务层,不经 MockMvc:这里要验的是"条件更新能不能在真并发下串行化",
        // 而 MockMvc 的线程安全性不是本用例的靶子。
        int threads = 2;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    start.await();
                    // 抢输的那一方不该报错,它应当安静地什么也不做
                    callbackService.handle(request);
                    return null;
                });
            }
            // 一起放行,把"谁先谁后"交给数据库的行锁去决定
            start.countDown();
            pool.shutdown();
            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS), "并发回调未在 30 秒内结束");
        } finally {
            pool.shutdownNow();
        }

        assertEquals(1, planMapper.selectCount(Wrappers.<Plan>lambdaQuery()
                .eq(Plan::getUserId, USER)), "两个并发回调各存一份方案 = 幂等被绕过了");
        Long planId = taskOf(taskId).getPlanId();
        assertEquals(2, planImageMapper.selectCount(Wrappers.<PlanImage>lambdaQuery()
                .eq(PlanImage::getPlanId, planId)), "图也该只有一份");
    }

    // ===============================================================
    // ⑤ 故障注入
    // ===============================================================

    @Test
    @DisplayName("故障:failed 回调记原因,轮询返回 40001 且 data 里带任务进度")
    void failedCallbackIsReportedToFrontend() throws Exception {
        String taskId = taskIdOf(postGenerate(generateBody(null, 1)));
        callback(callbackBody(taskId, "running", 40, null));

        String body = """
                {"taskId":"%s","status":"failed","progress":40,
                 "error":{"code":40001,"message":"图像网关连续三次超时"}}
                """.formatted(taskId);
        assertEquals(0, codeOf(callback(body)));

        GenerationTask task = taskOf(taskId);
        assertEquals(GenerationTask.STATUS_FAILED, task.getStatus());
        assertEquals("图像网关连续三次超时", task.getError());
        assertEquals(40, task.getProgress(), "进度停在失败那一刻,不清零");

        JsonNode response = objectMapper.readTree(
                progressOf(taskId).getResponse().getContentAsString());
        assertEquals(40001, response.get("code").asInt(),
                "契约把 40001 列为本接口的失败码,不给它就永远不可达(§35.3)");
        assertEquals("图像网关连续三次超时", response.get("message").asText());
        assertEquals(taskId, response.get("data").get("taskId").asText(),
                "失败也要带 data —— 否则前端分不清「我这次失败了」与「我查错了任务」");
        assertEquals(GenerationTask.STATUS_FAILED, response.get("data").get("status").asText());
    }

    @Test
    @DisplayName("故障:failed 不删任务也不动原方案 —— 补偿重投后它要能回到 running")
    void failedCallbackKeepsTaskRetryable() throws Exception {
        long planId = planIdOf(taskIdAfterSuccess(null, 1));

        String taskId = taskIdOf(postGenerate(generateBody(planId, 1)));
        callback(callbackBody(taskId, "running", 0, null));
        callback(callbackBody(taskId, "failed", 0, "替身网关炸了"));

        assertEquals(GenerationTask.STATUS_FAILED, taskOf(taskId).getStatus());
        assertEquals(1, planImageMapper.selectCount(Wrappers.<PlanImage>lambdaQuery()
                        .eq(PlanImage::getPlanId, planId)),
                "出图失败不该顺手把用户原本的方案改掉");

        // 重投之后回到 running,这正是"failed 不是终态"的意思
        callback(callbackBody(taskId, "running", 10, null));
        assertEquals(GenerationTask.STATUS_RUNNING, taskOf(taskId).getStatus());
    }

    @Test
    @DisplayName("故障:未知 taskId 的回调是 40003,不静默吞掉")
    void callbackForUnknownTaskIsNotFound() throws Exception {
        assertEquals(40003, codeOf(callback(callbackBody("g_ghost", "running", 0, null))));
        assertEquals(40003, codeOf(callback("""
                {"taskId":"g_ghost","status":"success",
                 "imageUrls":[{"url":"http://java/static/placeholder.svg","view":"default"}]}
                """)));
    }

    @Test
    @DisplayName("故障:success 却不带图 —— 10001,不存一个没有图的方案")
    void successWithoutImagesIsParamInvalid() throws Exception {
        String taskId = taskIdOf(postGenerate(generateBody(null, 1)));

        MvcResult result = callback("""
                {"taskId":"%s","status":"success","progress":100}
                """.formatted(taskId));

        assertEquals(10001, codeOf(result));
        assertEquals(GenerationTask.STATUS_PENDING, taskOf(taskId).getStatus(),
                "校验失败必须整体回滚 —— 不能留下一个 success 但没图的任务");
        assertEquals(0, planMapper.selectCount(Wrappers.<Plan>lambdaQuery()
                .eq(Plan::getUserId, USER)));
    }

    @Test
    @DisplayName("故障:回调里的错误码 Java 不改写(§10.4 只管 Java 返回给前端的码)")
    void callbackErrorCodeIsRecordedVerbatim() throws Exception {
        String taskId = taskIdOf(postGenerate(generateBody(null, 1)));

        callback("""
                {"taskId":"%s","status":"failed","error":{"code":90001,"message":"内部故障"}}
                """.formatted(taskId));

        assertEquals("内部故障", taskOf(taskId).getError(),
                "Python 报什么就记什么 —— 改写会让排查时看不到真因");
    }

    // ===============================================================
    // /internal/** 的服务令牌闸门
    // ===============================================================

    @Test
    @DisplayName("内部接口:不带服务令牌 401,带上才通")
    void internalEndpointsRequireServiceToken() throws Exception {
        assertEquals(401, mvc.perform(get("/internal/products/T11-NOPE")).andReturn()
                .getResponse().getStatus());
        assertEquals(401, mvc.perform(get("/internal/products/T11-NOPE")
                        .header(SERVICE_TOKEN_HEADER, "wrong-token")).andReturn()
                .getResponse().getStatus());

        // 带对了令牌就通到业务逻辑:这里问的是一个不存在的 SKU,所以是 30001 ——
        // 关键是"没被拦在门口",说明令牌校验放行了
        assertEquals(30001, codeOf(mvc.perform(get("/internal/products/T11-NOPE")
                .header(SERVICE_TOKEN_HEADER, SERVICE_TOKEN)).andReturn()));
    }

    // ===============================================================
    // 工具
    // ===============================================================

    /** 受理一次生成,返回 taskId。 */
    private String taskIdAfterSuccess(Long planId, Integer angles) throws Exception {
        String taskId = taskIdOf(postGenerate(generateBody(planId, angles)));
        String body = angles != null && angles > 1
                ? successBody(taskId, "default", "angle1")
                : successBody(taskId, "default");
        assertEquals(0, codeOf(callback(body)));
        return taskId;
    }

    private long planIdOf(String taskId) {
        Long planId = taskOf(taskId).getPlanId();
        assertNotNull(planId, "任务成功却没落方案 id");
        return planId;
    }

    /** 受理请求体。{@code planId}/{@code angles} 为 null 时整个字段省略。 */
    private String generateBody(Long planId, Integer angles) {
        StringBuilder sb = new StringBuilder("{\"space\":\"bathroom\",\"style\":\"modern\"");
        if (angles != null) {
            sb.append(",\"angles\":").append(angles);
        }
        if (planId != null) {
            sb.append(",\"planId\":").append(planId);
        }
        sb.append("""
                ,"layout":{"spaces":[{"type":"bathroom","name":"卫生间",
                  "length":2,"width":1.8,"height":2.4,"wallCount":4}]}
                ,"parts":[{"part":"floor","sku":"T11-FLOOR"},{"part":"wall","sku":"T11-WALL"}]
                }""");
        return sb.toString();
    }

    private String callbackBody(String taskId, String status, int progress, String error) {
        String errorJson = error == null ? "" : """
                ,"error":{"code":40001,"message":"%s"}""".formatted(error);
        return """
                {"taskId":"%s","status":"%s","progress":%d%s}
                """.formatted(taskId, status, progress, errorJson);
    }

    /** success 回调体,views 按顺序给视角名。 */
    private String successBody(String taskId, String... views) {
        StringBuilder images = new StringBuilder();
        for (int i = 0; i < views.length; i++) {
            if (i > 0) {
                images.append(',');
            }
            // 替身图的地址指向 Java(§3.3):Python 只在内网,前端够不到它的地址
            images.append("""
                    {"url":"http://java.test/static/placeholder.svg?v=%s","view":"%s","isCover":%s}"""
                    .formatted(views[i], views[i], "default".equals(views[i])));
        }
        return """
                {"taskId":"%s","status":"success","progress":100,"imageUrls":[%s]}
                """.formatted(taskId, images);
    }

    private MockHttpServletRequestBuilder postGenerate(String body) {
        return post(GENERATE).contentType(MediaType.APPLICATION_JSON).content(body)
                .cookie(cookieFor(USER));
    }

    /** 发一次内部回调。它总是要立刻拿到响应来断言,所以直接返回 MvcResult。 */
    private MvcResult callback(String body) throws Exception {
        return mvc.perform(post(CALLBACK).contentType(MediaType.APPLICATION_JSON).content(body)
                .header(SERVICE_TOKEN_HEADER, SERVICE_TOKEN)).andReturn();
    }

    private MvcResult progressOf(String taskId) throws Exception {
        return mvc.perform(get(GENERATE + "/" + taskId).cookie(cookieFor(USER))).andReturn();
    }

    /** 直接签一个 access 令牌当 Cookie —— 登录要 Redis,而本测试验的不是登录。 */
    private Cookie cookieFor(long userId) {
        return new Cookie("access_token", jwtService.issueAccess(userId, SubjectType.USER));
    }

    /** 受理一次生成并取回 taskId。 */
    private String taskIdOf(MockHttpServletRequestBuilder request) throws Exception {
        return dataOf(mvc.perform(request).andReturn()).get("taskId").asText();
    }

    private JsonNode dataOf(MockHttpServletRequestBuilder request) throws Exception {
        return dataOf(mvc.perform(request).andReturn());
    }

    private JsonNode dataOf(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString();
        JsonNode root = objectMapper.readTree(body);
        assertEquals(0, root.get("code").asInt(), "受理应成功: " + body);
        return root.get("data");
    }

    private static int codeOf(MvcResult result) throws Exception {
        return new ObjectMapper().readTree(result.getResponse().getContentAsString())
                .get("code").asInt();
    }

    private GenerationTask taskOf(String taskId) {
        return taskMapper.selectOne(Wrappers.<GenerationTask>lambdaQuery()
                .eq(GenerationTask::getTaskId, taskId));
    }

    private JsonNode payloadOf(String taskId) throws Exception {
        String payload = taskOf(taskId).getPayload();
        assertNotNull(payload);
        return objectMapper.readTree(payload);
    }

    private OutboxMessage outboxOf(String taskId) {
        List<OutboxMessage> rows = outboxMapper.selectList(Wrappers.<OutboxMessage>lambdaQuery()
                .eq(OutboxMessage::getAggregateId, taskId)
                .orderByAsc(OutboxMessage::getId));
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * 从 queue.generate 里捞出本次任务的消息。
     *
     * <p>按 taskId 筛选而不是"取第一条":投递器是定时任务,队列里还留着别的用例、
     * 别的测试类的消息,取第一条会拿到别人的东西 —— 那是彻底的假阳。
     */
    private JsonNode awaitMessage(String taskId) throws Exception {
        Predicate<JsonNode> mine = node -> taskId.equals(node.path("taskId").asText());
        long deadline = System.currentTimeMillis() + 30_000;
        JsonNode lastSeen = null;
        while (System.currentTimeMillis() < deadline) {
            Object received = rabbitTemplate.receiveAndConvert(RabbitTopologyConfig.QUEUE_GENERATE);
            if (received == null) {
                Thread.sleep(100);
                continue;
            }
            JsonNode node = objectMapper.readTree(String.valueOf(received));
            lastSeen = node;
            if (mine.test(node)) {
                return node;
            }
        }
        throw new AssertionError("30 秒内未在 queue.generate 收到 taskId=" + taskId
                + " 的消息,最后看到: " + lastSeen);
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
                planPartMapper.delete(Wrappers.<PlanPart>lambdaQuery()
                        .eq(PlanPart::getPlanId, plan.getId()));
                planImageMapper.delete(Wrappers.<PlanImage>lambdaQuery()
                        .eq(PlanImage::getPlanId, plan.getId()));
                planMapper.deleteById(plan.getId());
            }
        }
    }

}
