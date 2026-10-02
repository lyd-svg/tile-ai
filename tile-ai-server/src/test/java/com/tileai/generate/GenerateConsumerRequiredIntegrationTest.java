package com.tileai.generate;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mockito.ArgumentMatchers;
import org.springframework.amqp.rabbit.core.ChannelCallback;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tileai.common.security.JwtService;
import com.tileai.common.security.SubjectType;

import jakarta.servlet.http.Cookie;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 受理前"队列有没有人在听"的**端到端**把关(契约 {@code openapi-c.yaml} 的
 * {@code POST /generate};失败码 40002)。
 *
 * <p><b>需要 MySQL</b>({@code -Ddb.verify=true});<b>不需要 RabbitMQ 真的在跑</b>
 * —— {@code RabbitTemplate} 被替换成替身,由它给出"队列上有几个消费者"这个确定值。
 *
 * <p>为什么不直接依赖真实的消费者数:那取决于<b>跑测试时本机 Python 有没有启动</b>,
 * 同一份代码在开发机和 CI 上会得到相反的结果。这里要验的是
 * "<b>读到 0 ⇒ 当场 40002</b>"这条判断,而不是本机的环境状态。
 *
 * <p>对照组({@code consumerPresentAcceptsNormally})是必须的:没有它,一个"把所有人
 * 都拒掉"的实现也能让主用例通过。
 *
 * <h2>这条用例守的是什么</h2>
 *
 * <p>2026-10-02 实测:Python 没启动时,受理照常成功,任务永远停在 {@code pending},
 * 前端一直显示"正在生成…(已排队)",用户在零反馈的状态下白等三到四分钟。
 * 这个用例钉住的是"<b>当场拒绝</b>"这件事。
 */
@EnabledIfSystemProperty(named = "db.verify", matches = "true",
        disabledReason = "需要 MySQL;用 -Ddb.verify=true 显式启用")
@SpringBootTest(properties = {
        "spring.flyway.enabled=false",
        "tile.auth.secret=0123456789abcdef0123456789abcdef",
        "tile.crypto.layout-key=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=",
        // 关掉投递器:本类不验投递,只验受理这一道闸
        "tile.outbox.enabled=false",
        "tile.generate.compensation-enabled=false",
        // 默认就是 true,这里显式写出来是为了让"本类刻意开着探测"这个意图看得见
        "tile.generate.require-consumer=true"})
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GenerateConsumerRequiredIntegrationTest {

    private static final String GENERATE = "/api/v1/generate";
    private static final long USER = 8_811_0301L;

    /** 两个部位都给全 ⇒ 受理时**不会**去问 Python 补齐,用例不受 Python 是否在跑影响。 */
    private static final String BODY = """
            {"space":"bathroom","style":"modern","angles":1,
             "layout":{"spaces":[{"type":"bathroom","name":"卫生间",
               "length":2,"width":1.8,"height":2.4,"wallCount":4}]},
             "parts":[{"part":"floor","sku":"T-CONSUMER-FLOOR"},
                      {"part":"wall","sku":"T-CONSUMER-WALL"}]}
            """;

    @MockitoBean
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterAll
    void cleanup() {
        jdbcTemplate.update("DELETE FROM t_generation_task WHERE user_id = ?", USER);
    }

    private void stubConsumerCount(long count) {
        when(rabbitTemplate.execute(ArgumentMatchers.<ChannelCallback<Long>>any())).thenReturn(count);
    }

    @Test
    @DisplayName("队列上没有消费端 ⇒ 受理当场 40002,不让用户排队干等")
    void noConsumerIsRejectedImmediately() throws Exception {
        stubConsumerCount(0L);

        JsonNode root = postGenerate();

        // 项目约定:业务错误码走 body,HTTP 恒 200(只有 401/403 用真实状态码)
        assertEquals(40002, root.get("code").asInt(), root.toString());
        assertTrue(root.get("message").asText().contains("请稍后重试"),
                "给用户的话要能照着做,实际=" + root.get("message").asText());
    }

    @Test
    @DisplayName("对照:队列上有人在听 ⇒ 照常受理并返回 taskId")
    void consumerPresentAcceptsNormally() throws Exception {
        stubConsumerCount(1L);

        JsonNode root = postGenerate();

        assertEquals(0, root.get("code").asInt(), root.toString());
        assertNotNull(root.path("data").path("taskId").asText(null),
                "正常受理必须回 taskId,否则前端没法轮询");
    }

    private JsonNode postGenerate() throws Exception {
        MvcResult result = mvc.perform(post(GENERATE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY)
                        .cookie(new Cookie("access_token",
                                jwtService.issueAccess(USER, SubjectType.USER))))
                .andReturn();
        assertEquals(200, result.getResponse().getStatus());
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
