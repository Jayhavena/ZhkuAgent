package com.zhku.agent.agent;

import com.zhku.agent.agent.runtime.AgentRunContext;
import com.zhku.agent.agent.runtime.AgentRunState;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Agent 专用执行器达到容量上限时的生命周期测试。
 *
 * <p>测试使用真实线程池，确定性构造一个运行任务、一个排队任务和一个被拒绝
 * 任务。任务数量只用于验证饱和分支，不代表系统并发能力。</p>
 */
class BaseAgentExecutorRejectionTest {

    /**
     * 验证执行器拒绝任务时，提交线程完成失败状态和最终清理。
     */
    @Test
    void shouldFinalizeRejectedRunAsFailedAfterCleanup()
            throws Exception {
        ThreadPoolTaskExecutor executor = createSaturatedTestExecutor();
        BlockingStreamAgent runningAgent =
                new BlockingStreamAgent(executor);
        BlockingStreamAgent queuedAgent =
                new BlockingStreamAgent(executor);
        RejectedStreamAgent rejectedAgent =
                new RejectedStreamAgent(executor);

        try {
            runningAgent.runStream("running-task");
            assertThat(runningAgent.awaitStepEntered())
                    .as("第一个任务必须先占用唯一工作线程")
                    .isTrue();

            queuedAgent.runStream("queued-task");
            assertThat(executor.getQueueSize())
                    .as("第二个任务必须占用唯一队列位置")
                    .isEqualTo(1);

            assertThatThrownBy(
                    () -> rejectedAgent.runStream("rejected-task")
            )
                    .as("线程和队列均满时第三个任务必须被明确拒绝")
                    .isInstanceOf(RejectedExecutionException.class);

            AgentRunContext rejectedContext =
                    rejectedAgent.cleanupContext();
            assertThat(rejectedContext.state())
                    .isEqualTo(AgentRunState.FAILED);
            assertThat(rejectedAgent.cleanupCalls()).isEqualTo(1);
            assertThat(rejectedAgent.terminationDoneDuringCleanup())
                    .as("termination 不得早于 cleanup 返回")
                    .isFalse();
            assertThat(rejectedAgent.cleanupReturned()).isTrue();
            assertThat(rejectedContext.termination().get(
                    1,
                    TimeUnit.SECONDS
            )).isEqualTo(AgentRunState.FAILED);

            // 释放前两个任务并确认测试没有给线程池留下活动任务。
            runningAgent.releaseStep();
            assertThat(runningAgent.termination().get(
                    2,
                    TimeUnit.SECONDS
            )).isEqualTo(AgentRunState.SUCCEEDED);

            assertThat(queuedAgent.awaitStepEntered())
                    .as("第一个任务退出后，排队任务必须获得工作线程")
                    .isTrue();
            queuedAgent.releaseStep();
            assertThat(queuedAgent.termination().get(
                    2,
                    TimeUnit.SECONDS
            )).isEqualTo(AgentRunState.SUCCEEDED);
        } finally {
            // 任意断言失败时仍释放阻塞点，防止测试线程残留。
            runningAgent.releaseStep();
            queuedAgent.releaseStep();
            executor.shutdown();
        }
    }

    /** 创建容量为“1 工作 + 1 排队”的真实测试执行器。 */
    private static ThreadPoolTaskExecutor createSaturatedTestExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(1);
        executor.setThreadNamePrefix("agent-rejection-test-");
        executor.setRejectedExecutionHandler(
                new ThreadPoolExecutor.AbortPolicy()
        );
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(2);
        executor.initialize();
        return executor;
    }

    /** 能在真实工作线程中阻塞和释放的测试 Agent。 */
    private static final class BlockingStreamAgent extends BaseAgent {

        private final CountDownLatch stepEntered = new CountDownLatch(1);

        private final CountDownLatch stepRelease = new CountDownLatch(1);

        private final AtomicReference<AgentRunContext> context =
                new AtomicReference<>();

        private BlockingStreamAgent(ThreadPoolTaskExecutor executor) {
            super(executor);
            setMaxSteps(1);
        }

        @Override
        protected String step(AgentRunContext context) {
            this.context.set(context);
            stepEntered.countDown();

            try {
                if (!stepRelease.await(2, TimeUnit.SECONDS)) {
                    throw new IllegalStateException(
                            "等待测试释放步骤超时"
                    );
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(
                        "阻塞步骤收到非预期中断",
                        exception
                );
            }

            return "released";
        }

        private boolean awaitStepEntered() throws InterruptedException {
            return stepEntered.await(2, TimeUnit.SECONDS);
        }

        private void releaseStep() {
            stepRelease.countDown();
        }

        private java.util.concurrent.CompletableFuture<AgentRunState>
        termination() {
            AgentRunContext captured = context.get();
            if (captured == null) {
                throw new IllegalStateException(
                        "工作任务尚未捕获运行上下文"
                );
            }
            return captured.termination();
        }
    }

    /** 记录提交失败路径中真实 cleanup 时序的测试 Agent。 */
    private static final class RejectedStreamAgent extends BaseAgent {

        private final AtomicReference<AgentRunContext> cleanupContext =
                new AtomicReference<>();

        private final AtomicInteger cleanupCalls = new AtomicInteger();

        private volatile boolean terminationDoneDuringCleanup;

        private volatile boolean cleanupReturned;

        private RejectedStreamAgent(ThreadPoolTaskExecutor executor) {
            super(executor);
            setMaxSteps(1);
        }

        @Override
        protected String step(AgentRunContext context) {
            throw new IllegalStateException(
                    "被拒绝的任务不应进入工作步骤"
            );
        }

        @Override
        protected void cleanup(AgentRunContext context) {
            cleanupContext.set(context);
            cleanupCalls.incrementAndGet();
            terminationDoneDuringCleanup =
                    context.termination().isDone();
            cleanupReturned = true;
        }

        private AgentRunContext cleanupContext() {
            AgentRunContext captured = cleanupContext.get();
            if (captured == null) {
                throw new IllegalStateException(
                        "提交失败路径没有执行 cleanup"
                );
            }
            return captured;
        }

        private int cleanupCalls() {
            return cleanupCalls.get();
        }

        private boolean terminationDoneDuringCleanup() {
            return terminationDoneDuringCleanup;
        }

        private boolean cleanupReturned() {
            return cleanupReturned;
        }
    }
}
