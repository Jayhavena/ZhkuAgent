package com.yupi.yuaiagent.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * Agent 流式任务的专用执行器配置。
 *
 * <p>线程和队列都有明确上限。系统过载时选择显式拒绝，而不是让 HTTP 请求线程
 * 执行长任务或静默丢弃已经向客户端返回 SSE 的任务。</p>
 */
@Configuration
public class AgentExecutorConfig {

    /**
     * 创建由 Spring 管理生命周期的 Agent 专用执行器。
     *
     * <p>默认值只提供个人项目的初始资源边界，不代表经过生产压测的最优容量。
     * 所有参数都可以通过外部配置覆盖。</p>
     */
    @Bean(name = "agentRunExecutor")
    public ThreadPoolTaskExecutor agentRunExecutor(
            @Value("${agent.executor.core-pool-size:2}")
            int corePoolSize,
            @Value("${agent.executor.max-pool-size:4}")
            int maxPoolSize,
            @Value("${agent.executor.queue-capacity:16}")
            int queueCapacity,
            @Value("${agent.executor.await-termination-seconds:30}")
            int awaitTerminationSeconds
    ) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("agent-run-");

        // CallerRunsPolicy 会阻塞请求线程；丢弃策略则会破坏 RunContext 收尾。
        executor.setRejectedExecutionHandler(
                new ThreadPoolExecutor.AbortPolicy()
        );

        // 已接受的任务在关闭时获得有限时间完成最终清理。
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(awaitTerminationSeconds);
        return executor;
    }
}
