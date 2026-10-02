package com.tileai.common.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 效果图本地存储的装配。
 *
 * <p>把 {@link UrlPolicy} 单独做成 bean,是为了让
 * {@link ImageTransloader} 依赖<b>接口式</b>的策略对象 —— 测试里能换成一个
 * "放行 127.0.0.1"的策略去打本地桩服务,否则 SSRF 防护会把测试自己要用的
 * 环回地址也一起拒掉。
 */
@Configuration
public class StorageConfig {

    private static final Logger log = LoggerFactory.getLogger(StorageConfig.class);

    @Bean
    public UrlPolicy urlPolicy(StorageProperties properties) {
        return new UrlPolicy(properties.effectiveOwnBaseUrls());
    }

    @Bean
    public ImageTransloader imageTransloader(StorageProperties properties, UrlPolicy urlPolicy) {
        ImageTransloader transloader = new ImageTransloader(properties, urlPolicy);

        // 落盘目录在启动时打一次绝对路径。默认值在用户主目录下,不打印的话
        // "图到底存哪了"会变成一个要翻配置才能回答的问题。
        if (!properties.isEnabled()) {
            log.warn("效果图本地存储已关闭(tile.storage.enabled=false)—— 图片 URL 将原样入库,"
                    + "真实模型返回的临时地址 24 小时后会失效");
        } else {
            log.info("效果图本地存储:目录={} 对外前缀={}{}",
                    transloader.resolvedRoot(), properties.getPublicBaseUrl(), properties.normalizedMediaPath());
        }
        return transloader;
    }
}
