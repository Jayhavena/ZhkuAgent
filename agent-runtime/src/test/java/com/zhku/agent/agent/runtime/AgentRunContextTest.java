package com.zhku.agent.agent.runtime;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AgentRunContext 单元测试。
 *
 * <p>这些测试只验证进程内运行上下文的行为，不启动 Spring 容器，
 * 不调用真实大模型，也不执行任何外部工具。</p>
 */
class AgentRunContextTest {
    @Test void suppliedRunIdIsPreservedAndLegacyCreationRemainsRandom() {
        var id = java.util.UUID.randomUUID();
        assertThat(AgentRunContext.create(id,"合成任务").runId()).isEqualTo(id);
        assertThat(AgentRunContext.create("合成任务").runId()).isNotEqualTo(AgentRunContext.create("合成任务").runId());
        try (var owner=AgentRunObservationDispatcher.createOwned()) {
            var context=AgentRunContext.create(id,"合成任务", e->{},owner);
            assertThat(context.runId()).isEqualTo(id);
            context.start(); context.finish(AgentRunState.INCOMPLETE);
            context.cleanupOnce(()->{});context.signalTermination();context.publishTermination();
        }
    }
    @Test
    void completionFirstRejectsLateStopAndTerminalRewrites() {
        var context = AgentRunContext.create("完成先提交");
        context.start();
        assertThat(context.finish(AgentRunState.SUCCEEDED)).isTrue();
        assertThat(context.requestStop(AgentStopReason.USER_REQUEST)).isFalse();
        context.cleanupOnce(() -> {
        });
        context.signalTermination();
        assertThat(context.finish(AgentRunState.FAILED)).isFalse();
        assertThat(context.stopReason()).isEqualTo(AgentStopReason.NONE);
    }

    @Test
    void incompleteIsTerminalButCreatedCannotFinishNormally() {
        var context = AgentRunContext.create("未启动");
        assertThatThrownBy(() -> context.finish(AgentRunState.INCOMPLETE)).isInstanceOf(IllegalStateException.class);
        context.start();
        context.requestStop(AgentStopReason.RUN_TIMEOUT);
        assertThat(context.finish(AgentRunState.INCOMPLETE)).isFalse();
        context.finish(AgentRunState.CANCELLED);
        context.cleanupOnce(() -> {
        });
        context.signalTermination();
        assertThat(context.stopReason()).isEqualTo(AgentStopReason.RUN_TIMEOUT);
    }

    @Test
    void cleanupMustFinishBeforeTerminationCanBePublished() {
        var context = AgentRunContext.create("cleanup");
        context.start();
        context.finish(AgentRunState.SUCCEEDED);
        context.cleanupOnce(() -> assertThatThrownBy(context::signalTermination)
                .isInstanceOf(IllegalStateException.class));
        assertThat(context.signalTermination()).isTrue();
    }

    @Test
    void cleanupFailureSettlesAsFailedAndStillAllowsTermination() {
        var context = AgentRunContext.create("cleanup failure");
        context.start();
        context.finish(AgentRunState.SUCCEEDED);
        assertThatThrownBy(() -> context.cleanupOnce(() -> {
            throw new IllegalStateException("test cleanup failure");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(context.state()).isEqualTo(AgentRunState.FAILED);
        assertThat(context.signalTermination()).isTrue();
        assertThat(context.termination().join()).isEqualTo(AgentRunState.FAILED);
        assertThat(context.cleanupOnce(() -> {
            throw new AssertionError("重复清理");
        })).isFalse();
    }

    @Test
    void stopWinsAgainstLateSuccessWithoutIllegalTransition() {
        var context = AgentRunContext.create("race");
        context.start();
        context.requestStop(AgentStopReason.USER_REQUEST);
        assertThat(context.finish(AgentRunState.SUCCEEDED)).isFalse();
        assertThat(context.finish(AgentRunState.CANCELLED)).isTrue();
        assertThat(context.stopReason()).isEqualTo(AgentStopReason.USER_REQUEST);
    }

    /**
     * 验证正常运行的完整生命周期：
     *
     * <pre>
     * CREATED -> RUNNING -> SUCCEEDED
     *          -> cleanup -> termination
     * </pre>
     *
     * <p>关键点：进入 SUCCEEDED 只表示业务执行已经得到最终结果，
     * cleanup 和 termination 仍然是两个后续动作。</p>
     *
     * termination 是什么意思
     * <p>termination 信号表示整个运行上下文的生命周期已经结束，
     * 包括业务执行、资源清理和所有后续动作。</p>
     */
    @Test
    void shouldCompleteNormalLifecycleInOrder() {
        AgentRunContext context = AgentRunContext.create("你好");

        assertThat(context.state()).isEqualTo(AgentRunState.CREATED);
        assertThat(context.currentStep()).isZero();

        assertThat(context.start()).isTrue();
        assertThat(context.state()).isEqualTo(AgentRunState.RUNNING);

        assertThat(context.advanceStep()).isEqualTo(1);
        assertThat(context.advanceStep()).isEqualTo(2);

        assertThat(context.finish(AgentRunState.SUCCEEDED)).isTrue();
        assertThat(context.state()).isEqualTo(AgentRunState.SUCCEEDED);

        // 业务状态已经成功，但清理尚未执行，因此不能宣称任务彻底终止。
        assertThat(context.termination().isDone()).isFalse();

        assertThat(context.cleanupOnce(() -> {
            // 当前测试不持有真实资源，因此使用空清理动作。
        })).isTrue();

        assertThat(context.signalTermination()).isTrue();
        assertThat(context.termination().join())
                .isEqualTo(AgentRunState.SUCCEEDED);
    }

    /**
     * 验证“请求停止”和“已经终止”不是一回事。
     *
     * <p>SSE 超时只能提出停止请求。在工作线程退出、执行清理并发送
     * termination 信号以前，不能认为运行已经结束。</p>
     *
     * SSE 超时是什么意思
     * <p>SSE 超时是指在使用 Server-Sent Events (SSE) 进行实时数据传输时，客户端在一定时间内没有接收到服务器的响应，从而触发的超时事件。
     * 在这种情况下，客户端可能会请求停止当前的操作，但这并不意味着操作已经完全终止，仍需要等待工作线程完成清理和发送终止信号。</p>
     */
    @Test
    void shouldNotTreatStopRequestAsConfirmedTermination() {
        AgentRunContext context = AgentRunContext.create("执行一个长任务");
        context.start();

        assertThat(context.requestStop(AgentStopReason.SSE_TIMEOUT)).isTrue();
        assertThat(context.state()).isEqualTo(AgentRunState.STOP_REQUESTED);
        assertThat(context.stopReason()).isEqualTo(AgentStopReason.SSE_TIMEOUT);
        assertThat(context.termination().isDone()).isFalse();

        // 第一个停止原因必须保留，后到达的回调不能覆盖它。
        assertThat(context.requestStop(AgentStopReason.SEND_FAILURE)).isFalse();
        assertThat(context.stopReason()).isEqualTo(AgentStopReason.SSE_TIMEOUT);

        // 停止请求到达后，不应再开始新的业务步骤。
        assertThatThrownBy(context::advanceStep)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RUNNING");

        assertThat(context.finish(AgentRunState.CANCELLED)).isTrue();
        assertThat(context.cleanupOnce(() -> {
            // 模拟取消后的资源释放。
        })).isTrue();
        assertThat(context.signalTermination()).isTrue();

        assertThat(context.termination().join())
                .isEqualTo(AgentRunState.CANCELLED);
    }

    /**
     * 验证 cleanup 的幂等性。
     *
     * <p>即使 finally、onTimeout、onCompletion 等入口重复请求清理，
     * 真正的清理动作也只能执行一次。</p>
     */
    @Test
    void shouldExecuteCleanupOnlyOnce() {
        AgentRunContext context = AgentRunContext.create("测试重复清理");
        AtomicInteger cleanupCount = new AtomicInteger();

        assertThat(context.cleanupOnce(cleanupCount::incrementAndGet)).isTrue();
        assertThat(context.cleanupOnce(cleanupCount::incrementAndGet)).isFalse();
        assertThat(context.cleanupOnce(cleanupCount::incrementAndGet)).isFalse();

        assertThat(cleanupCount).hasValue(1);
    }

    /**
     * 验证两次运行拥有完全独立的消息列表。
     *
     * <p>同时验证 messageSnapshot 返回不可修改快照，
     * 防止调用者绕过 AgentRunContext 修改内部数据。</p>
     */
    @Test
    void shouldIsolateMessagesBetweenRunsAndExposeImmutableSnapshot() {
        AgentRunContext firstRun = AgentRunContext.create("用户 A 的问题");
        AgentRunContext secondRun = AgentRunContext.create("用户 B 的问题");

        firstRun.start();
        secondRun.start();

        firstRun.appendMessage(new AssistantMessage("只属于用户 A 的回答"));

        assertThat(firstRun.runId()).isNotEqualTo(secondRun.runId());

        assertThat(firstRun.messageSnapshot())
                .extracting(Message::getText)
                .containsExactly("用户 A 的问题", "只属于用户 A 的回答");

        assertThat(secondRun.messageSnapshot())
                .extracting(Message::getText)
                .containsExactly("用户 B 的问题");

        List<Message> snapshot = firstRun.messageSnapshot();

        assertThatThrownBy(
                () -> snapshot.add(new UserMessage("非法外部修改"))).isInstanceOf(UnsupportedOperationException.class);

        // 测试替换消息时，AgentRunContext 会复制输入集合，
        // 而不是保存调用者提供的可变 List 引用。
        firstRun.replaceMessages(List.of(
                new UserMessage("重建后的用户消息"),
                new AssistantMessage("重建后的助手消息")));

        assertThat(firstRun.messageSnapshot())
                .extracting(Message::getText)
                .containsExactly("重建后的用户消息", "重建后的助手消息");
    }

    /**
     * 验证停止请求发生在工作线程进入任务体之前时，
     * 不会提前取消 Future。
     *
     * <p>提前取消 FutureTask 可能导致任务体根本不执行，
     * 从而使 finally 和终止通知也没有机会运行。</p>
     */
    @Test
    void shouldKeepTaskSchedulableWhenStoppedBeforeWorkerEntry() {
        AgentRunContext context = AgentRunContext.create("排队中的任务");
        RecordingFuture future = new RecordingFuture();

        context.start();
        context.bindTask(future);

        assertThat(context.requestStop(AgentStopReason.USER_REQUEST)).isTrue();

        // 工作线程尚未进入任务体，因此这里只记录停止请求，不提前取消。
        assertThat(future.isCancelled()).isFalse();

        // 后续执行器仍然允许包装任务启动。
        // 包装任务启动后会发现 STOP_REQUESTED，并跳过真实业务逻辑。
        assertThat(context.markWorkerStarted()).isTrue();
        assertThat(context.isStopRequested()).isTrue();
    }

    /**
     * 验证工作线程已经进入任务体后，停止请求会尝试中断任务。
     *
     * <p>注意：cancel(true) 仍然只是最佳努力的中断请求，
     * 不能证明外部模型或工具已经停止。</p>
     */
    @Test
    void shouldRequestInterruptionAfterWorkerHasStarted() {
        AgentRunContext context = AgentRunContext.create("运行中的任务");
        RecordingFuture future = new RecordingFuture();

        context.start();
        context.bindTask(future);
        assertThat(context.markWorkerStarted()).isTrue();

        assertThat(context.requestStop(AgentStopReason.USER_REQUEST)).isTrue();

        assertThat(future.isCancelled()).isTrue();
        assertThat(future.isInterruptRequested()).isTrue();

        // Future 已被标记取消，也不能代替 RunContext 的真实终止信号。
        assertThat(context.termination().isDone()).isFalse();
    }

    /**
     * 测试专用 Future。
     *
     * <p>它不运行真实线程，只记录 AgentRunContext 是否调用了
     * cancel(true)。这样测试的是上下文的真实取消决策，而不是线程调度速度。</p>
     */
    private static final class RecordingFuture implements Future<Object> {

        private volatile boolean cancelled;
        private volatile boolean interruptRequested;

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            this.cancelled = true;
            this.interruptRequested = mayInterruptIfRunning;
            return true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        /**
         * 本测试替身把“已取消”视为 Future 自身已经结束。
         *
         * <p>这并不代表 AgentRunContext 对应的工作线程已经退出，
         * 因此测试仍然单独检查 termination 信号。</p>
         */
        @Override
        public boolean isDone() {
            return cancelled;
        }

        @Override
        public Object get() {
            throw new UnsupportedOperationException("该测试不读取 Future 结果");
        }

        @Override
        public Object get(long timeout, TimeUnit unit) {
            throw new UnsupportedOperationException("该测试不读取 Future 结果");
        }

        boolean isInterruptRequested() {
            return interruptRequested;
        }
    }
}
