package com.tileai.admin;

import java.nio.charset.StandardCharsets;
import java.util.List;

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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * F-0.2 验收:后台账号的 CRUD、两条防呆、以及审计不泄露密码。
 *
 * <p><b>需要 MySQL</b>(要真库才能体现"角色与状态是现查的")。显式启用:
 *
 * <pre>
 *   mvn -s D:\dev-tools\maven-settings.xml test "-Ddb.verify=true" -Dtest=AdminAccountIntegrationTest
 * </pre>
 *
 * <p>不做成"没库就 SKIP" —— 跳过看起来像通过。没库就是失败。
 *
 * <p><b>本类建的账号一律以 {@code t02_} 开头</b>,收尾按前缀删干净,不碰真实数据。
 *
 * <p>契约:{@code openapi-admin.yaml} 的 accounts 分组。⚠️ 那一组的路径/方法/字段
 * 是**推断项**(架构 §10.3 只给了资源前缀),裁决见 {@code openapi-verification.md} §58。
 */
@EnabledIfSystemProperty(named = "db.verify", matches = "true",
        disabledReason = "需要 MySQL;用 -Ddb.verify=true 显式启用(F-0.2 验证)")
@SpringBootTest(properties = {"spring.flyway.enabled=false",
        "tile.auth.secret=0123456789abcdef0123456789abcdef"})
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AdminAccountIntegrationTest {

    private static final String BASE = "/api/v1/admin/accounts";
    private static final String USERNAME_PREFIX = "t02_";
    /** 测试账号的初始密码 —— 用它验"编辑留空不改密码"。 */
    private static final String INITIAL_PASSWORD = "Probe@123";

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

    @BeforeAll
    void seedAdmins() {
        superAdminId = insertAdmin("t02_super", "super_admin", AdminUser.STATUS_ENABLED);
        operatorId = insertAdmin("t02_operator", "operator", AdminUser.STATUS_ENABLED);
        editorId = insertAdmin("t02_editor", "editor", AdminUser.STATUS_ENABLED);
    }

    @AfterAll
    void removeAdmins() {
        // 只删本测试自己建的(按前缀),绝不碰真实账号
        adminUserMapper.delete(Wrappers.<AdminUser>lambdaQuery()
                .likeRight(AdminUser::getUsername, USERNAME_PREFIX));
    }

    // ===============================================================
    // 权限:整组只有 super_admin
    // ===============================================================

    @Test
    @DisplayName("权限:operator / editor 调四个接口一律 403 + 10004")
    void onlySuperAdminCanManageAccounts() throws Exception {
        for (MockHttpServletRequestBuilder rb : List.of(
                get(BASE), post(BASE), put(BASE + "/{id}", superAdminId), delete(BASE + "/{id}", superAdminId))) {
            assertEquals(403, statusOf(operatorId, rb), "operator 不该够得到账号管理");
            assertEquals(403, statusOf(editorId, rb), "editor 不该够得到账号管理");
        }
        assertEquals(200, statusOf(superAdminId, get(BASE)),
                "super_admin 应能读 —— 否则上面那些 403 可能是别的原因造成的假阳");
    }

    // ===============================================================
    // 新增
    // ===============================================================

    @Test
    @DisplayName("新增:缺密码 20004、密码过短 20004、用户名重复 20003")
    void createValidatesPasswordAndUsername() throws Exception {
        assertEquals(20004, codeOf(asAdmin(superAdminId, postJson(BASE, """
                {"username":"t02_nopass","role":"operator"}"""))), "新增时密码必填");

        assertEquals(20004, codeOf(asAdmin(superAdminId, postJson(BASE, """
                {"username":"t02_short","role":"operator","password":"123"}"""))));

        assertEquals(20003, codeOf(asAdmin(superAdminId, postJson(BASE, """
                {"username":"t02_operator","role":"operator","password":"Whatever1"}"""))),
                "用户名唯一 —— 撞已有账号");
    }

    @Test
    @DisplayName("新增:成功则落库、密码是 BCrypt 密文、能通过校验")
    void createStoresHashedPassword() throws Exception {
        MvcResult result = asAdmin(superAdminId, postJson(BASE, """
                {"username":"t02_created","role":"operator","password":"Probe@123"}"""));
        assertEquals(0, codeOf(result));

        long id = objectMapper.readTree(body(result)).get("data").get("id").asLong();
        AdminUser created = adminUserMapper.selectById(id);
        assertNotNull(created);
        assertEquals("operator", created.getRole());
        assertEquals(AdminUser.STATUS_ENABLED, created.getStatus(), "不传 status 默认启用");
        assertFalse(created.getPasswordHash().contains("Probe@123"), "库里不能是明文");
        assertTrue(passwordEncoder.matches("Probe@123", created.getPasswordHash()));
    }

    // ===============================================================
    // 编辑:密码留空 = 不改(本类最要紧的一条)
    // ===============================================================

    @Test
    @DisplayName("编辑:密码留空 → 密码不变(旧密码仍能通过校验)")
    void updateWithoutPasswordKeepsIt() throws Exception {
        long id = createAccount("t02_keepass");
        String before = adminUserMapper.selectById(id).getPasswordHash();

        assertEquals(0, codeOf(asAdmin(superAdminId, putJson(BASE + "/" + id, """
                {"username":"t02_keepass","role":"editor"}"""))));

        AdminUser after = adminUserMapper.selectById(id);
        assertEquals(before, after.getPasswordHash(), "留空不该碰 password_hash");
        assertEquals("editor", after.getRole(), "角色该改的还是要改");
        assertEquals(AdminUser.STATUS_ENABLED, after.getStatus(), "不传 status 不该顺手把启用的账号停用");
    }

    @Test
    @DisplayName("编辑:带了新密码 → 换成新密码,旧的失效")
    void updateWithPasswordReplacesIt() throws Exception {
        long id = createAccount("t02_newpass");

        assertEquals(0, codeOf(asAdmin(superAdminId, putJson(BASE + "/" + id, """
                {"username":"t02_newpass","role":"operator","password":"Brand@New1"}"""))));

        String hash = adminUserMapper.selectById(id).getPasswordHash();
        assertTrue(passwordEncoder.matches("Brand@New1", hash));
        assertFalse(passwordEncoder.matches(INITIAL_PASSWORD, hash), "旧密码必须失效");
    }

    @Test
    @DisplayName("编辑:不存在的 id → 30001")
    void updateUnknownIsNotFound() throws Exception {
        assertEquals(30001, codeOf(asAdmin(superAdminId, putJson(BASE + "/99900001", """
                {"username":"t02_ghost","role":"operator"}"""))));
    }

    // ===============================================================
    // 两条防呆
    // ===============================================================

    @Test
    @DisplayName("防呆①:不能停用自己、不能删除自己(30002,且库里不变)")
    void cannotDisableOrDeleteSelf() throws Exception {
        assertEquals(30002, codeOf(asAdmin(superAdminId, putJson(BASE + "/" + superAdminId, """
                {"username":"t02_super","role":"super_admin","status":"disabled"}"""))));
        assertEquals(AdminUser.STATUS_ENABLED,
                adminUserMapper.selectById(superAdminId).getStatus(), "被拒之后不该真的停用");

        assertEquals(30002, codeOf(asAdmin(superAdminId, delete(BASE + "/{id}", superAdminId))));
        assertNotNull(adminUserMapper.selectById(superAdminId), "被拒之后不该真的删掉");
    }

    @Test
    @DisplayName("防呆②不该误伤:还有别的启用超管时,降级其中一个应当允许")
    void downgradeAllowedWhileAnotherSuperAdminExists() throws Exception {
        long id = insertAdmin("t02_super_b", "super_admin", AdminUser.STATUS_ENABLED);

        assertEquals(0, codeOf(asAdmin(superAdminId, putJson(BASE + "/" + id, """
                {"username":"t02_super_b","role":"operator"}"""))),
                "本测试的 t02_super 与真实 admin 都还是超管,人数不为 1 ⇒ 不该拦");
        assertEquals("operator", adminUserMapper.selectById(id).getRole());
    }

    /**
     * 防呆②**真正触发**的那条。
     *
     * <p>要触发它,系统里必须"只剩一个启用的超管" —— 而开发库里通常已经有一个真实
     * 的 admin 超管。所以这个用例**在事务里**把其它启用超管临时停用,跑完由
     * {@code @Transactional} **自动回滚**。
     *
     * <p>刻意不用"手工停用 + 手工恢复":那样子测试中途崩掉(或断言失败后没走到恢复那段)
     * 就会把开发机上的 admin 账号留在停用状态 —— <b>谁也进不去后台</b>。
     * 事务回滚是原子的:没提交,等于什么都没发生。
     */
    @Test
    @Transactional
    @DisplayName("防呆②:会让「启用的超级管理员」归零的操作被拒(含改角色降级)")
    void cannotRemoveLastEnabledSuperAdmin() throws Exception {
        long id = insertAdmin("t02_super_last", "super_admin", AdminUser.STATUS_ENABLED);

        // 把除 id 之外所有启用超管临时停用 —— 事务里改,结束回滚
        adminUserMapper.update(null, Wrappers.<AdminUser>lambdaUpdate()
                .set(AdminUser::getStatus, AdminUser.STATUS_DISABLED)
                .eq(AdminUser::getRole, "super_admin")
                .eq(AdminUser::getStatus, AdminUser.STATUS_ENABLED)
                .ne(AdminUser::getId, id));

        assertEquals(30002, codeOf(asAdmin(id, putJson(BASE + "/" + id, """
                {"username":"t02_super_last","role":"operator"}"""))),
                "降级最后一个启用超管必须被拒 —— 否则后台从此没人进得去");
        assertEquals("super_admin", adminUserMapper.selectById(id).getRole(), "被拒后不该真的降级");

        // 停用同样要被拒(规则原话覆盖的是这一条)
        assertEquals(30002, codeOf(asAdmin(id, putJson(BASE + "/" + id, """
                {"username":"t02_super_last","role":"super_admin","status":"disabled"}"""))));
    }

    // ===============================================================
    // 删除 + 审计
    // ===============================================================

    @Test
    @DisplayName("删除:真删掉那一行")
    void deleteRemovesRow() throws Exception {
        long id = createAccount("t02_todelete");

        assertEquals(0, codeOf(asAdmin(superAdminId, delete(BASE + "/{id}", id))));
        assertNull(adminUserMapper.selectById(id), "契约写的是删除账号,不是停用");
    }

    @Test
    @DisplayName("审计:三个写操作各留一条,且**不含密码明文**")
    void writesAreAuditedWithoutPassword() throws Exception {
        long id = createAccount("t02_audited");
        assertEquals(0, codeOf(asAdmin(superAdminId, putJson(BASE + "/" + id, """
                {"username":"t02_audited","role":"editor","password":"Audit@2026"}"""))));
        assertEquals(0, codeOf(asAdmin(superAdminId, delete(BASE + "/{id}", id))));

        List<OperationLog> logs = operationLogMapper.selectList(Wrappers.<OperationLog>lambdaQuery()
                .in(OperationLog::getAction, List.of("account.create", "account.update", "account.delete"))
                .eq(OperationLog::getAdminId, superAdminId)
                .orderByDesc(OperationLog::getId));

        assertTrue(logs.size() >= 3, "三个写操作都该留痕,实际 " + logs.size() + " 条");
        for (OperationLog log : logs) {
            assertNotNull(log.getDetail());
            assertFalse(log.getDetail().contains("Audit@2026"),
                    "审计详情里绝不能出现密码明文:" + log.getDetail());
            assertFalse(log.getDetail().contains("Probe@123"), "同上");
            assertTrue(log.getDetail().contains("\"method\""), "详情应含方法与路径");
        }
        assertTrue(logs.stream().anyMatch(l -> "account.delete".equals(l.getAction())),
                "删除也该留痕");
    }

    @Test
    @DisplayName("审计:被拒的操作**不**留痕(切面只记成功的方法)")
    void rejectedOperationsLeaveNoTrace() throws Exception {
        long before = countAccountLogs();

        // 防呆①,必然被拒
        assertEquals(30002, codeOf(asAdmin(superAdminId, putJson(BASE + "/" + superAdminId, """
                {"username":"t02_super","role":"super_admin","status":"disabled"}"""))));
        // 用户名重复,必然被拒
        assertEquals(20003, codeOf(asAdmin(superAdminId, postJson(BASE, """
                {"username":"t02_operator","role":"operator","password":"Whatever1"}"""))));

        assertEquals(before, countAccountLogs(),
                "被拒的尝试不该留下审计 —— 这条是**已知取舍**,不是 bug");
    }

    // ===============================================================
    // 工具
    // ===============================================================

    private long countAccountLogs() {
        return operationLogMapper.selectCount(Wrappers.<OperationLog>lambdaQuery()
                .in(OperationLog::getAction, List.of("account.create", "account.update", "account.delete")));
    }

    private long createAccount(String username) throws Exception {
        MvcResult result = asAdmin(superAdminId, postJson(BASE, """
                {"username":"%s","role":"operator","password":"%s"}"""
                .formatted(username, INITIAL_PASSWORD)));
        assertEquals(0, codeOf(result), "造数据失败:" + body(result));
        return objectMapper.readTree(body(result)).get("data").get("id").asLong();
    }

    private long insertAdmin(String username, String role, String status) {
        AdminUser admin = new AdminUser();
        admin.setUsername(username);
        admin.setPasswordHash(passwordEncoder.encode(INITIAL_PASSWORD));
        admin.setRole(role);
        admin.setStatus(status);
        adminUserMapper.insert(admin);
        return admin.getId();
    }

    private MockHttpServletRequestBuilder postJson(String url, String json) {
        return post(url).contentType(MediaType.APPLICATION_JSON).content(json.getBytes(StandardCharsets.UTF_8));
    }

    private MockHttpServletRequestBuilder putJson(String url, String json) {
        return put(url).contentType(MediaType.APPLICATION_JSON).content(json.getBytes(StandardCharsets.UTF_8));
    }

    private MvcResult asAdmin(long adminId, MockHttpServletRequestBuilder rb) throws Exception {
        String token = jwtService.issueAccess(adminId, SubjectType.ADMIN);
        return mvc.perform(rb.cookie(new Cookie("admin_access_token", token))).andReturn();
    }

    private int statusOf(long adminId, MockHttpServletRequestBuilder rb) throws Exception {
        return asAdmin(adminId, rb).getResponse().getStatus();
    }

    private static String body(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString();
    }

    private int codeOf(MvcResult result) throws Exception {
        JsonNode root = objectMapper.readTree(body(result));
        return root.get("code").asInt();
    }
}
