package com.tileai.plan;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 分享链接的配置(契约 {@code openapi-c.yaml} 的 share 分组;T-1.13)。
 *
 * <h2>为什么指向前端页面,而不是 API 地址</h2>
 *
 * <p>后端返回的 {@code shareUrl} 是**发给别人的东西** —— 家人、设计师、门店店员
 * 在浏览器里打开它。他们打开的是一个<b>页面</b>,不是去调接口。所以这个域名是
 * 前端站点的域名,与 {@code tile.agent.base-url}(那才是真正的 API 调用地址)
 * 是两件不同的事,别顺手改成同一个值。
 *
 * <h2>⚠️ 上线前必须改掉默认值</h2>
 *
 * <p>默认 {@code http://localhost:5173} 是本地开发的 Vite 端口,与
 * {@code tile.agent.base-url} 留成本地默认是同一个口径:<b>方便本地跑通</b>。
 *
 * <p>但它与 {@code tile.agent.base-url} 有一个要命的区别:那个配错了会在<b>调用时
 * 立刻失败</b>,日志里看得见;这个配错了是**静默产出一个打不开的链接**,而且
 * 是<b>发给别人</b>的 —— 分享者自己在浏览器里试不出来,只会收到一句"你这链接打不开"。
 * 所以它是一个必须在上线检查清单里的项(见 {@code openapi-verification.md} §43)。
 *
 * <p>由 {@code @ConfigurationPropertiesScan} 自动注册,无需 {@code @EnableConfigurationProperties}。
 */
@ConfigurationProperties(prefix = "tile.share")
public class ShareProperties {

    /** 前端站点地址(不是 API 地址)。末尾带不带 {@code /} 都行,拼接时会去掉。 */
    private String baseUrl = "http://localhost:5173";

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }
}
