package com.tileai.admin;

import java.time.LocalDateTime;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tileai.admin.entity.AdminUser;
import com.tileai.admin.entity.OperationLog;
import com.tileai.admin.mapper.AdminUserMapper;
import com.tileai.admin.mapper.OperationLogMapper;
import com.tileai.common.security.JwtService;
import com.tileai.common.security.SubjectType;

import jakarta.servlet.http.Cookie;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * F-0.2 验收:操作日志的查询(筛选、分页、`detail` 的类型、操作人富化)。
 *
 * <p><b>需要 MySQL</b>。显式启用:
 *
 * <pre>
 *   mvn -s D:\dev-tools\maven-settings.xml test "-Ddb.verify=true" -Dtest=AdminLogIntegrationTest
 * </pre>
 *
 * <p>本类造的日志一律打上 {@code t02.log.probe} 这个 action 作标记,**按它过滤**来做
 * 确定性断言 —— 开发库里已经有几千条真实日志,不隔离就没法数数。
 * {@code @AfterAll} 按这个标记删干净。
 */
@EnabledIfSystemProperty(named = "db.verify", matches = "true",
        disabledReason = "需要 MySQL;用 -Ddb.verify=true 显式启用(F-0.2 验证)")
@SpringBootTest(properties = {"spring.flyway.enabled=false",
        "tile.auth.secret=0123456789abcdef0123456789abcdef"})
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AdminLogIntegrationTest {

    private static final String BASE = "/api/v1/admin/logs";
    private static final String PROBE_ACTION = "t02.log.probe";
    private static final String USERNAME_PREFIX = "t02_log_";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private AdminUserMapper adminUserMapper;

    @Autowired
    private OperationLogMapper operationLogMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private long superAdminId;
    private long operatorId;
    private long editorId;
    private long actorId;

    @BeforeAll
    void seed() {
        superAdminId = insertAdmin("t02_log_super", "super_admin");
        operatorId = insertAdmin("t02_log_operator", "operator");
        editorId = insertAdmin("t02_log_editor", "editor");
        // 日志的"操作人"—— 单独一个账号,便于验富化与"已删除账号"两条
        actorId = insertAdmin("t02_log_actor", "operator");
    }

    @AfterAll
    void cleanup() {
        operationLogMapper.delete(Wrappers.<OperationLog>lambdaQuery()
                .eq(OperationLog::getAction, PROBE_ACTION));
        adminUserMapper.delete(Wrappers.<AdminUser>lambdaQuery()
                .likeRight(AdminUser::getUsername, USERNAME_PREFIX));
    }

    // ===============================================================
    // 权限:读日志是 super_admin / operator,不含 editor
    // ===============================================================

    @Test
    @DisplayName("权限:editor 读日志 403 + 10004;operator 与 super_admin 可读")
    void onlySuperAdminAndOperatorCanRead() throws Exception {
        MvcResult denied = asAdmin(editorId, get(BASE));
        assertEquals(403, denied.getResponse().getStatus());
        assertTrue(denied.getResponse().getContentAsString().contains("\"code\":10004"));

        assertEquals(200, statusOf(operatorId));
        assertEquals(200, statusOf(superAdminId));
    }

    // ===============================================================
    // detail 的类型 —— 契约说是对象,实体里是字符串
    // ===============================================================

    @Test
    @DisplayName("detail 是 **JSON 对象**,不是被转义的字符串")
    void detailIsAnObject() throws Exception {
        insertLog(actorId, LocalDateTime.now(), "{\"method\":\"PUT\",\"path\":\"/api/v1/admin/furnish\"}");

        JsonNode row = firstRow(dataOf(asAdmin(superAdminId,
                get(BASE).param("action", PROBE_ACTION))));

        JsonNode detail = row.get("detail");
        assertTrue(detail.isObject(),
                "detail 必须是对象 —— 若是字符串,Jackson 会把整段 JSON 转义,前端解不出 method/path:"
                        + detail);
        assertEquals("PUT", detail.get("method").asText());
        assertEquals("/api/v1/admin/furnish", detail.get("path").asText());
    }

    // ===============================================================
    // 分页与筛选
    // ===============================================================

    @Test
    @DisplayName("分页:total / page / size 由服务端给出,不是把全表倒出来")
    void paginationIsReal() throws Exception {
        for (int i = 0; i < 3; i++) {
            insertLog(actorId, LocalDateTime.now(), "{\"method\":\"GET\",\"path\":\"/p" + i + "\"}");
        }

        JsonNode data = dataOf(asAdmin(superAdminId,
                get(BASE).param("action", PROBE_ACTION).param("page", "1").param("size", "2")));

        assertEquals(2, data.get("list").size(), "每页 2 条");
        assertTrue(data.get("total").asLong() >= 3, "总数应覆盖本类造的全部探针日志");
        assertEquals(1, data.get("page").asInt());
        assertEquals(2, data.get("size").asInt());
    }

    @Test
    @DisplayName("筛选:adminId 精确匹配")
    void filterByAdminId() throws Exception {
        insertLog(actorId, LocalDateTime.now(), null);

        JsonNode mine = dataOf(asAdmin(superAdminId,
                get(BASE).param("action", PROBE_ACTION).param("adminId", String.valueOf(actorId))));
        assertTrue(mine.get("total").asLong() >= 1);

        JsonNode other = dataOf(asAdmin(superAdminId,
                get(BASE).param("action", PROBE_ACTION)
                        .param("adminId", String.valueOf(superAdminId))));
        assertEquals(0, other.get("total").asLong(), "该账号不是这些日志的操作人");
    }

    @Test
    @DisplayName("筛选:from / to 卡住时间窗,且**纯日期的 to 包含当天**")
    void filterByDateRange() throws Exception {
        insertLog(actorId, LocalDateTime.of(2026, 1, 15, 10, 0, 0), null);
        insertLog(actorId, LocalDateTime.of(2026, 1, 20, 10, 0, 0), null);

        JsonNode inRange = dataOf(asAdmin(superAdminId, get(BASE)
                .param("action", PROBE_ACTION).param("from", "2026-01-14").param("to", "2026-01-16")));
        assertEquals(1, inRange.get("total").asLong(), "只该命中 1-15 那条");

        // **这条是重点**:纯日期 to=2026-01-15 若被当成 00:00:00,当天 10:00 的记录会被漏掉
        JsonNode endOfDay = dataOf(asAdmin(superAdminId, get(BASE)
                .param("action", PROBE_ACTION).param("from", "2026-01-15").param("to", "2026-01-15")));
        assertEquals(1, endOfDay.get("total").asLong(),
                "to=当天 必须包含当天的记录 —— 否则「查到今天」永远少一天");
    }

    @Test
    @DisplayName("时间写错 → 10001(不静默忽略,否则用户以为过滤生效了)")
    void invalidDateIsParamInvalid() throws Exception {
        assertEquals(10001, codeOf(asAdmin(superAdminId, get(BASE).param("from", "2026-13-99"))));
    }

    // ===============================================================
    // 操作人富化 + 已删除账号
    // ===============================================================

    @Test
    @DisplayName("操作人:按 adminId 富化出用户名")
    void adminUsernameIsEnriched() throws Exception {
        insertLog(actorId, LocalDateTime.now(), null);

        JsonNode row = firstRow(dataOf(asAdmin(superAdminId,
                get(BASE).param("action", PROBE_ACTION).param("adminId", String.valueOf(actorId)))));

        assertEquals(actorId, row.get("adminId").asLong());
        assertEquals("t02_log_actor", row.get("adminUsername").asText(),
                "表里只有 id,用户名是读时富化的(§8.1)");
    }

    @Test
    @DisplayName("操作人已删除:adminUsername 为 null,不报错、不 500")
    void deletedActorYieldsNullUsername() throws Exception {
        long ghostId = insertAdmin("t02_log_ghost", "operator");
        insertLog(ghostId, LocalDateTime.now(), null);
        // 真删掉这个账号(与 F-0.2 的"删除"语义一致)
        adminUserMapper.deleteById(ghostId);

        JsonNode row = firstRow(dataOf(asAdmin(superAdminId,
                get(BASE).param("action", PROBE_ACTION).param("adminId", String.valueOf(ghostId)))));

        assertEquals(ghostId, row.get("adminId").asLong(), "日志还在 —— 审计记的是当时发生的事");
        assertTrue(row.get("adminUsername").isNull(),
                "账号没了就是 null,前端据此显示「已删除账号 #N」");
        assertFalse(asAdmin(superAdminId, get(BASE).param("adminId", String.valueOf(ghostId)))
                        .getResponse().getContentAsString().isEmpty(),
                "不该因为查不到人就 500");
    }

    // ===============================================================
    // 工具
    // ===============================================================

    private long insertAdmin(String username, String role) {
        AdminUser admin = new AdminUser();
        admin.setUsername(username);
        admin.setPasswordHash(passwordEncoder.encode("Probe@123"));
        admin.setRole(role);
        admin.setStatus(AdminUser.STATUS_ENABLED);
        adminUserMapper.insert(admin);
        return admin.getId();
    }

    private void insertLog(long adminId, LocalDateTime createdAt, String detail) {
        OperationLog log = new OperationLog();
        log.setAdminId(adminId);
        log.setAction(PROBE_ACTION);
        log.setTargetType("probe");
        log.setTargetId("t02");
        log.setDetail(detail);
        log.setIp("127.0.0.1");
        log.setCreatedAt(createdAt);
        operationLogMapper.insert(log);
    }

    private JsonNode firstRow(JsonNode data) {
        assertTrue(data.get("list").size() > 0, "没有查到任何日志:" + data);
        return data.get("list").get(0);
    }

    private JsonNode dataOf(MvcResult result) throws Exception {
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(0, root.get("code").asInt(), "查询应成功:" + root);
        return root.get("data");
    }

    private int codeOf(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("code").asInt();
    }

    private int statusOf(long adminId) throws Exception {
        return asAdmin(adminId, get(BASE)).getResponse().getStatus();
    }

    private MvcResult asAdmin(long adminId, MockHttpServletRequestBuilder rb) throws Exception {
        String token = jwtService.issueAccess(adminId, SubjectType.ADMIN);
        return mvc.perform(rb.cookie(new Cookie("admin_access_token", token))).andReturn();
    }
}
