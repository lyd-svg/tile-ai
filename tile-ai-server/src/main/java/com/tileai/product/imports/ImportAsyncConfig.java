package com.tileai.product.imports;

import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 批量导入的异步执行器(契约:导入是"异步任务,返回导入批次 batchId")。
 *
 * <p><b>为什么用独立执行器而不是 Spring 默认的</b>:默认执行器是全局共享的,
 * 导入这种长任务会把线程占满,连带拖垮其它异步工作。这里给一条独立队列,
 * 爆炸半径限于导入自身。
 *
 * <p>并发度刻意压到 1~2:导入是后台运营操作,并发跑多个大文件只会互相抢数据库连接,
 * 不会更快。
 *
 * <p>拒绝策略用 {@link ThreadPoolExecutor.CallerRunsPolicy} —— 队列满了就让
 * HTTP 线程自己跑。表现为"响应变慢"而不是"任务被丢弃"。导入任务被静默丢弃,
 * 运营会以为导入成功了(拿到 batchId 却在轮询里永远 pending),那更难排查。
 */
@Configuration
public class ImportAsyncConfig {

    public static final String EXECUTOR_BEAN = "productImportExecutor";

    @Bean(EXECUTOR_BEAN)
    public ThreadPoolTaskExecutor productImportExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("product-import-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 不在停机时等待长任务跑完 —— 未完成的批次会停在 running,
        // 由 T-2.x 的补偿/告警覆盖,而不是拖住整个进程退出。
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }
}
