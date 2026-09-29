package com.tileai.layout;

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
import com.tileai.admin.entity.AdminUser;
import com.tileai.admin.mapper.AdminUserMapper;
import com.tileai.common.security.JwtService;
import com.tileai.common.security.SubjectType;
import com.tileai.layout.entity.UnitTemplate;
import com.tileai.layout.mapper.UnitTemplateMapper;

import jakarta.servlet.http.Cookie;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * T-0.9 验收:后台户型模板维护的真实行为(架构 §8.3;PRD §5.3.1)。
 *
 * <p><b>需要 MySQL</b>。用 {@code -Ddb.verify=true} 显式启用。
 *
 * <p>两条判据都有直接断言:
 * <ol>
 *   <li>「存入的 layout 用 LayoutModel schema 校验通过」—— 从<b>数据库读回</b>落库的那份
 *       JSON,交给契约 schema 校验(见 {@link #storedLayoutPassesContractSchema});</li>
 *   <li>「areas 为服务端派生,录入时可省略」—— 见 {@link #areasAreDerivedWhenOmitted}
 *       与 {@link #explicitAreasArePreserved}。</li>
 * </ol>
 */
@EnabledIfSystemProperty(named = "db.verify", matches = "true",
        disabledReason = "需要 MySQL;用 -Ddb.verify=true 显式启用(T-0.9 验证)")
@SpringBootTest(properties = {"spring.flyway.enabled=false",
        "tile.auth.secret=0123456789abcdef0123456789abcdef"})
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AdminTemplateIntegrationTest {

    private static final String BASE = "/api/v1/admin/templates";
    /** 本测试创建的数据都带此前缀,便于清理时不误伤。 */
    private static final String NAME_PREFIX = "T09-";

    private static final JsonSchema LAYOUT_MODEL_SCHEMA =
            JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
                    .getSchema(ContractSchema.resolved("openapi-common.yaml", "LayoutModel"));

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private AdminUserMapper adminUserMapper;

    @Autowired
    private UnitTemplateMapper templateMapper;

    private long adminId;
    private String token;

    @BeforeAll
    void seedAdmin() {
        AdminUser admin = new AdminUser();
        admin.setUsername("t09_admin");
        admin.setPasswordHash("$2a$10$placeholder-not-used-because-no-login-here");
        admin.setRole("operator");
        admin.setStatus(AdminUser.STATUS_ENABLED);
        adminUserMapper.insert(admin);
        adminId = admin.getId();
        token = jwtService.issueAccess(adminId, SubjectType.ADMIN);
    }

    @AfterAll
    void cleanup() {
        templateMapper.delete(Wrappers.<UnitTemplate>lambdaQuery()
                .likeRight(UnitTemplate::getName, NAME_PREFIX));
        adminUserMapper.deleteById(adminId);
    }

    // ---------------------------------------------------------------
    // 判据二:areas 服务端派生、录入可省略
    // ---------------------------------------------------------------

    @Test
    @DisplayName("省略 areas → 服务端按公式补齐并落库")
    void areasAreDerivedWhenOmitted() throws Exception {
        long id = create("T09-推导", 1, "3-5㎡", "一室一厅",
                layoutJson(spaceJson("bathroom", "卫生间", "2.0", "1.8", "2.7", null)));

        JsonNode areas = dataOf(asAdmin(get(BASE + "/" + id)).andReturn())
                .get("layout").get("spaces").get(0).get("areas");

        assertNotNull(areas, "省略的 areas 应被服务端补上");
        assertDecimal("3.6000", areas.get("floor"), "floor = 长×宽 = 2.0×1.8");
        assertDecimal("20.5200", areas.get("wall"), "wall = 2×(长+宽)×层高 = 2×3.8×2.7");
        assertDecimal("0.0000", areas.get("local"));
    }

    @Test
    @DisplayName("显式给出 areas → 原样保留,不被公式覆盖")
    void explicitAreasArePreserved() throws Exception {
        String space = """
                {"type":"bathroom","name":"卫生间","length":2.0,"width":1.8,"height":2.7,
                 "areas":{"floor":99.1,"wall":88.2,"local":7.3}}
                """;
        long id = create("T09-保留", 1, "3-5㎡", "一室一厅", layoutJson(space));

        JsonNode areas = dataOf(asAdmin(get(BASE + "/" + id)).andReturn())
                .get("layout").get("spaces").get(0).get("areas");

        assertDecimal("99.1", areas.get("floor"), "人工核过的面积不该被公式改写");
        assertDecimal("88.2", areas.get("wall"));
        assertDecimal("7.3", areas.get("local"));
    }

    // ---------------------------------------------------------------
    // 判据一:落库的 layout 通过契约 schema 校验
    // ---------------------------------------------------------------

    @Test
    @DisplayName("从数据库读回落库的 layout,用契约 LayoutModel schema 校验通过")
    void storedLayoutPassesContractSchema() throws Exception {
        String spaces = spaceJson("bathroom", "卫生间", "2.0", "1.8", "2.7",
                "[{\"type\":\"door\",\"w\":0.8,\"h\":2.1},{\"type\":\"window\",\"w\":1.0,\"h\":1.2}]")
                + "," + spaceJson("kitchen", "厨房", "3.5", "2.4", "2.8", null);
        long id = create("T09-schema", 2, "60-90㎡", "两室一厅", layoutJson(spaces));

        // 直接读库,校验的是真正落盘的那份数据,而不是接口的回显
        UnitTemplate stored = templateMapper.selectById(id);
        assertNotNull(stored);
        JsonNode storedJson = objectMapper.valueToTree(stored.getLayout());

        Set<ValidationMessage> errors = LAYOUT_MODEL_SCHEMA.validate(storedJson);
        assertTrue(errors.isEmpty(),
                "落库的 layout 未通过契约 LayoutModel schema 校验: " + errors);

        // 同时确认派生确实发生在这份落库数据里
        assertNotNull(storedJson.get("spaces").get(0).get("areas"),
                "落库数据里应已带好派生的面积");
    }

    // ---------------------------------------------------------------
    // 校验
    // ---------------------------------------------------------------

    @Test
    @DisplayName("缺 name → 10001")
    void missingNameIsRejected() throws Exception {
        String json = objectMapper.writeValueAsString(objectMapper.createObjectNode()
                .set("layout", objectMapper.readTree(
                        layoutJson(spaceJson("bathroom", "卫生间", "2.0", "1.8", "2.7", null)))));

        MvcResult result = asAdmin(post(BASE).contentType(MediaType.APPLICATION_JSON)
                .content(json)).andReturn();
        assertEquals(10001, codeOf(result));
        assertTrue(result.getResponse().getContentAsString().contains("\"field\":\"name\""),
                result.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("缺 layout → 10001")
    void missingLayoutIsRejected() throws Exception {
        assertEquals(10001, codeOf(asAdmin(post(BASE).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"T09-无户型\"}")).andReturn()));
    }

    @Test
    @DisplayName("spaces 为空 → 10001(契约 minItems: 1)")
    void emptySpacesIsRejected() throws Exception {
        MvcResult result = asAdmin(post(BASE).contentType(MediaType.APPLICATION_JSON)
                .content(upsertJson("T09-空空间", 1, "3-5㎡", "一室一厅", "{\"spaces\":[]}")))
                .andReturn();

        assertEquals(10001, codeOf(result));
        assertTrue(result.getResponse().getContentAsString().contains("\"layout.spaces\""),
                "错误明细应指向嵌套路径 layout.spaces: "
                        + result.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("非法开口类型 → 10001")
    void invalidOpeningTypeIsRejected() throws Exception {
        String space = spaceJson("bathroom", "卫生间", "2.0", "1.8", "2.7",
                "[{\"type\":\"ceiling\",\"w\":1.0,\"h\":1.0}]");
        MvcResult result = asAdmin(post(BASE).contentType(MediaType.APPLICATION_JSON)
                .content(upsertJson("T09-坏开口", 1, "3-5㎡", "一室一厅", layoutJson(space))))
                .andReturn();

        assertEquals(10001, codeOf(result));
    }

    @Test
    @DisplayName("空间缺尺寸 → 10001")
    void spaceWithoutDimensionsIsRejected() throws Exception {
        MvcResult result = asAdmin(post(BASE).contentType(MediaType.APPLICATION_JSON)
                .content(upsertJson("T09-缺尺寸", 1, "3-5㎡", "一室一厅",
                        "{\"spaces\":[{\"type\":\"bathroom\"}]}"))).andReturn();

        assertEquals(10001, codeOf(result));
    }

    // ---------------------------------------------------------------
    // 筛选(PRD §5.3.1:房间数 + 面积区间 + 户型结构)
    // ---------------------------------------------------------------

    @Test
    @DisplayName("按 roomCount / areaRange / structure 筛选")
    void listFiltersWork() throws Exception {
        create("T09-筛两室", 2, "60-90㎡", "两室一厅",
                layoutJson(spaceJson("bedroom", "卧室", "3.0", "3.0", "2.8", null)));
        create("T09-筛三室", 3, "90-120㎡", "三室两厅",
                layoutJson(spaceJson("bedroom", "卧室", "3.0", "3.0", "2.8", null)));

        JsonNode byRoomCount = dataOf(asAdmin(get(BASE).param("roomCount", "3")
                .param("size", "100")).andReturn());
        byRoomCount.get("list").forEach(t ->
                assertEquals(3, t.get("roomCount").asInt()));

        JsonNode byAreaRange = dataOf(asAdmin(get(BASE).param("areaRange", "90-120㎡")
                .param("size", "100")).andReturn());
        assertTrue(byAreaRange.get("list").size() >= 1);
        byAreaRange.get("list").forEach(t ->
                assertEquals("90-120㎡", t.get("areaRange").asText()));

        JsonNode byStructure = dataOf(asAdmin(get(BASE).param("structure", "三室两厅")
                .param("size", "100")).andReturn());
        assertTrue(byStructure.get("list").size() >= 1);
        byStructure.get("list").forEach(t ->
                assertEquals("三室两厅", t.get("structure").asText()));

        assertTrue(byRoomCount.has("total") && byRoomCount.has("page") && byRoomCount.has("size"),
                "分页字段齐备(T-3)");
    }

    // ---------------------------------------------------------------
    // 编辑 / 删除 / 边界
    // ---------------------------------------------------------------

    @Test
    @DisplayName("编辑整体替换 layout,并重新推导面积")
    void updateReplacesLayoutAndRederives() throws Exception {
        long id = create("T09-待改", 1, "3-5㎡", "一室一厅",
                layoutJson(spaceJson("bathroom", "卫生间", "2.0", "1.8", "2.7", null)));

        assertEquals(0, codeOf(asAdmin(put(BASE + "/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(upsertJson("T09-改后", 2, "60-90㎡", "两室一厅",
                        layoutJson(spaceJson("living_room", "客厅", "5.0", "4.0", "2.8", null)))))
                .andReturn()));

        JsonNode data = dataOf(asAdmin(get(BASE + "/" + id)).andReturn());
        assertEquals("T09-改后", data.get("name").asText());
        JsonNode space = data.get("layout").get("spaces").get(0);
        assertEquals("living_room", space.get("type").asText());
        assertDecimal("20.0000", space.get("areas").get("floor"), "5.0×4.0,且按新尺寸重新推导");
    }

    @Test
    @DisplayName("对外只返回明文 layout,不含 layout_json / layout_enc(T-8)")
    void responseHasNoCiphertextField() throws Exception {
        long id = create("T09-字段检查", 1, "3-5㎡", "一室一厅",
                layoutJson(spaceJson("bathroom", "卫生间", "2.0", "1.8", "2.7", null)));

        String body = asAdmin(get(BASE + "/" + id)).andReturn().getResponse().getContentAsString();
        assertFalse(body.contains("layout_json"), "库内列名不得外泄: " + body);
        assertFalse(body.contains("layout_enc"), "密文字段不得出现在对外 schema(T-8): " + body);
        assertTrue(body.contains("\"layout\""), body);
    }

    @Test
    @DisplayName("删除后详情转 30001;不存在 → 30001")
    void deleteAndMissing() throws Exception {
        long id = create("T09-待删", 1, "3-5㎡", "一室一厅",
                layoutJson(spaceJson("bathroom", "卫生间", "2.0", "1.8", "2.7", null)));

        assertEquals(0, codeOf(asAdmin(delete(BASE + "/" + id)).andReturn()));
        assertEquals(30001, codeOf(asAdmin(get(BASE + "/" + id)).andReturn()));
        assertEquals(30001, codeOf(asAdmin(delete(BASE + "/" + id)).andReturn()));
        assertEquals(30001, codeOf(asAdmin(get(BASE + "/99999999")).andReturn()));
    }

    @Test
    @DisplayName("未传 status 时落在下架态(V8 已把默认值对齐为 off_sale)")
    void defaultStatusIsOffSale() throws Exception {
        long id = create("T09-状态", 1, "3-5㎡", "一室一厅",
                layoutJson(spaceJson("bathroom", "卫生间", "2.0", "1.8", "2.7", null)));

        assertEquals("off_sale", dataOf(asAdmin(get(BASE + "/" + id)).andReturn())
                .get("status").asText());
        assertNotNull(templateMapper.selectById(id).getCreatedAt(), "created_at 应由数据库填充");
    }

    // ---------------------------------------------------------------
    // 工具
    // ---------------------------------------------------------------

    private long create(String name, Integer roomCount, String areaRange, String structure,
                        String layoutJson) throws Exception {
        MvcResult result = asAdmin(post(BASE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(upsertJson(name, roomCount, areaRange, structure, layoutJson)))
                .andReturn();
        assertEquals(0, codeOf(result), "创建应成功: " + result.getResponse().getContentAsString());
        return dataOf(result).get("id").asLong();
    }

    private String upsertJson(String name, Integer roomCount, String areaRange, String structure,
                              String layoutJson) throws Exception {
        var node = objectMapper.createObjectNode();
        node.put("name", name);
        if (roomCount != null) {
            node.put("roomCount", roomCount);
        }
        if (areaRange != null) {
            node.put("areaRange", areaRange);
        }
        if (structure != null) {
            node.put("structure", structure);
        }
        node.set("layout", objectMapper.readTree(layoutJson));
        return objectMapper.writeValueAsString(node);
    }

    private static String layoutJson(String spaces) {
        return "{\"spaces\":[" + spaces + "]}";
    }

    /** 不传 areas,交给服务端推导。 */
    private static String spaceJson(String type, String name, String length, String width,
                                    String height, String openings) {
        return "{\"type\":\"" + type + "\",\"name\":\"" + name + "\","
                + "\"length\":" + length + ",\"width\":" + width + ",\"height\":" + height
                + (openings == null ? "" : ",\"openings\":" + openings) + "}";
    }

    private org.springframework.test.web.servlet.ResultActions asAdmin(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder rb)
            throws Exception {
        return mvc.perform(rb.cookie(new Cookie("access_token", token)));
    }

    private static int codeOf(MvcResult result) throws Exception {
        return new ObjectMapper().readTree(result.getResponse().getContentAsString())
                .get("code").asInt();
    }

    private JsonNode dataOf(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(body).get("data");
        assertNotNull(data, "响应缺 data: " + body);
        return data;
    }

    private static void assertDecimal(String expected, JsonNode actual) {
        assertDecimal(expected, actual, null);
    }

    private static void assertDecimal(String expected, JsonNode actual, String message) {
        assertNotNull(actual, (message == null ? "" : message + " —— ") + "字段缺失");
        assertEquals(0, new java.math.BigDecimal(expected).compareTo(actual.decimalValue()),
                (message == null ? "" : message + ";")
                        + "期望 " + expected + ",实际 " + actual.asText());
    }
}
