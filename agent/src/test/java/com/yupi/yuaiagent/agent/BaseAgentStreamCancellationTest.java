package com.yupi.yuaiagent.agent;

import com.yupi.yuaiagent.agent.runtime.AgentRunContext;
import com.yupi.yuaiagent.agent.runtime.AgentRunState;
import com.yupi.yuaiagent.agent.runtime.AgentStopReason;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BaseAgent 流式任务停止行为测试。
 *
 * <p>测试只使用内存中的可控 Agent，不连接真实模型、工具、网络或数据库。
 * 它通过 CountDownLatch 精确制造“工作线程正在 step 中运行”的时序。</p>
 */
class BaseAgentStreamCancellationTest {

    /**
     * 验证停止请求能够中断已经运行的流式工作线程。
     *
     * <p>该测试在旧实现上应当失败，因为 runStream 使用公共异步池时
     * 没有把任务句柄绑定到 AgentRunContext，也没有标记工作线程已启动。</p>
     */
    @Test
    void shouldInterruptRunningStreamWorkerAfterStopRequest()
            throws Exception {
        InterruptibleStreamAgent agent =
                new InterruptibleStreamAgent();

        try {
            // 启动真实的 BaseAgent.runStream 异步路径。
            // 返回的 SseEmitter 不是本用例的断言对象；本用例关注后台线程。
            agent.runStream("interrupt-running-stream");

            // 必须先确认工作线程已经进入 step，才能排除“任务尚未启动”的情况。
            assertThat(agent.awaitStepEntered())
                    .as("流式工作线程应当在时限内进入 step")
                    .isTrue();

            AgentRunContext context = agent.capturedContext();

            // 模拟 SSE timeout、断连或用户取消最终发出的停止请求。
            assertThat(
                    context.requestStop(AgentStopReason.USER_REQUEST)
            )
                    .as("第一个停止请求应当被接受")
                    .isTrue();

            // 这是本步骤的核心回归断言。
            // 若删除任务句柄绑定或 workerStarted 标记，这里会重新超时并返回 false。
            assertThat(agent.awaitWorkerInterrupted())
                    .as("停止请求必须中断正在运行的流式工作线程")
                    .isTrue();

            // 只有工作线程退出并完成 cleanup 后，termination 才能完成。
            assertThat(context.termination().get(2, TimeUnit.SECONDS))
                    .isEqualTo(AgentRunState.CANCELLED);

            // 即使有多个停止相关入口，当前运行的实际清理也只能执行一次。
            assertThat(agent.cleanupCalls()).isEqualTo(1);
        } finally {
            // RED 断言失败时仍释放阻塞点，避免旧实现的后台线程遗留在测试进程中。
            agent.releaseStep();
        }
    }

    /**
     * 能在 step 中受控阻塞，并记录线程是否收到中断的测试 Agent。
     */
    private static final class InterruptibleStreamAgent
            extends BaseAgent {

        /** 工作线程进入 step 后计数归零。 */
        private final CountDownLatch stepEntered =
                new CountDownLatch(1);

        /** 工作线程捕获 InterruptedException 后计数归零。 */
        private final CountDownLatch workerInterrupted =
                new CountDownLatch(1);

        /** RED 失败时，由测试线程主动释放旧实现中的阻塞步骤。 */
        private final CountDownLatch stepRelease =
                new CountDownLatch(1);

        /** 保存 BaseAgent 传入的真实运行上下文。 */
        private final AtomicReference<AgentRunContext> capturedContext =
                new AtomicReference<>();

        /** 记录当前运行实际执行 cleanup 的次数。 */
        private final AtomicInteger cleanupCalls =
                new AtomicInteger();

        private InterruptibleStreamAgent() {
            super(ForkJoinPool.commonPool());
            // 一个步骤足以观察任务取消与最终清理。
            setMaxSteps(1);
        }

        /**
         * 捕获本次 context，并阻塞到收到中断或测试主动释放。
         */
        @Override
        protected String step(AgentRunContext context) {
            // 每个测试实例只运行一次，因此第一次写入必须成功。
            if (!capturedContext.compareAndSet(null, context)) {
                throw new IllegalStateException(
                        "测试 Agent 不应捕获多个运行上下文"
                );
            }

            stepEntered.countDown();

            try {
                // 5 秒只是失败保护上限，不是测试期待的正常等待时间。
                // 正确实现应通过 cancel(true) 很快中断这里。
                if (!stepRelease.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException(
                            "测试步骤没有被中断，也没有被主动释放"
                    );
                }

                return "released-without-interrupt";
            } catch (InterruptedException exception) {
                // 记录真实工作线程确实观察到了 Future.cancel(true) 的中断。
                workerInterrupted.countDown();

                // 恢复中断标记，避免测试替身吞掉线程的取消语义。
                Thread.currentThread().interrupt();

                return "interrupted";
            }
        }

        /**
         * 记录 BaseAgent 最终清理的实际调用次数。
         */
        @Override
        protected void cleanup(AgentRunContext context) {
            cleanupCalls.incrementAndGet();
        }

        /** 等待工作线程进入 step。 */
        private boolean awaitStepEntered() throws InterruptedException {
            return stepEntered.await(2, TimeUnit.SECONDS);
        }

        /** 等待工作线程观察到中断。 */
        private boolean awaitWorkerInterrupted()
                throws InterruptedException {
            return workerInterrupted.await(1, TimeUnit.SECONDS);
        }

        /**
         * 返回 step 捕获的真实运行上下文。
         */
        private AgentRunContext capturedContext() {
            AgentRunContext context = capturedContext.get();

            if (context == null) {
                throw new IllegalStateException(
                        "流式工作线程尚未捕获 AgentRunContext"
                );
            }

            return context;
        }

        /** RED 失败后释放工作线程，避免测试线程泄漏。 */
        private void releaseStep() {
            stepRelease.countDown();
        }

        /** 返回实际 cleanup 次数。 */
        private int cleanupCalls() {
            return cleanupCalls.get();
        }
    }
}
