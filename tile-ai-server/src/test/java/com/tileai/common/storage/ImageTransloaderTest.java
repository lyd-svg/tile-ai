package com.tileai.common.storage;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.tileai.generate.dto.GenerateCallbackRequest.ImageRef;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 转存本身的单测 —— 用<b>绑在 127.0.0.1 的桩服务</b>供图,全程不出网、不消耗额度。
 *
 * <p>覆盖的核心主张只有一个:<b>任何一张图出问题,都只降级、不抛异常</b>。
 * 抛出会让回调事务回滚 ⇒ 任务退回可重试 ⇒ 补偿扫描重投 ⇒ 重跑一次真实模型。
 * 转存失败不该有这个代价。
 */
@DisplayName("ImageTransloader —— 抓图落盘与降级")
class ImageTransloaderTest {

    /** 最小 PNG 头 + 一点负载。魔数判定只认头 8 字节。 */
    private static final byte[] PNG = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            1, 2, 3, 4, 5, 6, 7, 8, 9, 10};

    @TempDir
    Path tempDir;

    private HttpServer server;
    private String base;
    private final AtomicInteger hits = new AtomicInteger();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    // ------------------------------------------------------------------
    // 正常路径
    // ------------------------------------------------------------------

    @Test
    @DisplayName("抓到图就落盘,并把地址换成自己的")
    void storesImageAndRewritesUrl() throws Exception {
        serveBytes("/ok.png", 200, "image/png", PNG);

        List<ImageRef> out = transloader(props()).localize(
                List.of(new ImageRef(base + "/ok.png", "default", true)), "task-1");

        String url = out.get(0).url();
        assertTrue(url.startsWith("http://localhost:8080/media/"), "应换成自有绝对地址,实际=" + url);
        assertTrue(url.endsWith(".png"), "扩展名应按魔数定为 png,实际=" + url);
        assertEquals(1, fileCount(), "应当只有一份文件");
        assertEquals(PNG.length, Files.readAllBytes(firstFile()).length);
        // 视角 / 封面这些非 URL 字段必须原样保留,否则方案页的封面就丢了
        assertEquals("default", out.get(0).view());
        assertEquals(Boolean.TRUE, out.get(0).isCover());
    }

    @Test
    @DisplayName("同一张图抓两次是幂等的(内容寻址)")
    void sameBytesAreIdempotent() {
        serveBytes("/same.png", 200, "image/png", PNG);
        ImageTransloader transloader = transloader(props());

        String first = transloader.localize(List.of(image(base + "/same.png")), "t").get(0).url();
        String second = transloader.localize(List.of(image(base + "/same.png")), "t").get(0).url();

        assertEquals(first, second);
        assertEquals(1, fileCount(), "内容相同就不该落第二份");
    }

    @Test
    @DisplayName("内容不同的两张图落成两份")
    void differentBytesGetDifferentFiles() throws Exception {
        serveBytes("/a.png", 200, "image/png", PNG);
        byte[] other = PNG.clone();
        other[other.length - 1] = 99;
        serveBytes("/b.png", 200, "image/png", other);

        List<ImageRef> out = transloader(props()).localize(
                List.of(image(base + "/a.png"), image(base + "/b.png")), "t");

        assertNotEquals(out.get(0).url(), out.get(1).url());
        assertEquals(2, fileCount());
    }

    // ------------------------------------------------------------------
    // 降级:一律不出异常、一律保留原地址
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Content-Type 不是图片 → 保留原地址")
    void nonImageContentTypeKeepsOriginal() {
        serveBytes("/not-image", 200, "text/html", PNG);

        assertKeptOriginal("/not-image");
    }

    @Test
    @DisplayName("Content-Type 冒充图片但魔数不是 → 保留原地址(防 200 + HTML 错误页)")
    void fakeImageByMagicNumberKeepsOriginal() {
        serveBytes("/fake.png", 200, "image/png", "<html>oops</html>".getBytes());

        assertKeptOriginal("/fake.png");
    }

    @Test
    @DisplayName("超过体积上限 → 保留原地址")
    void oversizedKeepsOriginal() {
        serveBytes("/big.png", 200, "image/png", new byte[4096]);
        StorageProperties props = props();
        props.setMaxBytes(1024);

        List<ImageRef> out = transloader(props).localize(List.of(image(base + "/big.png")), "t");

        assertEquals(base + "/big.png", out.get(0).url());
    }

    /**
     * 禁止重定向是 SSRF 防护的一部分:{@link UrlPolicy} 只判了第一跳,
     * 跟随 302 等于把它的全部判定绕开。
     */
    @Test
    @DisplayName("服务端 302 → 不跟随,保留原地址")
    void redirectKeepsOriginal() {
        server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().add("Location", base + "/ok.png");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        serveBytes("/ok.png", 200, "image/png", PNG);

        assertKeptOriginal("/redirect");
    }

    @Test
    @DisplayName("连不上 → 保留原地址(不抛)")
    void unreachableKeepsOriginal() {
        // 关掉桩服务,端口就没人听了
        server.stop(0);
        server = null;

        assertKeptOriginal("/ok.png");
    }

    @Test
    @DisplayName("服务端 500 → 保留原地址")
    void serverErrorKeepsOriginal() {
        serveBytes("/boom.png", 500, "image/png", PNG);

        assertKeptOriginal("/boom.png");
    }

    @Test
    @DisplayName("空响应体 → 保留原地址")
    void emptyBodyKeepsOriginal() {
        serveBytes("/empty.png", 200, "image/png", new byte[0]);

        assertKeptOriginal("/empty.png");
    }

    @Test
    @DisplayName("多张里坏一张,不影响其它张")
    void oneFailureDoesNotAffectOthers() {
        serveBytes("/good.png", 200, "image/png", PNG);
        serveBytes("/bad.png", 200, "text/plain", PNG);

        List<ImageRef> out = transloader(props()).localize(
                List.of(image(base + "/good.png"), image(base + "/bad.png")), "t");

        assertTrue(out.get(0).url().startsWith("http://localhost:8080/media/"));
        assertEquals(base + "/bad.png", out.get(1).url());
        assertEquals(2, out.size(), "返回列表必须等长 —— 少一张就是静默丢图");
    }

    // ------------------------------------------------------------------
    // 跳过
    // ------------------------------------------------------------------

    @Test
    @DisplayName("自己的地址不下载(占位图模式靠这条零开销)")
    void ownAddressIsNotDownloaded() {
        serveBytes("/ok.png", 200, "image/png", PNG);
        StorageProperties props = props();
        props.setPublicBaseUrl(base);   // 把桩服务当成"自己的地址"

        List<ImageRef> out = transloader(props).localize(List.of(image(base + "/ok.png")), "t");

        assertEquals(base + "/ok.png", out.get(0).url());
        assertEquals(0, hits.get(), "自己的地址不该发出任何请求");
    }

    @Test
    @DisplayName("开关关掉时原样返回,一个请求都不发")
    void disabledPassesThrough() {
        serveBytes("/ok.png", 200, "image/png", PNG);
        StorageProperties props = props();
        props.setEnabled(false);

        List<ImageRef> out = transloader(props).localize(List.of(image(base + "/ok.png")), "t");

        assertEquals(base + "/ok.png", out.get(0).url());
        assertEquals(0, hits.get());
    }

    @Test
    @DisplayName("空列表原样返回")
    void emptyListPassesThrough() {
        List<ImageRef> out = transloader(props()).localize(List.of(), "t");

        assertTrue(out.isEmpty());
    }

    // ------------------------------------------------------------------
    // 帮手
    // ------------------------------------------------------------------

    private ImageRef image(String url) {
        return new ImageRef(url, "default", true);
    }

    /** 断言 URL 被原样保留 —— 降级路径的统一断言。 */
    private void assertKeptOriginal(String path) {
        List<ImageRef> out = transloader(props()).localize(List.of(image(base + path)), "t");

        assertEquals(base + path, out.get(0).url());
        assertEquals(0, fileCount(), "降级时不该留下任何文件");
    }

    private StorageProperties props() {
        StorageProperties props = new StorageProperties();
        props.setRoot(tempDir.toString());
        props.setPublicBaseUrl("http://localhost:8080");
        return props;
    }

    /**
     * 打桩服务绑在 127.0.0.1 —— 不覆写主机判定的话,第一关就被自家的 SSRF 防护拒了。
     * 生产路径不做任何覆写(见 {@code UrlPolicy#isBlockedHost})。
     *
     * <p>自有域仍按配置装配(不能传空列表):否则 {@code ownAddressIsNotDownloaded}
     * 这条就永远走不到 OWN 分支,测了个寂寞。
     */
    private ImageTransloader transloader(StorageProperties props) {
        UrlPolicy permissive = new UrlPolicy(props.effectiveOwnBaseUrls()) {
            @Override
            protected boolean isBlockedHost(String host) {
                return false;
            }
        };
        return new ImageTransloader(props, permissive);
    }

    private void serveBytes(String path, int status, String contentType, byte[] body) {
        server.createContext(path, exchange -> {
            hits.incrementAndGet();
            exchange.getResponseHeaders().add("Content-Type", contentType);
            if (status / 100 == 3) {
                exchange.sendResponseHeaders(status, -1);
                exchange.close();
                return;
            }
            if (body.length == 0) {
                exchange.sendResponseHeaders(status, -1);
                exchange.close();
                return;
            }
            exchange.sendResponseHeaders(status, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
    }

    private long fileCount() {
        try (Stream<Path> walk = Files.walk(tempDir)) {
            return walk.filter(Files::isRegularFile).count();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private Path firstFile() {
        try (Stream<Path> walk = Files.walk(tempDir)) {
            return walk.filter(Files::isRegularFile).findFirst().orElseThrow();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
