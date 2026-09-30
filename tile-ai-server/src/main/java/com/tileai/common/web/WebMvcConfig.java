package com.tileai.common.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.tileai.admin.rbac.AdminPermissionInterceptor;
import com.tileai.common.security.ServiceTokenInterceptor;

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

    public WebMvcConfig(AdminPermissionInterceptor adminPermissionInterceptor,
                        ServiceTokenInterceptor serviceTokenInterceptor) {
        this.adminPermissionInterceptor = adminPermissionInterceptor;
        this.serviceTokenInterceptor = serviceTokenInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminPermissionInterceptor).addPathPatterns(ADMIN_PATH);
        registry.addInterceptor(serviceTokenInterceptor).addPathPatterns(INTERNAL_PATH);
    }
}
