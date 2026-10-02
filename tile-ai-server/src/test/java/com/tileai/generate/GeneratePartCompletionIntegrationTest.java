package com.tileai.generate;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tileai.common.agent.AgentClient;
import com.tileai.common.error.BusinessException;
import com.tileai.common.error.ErrorCode;
import com.tileai.common.outbox.OutboxMapper;
import com.tileai.common.outbox.OutboxMessage;
import com.tileai.common.security.JwtService;
import com.tileai.common.security.SubjectType;
import com.tileai.generate.entity.GenerationTask;
import com.tileai.generate.mapper.GenerationTaskMapper;

import jakarta.servlet.http.Cookie;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T-1.9 验收:受理生成时的未选部位 AI 补齐(架构 §11.2;PRD §5.2.4)。
 *
 * <p><b>需要 MySQL</b>({@code -Ddb.verify=true})。<b>不需要 Python</b> ——
 * {@link AgentClient} 用 {@code @MockitoBean} 换成替身,断言"该不该调、调的什么、
 * 拿回来的怎么并进去"。
 *
 * <p>三条判据,每条都对应一个会**静默出错**的地方:
 * <ol>
 *   <li><b>有缺才调</b> —— 两个部位都选了还去问一次模型,是白花一次钱(和 30s 超时窗口);</li>
 *   <li><b>补齐项真的进了消息体</b> —— 进了就必须带 {@code source=ai},否则方案里那块砖
 *       会被当成用户自己选的;</li>
 *   <li><b>补不上时降级、照常受理</b> —— 用户不该因为"库里没有合适的砖"而被卡住。</li>
 * </ol>
 *
 * <p>只对自己造的数据下结论:用户号段 {@code 8_819_00xx},清理只按它删。
 */
@EnabledIfSystemProperty(named = "db.verify", matches = "true",
        disabledReason = "需要 MySQL;用 -Ddb.verify=true 显式启用(T-1.9 验证)")
@SpringBootTest(properties = {
        "spring.flyway.enabled=false",
        "tile.auth.secret=0123456789abcdef0123456789abcdef",
        "tile.crypto.layout-key=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=",
        "tile.agent.service-token=t19-test-token",
        // 关掉补偿扫描:它会把 pending 任务挪走,断言"任务刚落库"的用例会飘
        "tile.generate.compensation-enabled=false",
        // 关掉"受理前探测消费端":本机跑测试没有 Python 消费端,consumerCount 恒为 0,
        // 不关的话本类所有 POST /generate 都会得到 40002。
        "tile.generate.require-consumer=false",
        "tile.outbox.enabled=false"})
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GeneratePartCompletionIntegrationTest {

    private static final String GENERATE = "/api/v1/generate";
    private static final long USER = 8_819_0001L;

    private static final String PARTS_FLOOR_ONLY = "[{\"part\":\"floor\",\"sku\":\"T19-FLOOR\"}]";
    private static final String PARTS_BOTH = "[{\"part\":\"floor\",\"sku\":\"T19-FLOOR\"},"
            + "{\"part\":\"wall\",\"sku\":\"T19-WALL\"}]";
    private static final String PARTS_LOCAL_ONLY = "[{\"part\":\"local\",\"sku\":\"T19-LOCAL\"}]";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private GenerationTaskMapper taskMapper;

    @Autowired
    private OutboxMapper outboxMapper;

    @MockitoBean
    private AgentClient agentClient;

    @BeforeEach
    void resetState() {
        clean();
    }

    @AfterAll
    void cleanupAfterAll() {
        clean();
    }

    // ------------------------------------------------------------------

    @Test
    @DisplayName("只选了地面 → 问一次补齐,补齐的墙面带 source=ai 进消息体")
    void completionRunsAndAiPartReachesPayload() throws Exception {
        when(agentClient.post(eq(PartCompletionService.COMPLETE_PATH), any())).thenReturn(
                partsNode("""
                        [{"part":"floor","sku":"T19-FLOOR","source":"user"},
                         {"part":"wall","sku":"T19-AI-WALL","source":"ai"}]"""));

        String taskId = taskIdOf(generateRequest(PARTS_FLOOR_ONLY));

        // ① 该调,而且请求体对
        ArgumentCaptor<Object> body = ArgumentCaptor.forClass(Object.class);
        verify(agentClient).post(eq(PartCompletionService.COMPLETE_PATH), body.capture());
        JsonNode sent = objectMapper.valueToTree(body.getValue());
        assertEquals("bathroom", sent.get("space").asText());
        assertEquals(1, sent.get("selectedParts").size(), "只该把自己选的部位递过去");
        assertEquals("floor", sent.get("selectedParts").get(0).get("part").asText());

        // ② 补齐项真的进了消息体,且标着 ai
        JsonNode parts = payloadOf(taskId).get("parts");
        assertEquals(2, parts.size());
        JsonNode wall = parts.get(1);
        assertEquals("wall", wall.get("part").asText());
        assertEquals("T19-AI-WALL", wall.get("sku").asText());
        assertEquals("ai", wall.get("source").asText(),
                "补齐的砖必须带 source=ai —— 否则会被当成用户自己选的");
        assertEquals("user", parts.get(0).get("source").asText());
    }

    @Test
    @DisplayName("发件箱里那一串与任务快照**逐字相同** —— Python 收到的就是库里存的")
    void outboxPayloadMatchesTaskSnapshot() throws Exception {
        when(agentClient.post(eq(PartCompletionService.COMPLETE_PATH), any())).thenReturn(
                partsNode("""
                        [{"part":"floor","sku":"T19-FLOOR","source":"user"},
                         {"part":"wall","sku":"T19-AI-WALL","source":"ai"}]"""));

        String taskId = taskIdOf(generateRequest(PARTS_FLOOR_ONLY));

        List<OutboxMessage> rows = outboxMapper.selectList(Wrappers.<OutboxMessage>lambdaQuery()
                .eq(OutboxMessage::getAggregateId, taskId));
        assertEquals(1, rows.size());
        assertEquals(taskPayload(taskId), rows.get(0).getPayload());
    }

    @Test
    @DisplayName("**两个部位都选了就不问** —— 白问一次就是白花一次模型调用")
    void bothPartsChosenSkipsCompletion() throws Exception {
        taskIdOf(generateRequest(PARTS_BOTH));

        verify(agentClient, never()).post(anyString(), any());
    }

    @Test
    @DisplayName("**补不上时降级:照常受理**,只是消息体里没有 ai 项")
    void completionFailureDegradesInsteadOfBlocking() throws Exception {
        when(agentClient.post(eq(PartCompletionService.COMPLETE_PATH), any()))
                .thenThrow(new BusinessException(ErrorCode.INTERNAL_ERROR));

        MvcResult result = mvc.perform(generateRequest(PARTS_FLOOR_ONLY)).andReturn();

        assertEquals(0, codeOf(result), "补齐失败不该把用户的生成请求打掉");
        String taskId = dataOf(result).get("taskId").asText();
        JsonNode parts = payloadOf(taskId).get("parts");
        assertEquals(1, parts.size(), "降级 = 只有用户自己选的那一个部位");
        assertFalse(parts.toString().contains("\"ai\""), "不该凭空多出 ai 项");
    }

    @Test
    @DisplayName("Python 回了坏结构也降级(而不是把半个结果并进去)")
    void malformedCompletionDegrades() throws Exception {
        when(agentClient.post(eq(PartCompletionService.COMPLETE_PATH), any()))
                .thenReturn(objectMapper.createObjectNode()); // 没有 parts 数组

        MvcResult result = mvc.perform(generateRequest(PARTS_FLOOR_ONLY)).andReturn();

        assertEquals(0, codeOf(result));
        assertEquals(1, payloadOf(dataOf(result).get("taskId").asText()).get("parts").size());
    }

    @Test
    @DisplayName("只选了局部 → 地面与墙面都要补,而**局部本身不被补成 ai**")
    void localDoesNotGetCompleted() throws Exception {
        when(agentClient.post(eq(PartCompletionService.COMPLETE_PATH), any())).thenReturn(
                partsNode("""
                        [{"part":"local","sku":"T19-LOCAL","source":"user"},
                         {"part":"floor","sku":"T19-AI-FLOOR","source":"ai"},
                         {"part":"wall","sku":"T19-AI-WALL","source":"ai"}]"""));

        String taskId = taskIdOf(generateRequest(PARTS_LOCAL_ONLY));

        JsonNode parts = payloadOf(taskId).get("parts");
        assertEquals(3, parts.size());
        assertEquals("local", parts.get(0).get("part").asText());
        assertEquals("user", parts.get(0).get("source").asText(), "局部是用户选的,不是 AI 补的");
        assertEquals("ai", parts.get(1).get("source").asText());
        assertEquals("ai", parts.get(2).get("source").asText());
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    private JsonNode partsNode(String partsJson) throws Exception {
        return objectMapper.createObjectNode()
                .set("parts", objectMapper.readTree(partsJson));
    }

    /** 发一次受理请求。名字刻意不叫 `post` —— 那会与 `MockMvcRequestBuilders.post` 撞名。 */
    private MockHttpServletRequestBuilder generateRequest(String partsJson) {
        return MockMvcRequestBuilders.post(GENERATE)
                .cookie(cookie())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(partsJson));
    }

    private String body(String partsJson) {
        return """
                {"space":"bathroom","style":"modern","angles":1,
                 "layout":{"spaces":[{"type":"bathroom","name":"卫生间",
                   "length":2,"width":1.8,"height":2.4,"wallCount":4}]},
                 "parts":%s}""".formatted(partsJson);
    }

    private Cookie cookie() {
        return new Cookie("access_token",
                jwtService.issueAccess(USER, SubjectType.USER));
    }

    private String taskIdOf(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
        MvcResult result = mvc.perform(request).andReturn();
        assertEquals(0, codeOf(result),
                () -> "受理应成功:" + new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8));
        return dataOf(result).get("taskId").asText();
    }

    private JsonNode dataOf(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private int codeOf(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("code").asInt();
    }

    private String taskPayload(String taskId) {
        GenerationTask task = taskMapper.selectOne(Wrappers.<GenerationTask>lambdaQuery()
                .eq(GenerationTask::getTaskId, taskId));
        assertTrue(task != null, "任务没落库:" + taskId);
        return task.getPayload();
    }

    private JsonNode payloadOf(String taskId) throws Exception {
        return objectMapper.readTree(taskPayload(taskId));
    }

    private void clean() {
        for (GenerationTask task : taskMapper.selectList(Wrappers.<GenerationTask>lambdaQuery()
                .eq(GenerationTask::getUserId, USER))) {
            outboxMapper.delete(Wrappers.<OutboxMessage>lambdaQuery()
                    .eq(OutboxMessage::getAggregateId, task.getTaskId()));
            taskMapper.deleteById(task.getId());
        }
    }
}
