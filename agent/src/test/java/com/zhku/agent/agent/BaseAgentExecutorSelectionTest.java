package com.zhku.agent.agent;

import com.zhku.agent.agent.runtime.AgentRunContext;
import com.zhku.agent.agent.runtime.AgentRunState;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BaseAgent 流式任务执行器选择测试。
 *
 * <p>测试使用一个由测试线程手动推进的真实 Executor。它验证的是任务是否
 * 服从构造器注入的执行入口，而不是读取 BaseAgent 私有字段或检查源码文本。</p>
 */
class BaseAgentExecutorSelectionTest {

    /**
     * 验证流式任务只有在注入执行器明确推进后才开始执行。
     */
    @Test
    void shouldRunStreamTaskOnlyWhenInjectedExecutorAdvances()
            throws Exception {
        ManualExecutor executor = new ManualExecutor();
        ExecutorControlledAgent agent =
                new ExecutorControlledAgent(executor);

        // runStream 只应创建并提交任务，不应绕过注入执行器立即执行 step。
        agent.runStream("executor-controlled-run");

        // 旧实现硬编码 commonPool，因此这里稳定得到 0 并形成 RED。
        assertThat(executor.queuedTaskCount())
                .as("流式 FutureTask 必须提交给构造器注入的执行器")
                .isEqualTo(1);
        assertThat(agent.stepCalls())
                .as("手动执行器推进前不应开始 Agent 步骤")
                .isZero();

        // 由测试线程执行队列中的真实 FutureTask，避免依赖调度和 sleep。
        executor.runNext();

        AgentRunContext context = agent.capturedContext();
        assertThat(context.termination().get(1, TimeUnit.SECONDS))
                .isEqualTo(AgentRunState.SUCCEEDED);
        assertThat(agent.stepCalls()).isEqualTo(1);
        assertThat(agent.cleanupCalls()).isEqualTo(1);
        assertThat(executor.queuedTaskCount()).isZero();
    }

    /**
     * 只保存已提交命令，并由测试显式决定执行时点的执行器。
     */
    private static final class ManualExecutor implements Executor {

        /** 测试线程是唯一读写者，无需引入额外并发噪声。 */
        private final Queue<Runnable> tasks = new ArrayDeque<>();

        @Override
        public void execute(Runnable command) {
            if (command == null) {
                throw new IllegalArgumentException(
                        "command must not be null"
                );
            }
            tasks.add(command);
        }

        /** 返回尚未被测试推进的任务数量。 */
        private int queuedTaskCount() {
            return tasks.size();
        }

        /** 执行下一项真实任务；没有任务表示生产代码没有使用该执行器。 */
        private void runNext() {
            Runnable task = tasks.poll();
            if (task == null) {
                throw new IllegalStateException(
                        "没有可执行的流式任务"
                );
            }
            task.run();
        }
    }

    /**
     * 记录真实 step、cleanup 和 RunContext 的最小流式 Agent。
     */
    private static final class ExecutorControlledAgent
            extends BaseAgent {

        /** 保存工作任务实际收到的运行上下文。 */
        private final AtomicReference<AgentRunContext> capturedContext =
                new AtomicReference<>();

        /** 记录真实步骤执行次数。 */
        private final AtomicInteger stepCalls = new AtomicInteger();

        /** 记录最终清理次数。 */
        private final AtomicInteger cleanupCalls = new AtomicInteger();

        private ExecutorControlledAgent(Executor executor) {
            super(executor);
            setMaxSteps(1);
        }

        @Override
        protected String step(AgentRunContext context) {
            if (!capturedContext.compareAndSet(null, context)) {
                throw new IllegalStateException(
                        "测试 Agent 不应执行多个运行上下文"
                );
            }
            stepCalls.incrementAndGet();
            context.finish(AgentRunState.SUCCEEDED);
            return "executor-controlled";
        }

        @Override
        protected void cleanup(AgentRunContext context) {
            cleanupCalls.incrementAndGet();
        }

        /** 返回工作任务捕获的真实运行上下文。 */
        private AgentRunContext capturedContext() {
            AgentRunContext context = capturedContext.get();
            if (context == null) {
                throw new IllegalStateException(
                        "注入执行器尚未推进流式任务"
                );
            }
            return context;
        }

        private int stepCalls() {
            return stepCalls.get();
        }

        private int cleanupCalls() {
            return cleanupCalls.get();
        }
    }
}
