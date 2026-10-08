package com.zhku.agent.agent;

import com.zhku.agent.agent.runtime.AgentRunContext;
import com.zhku.agent.agent.runtime.AgentRunState;
import com.zhku.agent.agent.runtime.AgentStopReason;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 专用执行器上的 Agent 停止、终止和资源回收验证。
 *
 * <p>这里使用真实线程池重复执行协作式取消，验证 termination 只会在工作线程
 * 响应中断、退出步骤并完成 cleanup 后出现。采集到的时间是本机测试环境中的
 * 生命周期样本，不是接口响应时间、吞吐量或生产环境 SLA。</p>
 */
class BaseAgentExecutorTerminationTest {

    /** 重复轮次与并发任务数是不同指标：本测试始终按顺序一次运行一个任务。 */
    private static final int REPETITION_ROUNDS = 20;

    /**
     * 重复验证停止时序，并确认所有任务结束后线程池没有活动任务和排队任务。
     */
    @Test
    void shouldTerminateAfterWorkerExitAndCleanupWithoutResidualTasks()
            throws Exception {
        ThreadPoolTaskExecutor executor = createTestExecutor();
        List<Long> terminationLatenciesNanos = new ArrayList<>();
        List<InterruptibleStreamAgent> agents = new ArrayList<>();

        try {
            for (int round = 1; round <= REPETITION_ROUNDS; round++) {
                InterruptibleStreamAgent agent =
                        new InterruptibleStreamAgent(executor);
                agents.add(agent);

                agent.runStream("termination-round-" + round);
                assertThat(agent.awaitStepEntered())
                        .as("第 %s 轮工作线程必须进入阻塞步骤", round)
                        .isTrue();

                AgentRunContext context = agent.capturedContext();
                long stopRequestedAt = System.nanoTime();

                assertThat(context.requestStop(
                        AgentStopReason.USER_REQUEST
                )).isTrue();
                assertThat(context.termination().get(
                        2,
                        TimeUnit.SECONDS
                )).isEqualTo(AgentRunState.CANCELLED);

                terminationLatenciesNanos.add(
                        System.nanoTime() - stopRequestedAt
                );
                assertThat(agent.workerInterrupted())
                        .as("第 %s 轮工作线程必须观察到中断", round)
                        .isTrue();
                assertThat(agent.cleanupCalls())
                        .as("第 %s 轮 cleanup 必须且只能执行一次", round)
                        .isEqualTo(1);
                assertThat(agent.cleanupObservedWorkerExit())
                        .as("第 %s 轮 cleanup 前步骤必须已经退出", round)
                        .isTrue();
                assertThat(agent.terminationDoneDuringCleanup())
                        .as("第 %s 轮 cleanup 返回前不得发出 termination", round)
                        .isFalse();

                awaitExecutorIdle(executor);
            }

            assertThat(executor.getActiveCount()).isZero();
            assertThat(executor.getQueueSize()).isZero();

            // 所有任务真实退出后再关闭 Spring 管理的执行资源。
            executor.shutdown();
            assertThat(executor.getThreadPoolExecutor().awaitTermination(
                    2,
                    TimeUnit.SECONDS
            )).isTrue();

            long p95Nanos = percentile95(terminationLatenciesNanos);
            long maxNanos = Collections.max(terminationLatenciesNanos);
            System.out.printf(
                    "TERMINATION_METRICS rounds=%d concurrentTasks=1 "
                            + "p95Ms=%.3f maxMs=%.3f residualActive=%d "
                            + "residualQueued=%d%n",
                    REPETITION_ROUNDS,
                    nanosToMillis(p95Nanos),
                    nanosToMillis(maxNanos),
                    executor.getActiveCount(),
                    executor.getQueueSize()
            );
        } finally {
            // 断言失败时主动释放所有阻塞点，避免测试污染后续 Maven 进程。
            agents.forEach(InterruptibleStreamAgent::releaseStep);
            if (!executor.getThreadPoolExecutor().isShutdown()) {
                executor.shutdown();
            }
        }
    }

    /**
     * 验证执行器关闭时会等待已经接受的运行中和排队任务完成。
     *
     * <p>这是当前选择的优雅关闭策略，不代表执行器会强制中断业务。测试先确认
     * shutdown 正在等待，再依次释放两个任务，最后验证上下文和线程池全部终止。</p>
     */
    @Test
    void shouldDrainAcceptedTasksBeforeExecutorShutdownReturns()
            throws Exception {
        ThreadPoolTaskExecutor executor = createGracefulShutdownExecutor();
        InterruptibleStreamAgent runningAgent =
                new InterruptibleStreamAgent(executor);
        InterruptibleStreamAgent queuedAgent =
                new InterruptibleStreamAgent(executor);
        ExecutorService shutdownCaller =
                Executors.newSingleThreadExecutor();

        try {
            runningAgent.runStream("running-before-shutdown");
            assertThat(runningAgent.awaitStepEntered()).isTrue();

            queuedAgent.runStream("queued-before-shutdown");
            assertThat(executor.getQueueSize()).isEqualTo(1);

            Future<?> shutdownFuture = shutdownCaller.submit(
                    executor::shutdown
            );
            awaitExecutorShutdownStarted(executor);

            assertThat(shutdownFuture.isDone())
                    .as("运行中任务未释放时，优雅关闭不能提前返回")
                    .isFalse();
            assertThat(runningAgent.capturedContext()
                    .termination().isDone()).isFalse();

            runningAgent.releaseStep();
            assertThat(runningAgent.capturedContext().termination().get(
                    2,
                    TimeUnit.SECONDS
            )).isEqualTo(AgentRunState.SUCCEEDED);

            assertThat(queuedAgent.awaitStepEntered())
                    .as("关闭开始前已接受的排队任务必须得到执行")
                    .isTrue();
            queuedAgent.releaseStep();
            assertThat(queuedAgent.capturedContext().termination().get(
                    2,
                    TimeUnit.SECONDS
            )).isEqualTo(AgentRunState.SUCCEEDED);

            shutdownFuture.get(2, TimeUnit.SECONDS);
            assertThat(executor.getThreadPoolExecutor().isTerminated())
                    .isTrue();
            assertThat(executor.getActiveCount()).isZero();
            assertThat(executor.getQueueSize()).isZero();
            assertThat(runningAgent.cleanupCalls()).isEqualTo(1);
            assertThat(queuedAgent.cleanupCalls()).isEqualTo(1);
        } finally {
            runningAgent.releaseStep();
            queuedAgent.releaseStep();
            if (!executor.getThreadPoolExecutor().isShutdown()) {
                executor.shutdown();
            }
            shutdownCaller.shutdownNow();
            assertThat(shutdownCaller.awaitTermination(
                    2,
                    TimeUnit.SECONDS
            )).isTrue();
        }
    }

    /** 创建与生产策略一致、容量更小的专用测试执行器。 */
    private static ThreadPoolTaskExecutor createTestExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(4);
        executor.setThreadNamePrefix("agent-termination-test-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(2);
        executor.initialize();
        return executor;
    }

    /** 创建“1 个工作线程 + 1 个排队位置”的优雅关闭测试执行器。 */
    private static ThreadPoolTaskExecutor createGracefulShutdownExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(1);
        executor.setThreadNamePrefix("agent-shutdown-test-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(2);
        executor.initialize();
        return executor;
    }

    /** 不使用固定 sleep，等待底层线程池确实进入 shutdown 状态。 */
    private static void awaitExecutorShutdownStarted(
            ThreadPoolTaskExecutor executor
    ) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);

        while (!executor.getThreadPoolExecutor().isShutdown()
                && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }

        assertThat(executor.getThreadPoolExecutor().isShutdown())
                .as("关闭线程必须调用到底层线程池 shutdown")
                .isTrue();
    }

    /**
     * 等待 termination 之后的 FutureTask 包装器退出工作线程。
     *
     * <p>termination 在 cleanup 后完成，但工作线程还需要从 finally 返回；因此
     * 线程池的 activeCount 可能存在极短观察窗口。这里等待真实空闲状态，而不是
     * 用固定 sleep 猜测调度速度。</p>
     */
    private static void awaitExecutorIdle(ThreadPoolTaskExecutor executor) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);

        while ((executor.getActiveCount() != 0
                || executor.getQueueSize() != 0)
                && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }

        assertThat(executor.getActiveCount()).isZero();
        assertThat(executor.getQueueSize()).isZero();
    }

    /** 采用 nearest-rank 定义计算 95 分位数。 */
    private static long percentile95(List<Long> samples) {
        List<Long> ordered = new ArrayList<>(samples);
        Collections.sort(ordered);
        int index = (int) Math.ceil(ordered.size() * 0.95D) - 1;
        return ordered.get(index);
    }

    /** 把纳秒样本转换成便于阅读的毫秒值。 */
    private static double nanosToMillis(long nanos) {
        return nanos / 1_000_000.0D;
    }

    /** 能观察工作线程退出与 cleanup 时序的测试 Agent。 */
    private static final class InterruptibleStreamAgent extends BaseAgent {

        private final CountDownLatch stepEntered = new CountDownLatch(1);

        private final CountDownLatch stepRelease = new CountDownLatch(1);

        private final AtomicReference<AgentRunContext> context =
                new AtomicReference<>();

        private final AtomicInteger cleanupCalls = new AtomicInteger();

        private volatile boolean workerInterrupted;

        private volatile boolean stepExited;

        private volatile boolean cleanupObservedWorkerExit;

        private volatile boolean terminationDoneDuringCleanup;

        private InterruptibleStreamAgent(
                ThreadPoolTaskExecutor executor
        ) {
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
                            "测试步骤没有收到停止中断"
                    );
                }
                context.finish(AgentRunState.SUCCEEDED);
                return "released";
            } catch (InterruptedException exception) {
                workerInterrupted = true;
                Thread.currentThread().interrupt();
                return "interrupted";
            } finally {
                stepExited = true;
            }
        }

        @Override
        protected void cleanup(AgentRunContext context) {
            cleanupCalls.incrementAndGet();
            cleanupObservedWorkerExit = stepExited;
            terminationDoneDuringCleanup =
                    context.termination().isDone();
        }

        private boolean awaitStepEntered() throws InterruptedException {
            return stepEntered.await(2, TimeUnit.SECONDS);
        }

        private AgentRunContext capturedContext() {
            AgentRunContext captured = context.get();
            if (captured == null) {
                throw new IllegalStateException(
                        "工作线程尚未捕获运行上下文"
                );
            }
            return captured;
        }

        private boolean workerInterrupted() {
            return workerInterrupted;
        }

        private int cleanupCalls() {
            return cleanupCalls.get();
        }

        private boolean cleanupObservedWorkerExit() {
            return cleanupObservedWorkerExit;
        }

        private boolean terminationDoneDuringCleanup() {
            return terminationDoneDuringCleanup;
        }

        private void releaseStep() {
            stepRelease.countDown();
        }
    }
}
