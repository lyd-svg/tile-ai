package com.tileai.agent;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tileai.agent.entity.ChatMessage;
import com.tileai.agent.entity.ChatSession;
import com.tileai.agent.mapper.ChatMessageMapper;
import com.tileai.agent.mapper.ChatSessionMapper;
import com.tileai.common.agent.AgentChatStreamClient;
import com.tileai.common.agent.ChatEvent;
import com.tileai.common.security.JwtService;
import com.tileai.common.security.SubjectType;

import jakarta.servlet.http.Cookie;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * T-1.6 验收:C 端「AI 咨询」三条接口
 * (契约 {@code openapi-c.yaml} 的 agent 分组;PRD §5.2.3、架构 §11.3)。
 *
 * <p><b>需要 MySQL</b>({@code -Ddb.verify=true} 显式启用)。<b>不需要 Redis、不需要
 * RabbitMQ</b> —— 登录态由 {@link JwtService} 直接签一个 access 令牌充当(登录本身
 * 要 Redis,而本测试验的不是登录)。<b>不需要 Python</b> ——
 * {@link AgentChatStreamClient} 用 {@code @MockitoBean} 换成可编程的替身。
 *
 * <p><b>为什么门禁拦不住这里的重点</b>:契约比对**不看 SSE 事件集**
 * (见 {@code openapi-verification.md} §40.4)。所以"首帧是 sessionId、refs 至多一帧、
 * 失败时 error 与 [DONE] 互斥、失败不落 assistant"这些,只有本类能证。
 *
 * <h2>只对自己造的数据下结论</h2>
 *
 * <p>用户 id 用 {@code 8_816_00xx} 这一段固定大号,清理只删这两个用户的会话与消息
 * —— 不对全表条数做任何断言。
 */
@EnabledIfSystemProperty(named = "db.verify", matches = "true",
        disabledReason = "需要 MySQL;用 -Ddb.verify=true 显式启用(T-1.6 验证)")
@SpringBootTest(properties = {
        "spring.flyway.enabled=false",
        "tile.auth.secret=0123456789abcdef0123456789abcdef",
        "tile.crypto.layout-key=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=",
        "tile.generate.compensation-enabled=false",
        "tile.outbox.enabled=false"})
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AgentChatIntegrationTest {

    private static final String CHAT = "/api/v1/agent/chat";
    private static final String SESSIONS = "/api/v1/agent/chat/sessions";

    private static final long USER = 8_816_0001L;
    private static final long OTHER_USER = 8_816_0002L;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ChatSessionMapper sessionMapper;

    @Autowired
    private ChatMessageMapper messageMapper;

    @MockitoBean
    private AgentChatStreamClient streamClient;

    @BeforeEach
    void cleanUp() {
        for (ChatSession session : sessionMapper.selectList(Wrappers.<ChatSession>lambdaQuery()
                .in(ChatSession::getUserId, List.of(USER, OTHER_USER)))) {
            messageMapper.delete(Wrappers.<ChatMessage>lambdaQuery()
                    .eq(ChatMessage::getSessionId, session.getId()));
        }
        sessionMapper.delete(Wrappers.<ChatSession>lambdaQuery()
                .in(ChatSession::getUserId, List.of(USER, OTHER_USER)));
    }

    // ===============================================================
    // 新会话:帧序列 + 落库
    // ===============================================================

    @Test
    @DisplayName("新会话:首帧 sessionId(仅一次)→ delta → refs → [DONE],并落 1 会话 + 2 消息")
    void newSessionProxiesFramesAndPersists() throws Exception {
        stubStream(
                new ChatEvent.Delta("雪瓷白"),
                new ChatEvent.Delta("很适合"),
                new ChatEvent.Refs(refsNode("TL-3012")),
                new ChatEvent.Done());

        String body = chatBody(null, "卫生间配什么墙砖");
        String text = awaitStream(call(post(CHAT).contentType(MediaType.APPLICATION_JSON).content(body), USER));

        List<String> frames = framesOf(text);
        assertTrue(frames.get(0).contains("\"sessionId\""), "首帧必须是 sessionId: " + frames);
        assertEquals(1, countOf(frames, "sessionId"), "sessionId 只该出现一次: " + frames);
        assertEquals("[DONE]", frames.get(frames.size() - 1), "正常结局以 [DONE] 收尾: " + frames);
        assertEquals(List.of("雪瓷白", "很适合"), deltasOf(frames), "delta 顺序与内容: " + frames);
        assertEquals(1, countOf(frames, "\"refs\""), "refs 至多一帧: " + frames);

        ChatSession session = onlySession(USER);
        List<ChatMessage> messages = messagesOf(session.getId());
        assertEquals(2, messages.size(), "一轮问答应落 user + assistant 两条: " + messages);
        assertEquals("user", messages.get(0).getRole());
        assertEquals("卫生间配什么墙砖", messages.get(0).getContent());
        assertEquals("assistant", messages.get(1).getRole());
        assertEquals("雪瓷白很适合", messages.get(1).getContent(), "assistant 内容应由 delta 累积而成");
        assertTrue(messages.get(1).getRefs().contains("TL-3012"), "refs 应落库: " + messages.get(1).getRefs());
    }

    @Test
    @DisplayName("Python 若发了 sessionId 帧,Java 吞掉 —— C 端仍只有一帧")
    void sessionFrameFromPythonIsSwallowed() throws Exception {
        stubStream(
                new ChatEvent.SessionFrame("99999"),
                new ChatEvent.Delta("好"),
                new ChatEvent.Done());

        String text = awaitStream(call(post(CHAT).contentType(MediaType.APPLICATION_JSON)
                .content(chatBody(null, "推荐")), USER));

        assertEquals(1, countOf(framesOf(text), "sessionId"), "不该把 Python 的 sessionId 也透出去");
        assertEquals(String.valueOf(onlySession(USER).getId()),
                sessionIdOf(framesOf(text)), "首帧的 sessionId 必须是 Java 自己那条会话");
    }

    // ===============================================================
    // 续聊
    // ===============================================================

    @Test
    @DisplayName("带 sessionId:复用同一条会话,只追加消息")
    void resumeReusesSession() throws Exception {
        long sessionId = insertSession(USER, "旧会话");
        stubStream(new ChatEvent.Delta("继续"), new ChatEvent.Done());

        awaitStream(call(post(CHAT).contentType(MediaType.APPLICATION_JSON)
                .content(chatBody(String.valueOf(sessionId), "接着说")), USER));

        List<ChatSession> sessions = sessionMapper.selectList(Wrappers.<ChatSession>lambdaQuery()
                .eq(ChatSession::getUserId, USER));
        assertEquals(1, sessions.size(), "续聊不该新建会话: " + sessions);
        assertEquals(2, messagesOf(sessionId).size(), "应只在原会话下追加两条");
    }

    // ===============================================================
    // 越权 / 未登录(开流之前的失败 → 普通 JSON,不是 SSE)
    // ===============================================================

    @Test
    @DisplayName("别人的会话 id → 30001,且不开流")
    void otherUsersSessionIsNotFound() throws Exception {
        long theirs = insertSession(OTHER_USER, "别人的会话");
        stubStream(new ChatEvent.Delta("x"), new ChatEvent.Done());

        MvcResult result = call(post(CHAT).contentType(MediaType.APPLICATION_JSON)
                .content(chatBody(String.valueOf(theirs), "偷看")), USER);

        assertEquals(30001, codeOf(result), "措辞与码都应与「不存在」一致,不能变成可枚举的探针");
        assertFalse(result.getRequest().isAsyncStarted(), "失败必须发生在开流之前");
    }

    @Test
    @DisplayName("不存在的 / 非数字的 sessionId → 一律 30001")
    void unknownOrMalformedSessionIsNotFound() throws Exception {
        assertEquals(30001, codeOf(call(post(CHAT).contentType(MediaType.APPLICATION_JSON)
                .content(chatBody("99999999", "x")), USER)));
        assertEquals(30001, codeOf(call(post(CHAT).contentType(MediaType.APPLICATION_JSON)
                .content(chatBody("s_abc", "x")), USER)));
    }

    @Test
    @DisplayName("三条接口未登录一律 401")
    void allEndpointsRequireLogin() throws Exception {
        assertEquals(401, mvc.perform(post(CHAT).contentType(MediaType.APPLICATION_JSON)
                .content(chatBody(null, "x"))).andReturn().getResponse().getStatus());
        assertEquals(401, mvc.perform(get(SESSIONS)).andReturn().getResponse().getStatus());
        assertEquals(401, mvc.perform(get(SESSIONS + "/1")).andReturn().getResponse().getStatus());
    }

    @Test
    @DisplayName("message 为空 → 10001(开流前的普通信封)")
    void blankMessageIsParamInvalid() throws Exception {
        assertEquals(10001, codeOf(call(post(CHAT).contentType(MediaType.APPLICATION_JSON)
                .content(chatBody(null, "   ")), USER)));
    }

    // ===============================================================
    // 流中途失败
    // ===============================================================

    @Test
    @DisplayName("流中途 error:error 是最后一帧、没有 [DONE]、不落 assistant")
    void midStreamErrorIsExclusiveAndNotPersisted() throws Exception {
        stubStream(
                new ChatEvent.Delta("半句"),
                new ChatEvent.Error(50001, "AI 咨询超出范围", "t-9"));

        String text = awaitStream(call(post(CHAT).contentType(MediaType.APPLICATION_JSON)
                .content(chatBody(null, "多少钱一平")), USER));

        List<String> frames = framesOf(text);
        assertTrue(frames.get(frames.size() - 1).contains("\"error\""), "最后必须是 error 帧: " + frames);
        assertFalse(frames.contains("[DONE]"), "error 与 [DONE] 互斥: " + frames);
        assertTrue(frames.get(frames.size() - 1).contains("50001"), "业务码应透传: " + frames);

        ChatSession session = onlySession(USER);
        List<ChatMessage> messages = messagesOf(session.getId());
        assertEquals(1, messages.size(), "失败不该落 assistant —— 半条答案不能沉淀成历史: " + messages);
        assertEquals("user", messages.get(0).getRole());
    }

    @Test
    @DisplayName("流意外结束(无 [DONE] 无 error)→ 补一帧 90001")
    void prematureEofBecomesInternalError() throws Exception {
        stubStream(new ChatEvent.Delta("只有半句"));

        String text = awaitStream(call(post(CHAT).contentType(MediaType.APPLICATION_JSON)
                .content(chatBody(null, "推荐")), USER));

        List<String> frames = framesOf(text);
        assertTrue(frames.get(frames.size() - 1).contains("90001"), "不能静默断流: " + frames);
        assertFalse(frames.contains("[DONE]"));
    }

    // ===============================================================
    // 列表 / 详情
    // ===============================================================

    @Test
    @DisplayName("列表:只看得到自己的会话,分页四字段齐备")
    void listShowsOnlyMine() throws Exception {
        long mine = insertSession(USER, "T16-我的会话");
        insertSession(OTHER_USER, "T16-别人的会话");

        JsonNode data = dataOf(call(get(SESSIONS), USER));

        List<String> ids = new ArrayList<>();
        data.get("list").forEach(node -> ids.add(node.get("id").asText()));
        assertTrue(ids.contains(String.valueOf(mine)), "自己的会话没出现: " + ids);
        assertEquals(1, ids.size(), "别人的会话不该出现: " + ids);
        assertTrue(data.has("list") && data.has("total") && data.has("page") && data.has("size"),
                "契约 T-3 的分页四字段: " + data);
    }

    @Test
    @DisplayName("详情:形状为 {session, messages[]},消息按 id 升序")
    void detailShapeAndOrdering() throws Exception {
        long sessionId = insertSession(USER, "T16-详情");
        insertMessage(sessionId, "user", "问题", null);
        insertMessage(sessionId, "assistant", "回答", "[{\"sku\":\"TL-3012\",\"part\":\"wall\"}]");

        JsonNode data = dataOf(call(get(SESSIONS + "/" + sessionId), USER));

        assertEquals(String.valueOf(sessionId), data.get("session").get("id").asText());
        JsonNode messages = data.get("messages");
        assertEquals(2, messages.size());
        assertEquals("user", messages.get(0).get("role").asText());
        assertEquals("assistant", messages.get(1).get("role").asText());
        assertEquals("TL-3012", messages.get(1).get("refs").get(0).get("sku").asText(),
                "refs 应被解析成结构化数组,而不是一串 JSON 文本");
    }

    @Test
    @DisplayName("详情:别人的 / 不存在的一律 30001,措辞一致")
    void detailIsNotFoundForOthers() throws Exception {
        long theirs = insertSession(OTHER_USER, "T16-别人的");

        MvcResult other = call(get(SESSIONS + "/" + theirs), USER);
        MvcResult missing = call(get(SESSIONS + "/99999999"), USER);

        assertEquals(30001, codeOf(other));
        assertEquals(30001, codeOf(missing));
        assertEquals(messageOf(other), messageOf(missing), "两种情形必须同一句话,否则成了枚举探针");
    }

    // ===============================================================
    // 工具
    // ===============================================================

    /** 打桩:让流式客户端返回一串预置事件。 */
    private void stubStream(ChatEvent... events) {
        AgentChatStreamClient.AgentChatStream stream = new AgentChatStreamClient.AgentChatStream() {
            @Override
            public Iterator<ChatEvent> events() {
                return List.of(events).iterator();
            }

            @Override
            public void close() {
                // 预置列表,无需释放
            }
        };
        when(streamClient.open(anyString(), anyLong(), anyString(), anyString())).thenReturn(stream);
    }

    private Cookie cookieFor(long userId) {
        return new Cookie("access_token", jwtService.issueAccess(userId, SubjectType.USER));
    }

    private MvcResult call(MockHttpServletRequestBuilder request, long userId) throws Exception {
        return mvc.perform(request.cookie(cookieFor(userId))).andReturn();
    }

    /**
     * 等 SSE 流跑完再取正文。
     *
     * <p>SSE 是**异步**的:{@code mvc.perform} 在编排线程把帧写完之前就返回了,
     * 所以要轮询等一个结束标记。{@code [DONE]} 不一定会出现(失败时是 error),
     * 故这里等到正文"不再增长"或看到任一终局标记为止。
     */
    private String awaitStream(MvcResult result) throws Exception {
        long deadline = System.currentTimeMillis() + 10_000;
        String last = "";
        while (System.currentTimeMillis() < deadline) {
            String content = new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
            if (content.contains("[DONE]") || content.contains("\"error\"")) {
                return content;
            }
            last = content;
            Thread.sleep(20);
        }
        return last;
    }

    private String chatBody(String sessionId, String message) throws Exception {
        return objectMapper.writeValueAsString(objectMapper.createObjectNode()
                .put("sessionId", sessionId)
                .put("message", message));
    }

    private JsonNode refsNode(String sku) {
        return objectMapper.createArrayNode().add(objectMapper.createObjectNode()
                .put("sku", sku).put("name", "雪瓷白").put("part", "wall"));
    }

    /** 取所有 {@code data:} 帧的载荷({@code [DONE]} 保持字面量)。 */
    private static List<String> framesOf(String body) {
        List<String> frames = new ArrayList<>();
        for (String line : body.split("\n")) {
            String trimmed = line.strip();
            if (trimmed.startsWith("data:")) {
                frames.add(trimmed.substring("data:".length()).strip());
            }
        }
        return frames;
    }

    private static List<String> deltasOf(List<String> frames) {
        List<String> deltas = new ArrayList<>();
        for (String frame : frames) {
            if (frame.contains("\"delta\"")) {
                deltas.add(frame.substring(frame.indexOf(':') + 1, frame.lastIndexOf('}')).replace("\"", ""));
            }
        }
        return deltas;
    }

    private static int countOf(List<String> frames, String needle) {
        return (int) frames.stream().filter(frame -> frame.contains(needle)).count();
    }

    private static String sessionIdOf(List<String> frames) {
        String frame = frames.get(0);
        return frame.replaceAll(".*\"sessionId\"\\s*:\\s*\"([^\"]*)\".*", "$1");
    }

    private long insertSession(long userId, String title) {
        ChatSession session = new ChatSession();
        session.setUserId(userId);
        session.setTitle(title);
        sessionMapper.insert(session);
        return session.getId();
    }

    private void insertMessage(long sessionId, String role, String content, String refs) {
        ChatMessage message = new ChatMessage();
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setContent(content);
        message.setRefs(refs);
        messageMapper.insert(message);
    }

    private ChatSession onlySession(long userId) {
        List<ChatSession> sessions = sessionMapper.selectList(Wrappers.<ChatSession>lambdaQuery()
                .eq(ChatSession::getUserId, userId));
        assertEquals(1, sessions.size(), "应当恰好有一条会话: " + sessions);
        assertNotNull(sessions.get(0).getId());
        return sessions.get(0);
    }

    private List<ChatMessage> messagesOf(long sessionId) {
        return messageMapper.selectList(Wrappers.<ChatMessage>lambdaQuery()
                .eq(ChatMessage::getSessionId, sessionId)
                .orderByAsc(ChatMessage::getId));
    }

    private int codeOf(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("code").asInt();
    }

    private String messageOf(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("message").asText();
    }

    private JsonNode dataOf(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }
}
