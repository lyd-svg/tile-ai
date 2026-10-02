package com.tileai.common.storage;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.tileai.generate.dto.GenerateCallbackRequest.ImageRef;

/**
 * 把外部图片抓进本机磁盘,换成一个我们自己的、长期有效的地址。
 *
 * <h2>解决什么问题</h2>
 *
 * <p>通义万相返回的是 <b>24 小时过期的 OSS 临时 URL</b>(架构 §15.1 实测)。
 * 直接入库 ⇒ 方案页与分享页<b>隔天图裂</b>。这里在回调时把图下载下来,
 * 库里只存 {@code {publicBaseUrl}/media/ab/cd/<哈希>.png} 这样的自有地址。
 *
 * <h2>三条贯穿全类的原则</h2>
 *
 * <ol>
 *   <li><b>绝不抛异常。</b>任何一张图出问题都只降级为"保留原始临时 URL"。
 *       抛出会 500 ⇒ 回调事务回滚 ⇒ 任务退回可重试 ⇒ 补偿扫描(默认 360 秒后)
 *       重投 ⇒ Python <b>真的重跑一次真实模型</b>。转存失败不该有这个代价 ——
 *       降级后图在 24 小时内仍然看得见,与加这个功能之前的行为完全一致。</li>
 *   <li><b>必须有时间上界。</b>总预算默认 8 秒,且必须小于 Python 的回调超时
 *       (10 秒)—— 理由同上,把任务卡在 {@code running} 就是在给补偿扫描递刀。</li>
 *   <li><b>禁止重定向。</b>{@link UrlPolicy} 只判了第一跳,一个公网地址 302 到
 *       {@code 169.254.169.254} 就能把它的全部判定绕过。这里用
 *       {@code Redirect.NEVER},收到 3xx 直接判失败。</li>
 * </ol>
 *
 * <h2>文件名为什么是内容哈希</h2>
 *
 * <p>不用 {@code planId}/{@code view} 拼文件名,原因有二:
 * ① {@code view} 由 Python 传来、契约只限长度 50,<b>可以含 {@code ../}</b>,
 * 进了路径就是目录穿越;② 同一张图被回调两次(补偿重投)时,内容哈希让两次
 * 落到<b>同一个文件</b>上 —— 天然幂等,不用额外去重逻辑。
 *
 * <p>代价是覆盖方案时会留下孤儿文件({@code replaceImages} 先删库行、不删文件)。
 * 已登记为技术债,一期量小、先接受。
 */
public class ImageTransloader {

    private static final Logger log = LoggerFactory.getLogger(ImageTransloader.class);

    private final StorageProperties properties;
    private final UrlPolicy urlPolicy;
    private final HttpClient http;
    private final Path root;

    public ImageTransloader(StorageProperties properties, UrlPolicy urlPolicy) {
        this(properties, urlPolicy, HttpClient.newBuilder()
                // ⚠️ 必须 NEVER:UrlPolicy 只判了第一跳,跟随重定向等于把 SSRF 防护整个绕开
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(properties.getConnectTimeout())
                .build());
    }

    /** 供测试注入自带 transport / 超时的客户端。 */
    ImageTransloader(StorageProperties properties, UrlPolicy urlPolicy, HttpClient http) {
        this.properties = properties;
        this.urlPolicy = urlPolicy;
        this.http = http;
        this.root = properties.resolvedRootPath();
    }

    /**
     * 逐张转存。返回<b>等长</b>的新列表:转存成功的换成自有地址,
     * 其余的<b>原样保留</b>。
     *
     * @param images 回调带来的图片(可能为空)
     * @param taskId 仅用于日志与落盘目录的排查线索,<b>不进文件名</b>
     */
    public List<ImageRef> localize(List<ImageRef> images, String taskId) {
        if (images == null || images.isEmpty()) {
            return images;
        }
        if (!properties.isEnabled()) {
            return images;
        }

        long deadline = System.nanoTime() + properties.getOverallBudget().toNanos();
        List<ImageRef> out = new ArrayList<>(images.size());
        for (int i = 0; i < images.size(); i++) {
            ImageRef image = images.get(i);
            if (i >= properties.getMaxImages()) {
                log.warn("回调图片数超过上限,其余原样保留 taskId={} max={}", taskId, properties.getMaxImages());
                out.add(image);
                continue;
            }
            out.add(localizeOne(image, taskId, deadline));
        }
        return out;
    }

    private ImageRef localizeOne(ImageRef image, String taskId, long deadline) {
        String url = image.url();
        UrlPolicy.Decision decision = urlPolicy.assess(url);

        // 自己的地址(占位图模式走这条):原样返回,零开销
        if (decision.isOwn()) {
            return image;
        }
        if (!decision.isFetchable()) {
            log.warn("图片地址被拒,保留原地址 taskId={} view={} 原因={}", taskId, image.view(), decision.reason());
            return image;
        }
        if (System.nanoTime() >= deadline) {
            log.warn("转存预算已耗尽,剩余图片保留原地址 taskId={} view={}", taskId, image.view());
            return image;
        }

        try {
            byte[] bytes = fetch(url, deadline);
            String ext = detectExtension(bytes);
            String stored = store(bytes, ext);
            log.info("效果图已转存 taskId={} view={} 字节={} 地址={}", taskId, image.view(), bytes.length, stored);
            return new ImageRef(stored, image.view(), image.isCover());
        } catch (Exception e) {
            // 刻意吞掉:见类注释原则 1。转存失败不该让一次成功的生成变成失败。
            log.warn("效果图转存失败,保留原地址 taskId={} view={} 原因={}", taskId, image.view(), e.toString());
            return image;
        }
    }

    // ------------------------------------------------------------------
    // 下载
    // ------------------------------------------------------------------

    private byte[] fetch(String url, long deadline) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(effectiveReadTimeout(deadline))
                .GET()
                .build();

        HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try {
            int status = response.statusCode();
            if (status / 100 == 3) {
                // 禁止重定向的显式落点 —— 这里不是"忘了处理",是刻意拒绝
                throw new IOException("服务端返回重定向 " + status + ",按策略拒绝跟随");
            }
            if (status != 200) {
                throw new IOException("HTTP " + status);
            }

            String contentType = response.headers().firstValue("content-type").orElse("");
            if (!contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
                throw new IOException("Content-Type 不是图片: " + contentType);
            }

            return readCapped(response.body());
        } finally {
            response.body().close();
        }
    }

    /**
     * 读取响应体,<b>边读边计字节</b>。
     *
     * <p>不信任 {@code Content-Length}:它由服务端给出,可以撒谎 ——
     * 声称 1KB 然后推 10GB 过来。只有实际读到的字节数算数。
     */
    private byte[] readCapped(InputStream body) throws IOException {
        long max = properties.getMaxBytes();
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        long total = 0;
        int read;
        while ((read = body.read(chunk)) != -1) {
            total += read;
            if (total > max) {
                throw new IOException("图片超过体积上限 " + max + " 字节");
            }
            buffer.write(chunk, 0, read);
        }
        if (total == 0) {
            throw new IOException("响应体为空");
        }
        return buffer.toByteArray();
    }

    /** 单张的读取超时不能超过总预算的剩余量。 */
    private Duration effectiveReadTimeout(long deadline) {
        long remainingNanos = deadline - System.nanoTime();
        Duration remaining = Duration.ofNanos(Math.max(1, remainingNanos));
        return remaining.compareTo(properties.getReadTimeout()) < 0 ? remaining : properties.getReadTimeout();
    }

    // ------------------------------------------------------------------
    // 落盘
    // ------------------------------------------------------------------

    /**
     * 按内容哈希落盘,返回对外可访问的绝对地址。
     *
     * <p>已存在则直接返回 —— 同一张图重投时不会重复写,也不需要额外的去重。
     */
    private String store(byte[] bytes, String ext) throws IOException {
        String hash = sha256Hex(bytes);
        Path dir = root.resolve(hash.substring(0, 2)).resolve(hash.substring(2, 4));
        Path target = dir.resolve(hash + "." + ext);

        if (Files.exists(target)) {
            return publicUrlOf(hash, ext);
        }

        Files.createDirectories(dir);
        // 先写临时文件再原子改名:避免并发下别的请求读到写了一半的文件
        Path temp = Files.createTempFile(dir, hash, ".part");
        try {
            Files.write(temp, bytes);
            moveInto(temp, target);
        } finally {
            Files.deleteIfExists(temp);
        }
        return publicUrlOf(hash, ext);
    }

    private static void moveInto(Path temp, Path target) throws IOException {
        try {
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            // 跨文件系统时原子改名不可用,退化成普通移动(目标已存在则视为成功)
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** {@code {publicBaseUrl}{mediaPath}/{ab}/{cd}/{sha}.{ext}} */
    public String publicUrlOf(String hash, String ext) {
        String base = properties.getPublicBaseUrl() == null ? "" : properties.getPublicBaseUrl().trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + properties.normalizedMediaPath() + "/"
                + hash.substring(0, 2) + "/" + hash.substring(2, 4) + "/" + hash + "." + ext;
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    /**
     * 按<b>魔数</b>定扩展名,不看 URL 也不看 Content-Type 的后半截。
     *
     * <p>防的是"200 + 一页 HTML 错误页"被当成图片存下来 —— 那种文件存进去,
     * 前端拿到的是一个永远显示不出来的地址,而且查不出原因。
     */
    static String detectExtension(byte[] bytes) throws IOException {
        if (bytes.length >= 8
                && (bytes[0] & 0xFF) == 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47
                && bytes[4] == 0x0D && bytes[5] == 0x0A && bytes[6] == 0x1A && bytes[7] == 0x0A) {
            return "png";
        }
        if (bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        if (bytes.length >= 12
                && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
            return "webp";
        }
        throw new IOException("无法识别的图片格式(魔数不匹配 png/jpeg/webp)");
    }

    static String sha256Hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 是 JDK 必备算法,走不到这里
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    /** 落盘根目录(绝对路径),供启动日志与静态资源映射使用。 */
    public Path resolvedRoot() {
        return root;
    }
}
