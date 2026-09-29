package com.tileai.admin;

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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tileai.admin.entity.AdminUser;
import com.tileai.admin.entity.OperationLog;
import com.tileai.admin.mapper.AdminUserMapper;
import com.tileai.admin.mapper.OperationLogMapper;
import com.tileai.common.security.JwtService;
import com.tileai.common.security.SubjectType;

import jakarta.servlet.http.Cookie;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * T-0.5 验收:逐条对照架构 §4.4 的权限矩阵,验证真实 HTTP 结果。
 *
 * <p><b>需要 MySQL</b>(要读 {@code t_admin_user} 才能解析角色)。显式启用:
 *
 * <pre>
 *   mvn -s D:\dev-tools\maven-settings.xml test "-Ddb.verify=true"
 * </pre>
 *
 * <p>不做成"没库就 SKIP" —— 跳过看起来像通过。没库就是失败。
 *
 * <p>用的是完整上下文 + 真实数据库 + 真实拦截器,而不是给 AdminAccountService 打桩:
 * 要验的正是"角色是现查的、停用立即生效"这类只有真库才能体现的行为。
 */
@EnabledIfSystemProperty(named = "db.verify", matches = "true",
        disabledReason = "需要 MySQL;用 -Ddb.verify=true 显式启用(T-0.5 验证)")
@SpringBootTest(properties = {"spring.flyway.enabled=false",
        "tile.auth.secret=0123456789abcdef0123456789abcdef"})
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AdminRbacIntegrationTest {

    private static final String BASE = "/api/v1/admin/rbac-probe";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private AdminUserMapper adminUserMapper;

    @Autowired
    private OperationLogMapper operationLogMapper;

    private long superAdminId;
    private long operatorId;
    private long editorId;

    @BeforeAll
    void seedAdmins() {
        superAdminId = insertAdmin("t05_super", "super_admin");
        operatorId = insertAdmin("t05_operator", "operator");
        editorId = insertAdmin("t05_editor", "editor");
    }

    @AfterAll
    void removeAdmins() {
        // 只删本测试自己建的三条,不碰其它账号
        adminUserMapper.delete(Wrappers.<AdminUser>lambdaQuery()
                .in(AdminUser::getUsername, List.of("t05_super", "t05_operator", "t05_editor")));
    }

    // ---------------------------------------------------------------
    // T-0.5 验收判据(任务清单逐条)
    // ---------------------------------------------------------------

    @Test
    @DisplayName("operator 调 DELETE /products/{id} → 成功(可删)")
    void operatorCanDeleteProduct() throws Exception {
        assertEquals(200, statusOf(operatorId, delete(BASE + "/products/1")));
    }

    @Test
    @DisplayName("editor 调 DELETE /products/{id} → 403(不可删)")
    void editorCannotDeleteProduct() throws Exception {
        MvcResult result = asAdmin(editorId, delete(BASE + "/products/1"));
        assertEquals(403, result.getResponse().getStatus());
        assertTrue(result.getResponse().getContentAsString().contains("\"code\":10004"),
                "403 响应体应为业务码 10004: " + result.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("editor 调 PATCH /products/{id}/status → 403(不可上下架)")
    void editorCannotPublishProduct() throws Exception {
        assertEquals(403, statusOf(editorId, patch(BASE + "/products/1/status")));
    }

    @Test
    @DisplayName("editor 调 POST /products → 成功(可创建)")
    void editorCanCreateProduct() throws Exception {
        assertEquals(200, statusOf(editorId, post(BASE + "/products")));
    }

    @Test
    @DisplayName("operator 与 editor 调 PUT /quote-params 均 → 403(报价参数仅 super_admin)")
    void nobodyButSuperAdminCanTouchQuoteParams() throws Exception {
        assertEquals(403, statusOf(operatorId, put(BASE + "/quote-params")),
                "operator 不得改报价参数 —— 架构 §4.4 裁决");
        assertEquals(403, statusOf(editorId, put(BASE + "/quote-params")));
        assertEquals(200, statusOf(superAdminId, put(BASE + "/quote-params")),
                "super_admin 应能改,否则上面两个 403 可能是别的原因导致的假阳");
    }

    @Test
    @DisplayName("operator 可写字典(V7 前叫 dict),editor 只读")
    void dictWriteIsOperatorOnly() throws Exception {
        assertEquals(200, statusOf(operatorId, put(BASE + "/dicts/9")));
        assertEquals(403, statusOf(editorId, put(BASE + "/dicts/9")));
    }

    @Test
    @DisplayName("editor 调 GET /accounts → 403(账号管理仅 super_admin)")
    void editorCannotReadAccounts() throws Exception {
        assertEquals(403, statusOf(editorId, get(BASE + "/accounts")));
        assertEquals(403, statusOf(operatorId, get(BASE + "/accounts")));
        assertEquals(200, statusOf(superAdminId, get(BASE + "/accounts")));
    }

    @Test
    @DisplayName("operator 调 GET /logs → 成功(可看操作日志)")
    void operatorCanReadLogs() throws Exception {
        assertEquals(200, statusOf(operatorId, get(BASE + "/logs")));
        assertEquals(403, statusOf(editorId, get(BASE + "/logs")));
    }

    @Test
    @DisplayName("批量导入只给 operator")
    void importIsOperatorOnly() throws Exception {
        assertEquals(200, statusOf(operatorId, post(BASE + "/products/import")));
        assertEquals(403, statusOf(editorId, post(BASE + "/products/import")));
    }

    // ---------------------------------------------------------------
    // 未标注权限点的接口由 ROLE_ADMIN 兜底
    // ---------------------------------------------------------------

    @Test
    @DisplayName("业主令牌访问后台探针 → 403(C 端与 B 端 ID 空间隔离)")
    void userTokenCannotReachAdminProbe() throws Exception {
        String userToken = jwtService.issueAccess(1L, SubjectType.USER);
        MvcResult result = mvc.perform(get(BASE + "/logs").cookie(new Cookie("access_token", userToken)))
                .andReturn();
        assertEquals(403, result.getResponse().getStatus());
    }

    @Test
    @DisplayName("无 Cookie → 401")
    void anonymousIsRejected() throws Exception {
        assertEquals(401, mvc.perform(get(BASE + "/logs")).andReturn().getResponse().getStatus());
    }

    @Test
    @DisplayName("账号被停用后,其令牌立即失效(角色是现查的,不是令牌里的快照)")
    void disabledAdminLosesAccessImmediately() throws Exception {
        long tempAdminId = insertAdmin("t05_tobedisabled", "operator");

        assertEquals(200, statusOf(tempAdminId, get(BASE + "/logs")),
                "停用前应可访问");

        AdminUser admin = adminUserMapper.selectById(tempAdminId);
        admin.setStatus(AdminUser.STATUS_DISABLED);
        adminUserMapper.updateById(admin);

        assertEquals(401, statusOf(tempAdminId, get(BASE + "/logs")),
                "账号停用后同一令牌必须立刻失效 —— 若把角色写进令牌,这里最长 2 小时才会生效");

        adminUserMapper.deleteById(tempAdminId);
    }

    // ---------------------------------------------------------------
    // 操作日志(§4.4:所有写操作记入 t_operation_log)
    // ---------------------------------------------------------------

    @Test
    @DisplayName("写操作被记入 t_operation_log,且操作人 / 动作 / 目标 / IP 齐全")
    void writeOperationIsAudited() throws Exception {
        long before = operationLogMapper.selectCount(null);

        assertEquals(200, statusOf(operatorId, put(BASE + "/dicts/42")));

        long after = operationLogMapper.selectCount(null);
        assertEquals(before + 1, after, "一次写操作应恰好产生一条审计记录");

        OperationLog latest = operationLogMapper.selectOne(Wrappers.<OperationLog>lambdaQuery()
                .eq(OperationLog::getAdminId, operatorId)
                .eq(OperationLog::getTargetId, "42")
                .orderByDesc(OperationLog::getId)
                .last("LIMIT 1"));

        assertNotNull(latest, "未找到本次写操作的审计记录");
        assertEquals("dict.update", latest.getAction());
        assertEquals("dict", latest.getTargetType());
        assertEquals("42", latest.getTargetId());
        assertEquals(operatorId, latest.getAdminId());
        assertNotNull(latest.getIp(), "审计记录必须带 IP");
        assertNotNull(latest.getCreatedAt(), "审计记录必须带时间");
        assertNotNull(latest.getDetail());
        assertTrue(latest.getDetail().contains("/api/v1/admin/rbac-probe/dicts/42"),
                "detail 应记录请求路径: " + latest.getDetail());
    }

    @Test
    @DisplayName("读接口不产生审计记录(否则日志会被查询淹没)")
    void readOperationsAreNotAudited() throws Exception {
        long before = operationLogMapper.selectCount(null);
        assertEquals(200, statusOf(operatorId, get(BASE + "/logs")));
        assertEquals(before, operationLogMapper.selectCount(null));
    }

    @Test
    @DisplayName("越权被拒的写操作不产生审计记录(记的是已发生的变更)")
    void rejectedWriteIsNotAudited() throws Exception {
        long before = operationLogMapper.selectCount(null);
        assertEquals(403, statusOf(editorId, delete(BASE + "/products/1")));
        assertEquals(before, operationLogMapper.selectCount(null),
                "被拒的操作没有改变任何数据,不应留下指向不存在资源的审计记录");
    }

    // ---------------------------------------------------------------
    // 探测端点没被漏保护
    // ---------------------------------------------------------------

    @Test
    @DisplayName("探针上每个标注权限点的端点,对无权限角色都必须挡住")
    void probeEndpointsAreActuallyProtected() throws Exception {
        // 若某个端点忘了标注解,这里会从 403 变成 200 —— 正是要防的静默放行
        assertNotEquals(200, statusOf(editorId, get(BASE + "/accounts")));
        assertNotEquals(200, statusOf(editorId, put(BASE + "/quote-params")));
        assertNotEquals(200, statusOf(editorId, patch(BASE + "/products/1/status")));
        assertNotEquals(200, statusOf(editorId, delete(BASE + "/products/1")));
    }

    // ---------------------------------------------------------------

    private long insertAdmin(String username, String role) {
        AdminUser admin = new AdminUser();
        admin.setUsername(username);
        // 本测试不验登录,给一个不可能是合法密码的占位串即可
        admin.setPasswordHash("$2a$10$not-a-real-hash-just-a-placeholder-for-rbac-tests");
        admin.setRole(role);
        admin.setStatus(AdminUser.STATUS_ENABLED);
        adminUserMapper.insert(admin);
        return admin.getId();
    }

    private int statusOf(long adminId, org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder rb)
            throws Exception {
        return asAdmin(adminId, rb).getResponse().getStatus();
    }

    private MvcResult asAdmin(long adminId,
                              org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder rb)
            throws Exception {
        String token = jwtService.issueAccess(adminId, SubjectType.ADMIN);
        return mvc.perform(rb.cookie(new Cookie("access_token", token))).andReturn();
    }
}
