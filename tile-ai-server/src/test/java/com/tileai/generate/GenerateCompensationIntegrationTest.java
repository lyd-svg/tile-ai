package com.tileai.generate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tileai.common.mq.RabbitTopologyConfig;
import com.tileai.common.outbox.OutboxMessage;
import com.tileai.common.outbox.OutboxMapper;
import com.tileai.generate.dto.GenerateTaskPayload;
import com.tileai.generate.dto.PartSelection;
import com.tileai.generate.entity.GenerationTask;
import com.tileai.generate.mapper.GenerationTaskMapper;
import com.tileai.layout.model.LayoutModel;
import com.tileai.layout.model.LayoutSpace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T-1.11 验收:补偿扫描(架构 §6.4「长时间停留的任务」)。
 *
 * <p><b>需要 MySQL</b>({@code -Ddb.verify=true})。<b>不需要 RabbitMQ</b> ——
 * 扫描这一环只写 {@code t_outbox},真正把消息发出去是投递器的事。
 *
 * <p>这里<b>直接造任务行</b>而不走 {@code POST /generate}:要验的是"停滞多久算卡住、
 * 什么情况下不该动、什么时候放弃",而这些状态要精确控制时间戳才能摆出来。
 * 走 HTTP 就只能干等 60 秒,还得靠 sleep 撞运气。
 *
 * <p>时间戳用 {@code JdbcTemplate} 直接改:{@code updated_at} 是 MySQL 的
 * {@code ON UPDATE} 自动维护的,没有别的办法把它往回拨。
 *
 * <p>定时器在测试配置里关掉了({@code tile.generate.compensation-enabled=false}),
 * 所以每一轮都靠显式调用 {@code scanOnce()} 驱动 —— 断言的是"这一轮做了什么",
 * 而不是"过一会儿大概会发生什么"。
 */
@EnabledIfSystemProperty(named = "db.verify", matches = "true",
        disabledReason = "需要 MySQL;用 -Ddb.verify=true 显式启用")
@SpringBootTest(properties = {
        "spring.flyway.enabled=false",
        "tile.auth.secret=0123456789abcdef0123456789abcdef",
        "tile.crypto.layout-key=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=",
        "tile.generate.compensation-enabled=false"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GenerateCompensationIntegrationTest {

    /** 与 {@code GenerateIntegrationTest} 用不同的号段,两个类同时跑也互不干扰。 */
    private static final long USER = 8_811_0101L;

    private static final String PENDING = GenerationTask.STATUS_PENDING;
    private static final String RUNNING = GenerationTask.STATUS_RUNNING;

    @Autowired
    private GenerateCompensationScanner scanner;

    @Autowired
    private GenerationTaskMapper taskMapper;

    @Autowired
    private OutboxMapper outboxMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetState() {
        cleanup();
    }

    @AfterAll
    void cleanupAfterAll() {
        cleanup();
    }

    // ===============================================================
    // pending:什么该动、什么不该动
    // ===============================================================

    @Test
    @DisplayName("补偿:pending 停滞且发件箱里没有它的待发消息 ⇒ 重投,attempt 变 2")
    void stuckPendingWithoutOutboxIsResent() throws Exception {
        String taskId = insertTask(PENDING, 1);
        backdateUpdatedAt(taskId, 120);

        assertEquals(1, scanner.scanOnce());

        assertEquals(2, attemptOf(taskId), "重投后 attempt 必须递增 —— 否则永远到不了上限");
        List<OutboxMessage> rows = outboxRows(taskId);
        assertEquals(1, rows.size(), "重投要新登记一条待发消息");
        assertEquals(OutboxMessage.AGGREGATE_TASK, rows.get(0).getAggregateType());
        assertEquals(RabbitTopologyConfig.QUEUE_GENERATE, rows.get(0).getQueue());
        assertEquals(2, objectMapper.readTree(rows.get(0).getPayload()).get("attempt").asInt(),
                "投出去的必须是带上新 attempt 的那份 payload,否则 Python 看到的次数是错的");
        assertEquals(GenerationTask.STATUS_PENDING, taskOf(taskId).getStatus(),
                "重投不动状态机 —— running 由 Python 的回调驱动(§35.2)");
    }

    @Test
    @DisplayName("补偿:pending 还在等投递器(发件箱有待发行)⇒ 不动 —— 重投纯属重复")
    void stuckPendingWithPendingOutboxIsUntouched() throws Exception {
        String taskId = insertTask(PENDING, 1);
        backdateUpdatedAt(taskId, 120);
        // next_retry_at 推到很远:既模拟"投递器还没轮到它",也让测试期间的定时投递器
        // 不会真的把它发出去(投递器只取 next_retry_at <= now 的)
        insertOutboxRow(taskId, payloadOf(taskId), OutboxMessage.STATUS_PENDING);

        assertEquals(0, scanner.scanOnce());
        assertEquals(1, attemptOf(taskId), "还在等投递,不该再排一条重复消息");
    }

    @Test
    @DisplayName("补偿:刚受理、还没到阈值的任务不动(阈值是 60 秒)")
    void freshPendingIsUntouched() throws Exception {
        String taskId = insertTask(PENDING, 1);

        assertEquals(0, scanner.scanOnce());
        assertEquals(1, attemptOf(taskId));
    }

    // ===============================================================
    // running
    // ===============================================================

    @Test
    @DisplayName("补偿:running 停滞超过 360 秒 ⇒ 重投")
    void stuckRunningIsResent() throws Exception {
        String taskId = insertTask(RUNNING, 40);
        backdateUpdatedAt(taskId, 400);

        assertEquals(1, scanner.scanOnce());

        assertEquals(2, attemptOf(taskId));
        assertEquals(GenerationTask.STATUS_RUNNING, taskOf(taskId).getStatus());
        assertEquals(40, taskOf(taskId).getProgress(), "重投不该把进度冲掉");
    }

    @Test
    @DisplayName("补偿:running 只是慢(30 秒没更新)⇒ 不动。活着的任务靠进度回调刷 updated_at")
    void freshRunningIsUntouched() throws Exception {
        String taskId = insertTask(RUNNING, 40);
        backdateUpdatedAt(taskId, 30);

        assertEquals(0, scanner.scanOnce(),
                "4 张图最坏要 240 秒,30 秒没消息完全正常 —— 这时候重投会烧掉一次真实模型调用");
    }

    // ===============================================================
    // 放弃的两个出口
    // ===============================================================

    @Test
    @DisplayName("补偿:attempt 已到上限(3)⇒ 判 failed,不再重投")
    void attemptLimitGivesUp() throws Exception {
        String taskId = insertTask(PENDING, 1);
        rewriteAttempt(taskId, 3);
        backdateUpdatedAt(taskId, 120);

        assertEquals(1, scanner.scanOnce());

        GenerationTask task = taskOf(taskId);
        assertEquals(GenerationTask.STATUS_FAILED, task.getStatus());
        assertNotNull(task.getError(), "失败必须留下原因,否则前端只有一句「失败了」");
        assertEquals(0, outboxRows(taskId).size(), "都放弃了就不该再排消息出去");
    }

    @Test
    @DisplayName("补偿:超过硬截止(30 分钟)⇒ 判 failed —— 哪怕它一直在刷新 updated_at")
    void pastHardDeadlineGivesUp() throws Exception {
        String taskId = insertTask(RUNNING, 90);
        // 关键:updated_at 是**刚刚**刷过的,所以按停滞阈值(360 秒)根本扫不到它。
        // 这正是硬截止存在的理由 —— 一个"一直活着、却永远到不了 success"的任务,
        // 只靠停滞判断会永远漏过去。
        backdateCreatedAt(taskId, 31 * 60);

        assertEquals(1, scanner.scanOnce());

        assertEquals(GenerationTask.STATUS_FAILED, taskOf(taskId).getStatus());
        assertEquals(90, taskOf(taskId).getProgress(),
                "进度停在那一刻,不清零 —— 用户至少知道自己等到哪儿了");
    }

    @Test
    @DisplayName("补偿:payload 坏了(解析不出)⇒ 判 failed,而不是无限重投一条 Python 解不开的消息")
    void brokenPayloadGivesUp() throws Exception {
        String taskId = insertTask(PENDING, 0);
        // ⚠️ 不能塞"语法非法的 JSON":`payload` 列的类型就是 MySQL 的 `JSON`,
        // 数据库自己会先拒掉,写得进去的**必定是合法 JSON**。
        // 真实会发生的坏法只有一种 —— 是合法 JSON,但不是 `GenerateTaskPayload` 那个形状
        // (写入方改过字段、或写进了别的消息体)。这里用一个 JSON 数组代表它。
        jdbcTemplate.update("UPDATE t_generation_task SET payload = ? WHERE task_id = ?",
                "[1, 2, 3]", taskId);
        backdateUpdatedAt(taskId, 120);

        assertEquals(1, scanner.scanOnce());

        assertEquals(GenerationTask.STATUS_FAILED, taskOf(taskId).getStatus());
        assertEquals(0, outboxRows(taskId).size());
    }

    @Test
    @DisplayName("补偿:一轮里多个候选都被处理,而不是只捡第一个")
    void scansWholeBatch() throws Exception {
        String a = insertTask(PENDING, 1);
        String b = insertTask(RUNNING, 10);
        backdateUpdatedAt(a, 120);
        backdateUpdatedAt(b, 400);

        assertEquals(2, scanner.scanOnce());

        assertEquals(2, attemptOf(a));
        assertEquals(2, attemptOf(b));
    }

    // ===============================================================
    // 工具
    // ===============================================================

    /** 造一个任务行,payload 是契约形状的 {@code GenerateTaskPayload}。 */
    private String insertTask(String status, int progress) {
        String taskId = "g_comp_" + Long.toHexString(System.nanoTime());
        GenerationTask task = new GenerationTask();
        task.setTaskId(taskId);
        task.setUserId(USER);
        task.setType(GenerationTask.TYPE_IMAGE);
        task.setStatus(status);
        task.setProgress(progress);
        task.setPayload(payloadOf(taskId));
        taskMapper.insert(task);
        return taskId;
    }

    private String payloadOf(String taskId) {
        try {
            return objectMapper.writeValueAsString(new GenerateTaskPayload(
                    taskId,
                    GenerationTask.TYPE_IMAGE,
                    "t11-comp-trace",
                    USER,
                    null,
                    "bathroom",
                    "modern",
                    1,
                    List.of(new PartSelection("floor", "T11-FLOOR", null, "user")),
                    new LayoutModel(List.of(new LayoutSpace("bathroom", "卫生间",
                            new BigDecimal("2"), new BigDecimal("1.8"), new BigDecimal("2.4"),
                            4, null, null))),
                    GenerateTaskPayload.INITIAL_ATTEMPT,
                    "2026-09-30T10:00:00"));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void insertOutboxRow(String taskId, String payload, String status) {
        OutboxMessage message = new OutboxMessage();
        message.setAggregateType(OutboxMessage.AGGREGATE_TASK);
        message.setAggregateId(taskId);
        message.setQueue(RabbitTopologyConfig.QUEUE_GENERATE);
        message.setPayload(payload);
        message.setStatus(status);
        message.setRetryCount(0);
        // 推到 10 分钟后:测试跑完它也不会被后台投递器捡走,断言因此是确定的
        message.setNextRetryAt(LocalDateTime.now().plusMinutes(10));
        outboxMapper.insert(message);
    }

    /**
     * 把 {@code updated_at} 往回拨 N 秒。
     *
     * <p>显式赋值会覆盖 MySQL 的 {@code ON UPDATE CURRENT_TIMESTAMP} ——
     * 这正是这条 SQL 唯一的作用,否则摆不出"停滞了 400 秒"这个状态。
     */
    private void backdateUpdatedAt(String taskId, int seconds) {
        jdbcTemplate.update("UPDATE t_generation_task SET updated_at = ? WHERE task_id = ?",
                LocalDateTime.now().minusSeconds(seconds), taskId);
    }

    private void backdateCreatedAt(String taskId, int seconds) {
        jdbcTemplate.update("UPDATE t_generation_task SET created_at = ? WHERE task_id = ?",
                LocalDateTime.now().minusSeconds(seconds), taskId);
    }

    /** 把 payload 里的 attempt 改成指定值,模拟"已经重投过若干次"。 */
    private void rewriteAttempt(String taskId, int attempt) throws Exception {
        GenerateTaskPayload payload = objectMapper.readValue(
                taskOf(taskId).getPayload(), GenerateTaskPayload.class);
        jdbcTemplate.update("UPDATE t_generation_task SET payload = ? WHERE task_id = ?",
                objectMapper.writeValueAsString(payload.withAttempt(attempt)), taskId);
    }

    private int attemptOf(String taskId) throws Exception {
        return objectMapper.readTree(taskOf(taskId).getPayload()).get("attempt").asInt();
    }

    private GenerationTask taskOf(String taskId) {
        return taskMapper.selectOne(Wrappers.<GenerationTask>lambdaQuery()
                .eq(GenerationTask::getTaskId, taskId));
    }

    private List<OutboxMessage> outboxRows(String taskId) {
        return outboxMapper.selectList(Wrappers.<OutboxMessage>lambdaQuery()
                .eq(OutboxMessage::getAggregateId, taskId));
    }

    private void cleanup() {
        for (GenerationTask task : taskMapper.selectList(Wrappers.<GenerationTask>lambdaQuery()
                .eq(GenerationTask::getUserId, USER))) {
            assertTrue(task.getTaskId().startsWith("g_comp_"),
                    "清理只动本测试造的任务,绝不碰别人的行: " + task.getTaskId());
            outboxMapper.delete(Wrappers.<OutboxMessage>lambdaQuery()
                    .eq(OutboxMessage::getAggregateId, task.getTaskId()));
            taskMapper.deleteById(task.getId());
        }
    }
}
