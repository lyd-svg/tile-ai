package com.tileai.common.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tileai.common.storage.StorageProperties;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 配置(架构 §10.1 · D1 定稿)。
 *
 * <p><b>授权默认值刻意分两端</b>,与产品形态一致(PRD §5.1:浏览免登录,
 * 生成/保存/问 AI 才要登录):
 * <ul>
 *   <li><b>C 端默认放行</b> —— 产品库、搭配、户型模板、报价预览、门店、字典都可匿名访问;</li>
 *   <li><b>后台默认拦截</b> —— {@code /api/v1/admin/**} 除登录外一律要后台身份。
 *       新增后台接口若忘了配权限,会落到这里被挡住,而不是裸奔。</li>
 * </ul>
 *
 * <p>权限判定读的是<b>当前请求的身份</b>,不依赖令牌里携带的角色快照 —— 见
 * {@link AuthPrincipal} 的说明。
 */
@Configuration
@EnableWebSecurity
// 与 WebMvcConfig 上的那一份同理:@WebMvcTest 切片不跑 @ConfigurationPropertiesScan,
// 本类又要用 StorageProperties 放行 {media-path}/** —— 自己注册一份,切片才能起来。
@EnableConfigurationProperties(StorageProperties.class)
public class SecurityConfig {

    /**
     * C 端需登录的路径 —— 逐条对应 {@code openapi-c.yaml} 中标注
     * {@code security: cookieAuth} 的接口。改这份清单等同于改契约的鉴权矩阵。
     *
     * <p>用 {@code hasAuthority("ROLE_USER")} 而非 {@code authenticated()}:显式要求
     * <b>业主身份</b>,把后台令牌挡在外面(理由见下一条)。
     *
     * <p>这里<b>不接受后台身份</b>(ROLE_ADMIN)。方案归属 {@code t_plan.user_id},
     * 与 {@code t_admin_user.id} 是两套 ID 空间;若允许后台令牌访问 C 端写接口,
     * 就可能出现"管理员 id=5"操作到"业主 id=5"的方案。这类越权靠 ID 空间隔离来挡,
     * 不能靠代码自觉。
     */
    private static final String[] PROTECTED_C_PATHS = {
            "/api/v1/agent/**",      // AI 咨询(路径 C)及对话记录
            "/api/v1/generate/**",   // 效果图生成与进度查询
            "/api/v1/plans/**",      // 我的方案(含分享、重算)
            "/api/v1/auth/password", // 修改密码
            "/api/v1/auth/logout",   // 登出
            "/api/v1/auth/me",       // 取当前登录账号(会话恢复)
    };

    /**
     * 鉴权过滤器。
     *
     * <p>用 {@code @Bean} 而非 {@code @Component} 注册,是为了避免它被 Spring Boot
     * 同时当作 servlet 过滤器自动注册一遍 —— 那样每个请求会经过它两次。
     */
    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(JwtService jwtService,
                                                           JwtProperties props) {
        return new JwtAuthenticationFilter(jwtService, props);
    }

    /**
     * 密码哈希器。BCrypt(架构 §13.5「密码 BCrypt」)。
     *
     * <p>强度用默认值 10 —— 单店后台的登录频率极低,没必要为省几十毫秒而降低强度。
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public RestAuthenticationEntryPoint restAuthenticationEntryPoint(ObjectMapper objectMapper) {
        return new RestAuthenticationEntryPoint(objectMapper);
    }

    @Bean
    public RestAccessDeniedHandler restAccessDeniedHandler(ObjectMapper objectMapper) {
        return new RestAccessDeniedHandler(objectMapper);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtAuthenticationFilter jwtFilter,
                                                   RestAuthenticationEntryPoint entryPoint,
                                                   RestAccessDeniedHandler deniedHandler,
                                                   StorageProperties storageProperties) throws Exception {
        http
                // 关闭 CSRF:本服务是无状态的纯 JSON API,不依赖表单;
                // 跨站发起的状态变更请求会被 SameSite=Lax 挡在门外(见 JwtProperties#sameSite),
                // 且契约未提供 CSRF token 端点,无法采用 token 方案。
                // ⚠️ 若将来前端改为跨站部署(非同源),此处必须重新评估 —— 那时 SameSite
                // 需要放宽,CSRF 就不再被挡住了。
                .csrf(AbstractHttpConfigurer::disable)
                // 不用表单登录 / Basic:鉴权只走 Cookie(§10.1;v0.7 起无 Bearer 例外)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                // 关掉 Spring 自带的 /logout,登出走 /api/v1/auth/logout(T-1.1),
                // 那里要做 refresh 令牌拉黑,默认实现做不到。
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // ⚠️ **只对 REQUEST / FORWARD 判权,跳过 ASYNC 与 ERROR 回派。**
                        // 不加这一行,SSE(`SseEmitter`)会在**流结束时**炸:
                        // 异步回派会再走一遍过滤链,而自定义的 JwtAuthenticationFilter
                        // 继承自 OncePerRequestFilter —— 它**默认跳过 ASYNC 回派**,
                        // 于是回派时无人认证;但鉴权过滤器默认**会**在 ASYNC 上再判一次,
                        // 结果就是 `AuthorizationDeniedException: Access Denied`,
                        // 且因为响应已经提交而无法改状态码(2026-10-02 联调实测踩到)。
                        // ASYNC 回派不会重新执行 controller,放行它不放宽任何权限。
                        .shouldFilterAllDispatcherTypes(false)
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // 效果图。匿名必须可读 —— 分享页(§43)就是给没登录的人看的,
                        // 把这条收进 PROTECTED_C_PATHS 会让分享页静默图裂(不报错,
                        // 只是图不显示),所以显式写出来钉住这个意图。
                        .requestMatchers(storageProperties.normalizedMediaPath() + "/**").permitAll()

                        // 注册 / 登录 / 刷新 —— 契约声明 security: [],本就无需登录态
                        // (最终会落到下方的 anyRequest().permitAll())。显式列出是为了让
                        // "哪些 auth 接口是公开的"一眼可见。
                        // v0.7 起注册与登录各是一次请求,不再有「验证码登录 → 待设密码」两阶段,
                        // 因此也没有了原来的 tempToken 通道。
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/auth/register",
                                "/api/v1/auth/login",
                                "/api/v1/auth/refresh").permitAll()

                        // 后台:登录与刷新放行,其余一律要后台身份
                        // (刷新凭 admin_refresh_token Cookie 自证,与 C 端 /auth/refresh 同理)
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/admin/auth/login",
                                "/api/v1/admin/auth/refresh").permitAll()
                        .requestMatchers("/api/v1/admin/**").hasAuthority("ROLE_ADMIN")

                        // C 端需登录的接口
                        .requestMatchers(PROTECTED_C_PATHS).hasAuthority("ROLE_USER")

                        // 其余放行:浏览免登录(PRD §5.1)
                        .anyRequest().permitAll())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(deniedHandler))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
