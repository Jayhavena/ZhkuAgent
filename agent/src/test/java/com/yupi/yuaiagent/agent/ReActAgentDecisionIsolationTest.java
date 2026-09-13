package com.yupi.yuaiagent.agent;

import com.yupi.yuaiagent.agent.model.AgentStepDecision;
import com.yupi.yuaiagent.agent.runtime.AgentRunContext;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.Message;

import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ReActAgent 在并发运行时的决策配对测试。
 *
 * <p>测试使用确定性的内存 Agent，不连接真实大模型、工具、网络或数据库。
 * 它验证的是项目自己的 think → decision → act 参数传递契约。</p>
 */
class ReActAgentDecisionIsolationTest {

    /**
     * 验证两个并发运行分别把自己的 think 决策传给自己的 act。
     */
    @Test
    void shouldPassEachConcurrentRunItsOwnDecision() throws Exception {
        // 部分 Windows/JDK 环境第一次生成 UUID 时会初始化安全随机数源。
        // 在同步计时窗口前预热，避免把 JVM 冷启动误判为并发失败。
        UUID.randomUUID();

        DecisionPairingAgent agent = new DecisionPairingAgent();
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            // 先启动运行 A。A 会在 decision-A.shouldAct() 内主动停住。
            Future<String> firstRun = executor.submit(
                    () -> agent.run("run-A")
            );

            // 必须先确认 A 已经持有 decision-A 并开始检查它，
            // 然后再启动 B，才能稳定模拟“共享字段被 B 覆盖”的风险窗口。
            assertThat(agent.firstDecisionCheckStarted.await(
                    2,
                    TimeUnit.SECONDS
            ))
                    .as("运行 A 应当进入 decision-A 的 shouldAct 检查")
                    .isTrue();

            Future<String> secondRun = executor.submit(
                    () -> agent.run("run-B")
            );

            // B 检查 decision-B 时会释放 A。这个事件证明两个运行
            // 确实在同一个 Agent 实例上形成了受控交错。
            assertThat(agent.secondDecisionChecked.await(
                    2,
                    TimeUnit.SECONDS
            ))
                    .as("运行 B 应当检查 decision-B 并释放运行 A")
                    .isTrue();

            // 先取得结果，证明两个任务没有异常退出；稍后再断言结果归属。
            String firstResult = firstRun.get(2, TimeUnit.SECONDS);
            String secondResult = secondRun.get(2, TimeUnit.SECONDS);

            assertThat(agent.observations).hasSize(2);

            // 核心断言：act 收到的 context 和 decision 必须来自同一次运行。
            // 如果 A 的 context 配上 B 的 decision，这里会直接失败。
            assertThat(agent.observations).allSatisfy(observation -> {
                assertThat(observation.contextRunId())
                        .isEqualTo(observation.decisionRunId());
                assertThat(observation.contextPrompt())
                        .isEqualTo(observation.decisionPrompt());
            });

            // 再用手工给出的字面值确认 A、B 两次行动都真实发生。
            assertThat(agent.observations)
                    .extracting(DecisionObservation::contextPrompt)
                    .containsExactlyInAnyOrder("run-A", "run-B");

            // 输出也必须来自各自的 decision，不能把 B 的结果返回给 A。
            assertThat(firstResult).contains("acted-run-A");
            assertThat(secondResult).contains("acted-run-B");
        } finally {
            // 当前面任意断言失败时，仍释放可能正在等待的运行 A。
            agent.secondDecisionChecked.countDown();

            // 只关闭本测试创建的线程池，避免失败测试遗留工作线程。
            executor.shutdownNow();
            executor.awaitTermination(2, TimeUnit.SECONDS);
        }
    }

    /**
     * 用于测试 think → decision → act 配对关系的确定性 Agent。
     */
    private static final class DecisionPairingAgent
            extends ReActAgent<TestDecision> {

        /** A 开始检查 decision-A 时归零。 */
        private final CountDownLatch firstDecisionCheckStarted =
                new CountDownLatch(1);

        /** B 检查 decision-B 时归零，同时解除 A 的等待。 */
        private final CountDownLatch secondDecisionChecked =
                new CountDownLatch(1);

        /** act 的多个并发调用会写入该线程安全队列。 */
        private final Queue<DecisionObservation> observations =
                new ConcurrentLinkedQueue<>();

        private DecisionPairingAgent() {
            super(Runnable::run);
            // 每次运行只执行一步，测试只关注一次 think-act 配对。
            setMaxSteps(1);
        }

        /**
         * 为当前 context 创建只属于本次运行的不可变决策。
         */
        @Override
        protected TestDecision think(AgentRunContext context) {
            String prompt = context.messageSnapshot()
                    .stream()
                    .findFirst()
                    .map(Message::getText)
                    .orElseThrow();

            if ("run-A".equals(prompt)) {
                return new TestDecision(
                        context.runId(),
                        prompt,
                        () -> {
                            // 告诉测试线程：A 已经进入 decision-A.shouldAct()。
                            firstDecisionCheckStarted.countDown();

                            // 等待 B 检查自己的 decision，制造确定性交错。
                            awaitOrThrow(
                                    secondDecisionChecked,
                                    "等待运行 B 检查 decision-B 超时"
                            );
                        }
                );
            }

            if ("run-B".equals(prompt)) {
                return new TestDecision(
                        context.runId(),
                        prompt,
                        // B 开始检查自己的 decision 时解除 A。
                        secondDecisionChecked::countDown
                );
            }

            throw new IllegalArgumentException(
                    "测试只接受 run-A 或 run-B，实际为：" + prompt
            );
        }

        /**
         * 记录 act 实际收到的 context 与 decision，供测试做行为断言。
         */
        @Override
        protected String act(
                AgentRunContext context,
                TestDecision decision
        ) {
            String contextPrompt = context.messageSnapshot()
                    .stream()
                    .findFirst()
                    .map(Message::getText)
                    .orElseThrow();

            observations.add(new DecisionObservation(
                    context.runId(),
                    contextPrompt,
                    decision.runId(),
                    decision.prompt()
            ));

            return "acted-" + decision.prompt();
        }
    }

    /**
     * 测试使用的不可变步骤决策。
     *
     * @param runId           生成该决策的运行标识
     * @param prompt          生成该决策的用户输入
     * @param beforeActCheck  shouldAct 返回前执行的同步动作
     */
    private record TestDecision(
            UUID runId,
            String prompt,
            Runnable beforeActCheck
    ) implements AgentStepDecision {

        /**
         * 执行测试安排的同步动作，并声明本步骤需要进入 act。
         */
        @Override
        public boolean shouldAct() {
            beforeActCheck.run();
            return true;
        }
    }

    /**
     * act 实际收到的一组 context 与 decision 数据。
     *
     * @param contextRunId   act 的 context 所属运行
     * @param contextPrompt  act 的 context 中的用户输入
     * @param decisionRunId  act 的 decision 所属运行
     * @param decisionPrompt act 的 decision 中的用户输入
     */
    private record DecisionObservation(
            UUID contextRunId,
            String contextPrompt,
            UUID decisionRunId,
            String decisionPrompt
    ) {
    }

    /**
     * 等待明确事件；超时或中断时转换成可读的测试失败。
     */
    private static void awaitOrThrow(
            CountDownLatch latch,
            String timeoutMessage
    ) {
        try {
            if (!latch.await(2, TimeUnit.SECONDS)) {
                throw new IllegalStateException(timeoutMessage);
            }
        } catch (InterruptedException exception) {
            // 恢复中断标志，避免吞掉测试线程的中断请求。
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "等待决策同步事件时被中断",
                    exception
            );
        }
    }
}
