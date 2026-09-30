package com.tileai.common.agent;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Python 智能体服务的调用配置(架构 §6.1)。
 *
 * <p>这是 Java 侧**第一次**出网调 Python。三条约束:
 *
 * <ul>
 *   <li><b>仅内网</b> —— Python 不对外,{@code /internal/*} 由网关阻断(§6.1、不变量 3);</li>
 *   <li><b>服务令牌</b> —— 请求头 {@code X-Service-Token}
 *       (契约 {@code openapi-common.yaml#/components/securitySchemes/serviceToken});
 *       令牌只走环境变量(§13.6),<b>不落代码</b>;</li>
 *   <li><b>超时必设</b> —— 没有超时的内部调用会把上游线程一起拖死。
 *       户型解析含一次模型调用,默认给 30s(实测真实解析约 2–5s)。</li>
 * </ul>
 *
 * <p>由 {@code @ConfigurationPropertiesScan} 自动注册,无需 {@code @EnableConfigurationProperties}。
 */
@ConfigurationProperties(prefix = "tile.agent")
public class AgentProperties {

    /** 智能体服务地址。本地开发默认直连;生产经内网服务名(架构 §12.1)。 */
    private String baseUrl = "http://localhost:8000";

    /**
     * 服务令牌。由环境变量 {@code AGENT_SERVICE_TOKEN} 注入(§13.6)。
     * <p>留空时不发送该请求头,Python 侧也会放行 —— 便于本地联调;
     * 生产必须配置,否则 {@code /internal/*} 等同于裸奔(虽有内网与 mTLS 兜着)。
     */
    private String serviceToken = "";

    /** 单次调用超时。解析含一次模型调用,给足余量但仍要有限。 */
    private Duration timeout = Duration.ofSeconds(30);

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getServiceToken() {
        return serviceToken;
    }

    public void setServiceToken(String serviceToken) {
        this.serviceToken = serviceToken;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }
}
