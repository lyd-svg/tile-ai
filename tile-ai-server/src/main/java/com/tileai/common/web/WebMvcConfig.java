package com.tileai.common.web;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.tileai.admin.rbac.AdminPermissionInterceptor;
import com.tileai.common.agent.AgentProperties;
import com.tileai.common.security.ServiceTokenInterceptor;
import com.tileai.common.storage.StorageProperties;

/**
 * Web 层装配 —— 把两个拦截器挂到各自的路径上。
 *
 * <p>两条路径的授权模型完全不同,<b>刻意不混在一起</b>:
 * <ul>
 *   <li>{@code /api/v1/admin/**} —— 后台 RBAC,权限点由
 *       {@link AdminPermissionInterceptor} 逐个接口判定;</li>
 *   <li>{@code /internal/**} —— 服务间调用,只有一道
 *       {@link ServiceTokenInterceptor} 服务令牌,没有"角色"的概念。</li>
 * </ul>
 *
 * <p>C 端没有拦截器:登录态要求由 {@code SecurityConfig} 的路径规则负责。
 */
@Configuration
// ⚠️ 这一行不是可有可无的。本类被 @WebMvcTest 切片加载,而切片**不跑**
// @ConfigurationPropertiesScan —— 少了它,切片里就没有 StorageProperties 这个 bean,
// 整个上下文起不来(实测:11 个契约测试全红)。@EnableConfigurationProperties 是
// 幂等的,和 SecurityConfig 上的那一份重复注册也无害。
@EnableConfigurationProperties(StorageProperties.class)
public class WebMvcConfig implements WebMvcConfigurer {

    private static final String ADMIN_PATH = "/api/v1/admin/**";

    /**
     * 内部接口路径。
     *
     * <p>契约里的这几个端点都是 {@code /internal/*},<b>不带 {@code /api/v1} 前缀</b>
     * —— 它们不属于对外 API 的版本体系(不变量 3:Java 是唯一对外 API,
     * 而 {@code /internal/*} 对外由网关阻断)。
     */
    private static final String INTERNAL_PATH = "/internal/**";

    private final AdminPermissionInterceptor adminPermissionInterceptor;
    private final ServiceTokenInterceptor serviceTokenInterceptor;
    private final AgentProperties agentProperties;
    private final StorageProperties storageProperties;

    public WebMvcConfig(AdminPermissionInterceptor adminPermissionInterceptor,
                        ServiceTokenInterceptor serviceTokenInterceptor,
                        AgentProperties agentProperties,
                        StorageProperties storageProperties) {
        this.adminPermissionInterceptor = adminPermissionInterceptor;
        this.serviceTokenInterceptor = serviceTokenInterceptor;
        this.agentProperties = agentProperties;
        this.storageProperties = storageProperties;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminPermissionInterceptor).addPathPatterns(ADMIN_PATH);
        registry.addInterceptor(serviceTokenInterceptor).addPathPatterns(INTERNAL_PATH);
    }

    /**
     * Servlet 异步请求的超时。
     *
     * <p><b>T-1.6 的 SSE 依赖它。</b>Servlet 容器的异步默认超时(Tomcat 约 30s)
     * 会让一次较长的问答在图还没出完时被<b>静默截断</b> —— 前端只看到"流断了",
     * 分不清是模型慢还是网络坏。这里用与 {@code SseEmitter} 同一个
     * {@code tile.agent.chat-timeout} 作为上界。
     */
    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setDefaultTimeout(agentProperties.getChatTimeout().toMillis());
    }

    /**
     * 效果图的静态托管。
     *
     * <p>把 {@code tile.storage.root} 这个磁盘目录映射到
     * {@code {media-path}/**}。图片不在 classpath 里(是运行时抓下来的),
     * 所以不能像 {@code placeholder.svg} 那样靠 Spring Boot 的默认
     * {@code classpath:/static/} 映射,必须显式指出去。
     *
     * <p>缓存可以放心地设长:{@link com.tileai.common.storage.ImageTransloader}
     * 是<b>按内容哈希</b>命名的,同一个地址的字节永远不会变。
     *
     * <p>访问权限不在这里管 —— {@code /media/**} 不在
     * {@code SecurityConfig} 的受保护路径里,匿名可读(分享页要用)。
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String pattern = storageProperties.normalizedMediaPath() + "/**";
        registry.addResourceHandler(pattern)
                .addResourceLocations(storageProperties.resolvedRootUri())
                .setCacheControl(CacheControl.maxAge(java.time.Duration.ofDays(30)).cachePublic());
    }
}
