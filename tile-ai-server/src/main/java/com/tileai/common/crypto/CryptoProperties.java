package com.tileai.common.crypto;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 隐私字段静态加密的配置(架构 §13.5)。
 *
 * <p>由 {@code @ConfigurationPropertiesScan} 自动注册,无需
 * {@code @EnableConfigurationProperties}。
 */
@ConfigurationProperties(prefix = "tile.crypto")
public class CryptoProperties {

    /**
     * 户型快照的加密密钥:Base64 编码的 **32 字节**(AES-256)。
     *
     * <p>由环境变量 {@code LAYOUT_ENC_KEY} 注入 —— <b>只走环境变量</b>(§13.6):
     * 不落代码、不落库、不写进配置文件的默认值,日志里也不打印。
     *
     * <p>留空时应用<b>照常启动</b>,但任何写入 {@code layout_enc} 的操作都会失败。
     * 刻意<b>不</b>退化为明文存储:那等于在没人注意的时候把加密悄悄关掉,
     * 而"加密没生效"和"加密生效了"在库里看起来一模一样(都是能读的字节)。
     */
    private String layoutKey = "";

    public String getLayoutKey() {
        return layoutKey;
    }

    public void setLayoutKey(String layoutKey) {
        this.layoutKey = layoutKey;
    }
}
