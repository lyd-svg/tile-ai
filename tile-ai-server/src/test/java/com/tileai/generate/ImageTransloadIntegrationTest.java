package com.tileai.generate;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.List;

import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tileai.common.storage.StorageProperties;
import com.tileai.common.storage.UrlPolicy;
import com.tileai.generate.dto.GenerateTaskPayload;
import com.tileai.generate.dto.PartSelection;
import com.tileai.generate.entity.GenerationTask;
import com.tileai.generate.mapper.GenerationTaskMapper;
import com.tileai.layout.model.LayoutModel;
import com.tileai.layout.model.LayoutSpace;
import com.tileai.plan.entity.PlanImage;
import com.tileai.plan.mapper.PlanImageMapper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 效果图转存的端到端验收(架构 §15.1)。
 *
 * <p><b>需要 MySQL</b>({@code -Ddb.verify=true});<b>不需要 RabbitMQ,也不需要额度</b>
 * —— 图片由一个绑在 127.0.0.1 的桩服务提供,直接 POST 内部回调接口就能把整条链路
 * 跑一遍:转存 → 落盘 → 改库 → 匿名取图。
 *
 * <p>验的是三件单测里看不出来的事:
 * <ol>
 *   <li>Spring 装配真的把 {@code /media/**} 映射到了磁盘目录(单测只验了下载本身);</li>
 *   <li>{@code t_plan_image.url} 与 {@code t_generation_task.result_ref} <b>都被换掉</b>
 *       —— 后者是进度接口返回的封面,漏掉它前端拿到的还是临时地址;</li>
 *   <li>转存的图<b>匿名可访问</b> —— 分享页就是给没登录的人看的,这条断了会静默图裂。</li>
 * </ol>
 *
 * <p>断言一律落在<b>由内容算出的确切路径</b>上,不做"目录里数了几个文件"这种全局统计:
 * 落盘目录是整个测试类共用的,数数会被别的用例污染。
 */
@EnabledIfSystemProperty(named = "db.verify", matches = "true",
        disabledReason = "需要 MySQL;用 -Ddb.verify=true 显式启用")
@SpringBootTest(properties = {
        "spring.flyway.enabled=false",
        "tile.auth.secret=0123456789abcdef0123456789abcdef",
        "tile.crypto.layout-key=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=",
        "tile.agent.service-token=t11-storage-token",
        "tile.generate.compensation-enabled=false",
        "tile.storage.enabled=true",
        "tile.storage.public-base-url=http://localhost:8080"})
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ImageTransloadIntegrationTest {

    /**
     * 覆写 URL 判定,放行环回 —— 桩服务只能绑在本机。
     *
     * <p>自有域仍按配置装配(传空列表会让"跳过转存"那条分支永远走不到)。
     * 生产路径不做任何覆写。
     */
    @TestConfiguration
    static class LoopbackFriendlyPolicy {
        @Bean
        @Primary
        UrlPolicy testUrlPolicy(StorageProperties properties) {
            return new UrlPolicy(properties.effectiveOwnBaseUrls()) {
                @Override
                protected boolean isBlockedHost(String host) {
                    return false;
                }
            };
        }
    }

    private static final String CALLBACK = "/internal/generate/callback";
    private static final String SERVICE_TOKEN_HEADER = "X-Service-Token";
    private static final String SERVICE_TOKEN = "t11-storage-token";
    private static final long USER = 8_811_0201L;
    private static final String MEDIA_PREFIX = "http://localhost:8080/media/";

    private static final byte[] PNG = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            1, 2, 3, 4, 5, 6, 7, 8, 9, 10};

    /** 落盘目录。必须在容器起来之前定下来,所以走静态初始化 + DynamicPropertySource。 */
    private static final Path MEDIA_ROOT = createTempRoot();

    private static Path createTempRoot() {
        try {
            return Files.createTempDirectory("tile-media-it-");
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @DynamicPropertySource
    static void storageProps(DynamicPropertyRegistry registry) {
        registry.add("tile.storage.root", MEDIA_ROOT::toString);
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private GenerationTaskMapper taskMapper;

    @Autowired
    private PlanImageMapper planImageMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private HttpServer server;
    private String stubBase;

    @BeforeEach
    void startStub() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        stubBase = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopStub() {
        if (server != null) {
            server.stop(0);
        }
    }

    @AfterAll
    void cleanup() {
        jdbcTemplate.update("DELETE FROM t_plan_image WHERE plan_id IN (SELECT id FROM t_plan WHERE user_id = ?)", USER);
        jdbcTemplate.update("DELETE FROM t_plan_part WHERE plan_id IN (SELECT id FROM t_plan WHERE user_id = ?)", USER);
        jdbcTemplate.update("DELETE FROM t_plan WHERE user_id = ?", USER);
        jdbcTemplate.update("DELETE FROM t_generation_task WHERE user_id = ?", USER);
    }

    // ==================================================================

    @Test
    @DisplayName("成功回调:图落到本机,方案图与封面都换成本地地址")
    void successCallbackStoresImageAndRewritesUrl() throws Exception {
        byte[] payload = pngVariant(11);
        serve("/ok.png", payload);
        String taskId = insertTask();

        callback(taskId, stubBase + "/ok.png");

        String planImageUrl = planImageUrlOf(taskId);
        assertTrue(planImageUrl.startsWith(MEDIA_PREFIX), "方案里的图应换成本地地址,实际=" + planImageUrl);
        assertEquals(MEDIA_PREFIX + relativePath(payload), planImageUrl,
                "地址应当是内容哈希决定的路径");
        assertEquals(planImageUrl, coverUrlOf(taskId),
                "封面(result_ref)走同一条路径,漏掉它进度接口返回的仍是临时地址");
        assertTrue(Files.exists(MEDIA_ROOT.resolve(relativePath(payload))), "文件应当真的落盘了");
    }

    @Test
    @DisplayName("落盘的图匿名可取(分享页依赖这条)")
    void storedImageIsPubliclyReadable() throws Exception {
        byte[] payload = pngVariant(12);
        serve("/ok.png", payload);
        String taskId = insertTask();

        callback(taskId, stubBase + "/ok.png");

        String path = URI.create(planImageUrlOf(taskId)).getPath();
        MvcResult result = mvc.perform(get(path)).andReturn();

        assertEquals(200, result.getResponse().getStatus(), "匿名 GET 必须能拿到图");
        assertEquals(payload.length, result.getResponse().getContentAsByteArray().length);
    }

    @Test
    @DisplayName("同一回调重复发:地址与文件都不变(内容寻址)")
    void repeatCallbackIsIdempotent() throws Exception {
        byte[] payload = pngVariant(13);
        serve("/ok.png", payload);
        String taskId = insertTask();

        callback(taskId, stubBase + "/ok.png");
        String first = planImageUrlOf(taskId);

        callback(taskId, stubBase + "/ok.png");

        assertEquals(first, planImageUrlOf(taskId));
        assertTrue(Files.exists(MEDIA_ROOT.resolve(relativePath(payload))));
    }

    /**
     * 降级路径。这条最重要:转存失败若抛出去,回调会 500 ⇒ 事务回滚 ⇒ 任务退回可重试
     * ⇒ 补偿扫描重投 ⇒ <b>重跑一次真实模型</b>。
     */
    @Test
    @DisplayName("图抓不到:保留原地址,任务照样成功")
    void unreachableImageKeepsOriginalUrlAndSucceeds() throws Exception {
        byte[] payload = pngVariant(14);
        server.stop(0);
        server = null;
        String taskId = insertTask();

        MvcResult result = callback(taskId, stubBase + "/gone.png");

        assertEquals(200, result.getResponse().getStatus(), "转存失败不该让回调失败");
        assertEquals(stubBase + "/gone.png", planImageUrlOf(taskId), "抓不到就保留原地址");
        assertEquals(GenerationTask.STATUS_SUCCESS, taskOf(taskId).getStatus());
        assertFalse(Files.exists(MEDIA_ROOT.resolve(relativePath(payload))));
    }

    /**
     * 拒绝路径 ⇒ 降级。
     *
     * <p>本测试类为了让桩服务可用覆写了主机判定,所以这里用一个明确不是 http(s) 的
     * 协议来触发拒绝 —— 验的是"被拒之后怎么处理",不是"内网判定本身"
     * (那条在 {@code UrlPolicyTest} 里逐个地址钉过)。
     */
    @Test
    @DisplayName("非 http(s) 协议被拒:降级保留原地址,不落任何文件")
    void rejectedSchemeIsKeptAsIs() throws Exception {
        String taskId = insertTask();

        callback(taskId, "file:///etc/passwd");

        assertEquals("file:///etc/passwd", planImageUrlOf(taskId));
        assertEquals(GenerationTask.STATUS_SUCCESS, taskOf(taskId).getStatus());
    }

    // ==================================================================
    // 帮手
    // ==================================================================

    /** 造一份内容独特的 PNG,好让每个用例的哈希互不相同。 */
    private static byte[] pngVariant(int seed) {
        byte[] bytes = PNG.clone();
        bytes[bytes.length - 1] = (byte) seed;
        return bytes;
    }

    /** {@code ab/cd/{sha256}.png} —— 与 {@code ImageTransloader} 的落盘规则一致。 */
    private static String relativePath(byte[] bytes) {
        String hash;
        try {
            hash = HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return hash.substring(0, 2) + "/" + hash.substring(2, 4) + "/" + hash + ".png";
    }

    private String insertTask() {
        String taskId = "g_store_" + Long.toHexString(System.nanoTime());
        GenerationTask task = new GenerationTask();
        task.setTaskId(taskId);
        task.setUserId(USER);
        task.setType(GenerationTask.TYPE_IMAGE);
        task.setStatus(GenerationTask.STATUS_PENDING);
        task.setProgress(0);
        task.setPayload(payloadOf(taskId));
        taskMapper.insert(task);
        return taskId;
    }

    private String payloadOf(String taskId) {
        try {
            return objectMapper.writeValueAsString(new GenerateTaskPayload(
                    taskId,
                    GenerationTask.TYPE_IMAGE,
                    "t11-storage-trace",
                    USER,
                    null,
                    "bathroom",
                    "modern",
                    1,
                    List.of(new PartSelection("floor", "T11-FLOOR", null, "user")),
                    new LayoutModel(List.of(new LayoutSpace("bathroom", "卫生间",
                            new BigDecimal("2"), new BigDecimal("1.8"), new BigDecimal("2.4"),
                            4, null, null))),
                    GenerateTaskPayload.INITIAL_ATTEMPT,
                    "2026-10-02T10:00:00"));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private MvcResult callback(String taskId, String imageUrl) throws Exception {
        String body = """
                {"taskId":"%s","status":"success","progress":100,"imageUrls":[
                  {"url":"%s","view":"default","isCover":true}]}
                """.formatted(taskId, imageUrl);
        MockHttpServletRequestBuilder request = post(CALLBACK)
                .contentType(MediaType.APPLICATION_JSON).content(body)
                .header(SERVICE_TOKEN_HEADER, SERVICE_TOKEN);
        return mvc.perform(request).andReturn();
    }

    /**
     * 按业务主键取任务。
     *
     * <p>不能用 {@code selectById} —— 那走的是自增主键 {@code id},
     * 而 {@code taskId} 是另一个列(见补偿测试的同名帮手)。
     */
    private GenerationTask taskOf(String taskId) {
        return taskMapper.selectOne(
                Wrappers.<GenerationTask>lambdaQuery().eq(GenerationTask::getTaskId, taskId));
    }

    private String planImageUrlOf(String taskId) {
        Long planId = taskOf(taskId).getPlanId();
        assertTrue(planId != null, "成功回调后应当回填 planId");
        List<PlanImage> images = planImageMapper.selectList(
                Wrappers.<PlanImage>lambdaQuery().eq(PlanImage::getPlanId, planId));
        assertEquals(1, images.size());
        return images.get(0).getUrl();
    }

    private String coverUrlOf(String taskId) {
        return taskOf(taskId).getResultRef();
    }

    private void serve(String path, byte[] body) {
        server.createContext(path, exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "image/png");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
    }
}
