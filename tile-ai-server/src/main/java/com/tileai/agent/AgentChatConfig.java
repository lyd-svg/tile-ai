package com.tileai.agent;

import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 对话流(T-1.6)的执行器装配。
 *
 * <p><b>为什么需要它。</b>一次问答是"打开到 Python 的流 + 逐帧往浏览器推",
 * 不能占用 Servlet 请求线程 —— 请求线程必须尽快返回,把响应交给异步处理
 * ({@code SseEmitter} 的价值就在于此)。这里给一条独立的池,与 Tomcat 的请求线程
 * 和 Outbox / 生成补偿那些后台线程互不挤占。
 *
 * <p><b>拒绝策略是 Abort。</b>池满时宁可当场拒绝(转成一帧 error 90001),
 * 也不要让 {@code CallerRuns} 把 Servlet 线程拖进流式循环里 —— 那会让"异步"
 * 变成一个假的异步。每个活跃流占用一个线程,上限由这里的数字决定。
 */
@Configuration
public class AgentChatConfig {

    public static final String EXECUTOR_BEAN_NAME = "agentChatExecutor";

    @Bean(name = EXECUTOR_BEAN_NAME)
    public ThreadPoolTaskExecutor agentChatExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(32);
        executor.setThreadNamePrefix("agent-chat-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }
}
