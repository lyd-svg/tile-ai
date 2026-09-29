package com.tileai.common.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.tileai.admin.rbac.AdminPermissionInterceptor;

/**
 * Web 层装配。
 *
 * <p>目前只做一件事:把 {@link AdminPermissionInterceptor} 挂到后台路径上。
 * <b>只挂 {@code /api/v1/admin/**}</b> —— C 端没有 RBAC,登录态要求由
 * {@code SecurityConfig} 的路径规则负责,两者不要混在一起。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private static final String ADMIN_PATH = "/api/v1/admin/**";

    private final AdminPermissionInterceptor adminPermissionInterceptor;

    public WebMvcConfig(AdminPermissionInterceptor adminPermissionInterceptor) {
        this.adminPermissionInterceptor = adminPermissionInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminPermissionInterceptor).addPathPatterns(ADMIN_PATH);
    }
}
