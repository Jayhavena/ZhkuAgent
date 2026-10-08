package com.zhku.agent.agent;

import com.zhku.agent.agent.runtime.AgentRunContext;
import com.zhku.agent.agent.runtime.AgentRunState;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class BaseAgentCleanupLifecycleTest {
    @Test
    void blockedCleanupDoesNotPublishTermination() throws Exception {
        var entered = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var agent = new BaseAgent(Runnable::run) {
            AgentRunContext captured;

            protected String step(AgentRunContext context) {
                captured = context;
                context.finish(AgentRunState.SUCCEEDED);
                return "完成";
            }

            protected void cleanup(AgentRunContext context) {
                entered.countDown();
                try {
                    if (!release.await(5, java.util.concurrent.TimeUnit.SECONDS))
                        throw new IllegalStateException("等待清理超时");
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("中断");
                }
            }
        };
        try (var caller = java.util.concurrent.Executors.newSingleThreadExecutor()) {
            var future = caller.submit(() -> agent.run("测试清理阻塞"));
            try {
                assertThat(entered.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                assertThat(agent.captured.termination().isDone()).isFalse();
                assertThatThrownBy(agent.captured::signalTermination).isInstanceOf(IllegalStateException.class);
                assertThat(future.isDone()).isFalse();
            } finally {
                release.countDown();
            }
            assertThat(future.get(5, java.util.concurrent.TimeUnit.SECONDS)).contains("完成");
            assertThat(agent.captured.termination().join()).isEqualTo(AgentRunState.SUCCEEDED);
        } finally {
            release.countDown();
        }
    }

    @Test
    void cleanupExceptionMustNotLeaveSuccessfulUnterminatedRun() {
        var agent = new BaseAgent(Runnable::run) {
            AgentRunContext captured;

            protected String step(AgentRunContext context) {
                captured = context;
                context.finish(AgentRunState.SUCCEEDED);
                return "完成";
            }

            protected void cleanup(AgentRunContext context) {
                throw new IllegalStateException("cleanup failed");
            }
        };
        assertThatThrownBy(() -> agent.run("测试收尾")).isInstanceOf(IllegalStateException.class);
        assertThat(agent.captured.state()).isEqualTo(AgentRunState.FAILED);
        assertThat(agent.captured.termination().isDone()).isTrue();
    }
}
