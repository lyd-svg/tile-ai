package com.tileai.common;

import java.util.LinkedHashSet;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.tileai.admin.AdminAccountService;

import com.tileai.common.agent.AgentProperties;
import com.tileai.common.error.ErrorCode;
import com.tileai.common.error.GlobalExceptionHandler;
import com.tileai.common.probe.ProbeController;
import com.tileai.common.trace.TraceContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * T-0.3 验收:在<b>实现侧</b>验证契约校验表 T-1 / T-2 / T-13 成立。
 *
 * <ul>
 *   <li><b>T-1</b> —— 所有响应体含 {@code code / message / traceId}(本节逐条断言)</li>
 *   <li><b>T-2</b> —— 业务码 ∈ ErrorCode 枚举(见 {@link ErrorCodeContractTest},那里与契约逐值比对)</li>
 *   <li><b>T-13</b> —— 业务结果一律 HTTP 200,<b>仅</b> 401/403 用真实状态码(本节逐条断言)</li>
 * </ul>
 *
 * <p>本测试是切片测试({@code @WebMvcTest}),不加载数据源,因此不需要 MySQL。
 *
 * <p><b>刻意排除 Spring Security 的自动配置</b>:本测试的对象是"响应体长什么样",
 * 与鉴权无关。若不排除,{@code @WebMvcTest} 会套上默认安全链(httpBasic),
 * 所有探针接口一律 401,断言全部失焦。鉴权行为由
 * {@code SecurityFilterContractTest} 单独覆盖。
 *
 * <p>同理,{@code AdminAccountService} 在此打桩:{@code @WebMvcTest} 的类型过滤器
 * <b>包含 HandlerInterceptor</b>,会把 {@code AdminPermissionInterceptor}(T-0.5)
 * 一并拉进切片上下文,而它依赖访问数据库的 {@code AdminAccountService} ——
 * 切片里没有数据源。探针路径不在 {@code /api/v1/admin/**} 下,该拦截器实际不会执行,
 * 打桩只是让上下文能起来,不影响本测试的断言。
 *
 * <p>{@code AgentProperties} 打桩是同一个理由的第二次出现:{@code ServiceTokenInterceptor}
 * (T-1.11)也在切片里,而它依赖 {@code @ConfigurationProperties} 的 {@code AgentProperties}
 * —— 那个 bean 由启动类上的 {@code @ConfigurationPropertiesScan} 注册,
 * <b>切片上下文不跑那个扫描</b>,不打桩就起不来。同理,探针路径不在 {@code /internal/**} 下,
 * 该拦截器不会执行。
 */
@WebMvcTest(controllers = ProbeController.class,
        excludeAutoConfiguration = {
                SecurityAutoConfiguration.class,
                SecurityFilterAutoConfiguration.class,
                UserDetailsServiceAutoConfiguration.class})
@Import(GlobalExceptionHandler.class)
class ApiResponseContractTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AdminAccountService adminAccountService;

    @MockitoBean
    private AgentProperties agentProperties;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("T-1:成功响应含 code=0 / message / traceId,且 data 正常返回")
    void successResponse() throws Exception {
        String body = perform(get("/probe/ok"), 200);

        assertTrue(body.contains("\"code\":0"), body);
        assertTrue(body.contains("\"message\":\"ok\""), body);
        assertTrue(body.contains("\"data\":{\"hello\":\"world\"}"), body);
        assertTraceIdPresent(body);
        assertContractShape(body);
    }

    @Test
    @DisplayName("T-1:无数据时 data 为 null,但 code/message/traceId 仍在")
    void successWithoutData() throws Exception {
        String body = perform(get("/probe/empty"), 200);

        assertTrue(body.contains("\"code\":0"), body);
        assertTrue(body.contains("\"data\":null"), body);
        assertTraceIdPresent(body);
    }

    @Test
    @DisplayName("T-13:业务失败仍是 HTTP 200(30001 资源不存在)")
    void businessFailureIsHttp200() throws Exception {
        String body = perform(get("/probe/not-found"), 200);

        assertTrue(body.contains("\"code\":30001"), body);
        assertTrue(body.contains("SKU 不存在: SKU-123"), body);
        assertTraceIdPresent(body);
    }

    @Test
    @DisplayName("T-13:10005 限流也是 HTTP 200")
    void rateLimitedIsHttp200() throws Exception {
        String body = perform(get("/probe/rate-limited"), 200);
        assertTrue(body.contains("\"code\":10005"), body);
    }

    @Test
    @DisplayName("T-13:未登录用真实 HTTP 401,业务码 10002")
    void unauthenticatedIsReal401() throws Exception {
        String body = perform(get("/probe/unauth"), 401);
        assertTrue(body.contains("\"code\":10002"), body);
        assertTraceIdPresent(body);
    }

    @Test
    @DisplayName("T-13:令牌过期用真实 HTTP 401,业务码 10003")
    void tokenExpiredIsReal401() throws Exception {
        String body = perform(get("/probe/expired"), 401);
        assertTrue(body.contains("\"code\":10003"), body);
    }

    @Test
    @DisplayName("T-13:无权限用真实 HTTP 403,业务码 10004")
    void forbiddenIsReal403() throws Exception {
        String body = perform(get("/probe/forbidden"), 403);
        assertTrue(body.contains("\"code\":10004"), body);
    }

    @Test
    @DisplayName("校验失败:HTTP 200 + code=10001 + data.errors[] 带 field/reason")
    void validationFailureCarriesErrorItems() throws Exception {
        MvcResult result = mvc.perform(post("/probe/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"qty\":0}"))
                .andReturn();

        assertEquals(200, result.getResponse().getStatus(),
                "参数校验失败属业务结果,必须是 HTTP 200(仅 401/403 例外)");

        String body = result.getResponse().getContentAsString();
        assertTrue(body.contains("\"code\":10001"), body);
        // 明细必须落在 data.errors[],即 data 是对象而非数组(架构 §10.5)
        assertTrue(body.contains("\"data\":{\"errors\":["), "errors 必须位于 data.errors[]: " + body);
        assertTrue(body.contains("\"field\":\"name\""), body);
        assertTrue(body.contains("\"field\":\"qty\""), body);
        assertTrue(body.contains("\"reason\""), body);
        assertTraceIdPresent(body);
        assertContractShape(body);
    }

    @Test
    @DisplayName("未预期异常:HTTP 200 + 90001,且不泄露内部细节")
    void unexpectedExceptionHidesDetails() throws Exception {
        String body = perform(get("/probe/boom"), 200);

        assertTrue(body.contains("\"code\":90001"), body);
        assertFalse(body.contains("jdbc:mysql"), "90001 绝不能把异常消息透给前端: " + body);
        assertFalse(body.contains("10.0.0.1"), "90001 不能泄露内部地址: " + body);
        assertTraceIdPresent(body);
    }

    @Test
    @DisplayName("traceId 透传:上游带 X-Trace-Id 时原样沿用,便于跨进程串联")
    void inboundTraceIdIsPropagated() throws Exception {
        String given = "abc123def456";
        MvcResult result = mvc.perform(get("/probe/ok").header(TraceContext.HEADER, given)).andReturn();

        assertEquals(given, result.getResponse().getHeader(TraceContext.HEADER));
        assertTrue(result.getResponse().getContentAsString().contains("\"traceId\":\"" + given + "\""),
                result.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("ErrorCode 状态码口径:除 401/403 三个码外,全部为 HTTP 200")
    void onlyAuthCodesUseRealHttpStatus() {
        for (ErrorCode code : ErrorCode.values()) {
            boolean isAuthCode = code == ErrorCode.UNAUTHENTICATED
                    || code == ErrorCode.TOKEN_EXPIRED
                    || code == ErrorCode.FORBIDDEN;
            if (isAuthCode) {
                assertTrue(code.httpStatus().value() == 401 || code.httpStatus().value() == 403,
                        code + " 应使用真实 401/403");
            } else {
                assertEquals(200, code.httpStatus().value(),
                        code + " 必须返回 HTTP 200 —— 业务码靠响应体区分(T-13)");
            }
        }
    }

    // ------------------------------------------------------------------

    private String perform(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder rb,
                           int expectedStatus) throws Exception {
        MvcResult result = mvc.perform(rb).andReturn();
        assertEquals(expectedStatus, result.getResponse().getStatus(),
                "HTTP 状态不符,响应体: " + result.getResponse().getContentAsString());
        return result.getResponse().getContentAsString();
    }

    /**
     * 响应体的字段集合必须<b>恰好</b>是 code / message / data / traceId。
     *
     * <p>用集合相等而非"包含",是为了拦住<b>多出来的字段</b> —— 例如 record 上的
     * {@code isSuccess()} 若忘了加 {@code @JsonIgnore},Jackson 会把它序列化成
     * 契约里没有的 {@code "success"}。只查"包含"是发现不了的。
     */
    private void assertContractShape(String body) throws Exception {
        JsonNode node = objectMapper.readTree(body);
        Set<String> fields = new LinkedHashSet<>();
        node.fieldNames().forEachRemaining(fields::add);
        assertEquals(Set.of("code", "message", "data", "traceId"), fields,
                "响应体字段集合与契约 ApiResponse 不符: " + body);
    }

    private static void assertTraceIdPresent(String body) {
        assertTrue(body.contains("\"traceId\":\""), "响应体缺少 traceId(T-1): " + body);
        String traceId = body.substring(body.indexOf("\"traceId\":\"") + 11);
        traceId = traceId.substring(0, traceId.indexOf('"'));
        assertFalse(traceId.isBlank(), "traceId 不得为空: " + body);
    }
}
