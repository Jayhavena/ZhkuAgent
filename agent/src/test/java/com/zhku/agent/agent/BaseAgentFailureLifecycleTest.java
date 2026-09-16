package com.zhku.agent.agent;

import com.zhku.agent.agent.model.AgentStepDecision;
import com.zhku.agent.agent.runtime.AgentRunContext;
import com.zhku.agent.agent.runtime.AgentRunState;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * BaseAgent 同步失败路径的生命周期测试。
 *
 * <p>测试使用内存中的确定性 Agent 主动制造 think 或 act 异常，不连接
 * 真实大模型、工具、网络或数据库。</p>
 */
class BaseAgentFailureLifecycleTest {

    /**
     * 验证 think 抛出异常后，运行进入 FAILED 并完成一次清理和终止通知。
     */
    @Test
    void shouldFinalizeFailedRunWhenThinkThrows() {
        FailureRecordingAgent agent = new FailureRecordingAgent(
                FailurePoint.THINK
        );

        // 原始异常必须继续传播给调用者，不能被转换成普通结果字符串。
        assertThatThrownBy(() -> agent.run("fail-in-think"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("think failure");

        assertFailedLifecycle(agent);
    }

    /**
     * 验证 act 抛出异常后，运行进入 FAILED 并完成一次清理和终止通知。
     */
    @Test
    void shouldFinalizeFailedRunWhenActThrows() {
        FailureRecordingAgent agent = new FailureRecordingAgent(
                FailurePoint.ACT
        );

        // think 会正常生成 decision，异常只在 act 中发生。
        assertThatThrownBy(() -> agent.run("fail-in-act"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("act failure");

        assertFailedLifecycle(agent);
    }

    /**
     * 对两种异常路径应用相同的生命周期断言。
     */
    private static void assertFailedLifecycle(
            FailureRecordingAgent agent
    ) {
        AgentRunContext context = agent.capturedContext();

        // 异常发生后，本次运行必须形成明确的最终失败状态。
        assertThat(context.state()).isEqualTo(AgentRunState.FAILED);

        // BaseAgent 的 finally 只能让当前运行的清理动作执行一次。
        assertThat(agent.cleanupCalls()).isEqualTo(1);

        // cleanup 开始前，BaseAgent 必须已经把状态确定为 FAILED。
        assertThat(agent.stateObservedDuringCleanup())
                .isEqualTo(AgentRunState.FAILED);

        // cleanup 尚未返回时，不允许提前宣布整个运行已经终止。
        assertThat(agent.terminationDoneDuringCleanup()).isFalse();

        // run 已退出，说明 cleanup 已返回；此时 termination 必须完成。
        assertThat(agent.cleanupReturned()).isTrue();
        assertThat(context.termination().isDone()).isTrue();
        assertThat(context.termination().join())
                .isEqualTo(AgentRunState.FAILED);
    }

    /**
     * 指定测试 Agent 应在哪个 ReAct 阶段制造异常。
     */
    private enum FailurePoint {
        /** 在 think 阶段失败。 */
        THINK,

        /** 在 act 阶段失败。 */
        ACT
    }

    /**
     * 能在指定阶段抛出异常，并记录清理过程的测试 Agent。
     */
    private static final class FailureRecordingAgent
            extends ReActAgent<TestDecision> {

        /** 本用例指定的失败位置。 */
        private final FailurePoint failurePoint;

        /** think 阶段观察到的本次运行 context。 */
        private AgentRunContext context;

        /** cleanup 的实际调用次数。 */
        private int cleanupCalls;

        /** cleanup 执行时观察到的最终状态。 */
        private AgentRunState stateObservedDuringCleanup;

        /** cleanup 执行期间 termination 是否已经完成。 */
        private boolean terminationDoneDuringCleanup;

        /** cleanup 方法是否已经执行到返回点。 */
        private boolean cleanupReturned;

        private FailureRecordingAgent(FailurePoint failurePoint) {
            super(Runnable::run);
            this.failurePoint = failurePoint;

            // 每次运行只需要一个步骤就能触发目标失败。
            setMaxSteps(1);
        }

        /**
         * 保存当前 context，并按测试配置决定是否在 think 阶段失败。
         */
        @Override
        protected TestDecision think(AgentRunContext context) {
            this.context = context;

            if (failurePoint == FailurePoint.THINK) {
                throw new IllegalStateException("think failure");
            }

            // ACT 用例需要继续进入 act，因此返回 shouldAct=true。
            return new TestDecision(true);
        }

        /**
         * ACT 用例在这里制造异常；THINK 用例不会进入该方法。
         */
        @Override
        protected String act(
                AgentRunContext context,
                TestDecision decision
        ) {
            if (failurePoint == FailurePoint.ACT) {
                throw new IllegalStateException("act failure");
            }

            throw new IllegalStateException(
                    "测试配置错误：当前用例不应正常完成 act"
            );
        }

        /**
         * 记录真实 BaseAgent.finalizeRun 调用 cleanup 时的生命周期信息。
         */
        @Override
        protected void cleanup(AgentRunContext context) {
            cleanupCalls++;
            stateObservedDuringCleanup = context.state();

            // 该值必须为 false，证明 termination 没有早于 cleanup 完成。
            terminationDoneDuringCleanup = context.termination().isDone();

            // 该赋值位于 cleanup 最后，用来证明清理逻辑已经执行到返回点。
            cleanupReturned = true;
        }

        /**
         * 返回 think 捕获的本次运行 context。
         */
        private AgentRunContext capturedContext() {
            if (context == null) {
                throw new IllegalStateException(
                        "测试未捕获到 AgentRunContext"
                );
            }
            return context;
        }

        /** 返回 cleanup 的实际调用次数。 */
        private int cleanupCalls() {
            return cleanupCalls;
        }

        /** 返回 cleanup 执行时观察到的状态。 */
        private AgentRunState stateObservedDuringCleanup() {
            return stateObservedDuringCleanup;
        }

        /** 返回 cleanup 期间 termination 是否已经完成。 */
        private boolean terminationDoneDuringCleanup() {
            return terminationDoneDuringCleanup;
        }

        /** 返回 cleanup 是否已经执行到返回点。 */
        private boolean cleanupReturned() {
            return cleanupReturned;
        }
    }

    /**
     * ACT 用例使用的最小不可变决策。
     *
     * @param shouldAct 是否进入 act 阶段
     */
    private record TestDecision(
            boolean shouldAct
    ) implements AgentStepDecision {
    }
}
