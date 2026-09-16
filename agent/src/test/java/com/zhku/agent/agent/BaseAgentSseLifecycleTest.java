package com.zhku.agent.agent;

import com.zhku.agent.agent.runtime.AgentRunContext;
import com.zhku.agent.agent.runtime.AgentRunState;
import com.zhku.agent.agent.runtime.AgentStopReason;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SSE 生命周期事件到 AgentRunContext 最终状态的行为测试。
 *
 * <p>可控 emitter 只替代网络事件来源，Agent 仍通过真实工作线程运行，停止请求
 * 仍沿 Future.cancel(true) 中断真实步骤。这样测试的是生命周期行为，而不是检查
 * 私有字段、源代码文本或 mock 调用次数。</p>
 */
class BaseAgentSseLifecycleTest {

    /** SSE 超时属于客户端通道取消，不应由回调线程直接清理。 */
    @Test
    void shouldCancelAfterSseTimeoutAndWorkerCleanup() throws Exception {
        try (SseTestHarness harness = SseTestHarness.blocking()) {
            AgentRunContext context = harness.start("timeout");

            harness.emitter().fireTimeout();

            assertCancelledLifecycle(
                    harness.agent(),
                    context,
                    AgentStopReason.SSE_TIMEOUT
            );
        }
    }

    /** 客户端断连通过 completion 回调请求停止，最终由工作线程取消。 */
    @Test
    void shouldCancelAfterClientDisconnectAndWorkerCleanup()
            throws Exception {
        try (SseTestHarness harness = SseTestHarness.blocking()) {
            AgentRunContext context = harness.start("disconnect");

            harness.emitter().fireCompletion();

            assertCancelledLifecycle(
                    harness.agent(),
                    context,
                    AgentStopReason.CLIENT_DISCONNECTED
            );
        }
    }

    /**
     * SSE 通道报告错误时必须标记 FAILED，而不是把传输故障伪装成普通取消。
     */
    @Test
    void shouldFailAfterEmitterErrorAndWorkerCleanup() throws Exception {
        try (SseTestHarness harness = SseTestHarness.blocking()) {
            AgentRunContext context = harness.start("emitter-error");

            harness.emitter().fireError(
                    new IOException("simulated connection error")
            );

            assertFailedLifecycle(harness.agent(), context);
        }
    }

    /** emitter.send 抛出 IOException 时必须进入 FAILED 并执行一次清理。 */
    @Test
    void shouldFailWhenSendingStepResultThrowsIOException()
            throws Exception {
        try (SseTestHarness harness = SseTestHarness.sendFailure()) {
            AgentRunContext context = harness.start("send-failure");

            harness.agent().releaseStep();

            assertFailedLifecycle(harness.agent(), context);
            assertThat(harness.emitter().completedError())
                    .isInstanceOf(IOException.class);
        }
    }

    /** 并发到达的后续回调不得覆盖最先被接受的停止原因。 */
    @Test
    void shouldPreserveFirstStopReasonAcrossRepeatedCallbacks()
            throws Exception {
        try (SseTestHarness harness = SseTestHarness.blocking()) {
            AgentRunContext context = harness.start("repeated-callbacks");

            harness.emitter().fireTimeout();
            harness.emitter().fireError(
                    new IOException("later error must not overwrite timeout")
            );
            harness.emitter().fireCompletion();

            assertCancelledLifecycle(
                    harness.agent(),
                    context,
                    AgentStopReason.SSE_TIMEOUT
            );
        }
    }

    /** 断言取消路径的状态、原因、中断和最终时序。 */
    private static void assertCancelledLifecycle(
            ControllableSseAgent agent,
            AgentRunContext context,
            AgentStopReason expectedReason
    ) throws Exception {
        assertThat(context.termination().get(
                2,
                TimeUnit.SECONDS
        )).isEqualTo(AgentRunState.CANCELLED);
        assertThat(context.stopReason()).isEqualTo(expectedReason);
        assertCommonFinalization(agent);
        assertThat(agent.workerInterrupted()).isTrue();
    }

    /** 断言传输失败路径的状态、原因和最终时序。 */
    private static void assertFailedLifecycle(
            ControllableSseAgent agent,
            AgentRunContext context
    ) throws Exception {
        assertThat(context.termination().get(
                2,
                TimeUnit.SECONDS
        )).isEqualTo(AgentRunState.FAILED);
        assertThat(context.stopReason())
                .isEqualTo(AgentStopReason.SEND_FAILURE);
        assertCommonFinalization(agent);
    }

    /** 所有 SSE 结束路径都必须遵守的 cleanup/termination 不变量。 */
    private static void assertCommonFinalization(
            ControllableSseAgent agent
    ) {
        assertThat(agent.cleanupCalls()).isEqualTo(1);
        assertThat(agent.cleanupObservedStepExit()).isTrue();
        assertThat(agent.terminationDoneDuringCleanup()).isFalse();
    }

    /** 管理每个测试独享的线程、Agent 和 emitter。 */
    private static final class SseTestHarness implements AutoCloseable {

        private final ExecutorService executor;

        private final ControllableSseEmitter emitter;

        private final ControllableSseAgent agent;

        private SseTestHarness(boolean failOnSend) {
            executor = Executors.newSingleThreadExecutor();
            emitter = new ControllableSseEmitter(failOnSend);
            agent = new ControllableSseAgent(executor, emitter);
        }

        private static SseTestHarness blocking() {
            return new SseTestHarness(false);
        }

        private static SseTestHarness sendFailure() {
            return new SseTestHarness(true);
        }

        /** 启动真实异步任务，并等到工作线程进入步骤后返回 context。 */
        private AgentRunContext start(String prompt) throws Exception {
            agent.runStream(prompt);
            assertThat(agent.awaitStepEntered()).isTrue();
            return agent.capturedContext();
        }

        private ControllableSseEmitter emitter() {
            return emitter;
        }

        private ControllableSseAgent agent() {
            return agent;
        }

        @Override
        public void close() throws Exception {
            // 任意断言失败也释放阻塞步骤，随后中断并回收测试线程。
            agent.releaseStep();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(2, TimeUnit.SECONDS))
                    .isTrue();
        }
    }

    /**
     * 保存 BaseAgent 注册的真实回调，让测试可以确定性触发 Servlet 生命周期事件。
     */
    private static final class ControllableSseEmitter extends SseEmitter {

        private final boolean failOnSend;

        private final AtomicReference<Runnable> timeoutCallback =
                new AtomicReference<>();

        private final AtomicReference<Consumer<Throwable>> errorCallback =
                new AtomicReference<>();

        private final AtomicReference<Runnable> completionCallback =
                new AtomicReference<>();

        private final AtomicReference<Throwable> completedError =
                new AtomicReference<>();

        private ControllableSseEmitter(boolean failOnSend) {
            super(300000L);
            this.failOnSend = failOnSend;
        }

        @Override
        public void onTimeout(Runnable callback) {
            timeoutCallback.set(callback);
        }

        @Override
        public void onError(Consumer<Throwable> callback) {
            errorCallback.set(callback);
        }

        @Override
        public void onCompletion(Runnable callback) {
            completionCallback.set(callback);
        }

        @Override
        public void send(Object object) throws IOException {
            if (failOnSend) {
                throw new IOException("simulated send failure");
            }
        }

        @Override
        public void completeWithError(Throwable error) {
            completedError.set(error);
        }

        private void fireTimeout() {
            requiredCallback(timeoutCallback.get(), "timeout").run();
        }

        private void fireError(Throwable error) {
            Consumer<Throwable> callback = errorCallback.get();
            if (callback == null) {
                throw new IllegalStateException(
                        "BaseAgent 未注册 error 回调"
                );
            }
            callback.accept(error);
        }

        private void fireCompletion() {
            requiredCallback(
                    completionCallback.get(),
                    "completion"
            ).run();
        }

        private Throwable completedError() {
            return completedError.get();
        }

        private static Runnable requiredCallback(
                Runnable callback,
                String name
        ) {
            if (callback == null) {
                throw new IllegalStateException(
                        "BaseAgent 未注册 " + name + " 回调"
                );
            }
            return callback;
        }
    }

    /** 能阻塞步骤并记录最终清理时序的测试 Agent。 */
    private static final class ControllableSseAgent extends BaseAgent {

        private final ControllableSseEmitter emitter;

        private final CountDownLatch stepEntered = new CountDownLatch(1);

        private final CountDownLatch stepRelease = new CountDownLatch(1);

        private final AtomicReference<AgentRunContext> context =
                new AtomicReference<>();

        private final AtomicInteger cleanupCalls = new AtomicInteger();

        private volatile boolean workerInterrupted;

        private volatile boolean stepExited;

        private volatile boolean cleanupObservedStepExit;

        private volatile boolean terminationDoneDuringCleanup;

        private ControllableSseAgent(
                ExecutorService executor,
                ControllableSseEmitter emitter
        ) {
            super(executor);
            this.emitter = emitter;
            setMaxSteps(1);
        }

        @Override
        protected SseEmitter createStreamEmitter() {
            return emitter;
        }

        @Override
        protected String step(AgentRunContext context) {
            this.context.set(context);
            stepEntered.countDown();

            try {
                if (!stepRelease.await(2, TimeUnit.SECONDS)) {
                    throw new IllegalStateException(
                            "测试步骤没有收到事件或释放信号"
                    );
                }
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
            cleanupObservedStepExit = stepExited;
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

        private void releaseStep() {
            stepRelease.countDown();
        }

        private boolean workerInterrupted() {
            return workerInterrupted;
        }

        private int cleanupCalls() {
            return cleanupCalls.get();
        }

        private boolean cleanupObservedStepExit() {
            return cleanupObservedStepExit;
        }

        private boolean terminationDoneDuringCleanup() {
            return terminationDoneDuringCleanup;
        }
    }
}
