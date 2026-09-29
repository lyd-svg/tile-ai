package com.tileai.common.security;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 鉴权与会话配置(架构 §10.1 · D1 定稿)。
 *
 * <p>各时长与 Cookie 属性<b>不是随手取的</b>,它们直接来自契约
 * {@code openapi-common.yaml#/components/securitySchemes/cookieAuth}:
 * access 2 小时、refresh 14 天、refresh 的 Path 收窄至 {@code /api/v1/auth}。
 * 改这些默认值等同于改契约,须走上游改文档的流程。
 *
 * <p>密钥只走环境变量(§13.6),见 {@code secret} 字段的说明。
 */
@ConfigurationProperties(prefix = "tile.auth")
public class JwtProperties {

    private String issuer = "tile-ai";

    /**
     * JWT 签名密钥(HS256,至少 32 字节)。
     *
     * <p>由环境变量 {@code JWT_SECRET} 注入(§13.6:密钥不落库、不落代码)。
     * 留空时:非 prod 环境自动生成随机密钥并打 WARN(重启即失效,仅便于本地开发);
     * prod 环境<b>直接启动失败</b> —— 宁可起不来,也不能用可预测的密钥签令牌。
     */
    private String secret = "";

    /** access_token 有效期 2 小时(契约)。 */
    private Duration accessTtl = Duration.ofHours(2);

    /** refresh_token 有效期 14 天(契约)。 */
    private Duration refreshTtl = Duration.ofDays(14);

    private String accessCookieName = "access_token";

    private String refreshCookieName = "refresh_token";

    /** access_token 作用于全部接口。 */
    private String accessCookiePath = "/";

    /** refresh_token 只在该前缀下发送 —— 收窄暴露面(契约明确要求)。 */
    private String refreshCookiePath = "/api/v1/auth";

    /**
     * Cookie 的 SameSite 属性。
     *
     * <p><b>架构 §13.5 未规定此属性</b>,属上游未定义项。这里取 {@code Lax}:
     * 跨站发起的 POST/PATCH/DELETE 不会携带 Cookie,足以挡住 CSRF;
     * 同时不像 {@code Strict} 那样让"从外部链接进入"的用户看起来像掉线。
     * 契约未提供 CSRF token 端点,故未采用 token 方案。
     */
    private String sameSite = "Lax";

    /** HTTPS 下必须置 true(生产);本地 http 开发保持 false,否则浏览器不保存 Cookie。 */
    private boolean secure = false;

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public Duration getAccessTtl() {
        return accessTtl;
    }

    public void setAccessTtl(Duration accessTtl) {
        this.accessTtl = accessTtl;
    }

    public Duration getRefreshTtl() {
        return refreshTtl;
    }

    public void setRefreshTtl(Duration refreshTtl) {
        this.refreshTtl = refreshTtl;
    }

    public String getAccessCookieName() {
        return accessCookieName;
    }

    public void setAccessCookieName(String accessCookieName) {
        this.accessCookieName = accessCookieName;
    }

    public String getRefreshCookieName() {
        return refreshCookieName;
    }

    public void setRefreshCookieName(String refreshCookieName) {
        this.refreshCookieName = refreshCookieName;
    }

    public String getAccessCookiePath() {
        return accessCookiePath;
    }

    public void setAccessCookiePath(String accessCookiePath) {
        this.accessCookiePath = accessCookiePath;
    }

    public String getRefreshCookiePath() {
        return refreshCookiePath;
    }

    public void setRefreshCookiePath(String refreshCookiePath) {
        this.refreshCookiePath = refreshCookiePath;
    }

    public String getSameSite() {
        return sameSite;
    }

    public void setSameSite(String sameSite) {
        this.sameSite = sameSite;
    }

    public boolean isSecure() {
        return secure;
    }

    public void setSecure(boolean secure) {
        this.secure = secure;
    }
}
