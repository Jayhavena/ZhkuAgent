package com.zhku.knowledge.web;

import com.zhku.agent.agent.runtime.AgentRunState;
import com.zhku.agent.agent.runtime.AgentStopReason;
import com.zhku.knowledge.campusagent.CampusCreditAgentManager;
import com.zhku.knowledge.campusagent.CampusCreditAgentService;
import com.zhku.knowledge.tools.CampusToolRegistry;
import com.zhku.knowledge.tools.CourseCreditSummaryTool;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

/** 使用真实配置的 2/2/16 执行器；取消标志不能冒充队列或物理工作线程释放。 */
class CampusAgentSaturationContractTest {
    private static final long READY_MS = 2_000;
    private static final long CONTROL_MS = 200;

    @Test
    void rejectsNineteenthAndRetainsStoppedQueueUntilRealWorkersDrain() throws Exception {
        var worker = (ThreadPoolExecutor) new CampusAgentPreviewConfiguration().zhkuCampusAgentWorker();
        var timer = new ControlledTimer();
        var entered = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        var calls = new AtomicInteger();
        ChatModel model = prompt -> {
            calls.incrementAndGet();
            entered.countDown();
            // 故意暂时忽略中断，测试容量依赖实际返回；finally 屏障保证夹具可退出。
            while (release.getCount() != 0) {
                try {
                    release.await();
                } catch (InterruptedException ignored) {
                    // 测试替身，不代表可以强制终止任意供应商调用。
                }
            }
            return new ChatResponse(List.of(new Generation(new AssistantMessage("请补充课程"))));
        };
        var service = new CampusCreditAgentService(model,
                new CampusToolRegistry(List.of(new CourseCreditSummaryTool())), question -> {
                    throw new AssertionError("缺课程时不应调用政策");
                }, 3);
        var manager = new CampusCreditAgentManager(service, worker, timer, Duration.ofSeconds(30));
        var controller = new CampusAgentPreviewController(manager);
        var request = new CampusCreditAgentService.Request("请汇总虚构课程", List.of());
        var accepted = new ArrayList<CampusCreditAgentManager.Snapshot>();
        try {
            assertThat(worker.getCorePoolSize()).isEqualTo(2);
            assertThat(worker.getMaximumPoolSize()).isEqualTo(2);
            assertThat(worker.getQueue().remainingCapacity()).isEqualTo(16);
            for (int i = 0; i < 2; i++) {
                var response = controller.start(request);
                assertThat(response.getStatusCode().value()).isEqualTo(202);
                accepted.add((CampusCreditAgentManager.Snapshot) response.getBody());
            }
            assertThat(entered.await(READY_MS, TimeUnit.MILLISECONDS)).isTrue();
            for (int i = 0; i < 16; i++) {
                var response = controller.start(request);
                assertThat(response.getStatusCode().value()).isEqualTo(202);
                accepted.add((CampusCreditAgentManager.Snapshot) response.getBody());
            }
            assertThat(worker.getActiveCount()).isEqualTo(2);
            assertThat(worker.getQueue()).hasSize(16);
            var rejected = controller.start(request);
            assertThat(rejected.getStatusCode().value()).isEqualTo(503);
            var rejectedRun = (CampusCreditAgentManager.Snapshot) rejected.getBody();
            assertThat(rejectedRun.state()).isEqualTo(AgentRunState.FAILED);
            assertThat(rejectedRun.failureCategory()).isEqualTo("EXECUTOR_REJECTED");
            assertThat(rejectedRun.terminated()).isTrue();
            assertThat(manager.find(rejectedRun.runId())).isEqualTo(rejectedRun);

            long started = System.nanoTime();
            manager.cancel(accepted.get(2).runId());
            assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)).isLessThanOrEqualTo(CONTROL_MS);
            timer.fire(2);
            started = System.nanoTime();
            timer.fire(3);
            assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)).isLessThanOrEqualTo(CONTROL_MS);
            manager.cancel(accepted.get(3).runId());
            assertThat(manager.find(accepted.get(2).runId()).stopReason()).isEqualTo(AgentStopReason.USER_REQUEST);
            assertThat(manager.find(accepted.get(3).runId()).stopReason()).isEqualTo(AgentStopReason.RUN_TIMEOUT);
            for (int i : List.of(2, 3)) {
                var stopped = manager.find(accepted.get(i).runId());
                assertThat(stopped.state()).isEqualTo(AgentRunState.STOP_REQUESTED);
                assertThat(stopped.terminated()).isFalse();
            }
            assertThat(worker.getQueue()).hasSize(16);
            assertThat(calls.get()).isEqualTo(2);
            assertThat(controller.start(request).getStatusCode().value()).isEqualTo(503);

            release.countDown();
            await(() -> accepted.stream().allMatch(run -> manager.find(run.runId()).terminated()));
            for (int i : List.of(2, 3)) {
                var stopped = manager.find(accepted.get(i).runId());
                assertThat(stopped.state()).isEqualTo(AgentRunState.CANCELLED);
                assertThat(stopped.status()).isEqualTo(CampusCreditAgentService.Status.CANCELLED);
            }
            assertThat(calls.get()).isEqualTo(16);
            await(() -> worker.getActiveCount() == 0 && worker.getQueue().isEmpty());
            var recovery = controller.start(request);
            assertThat(recovery.getStatusCode().value()).isEqualTo(202);
            var recoveryRun = (CampusCreditAgentManager.Snapshot) recovery.getBody();
            await(() -> manager.find(recoveryRun.runId()).terminated());
            assertThat(manager.find(recoveryRun.runId()).state()).isEqualTo(AgentRunState.INCOMPLETE);
            assertThat(manager.find(recoveryRun.runId()).failureCategory()).isEqualTo("NONE");
            assertThat(calls.get()).isEqualTo(17);
        } finally {
            release.countDown();
            worker.shutdownNow();
            timer.shutdownNow();
            assertThat(worker.awaitTermination(READY_MS, TimeUnit.MILLISECONDS)).isTrue();
            assertThat(timer.awaitTermination(READY_MS, TimeUnit.MILLISECONDS)).isTrue();
        }
    }

    private static void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(READY_MS);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(1);
        }
        assertThat(condition.getAsBoolean()).isTrue();
    }

    private static final class ControlledTimer extends ScheduledThreadPoolExecutor {
        private final List<Runnable> deadlines = new ArrayList<>();

        private ControlledTimer() {
            super(1);
            setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        }

        @Override
        public <V> ScheduledFuture<V> schedule(Callable<V> action, long delay, TimeUnit unit) {
            deadlines.add(() -> {
                try {
                    action.call();
                } catch (Exception exception) {
                    throw new AssertionError(exception);
                }
            });
            // 只固定截止屏障；fire 执行 Manager 原始 callback，不伪造停止状态。
            return super.schedule(action, 1, TimeUnit.DAYS);
        }

        private void fire(int index) {
            deadlines.get(index).run();
        }
    }
}
