package com.yupi.yuaiagent.config;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Agent 专用执行器的 Spring 配置测试。
 *
 * <p>测试只启动 config 包的轻量上下文，不加载模型、数据库或 Web 服务器。
 * 它验证项目声明的资源边界，而不是重复测试线程池框架内部实现。</p>
 */
class AgentExecutorConfigTest {

    /**
     * 验证容器提供命名明确、容量有限并快速拒绝的 Agent 执行器。
     */
    @Test
    void shouldExposeDedicatedBoundedAgentExecutor() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext()) {
            context.scan("com.yupi.yuaiagent.config");
            context.refresh();

            assertThat(context.containsBean("agentRunExecutor"))
                    .as("Spring 容器必须拥有 Agent 专用执行器")
                    .isTrue();

            ThreadPoolTaskExecutor executor = context.getBean(
                    "agentRunExecutor",
                    ThreadPoolTaskExecutor.class
            );

            assertThat(executor.getCorePoolSize()).isEqualTo(2);
            assertThat(executor.getMaxPoolSize()).isEqualTo(4);
            assertThat(executor.getThreadNamePrefix())
                    .isEqualTo("agent-run-");
            assertThat(executor.getThreadPoolExecutor()
                    .getQueue()
                    .remainingCapacity())
                    .isEqualTo(16);
            assertThat(executor.getThreadPoolExecutor()
                    .getRejectedExecutionHandler())
                    .isInstanceOf(ThreadPoolExecutor.AbortPolicy.class);
        }
    }
}
