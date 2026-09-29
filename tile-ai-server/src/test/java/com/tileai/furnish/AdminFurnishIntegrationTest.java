package com.tileai.furnish;

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
import com.tileai.admin.entity.AdminUser;
import com.tileai.admin.mapper.AdminUserMapper;
import com.tileai.common.security.JwtService;
import com.tileai.common.security.SubjectType;
import com.tileai.furnish.entity.FurnishStyle;
import com.tileai.furnish.mapper.FurnishStyleMapper;

import jakarta.servlet.http.Cookie;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * T-0.10 验收:后台陈设风格配置(架构 §8.5;PRD §5.4、§5.7)。
 *
 * <p><b>需要 MySQL</b>。用 {@code -Ddb.verify=true} 显式启用。
 *
 * <p>判据:「同 (space,style) 重复提交为<b>更新而非新增</b>;矩阵无重复组合」。
 */
@EnabledIfSystemProperty(named = "db.verify", matches = "true",
        disabledReason = "需要 MySQL;用 -Ddb.verify=true 显式启用(T-0.10 验证)")
@SpringBootTest(properties = {"spring.flyway.enabled=false",
        "tile.auth.secret=0123456789abcdef0123456789abcdef"})
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AdminFurnishIntegrationTest {

    private static final String BASE = "/api/v1/admin/furnish";
    /** 本测试用专属空间键,避免与真实字典数据混淆。 */
    private static final String SPACE = "t10_probe_space";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private AdminUserMapper adminUserMapper;

    @Autowired
    private FurnishStyleMapper furnishStyleMapper;

    private long adminId;
    private String token;

    @BeforeAll
    void seed() {
        AdminUser admin = new AdminUser();
        admin.setUsername("t10_admin");
        admin.setPasswordHash("$2a$10$placeholder-not-used-because-no-login-here");
        admin.setRole("operator");
        admin.setStatus(AdminUser.STATUS_ENABLED);
        adminUserMapper.insert(admin);
        adminId = admin.getId();
        token = jwtService.issueAccess(adminId, SubjectType.ADMIN);
    }

    @AfterAll
    void cleanup() {
        furnishStyleMapper.delete(Wrappers.<FurnishStyle>lambdaQuery()
                .eq(FurnishStyle::getSpace, SPACE));
        adminUserMapper.deleteById(adminId);
    }

    // ---------------------------------------------------------------
    // 判据:upsert 语义,矩阵无重复
    // ---------------------------------------------------------------

    @Test
    @DisplayName("同 (space,style) 重复提交是更新而非新增,矩阵里始终只有一条")
    void repeatedUpsertUpdatesInsteadOfDuplicating() throws Exception {
        assertEquals(0, codeOf(putJson(SPACE, "modern", "第一次的提示词")));
        long afterFirst = count(SPACE, "modern");
        assertEquals(1, afterFirst);

        assertEquals(0, codeOf(putJson(SPACE, "modern", "改过的提示词")));
        assertEquals(1, count(SPACE, "modern"), "重复提交不得产生第二条 —— 矩阵以 space+style 唯一");

        JsonNode list = dataOf(asAdmin(get(BASE)
                .param("space", SPACE).param("style", "modern")).andReturn()).get("list");
        assertEquals(1, list.size());
        assertEquals("改过的提示词", list.get(0).get("prompt").asText(), "内容应被更新");
    }

    @Test
    @DisplayName("不同风格是不同条目,互不覆盖")
    void differentStylesAreSeparateEntries() throws Exception {
        putJson(SPACE, "modern", "现代风的陈设");
        putJson(SPACE, "nordic", "北欧风的陈设");

        JsonNode list = dataOf(asAdmin(get(BASE).param("space", SPACE)).andReturn()).get("list");
        assertEquals(2, list.size(), "同一空间的两种风格应是两条");
    }

    @Test
    @DisplayName("契约声明的是 { list } 而非分页 —— 不带 total/page/size")
    void listIsNotPaginated() throws Exception {
        putJson(SPACE, "cream", "奶油风");

        JsonNode data = dataOf(asAdmin(get(BASE).param("space", SPACE)).andReturn());
        assertTrue(data.has("list"));
        assertTrue(!data.has("total") && !data.has("page") && !data.has("size"),
                "契约对 /furnish 声明的 data 只有 list: " + data);
    }

    // ---------------------------------------------------------------
    // 边界
    // ---------------------------------------------------------------

    @Test
    @DisplayName("未传 status 时新增落在下架态;更新时不动既有状态")
    void statusHandling() throws Exception {
        putJson(SPACE, "light_luxury", "轻奢风");
        JsonNode created = firstOf(SPACE, "light_luxury");
        assertEquals("off_sale", created.get("status").asText(), "未传 status 应落到下架态");

        // 显式上架
        assertEquals(0, codeOf(asAdmin(put(BASE).contentType(MediaType.APPLICATION_JSON)
                .content(body(SPACE, "light_luxury", "轻奢风", "on_sale"))).andReturn()));
        assertEquals("on_sale", firstOf(SPACE, "light_luxury").get("status").asText());

        // 再改提示词但不传 status —— 不应把已上架的打回下架
        putJson(SPACE, "light_luxury", "改过的轻奢风");
        JsonNode after = firstOf(SPACE, "light_luxury");
        assertEquals("on_sale", after.get("status").asText(),
                "只改提示词不该改动上下架状态");
        assertEquals("改过的轻奢风", after.get("prompt").asText());
    }

    @Test
    @DisplayName("缺必填字段 → 10001")
    void missingRequiredIsRejected() throws Exception {
        MvcResult result = asAdmin(put(BASE).contentType(MediaType.APPLICATION_JSON)
                .content("{\"space\":\"" + SPACE + "\",\"style\":\"modern\"}")).andReturn();

        assertEquals(10001, codeOf(result));
        assertTrue(result.getResponse().getContentAsString().contains("\"field\":\"prompt\""),
                result.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("非法 status → 10001")
    void invalidStatusIsRejected() throws Exception {
        MvcResult result = asAdmin(put(BASE).contentType(MediaType.APPLICATION_JSON)
                .content(body(SPACE, "modern", "x", "whatever"))).andReturn();
        assertEquals(10001, codeOf(result));
    }

    @Test
    @DisplayName("删除:成功后再删返回 30001")
    void deleteThenMissing() throws Exception {
        putJson(SPACE, "to_delete", "待删");
        long id = firstRawId(SPACE, "to_delete");
        assertNotNull(id);

        assertEquals(0, codeOf(asAdmin(delete(BASE + "/" + id)).andReturn()));
        assertEquals(30001, codeOf(asAdmin(delete(BASE + "/" + id)).andReturn()));
        assertEquals(30001, codeOf(asAdmin(delete(BASE + "/99999999")).andReturn()));
    }

    // ---------------------------------------------------------------

    private MvcResult putJson(String space, String style, String prompt) throws Exception {
        return asAdmin(put(BASE).contentType(MediaType.APPLICATION_JSON)
                .content(body(space, style, prompt, null))).andReturn();
    }

    private String body(String space, String style, String prompt, String status)
            throws Exception {
        var node = objectMapper.createObjectNode();
        node.put("space", space);
        node.put("style", style);
        node.put("prompt", prompt);
        if (status != null) {
            node.put("status", status);
        }
        return objectMapper.writeValueAsString(node);
    }

    private long count(String space, String style) {
        return furnishStyleMapper.selectCount(Wrappers.<FurnishStyle>lambdaQuery()
                .eq(FurnishStyle::getSpace, space).eq(FurnishStyle::getStyle, style));
    }

    private long firstRawId(String space, String style) {
        FurnishStyle entity = furnishStyleMapper.selectOne(Wrappers.<FurnishStyle>lambdaQuery()
                .eq(FurnishStyle::getSpace, space).eq(FurnishStyle::getStyle, style));
        return entity == null ? -1 : entity.getId();
    }

    private JsonNode firstOf(String space, String style) throws Exception {
        return dataOf(asAdmin(get(BASE).param("space", space).param("style", style)).andReturn())
                .get("list").get(0);
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
}
