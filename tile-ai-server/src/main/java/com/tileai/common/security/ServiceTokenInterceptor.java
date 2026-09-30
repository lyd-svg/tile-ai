package com.tileai.common.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import com.tileai.common.agent.AgentProperties;
import com.tileai.common.error.BusinessException;
import com.tileai.common.error.ErrorCode;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * {@code /internal/**} 的服务令牌校验(契约 {@code serviceToken};架构 §6.1、§13.5)。
 *
 * <p><b>它挡的是谁</b>:{@code /internal/*} 的调用方只有 Python 智能体,理论上由内网
 * 与网关隔离(不变量 3)。但"对外由网关阻断"是部署侧的承诺,不是本服务的保证 ——
 * 一旦网关规则配错、或有人从内网横向发起请求,这些接口就是裸奔的。令牌让
 * <b>绕过网关也进不来</b>,这就是纵深防御与"只靠一层"的区别。
 *
 * <p><b>为什么不是 Spring Security 的过滤链</b>:与 {@link AdminPermissionInterceptor}
 * 同理 —— 本类的失败要变成统一响应体(HTTP 401 + 业务码 10002),
 * 而 {@code SecurityConfig} 里 {@code /internal/**} 落在 {@code anyRequest().permitAll()}
 * 的兜底上。把校验放在 MVC 拦截器里,异常直接交给 {@code GlobalExceptionHandler},
 * 响应体口径只有一处。
 *
 * <p><b>令牌复用的是一处配置</b>:{@code tile.agent.service-token}({@link AgentProperties}),
 * 不另起一个密钥。收发两侧用同一个值,否则会出现"Java 发的头 Java 自己都不认"
 * 这种要查半天的错。
 *
 * <p><b>未配置时放行并告警</b> —— 与 Python 侧 {@code require_service_token} 同口径:
 * 本地开发默认不配,直接拒绝会让所有联调卡住。生产必须配置(§13.6)。
 */
@Component
public class ServiceTokenInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(ServiceTokenInterceptor.class);

    /** 契约 {@code securitySchemes.serviceToken} 声明的请求头名。 */
    public static final String HEADER = "X-Service-Token";

    private final AgentProperties properties;

    public ServiceTokenInterceptor(AgentProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) {
        String expected = properties.getServiceToken();
        if (!StringUtils.hasText(expected)) {
            log.warn("未配置 tile.agent.service-token,/internal{} 未做校验 —— "
                    + "仅限本地联调;生产必须配置(架构 §13.6)", request.getRequestURI());
            return true;
        }

        String presented = request.getHeader(HEADER);
        if (!matches(expected, presented)) {
            // 不告诉调用方"令牌错"还是"没带令牌",也不回显它带了什么 —— 内部接口同样按
            // §10.5 的口径少说话。401 交给 GlobalExceptionHandler 落成 10002。
            throw new BusinessException(ErrorCode.UNAUTHENTICATED, "服务令牌无效");
        }
        return true;
    }

    /**
     * 定长比较。
     *
     * <p>用 {@link MessageDigest#isEqual} 而不是 {@code String.equals}:后者在第一个
     * 不同的字节处就返回,比较耗时随"猜对了几个字符"变化。内部接口+内网让这条风险不高,
     * 但改成一个方法调用就能消掉,没有理由留着。
     */
    private static boolean matches(String expected, String presented) {
        if (presented == null) {
            return false;
        }
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                presented.getBytes(StandardCharsets.UTF_8));
    }
}
