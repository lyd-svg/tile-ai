package com.tileai.common.storage;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 效果图的本地存储配置(架构 §15.5)。
 *
 * <h2>为什么是有这个类</h2>
 *
 * <p>真实图像模型(通义万相)返回的是 <b>24 小时过期的 OSS 临时 URL</b>
 * (架构 §15.1 实测)。URL 直接入库的后果是<b>方案页的图隔天全裂</b>,
 * 分享页尤其难看 —— 链接不过期,但接收方打开是空图。所以在回调时把图
 * 下载进本机磁盘,库里只存<b>我们自己的、长期有效的地址</b>。
 *
 * <h2>形态:本机磁盘 + 抽一层接口</h2>
 *
 * <p>一期按<b>本机磁盘</b>落地(毕业项目单机运行,不引入云 OSS / MinIO)。
 * 但转存动作走 {@link ImageTransloader},将来换对象存储只换实现。
 *
 * <h2>⚠️ {@code publicBaseUrl} 是 Java API 的地址,不是前端地址</h2>
 *
 * <p>别顺手抄 {@code tile.share.base-url} —— 那个是<b>前端页面</b>的地址
 * (默认 {@code http://localhost:5173}),拿它拼出来的图片地址在 dev 下会打到
 * Vite 而 404(Vite 的代理只转发 {@code /api})。这里要的是<b>图片真正由谁提供</b>
 * ——也就是本服务自己。
 *
 * <p>由 {@code @ConfigurationPropertiesScan} 自动注册,无需
 * {@code @EnableConfigurationProperties}。
 */
@ConfigurationProperties(prefix = "tile.storage")
public class StorageProperties {

    /**
     * 总开关。关掉则回调里的图片 URL <b>原样入库</b>(与本功能不存在时的行为一致)。
     *
     * <p>存在的意义是让集成测试不受网络影响 —— 它们用的假地址
     * ({@code http://java.test/...})会触发真实下载尝试,白等超时。
     */
    private boolean enabled = true;

    /**
     * 图片落盘根目录。
     *
     * <p>默认放用户主目录而不是应用工作目录:<b>相对路径按谁的工作目录解析是不确定的</b>
     * (IDE 跑与 {@code mvn spring-boot:run} 跑不是同一个目录),那会变成"图存到哪了"
     * 这种查半天的问题。启动时会解析成绝对路径并打 INFO 日志。
     */
    private String root = System.getProperty("user.home") + "/.tile-ai/media";

    /** 对外访问前缀 = <b>本服务(Java)</b> 的地址。上线必配。 */
    private String publicBaseUrl = "http://localhost:8080";

    /** 对外路径前缀,与 {@code WebMvcConfig#addResourceHandlers} 的映射一致。 */
    private String mediaPath = "/media";

    /**
     * 除 {@code publicBaseUrl} 之外,还要当成"自己的地址"的 base URL。
     *
     * <p>命中的 URL <b>跳过转存</b>、原样保留。占位图模式靠它零开销 ——
     * 那时 Python 返回的本来就是我们自己的 {@code /placeholder.svg}。
     */
    private List<String> ownBaseUrls = new ArrayList<>();

    /** 单张图连接超时。 */
    private Duration connectTimeout = Duration.ofSeconds(3);

    /** 单张图读取超时。 */
    private Duration readTimeout = Duration.ofSeconds(10);

    /**
     * 一次成功回调的<b>下载总预算</b>。
     *
     * <p>⚠️ <b>必须小于 Python 的回调超时</b>({@code java_callback_timeout_seconds},
     * 当前 10 秒,见 {@code tile-ai-agent/app/core/config.py})。超出预算就放弃剩余图、
     * 降级保留临时 URL。
     *
     * <p>这不是性能优化,是<b>硬约束</b>:下载若长时间挂着,任务就迟迟到不了
     * {@code success},而 Java 的补偿扫描({@code running-stuck-after},默认 360 秒)
     * 会把它当"卡住"重投 —— 那会<b>真的重跑一次真实模型</b>,白烧额度。
     */
    private Duration overallBudget = Duration.ofSeconds(8);

    /** 单张图体积上限。按实际读取字节数计,<b>不信任 Content-Length</b>。 */
    private long maxBytes = 10L * 1024 * 1024;

    /** 一次回调最多处理几张图。契约 angles 上限是 4,留一倍余量。 */
    private int maxImages = 8;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getRoot() {
        return root;
    }

    public void setRoot(String root) {
        this.root = root;
    }

    public String getPublicBaseUrl() {
        return publicBaseUrl;
    }

    public void setPublicBaseUrl(String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl;
    }

    public String getMediaPath() {
        return mediaPath;
    }

    public void setMediaPath(String mediaPath) {
        this.mediaPath = mediaPath;
    }

    public List<String> getOwnBaseUrls() {
        return ownBaseUrls;
    }

    public void setOwnBaseUrls(List<String> ownBaseUrls) {
        this.ownBaseUrls = ownBaseUrls;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }

    public Duration getOverallBudget() {
        return overallBudget;
    }

    public void setOverallBudget(Duration overallBudget) {
        this.overallBudget = overallBudget;
    }

    public long getMaxBytes() {
        return maxBytes;
    }

    public void setMaxBytes(long maxBytes) {
        this.maxBytes = maxBytes;
    }

    public int getMaxImages() {
        return maxImages;
    }

    public void setMaxImages(int maxImages) {
        this.maxImages = maxImages;
    }

    /**
     * 全部"自己的地址" —— {@code publicBaseUrl} 恒在内,外加显式配置的那些。
     *
     * <p>{@code publicBaseUrl} 自动纳入,是为了让"只配了对外地址、忘了配自有域"
     * 这种常见配置也能正确跳过转存。
     */
    public List<String> effectiveOwnBaseUrls() {
        List<String> all = new ArrayList<>();
        if (publicBaseUrl != null && !publicBaseUrl.isBlank()) {
            all.add(publicBaseUrl);
        }
        if (ownBaseUrls != null) {
            for (String url : ownBaseUrls) {
                if (url != null && !url.isBlank()) {
                    all.add(url);
                }
            }
        }
        return all;
    }

    /** 对外路径前缀,规范化成"以 {@code /} 开头、不以 {@code /} 结尾"。 */
    public String normalizedMediaPath() {
        String path = mediaPath == null || mediaPath.isBlank() ? "/media" : mediaPath.trim();
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        while (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }

    /** 落盘根目录的绝对路径。 */
    public Path resolvedRootPath() {
        return Path.of(root).toAbsolutePath().normalize();
    }

    /** 落盘根目录的 {@code file:} URI,给静态资源映射用(必须以 {@code /} 结尾)。 */
    public String resolvedRootUri() {
        String uri = resolvedRootPath().toUri().toString();
        return uri.endsWith("/") ? uri : uri + "/";
    }
}
