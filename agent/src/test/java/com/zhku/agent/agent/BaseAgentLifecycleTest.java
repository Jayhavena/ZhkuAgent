package com.zhku.agent.agent;

import com.zhku.agent.agent.runtime.AgentRunContext;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.Message;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * BaseAgent 生命周期行为测试。
 *
 * <p>测试只使用内存中的确定性假 Agent，不连接真实大模型、工具、网络或
 * 数据库。</p>
 */
class BaseAgentLifecycleTest {

    /**
     * 验证同一个 Agent 实例能够连续运行两次，并且两次运行互不污染。
     */
    @Test
    void shouldIsolateSequentialRunsOnSameAgentInstance() {
        RecordingAgent agent = new RecordingAgent();

        String firstResult = agent.run("first-run");

        assertThatCode(() -> agent.run("second-run"))
                .doesNotThrowAnyException();

        assertThat(firstResult).contains("Step 1: recorded");

        assertThat(agent.messageSnapshots).containsExactly(
                List.of("first-run"),
                List.of("second-run")
        );

        assertThat(agent.stepSnapshots).containsExactly(1, 1);
    }

    /**
     * 验证同一个 Agent 实例的两个运行能够同时进入步骤，且运行数据完全隔离。
     */
    @Test
    void shouldIsolateConcurrentRunsOnSameAgentInstance() throws Exception {
        // 部分 Windows/JDK 环境第一次生成 UUID 时会初始化安全随机数源，
        // 可能耗时数秒。这个冷启动与 Agent 并发隔离无关，因此在进入
        // 2 秒同步窗口前预热，避免把环境启动开销误判为并发失败。
        UUID.randomUUID();

        ConcurrentRecordingAgent agent = new ConcurrentRecordingAgent();

        // 两个工作线程分别执行同一个 Agent 实例的两个 run 调用。
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<String> firstRun = executor.submit(
                    () -> agent.run("run-A")
            );

            // 先确认运行 A 已进入 step 并被阻塞，再提交运行 B。
            // 对旧共享 state 实现而言，运行 B 会因为 A 仍是 RUNNING 而被拒绝。
            assertThat(agent.firstRunEntered.await(2, TimeUnit.SECONDS))
                    .as("运行 A 应当在时限内进入 step")
                    .isTrue();

            Future<String> secondRun = executor.submit(
                    () -> agent.run("run-B")
            );

            // 两个运行必须都进入 step，才能证明测试制造了真实重叠区间。
            boolean bothEntered = agent.bothRunsEntered.await(
                    2,
                    TimeUnit.SECONDS
            );

            // 无论断言是否成功，都应先释放工作线程，避免测试失败时遗留阻塞线程。
            agent.releaseRuns.countDown();

            assertThat(bothEntered)
                    .as("同一 Agent 的两个运行应当同时进入各自的 step")
                    .isTrue();

            // 两个任务都必须正常返回，而不是某一个被共享状态检查拒绝。
            assertThat(firstRun.get(2, TimeUnit.SECONDS))
                    .contains("Step 1: concurrent-recorded");
            assertThat(secondRun.get(2, TimeUnit.SECONDS))
                    .contains("Step 1: concurrent-recorded");

            assertThat(agent.observations).hasSize(2);

            // 每次 AgentRunContext.create 都应产生唯一 runId。
            assertThat(agent.observations)
                    .extracting(RunObservation::runId)
                    .doesNotHaveDuplicates();

            // 两个上下文的步骤计数必须分别从 1 开始。
            assertThat(agent.observations)
                    .extracting(RunObservation::stepNumber)
                    .containsOnly(1);

            // 两个上下文必须分别只包含自己的用户消息。
            assertThat(agent.observations)
                    .extracting(RunObservation::messageTexts)
                    .containsExactlyInAnyOrder(
                            List.of("run-A"),
                            List.of("run-B")
                    );
        } finally {
            // 如果前面的任意等待或断言失败，finally 仍会释放阻塞点。
            agent.releaseRuns.countDown();

            // shutdownNow 防止失败测试留下线程；它只关闭本测试创建的线程池。
            executor.shutdownNow();
            executor.awaitTermination(2, TimeUnit.SECONDS);
        }
    }

    /**
     * 记录顺序运行中 BaseAgent 传入的 context 数据。
     */
    private static final class RecordingAgent extends BaseAgent {

        /** 保存每次运行在第一步看到的消息文本。 */
        private final List<List<String>> messageSnapshots =
                new ArrayList<>();

        /** 保存每次运行在第一步看到的步骤编号。 */
        private final List<Integer> stepSnapshots = new ArrayList<>();

        private RecordingAgent() {
            super(Runnable::run);
            setMaxSteps(1);
        }

        @Override
        protected String step(AgentRunContext context) {
            List<String> currentMessages = context.messageSnapshot()
                    .stream()
                    .map(Message::getText)
                    .toList();
            messageSnapshots.add(List.copyOf(currentMessages));

            stepSnapshots.add(context.currentStep());

            return "recorded";
        }
    }

    /**
     * 让两个并发运行在 step 中形成可控制重叠的测试 Agent。
     */
    private static final class ConcurrentRecordingAgent extends BaseAgent {

        /** 第一个运行进入 step 时释放。 */
        private final CountDownLatch firstRunEntered =
                new CountDownLatch(1);

        /** 两个运行都进入 step 后计数归零。 */
        private final CountDownLatch bothRunsEntered =
                new CountDownLatch(2);

        /** 由测试线程控制两个运行何时离开 step。 */
        private final CountDownLatch releaseRuns =
                new CountDownLatch(1);

        /**
         * 多个工作线程会同时写入观察结果，因此使用线程安全队列。
         */
        private final Queue<RunObservation> observations =
                new ConcurrentLinkedQueue<>();

        private ConcurrentRecordingAgent() {
            super(Runnable::run);
            setMaxSteps(1);
        }

        @Override
        protected String step(AgentRunContext context) {
            List<String> messageTexts = context.messageSnapshot()
                    .stream()
                    .map(Message::getText)
                    .toList();

            // 在阻塞前记录当前 context，保证 bothRunsEntered 归零时已有两份证据。
            observations.add(new RunObservation(
                    context.runId(),
                    context.currentStep(),
                    List.copyOf(messageTexts)
            ));

            firstRunEntered.countDown();
            bothRunsEntered.countDown();

            try {
                // 等待测试线程确认两个运行都进入后统一释放。
                if (!releaseRuns.await(2, TimeUnit.SECONDS)) {
                    throw new IllegalStateException(
                            "等待并发步骤释放超时"
                    );
                }
            } catch (InterruptedException exception) {
                // 恢复中断标记，让上层能够观察到线程收到中断请求。
                Thread.currentThread().interrupt();
                throw new IllegalStateException(
                        "并发测试步骤被中断",
                        exception
                );
            }

            return "concurrent-recorded";
        }
    }

    /**
     * 一次并发 step 观察到的不可变数据。
     *
     * @param runId        当前运行唯一标识
     * @param stepNumber   当前运行步骤编号
     * @param messageTexts 当前运行消息文本快照
     */
    private record RunObservation(
            UUID runId,
            int stepNumber,
            List<String> messageTexts
    ) {
    }
}
