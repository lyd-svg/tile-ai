package com.tileai.layout;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
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

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import com.tileai.ContractSchema;
import com.tileai.common.agent.AgentClient;
import com.tileai.layout.entity.UnitTemplate;
import com.tileai.layout.mapper.UnitTemplateMapper;
import com.tileai.layout.model.LayoutAreas;
import com.tileai.layout.model.LayoutModel;
import com.tileai.layout.model.LayoutSpace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * T-1.7:C 端户型录入(契约 {@code openapi-c.yaml}:{@code /layout/templates}、{@code /layout/parse})。
 *
 * <p><b>需要 MySQL</b>。用 {@code -Ddb.verify=true} 显式启用。
 *
 * <p>{@link AgentClient} 被替换成替身 —— Java 侧不该在单测里真的调 Python。
 * Python 侧自身的解析逻辑另有单测与真实联调({@code tests/test_layout_parse.py}、
 * {@code tests/test_gateway_live.py})。真实跨进程联调见 {@code /verify} 阶段的人工验证。
 */
@EnabledIfSystemProperty(named = "db.verify", matches = "true",
        disabledReason = "需要 MySQL;用 -Ddb.verify=true 显式启用(T-1.7 验证)")
@SpringBootTest(properties = {"spring.flyway.enabled=false",
        "tile.auth.secret=0123456789abcdef0123456789abcdef"})
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CLayoutIntegrationTest {

    private static final String TEMPLATES = "/api/v1/layout/templates";
    private static final String PARSE = "/api/v1/layout/parse";
    private static final String NAME_PREFIX = "T17-";

    private static final JsonSchema LAYOUT_MODEL_SCHEMA =
            JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
                    .getSchema(ContractSchema.resolved("openapi-common.yaml", "LayoutModel"));

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UnitTemplateMapper templateMapper;

    /** 替身:Java 侧不该在测试里真的出网调 Python。 */
    @MockitoBean
    private AgentClient agentClient;

    private Long onSaleId;
    private Long offSaleId;

    @BeforeAll
    void seed() {
        onSaleId = insertTemplate(NAME_PREFIX + "在售三居", UnitTemplate.STATUS_ON_SALE);
        offSaleId = insertTemplate(NAME_PREFIX + "下架三居", UnitTemplate.STATUS_OFF_SALE);
    }

    @AfterAll
    void cleanup() {
        templateMapper.delete(Wrappers.<UnitTemplate>lambdaQuery()
                .likeRight(UnitTemplate::getName, NAME_PREFIX));
    }

    private Long insertTemplate(String name, String status) {
        UnitTemplate template = new UnitTemplate();
        template.setName(name);
        template.setRoomCount(3);
        template.setAreaRange("80-100㎡");
        template.setStructure("三室两厅");
        template.setStatus(status);
        template.setLayout(new LayoutModel(List.of(room("bathroom", "卫生间",
                "2.0", "1.8", "2.7"))));
        templateMapper.insert(template);
        return template.getId();
    }

    private static LayoutSpace room(String type, String name, String length,
                                    String width, String height) {
        return new LayoutSpace(type, name, new BigDecimal(length), new BigDecimal(width),
                new BigDecimal(height), 4, null, null);
    }

    // ------------------------------------------------------------------
    // GET /layout/templates
    // ------------------------------------------------------------------

    @Test
    @DisplayName("模板列表**只返回上架项** —— 与产品库同一口径")
    void templatesOnlyExposeOnSale() throws Exception {
        MvcResult result = mvc.perform(get(TEMPLATES).param("size", "50")).andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());

        assertEquals(200, result.getResponse().getStatus());
        assertEquals(0, body.path("code").asInt(), body.toString());

        Set<String> statuses = new java.util.HashSet<>();
        boolean sawOnSale = false;
        for (JsonNode row : body.path("data").path("list")) {
            if (row.path("name").asText().startsWith(NAME_PREFIX)) {
                statuses.add(row.path("status").asText());
                sawOnSale = true;
            }
        }
        assertTrue(sawOnSale, "本该看到自己造的模板:" + body.path("data").path("list"));
        assertEquals(Set.of(UnitTemplate.STATUS_ON_SALE), statuses,
                "下架模板不该出现在 C 端列表里:" + statuses);
    }

    @Test
    @DisplayName("分页四字段齐备(契约 T-3)")
    void templatesArePaginated() throws Exception {
        JsonNode data = objectMapper.readTree(
                        mvc.perform(get(TEMPLATES)).andReturn().getResponse().getContentAsString())
                .path("data");

        assertTrue(data.has("list") && data.has("total") && data.has("page") && data.has("size"),
                data.toString());
    }

    // ------------------------------------------------------------------
    // POST /layout/parse —— params 路径
    // ------------------------------------------------------------------

    @Test
    @DisplayName("params 路径**不调模型**,就地校验并派生面积")
    void paramsPathDoesNotCallAgent() throws Exception {
        String body = """
                {"params":{"spaces":[{"type":"bathroom","name":"卫生间",
                  "length":2.0,"width":1.8,"height":2.7}]}}
                """;

        JsonNode response = postJson(PARSE, body);

        assertEquals(0, response.path("code").asInt(), response.toString());
        verify(agentClient, never()).post(anyString(), any());

        JsonNode space = response.path("data").path("spaces").get(0);
        // floor = 2.0 × 1.8 = 3.6;wall = 2×(2.0+1.8)×2.7 = 20.52;local = 0
        assertEquals(3.6, space.path("areas").path("floor").asDouble(), 0.0001);
        assertEquals(20.52, space.path("areas").path("wall").asDouble(), 0.0001);
        assertEquals(0.0, space.path("areas").path("local").asDouble(), 0.0001);
    }

    @Test
    @DisplayName("params 缺尺寸 → 10001,且 `data.errors[]` 指出缺哪一项")
    void paramsPathReportsMissingDimension() throws Exception {
        String body = """
                {"params":{"spaces":[{"type":"bathroom","length":2.0,"height":2.7}]}}
                """;

        MvcResult result = mvc.perform(post(PARSE).contentType(MediaType.APPLICATION_JSON)
                .content(body)).andReturn();
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());

        assertEquals(200, result.getResponse().getStatus(), "业务失败仍是 HTTP 200(T-13)");
        assertEquals(10001, response.path("code").asInt(), response.toString());
        assertTrue(response.path("data").path("errors").isArray(), response.toString());
        verify(agentClient, never()).post(anyString(), any());
    }

    @Test
    @DisplayName("params 与 text 都给时 **params 优先**")
    void paramsWinsOverText() throws Exception {
        String body = """
                {"text":"随便写点什么","params":{"spaces":[{"type":"bedroom",
                  "length":3.0,"width":3.0,"height":2.7}]}}
                """;

        JsonNode response = postJson(PARSE, body);

        assertEquals("bedroom", response.path("data").path("spaces").get(0).path("type").asText());
        verify(agentClient, never()).post(anyString(), any());
    }

    @Test
    @DisplayName("两者都不给 → 10001")
    void neitherInputIsRejected() throws Exception {
        JsonNode response = postJson(PARSE, "{}");

        assertEquals(10001, response.path("code").asInt(), response.toString());
        verify(agentClient, never()).post(anyString(), any());
    }

    // ------------------------------------------------------------------
    // POST /layout/parse —— text 路径(AgentClient 为替身)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("text 路径交给 Python,返回的户型仍经 Java 派生面积")
    void textPathDelegatesAndDerives() throws Exception {
        when(agentClient.post(eq("/internal/agent/parse-layout"), any()))
                .thenReturn(objectMapper.readTree("""
                        {"spaces":[{"type":"bathroom","name":"卫生间",
                          "length":2.0,"width":1.8,"height":2.7}]}
                        """));

        JsonNode response = postJson(PARSE, "{\"text\":\"卫生间 2m×1.8m\"}");

        assertEquals(0, response.path("code").asInt(), response.toString());
        JsonNode space = response.path("data").path("spaces").get(0);
        // **Python 不派生 areas**,派生只在这边发生一次
        assertEquals(3.6, space.path("areas").path("floor").asDouble(), 0.0001);
    }

    @Test
    @DisplayName("解析结果能通过契约 LayoutModel schema 校验")
    void parsedLayoutPassesContractSchema() throws Exception {
        when(agentClient.post(eq("/internal/agent/parse-layout"), any()))
                .thenReturn(objectMapper.readTree("""
                        {"spaces":[{"type":"living_room","name":"客厅","length":5.0,
                          "width":4.0,"height":2.7,
                          "openings":[{"type":"door","w":0.9,"h":2.1}]}]}
                        """));

        JsonNode layout = postJson(PARSE, "{\"text\":\"客厅 5m×4m\"}").path("data");
        Set<ValidationMessage> errors = LAYOUT_MODEL_SCHEMA.validate(layout);

        assertTrue(errors.isEmpty(), "解析结果不符合契约 schema:" + errors);
    }

    @Test
    @DisplayName("Python 报 10001 时**透传原码与逐项原因**")
    void agentValidationFailureIsPassedThrough() throws Exception {
        // 架构 §10.4 约定 1:只透传**已定义**的业务码,并带上 data.errors[]
        when(agentClient.post(anyString(), any()))
                .thenThrow(new com.tileai.common.error.BusinessException(
                        com.tileai.common.error.ErrorCode.PARAM_INVALID, "参数校验失败",
                        com.tileai.common.api.ValidationErrors.of(List.of(
                                com.tileai.common.api.ErrorItem.of("spaces[0].length", "未给出长")))));

        JsonNode response = postJson(PARSE, "{\"text\":\"一个没有长宽的卫生间\"}");

        assertEquals(10001, response.path("code").asInt(), response.toString());
        assertTrue(response.path("data").path("errors").isArray(), response.toString());
        assertEquals("spaces[0].length",
                response.path("data").path("errors").get(0).path("field").asText());
    }

    @Test
    @DisplayName("服务故障 → 90001(**不能伪装成「你的描述有问题」**)")
    void agentFailureIsInternalError() throws Exception {
        when(agentClient.post(anyString(), any()))
                .thenThrow(new com.tileai.common.error.BusinessException(
                        com.tileai.common.error.ErrorCode.INTERNAL_ERROR));

        JsonNode response = postJson(PARSE, "{\"text\":\"卫生间 2m×1.8m\"}");

        // 两者对用户的含义完全不同:一个该改描述,一个该重试/找运维。
        // 混在一起会让用户对着一个配置错误反复改自己的输入。
        assertEquals(90001, response.path("code").asInt(), response.toString());
    }

    @Test
    @DisplayName("两个接口都不需要登录(PRD §5.1:浏览免登录)")
    void layoutEndpointsArePublic() throws Exception {
        assertEquals(200, mvc.perform(get(TEMPLATES)).andReturn().getResponse().getStatus());
        assertEquals(200, mvc.perform(post(PARSE).contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andReturn().getResponse().getStatus());
    }

    // ------------------------------------------------------------------

    /**
     * 发一个 JSON 请求并取响应。
     *
     * <p>不叫 {@code post} —— 会与静态导入的 {@code MockMvcRequestBuilders.post} 撞名,
     * 而它是可变参数 {@code (String, Object...)},两个重载都能匹配,编译器会选错那个。
     */
    private JsonNode postJson(String url, String body) throws Exception {
        MvcResult result = mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON)
                .content(body)).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
