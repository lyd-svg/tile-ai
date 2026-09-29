package com.tileai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 瓷砖选购 AI — Java 业务核入口。
 *
 * <p>本服务是<b>唯一对外 API</b>(CLAUDE.md 架构不变量 3):前端只连本服务;
 * Python 智能体仅内网、不直接对外,{@code /internal/*} 由网关阻断。
 *
 * <p>模块按业务域划分为 package(架构 §4.1),一期同进程,后续可独立成服务。
 * 各模块职责见对应 package 的 {@code package-info.java}。
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class TileAiServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(TileAiServerApplication.class, args);
    }
}
