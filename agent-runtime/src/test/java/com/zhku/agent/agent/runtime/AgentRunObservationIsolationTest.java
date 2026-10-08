package com.zhku.agent.agent.runtime;

import com.zhku.agent.agent.runtime.contract.AgentRunEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import com.zhku.agent.agent.runtime.contract.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.ArrayList;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class AgentRunObservationIsolationTest {
    private AgentRunObservationDispatcher owner;
    @BeforeEach void open() { owner = AgentRunObservationDispatcher.createOwned(); }
    @AfterEach void close() throws Exception {
        owner.close();
        AgentRunObservationDispatcherTest.await(() -> owner.status().liveThreadCount() == 0, 2000);
    }
    @ParameterizedTest
    @EnumSource(value = AgentRunEvent.Type.class, names = {"RUN_STARTED", "STEP_STARTED", "STOP_REQUESTED"})
    void blockedObserverDoesNotDelayStopOrInterrupt(AgentRunEvent.Type blocked) throws Exception {
        var observerEntered = new CountDownLatch(1);
        var releaseObserver = new CountDownLatch(1);
        var workerEntered = new CountDownLatch(1);
        var interruptSeen = new CountDownLatch(1);
        var stopReturned = new CountDownLatch(1);
        var observerAt = new AtomicLong();
        var stopAt = new AtomicLong();
        var interruptAt = new AtomicLong();
        var context = AgentRunContext.create("合成停止测试", event -> {
            if (event.type() == blocked) {
                observerAt.set(System.nanoTime());
                observerEntered.countDown();
                awaitIgnoringInterrupt(releaseObserver);
            }
        }, owner);
        var task = AgentRunTask.create(context, () -> {
            workerEntered.countDown();
            try {
                new CountDownLatch(1).await();
            } catch (InterruptedException expected) {
                interruptAt.set(System.nanoTime());
                interruptSeen.countDown();
            }
        });
        var worker = new Thread(task, "r13-business");
        var starter = new Thread(() -> { context.start(); if (blocked == AgentRunEvent.Type.STEP_STARTED) context.advanceStep(); }, "r13-host-start");
        var stopper = new Thread(() -> {
            context.requestStop(AgentStopReason.USER_REQUEST);
            stopReturned.countDown();
        }, "r13-control");
        try {
            worker.start();
            assertThat(workerEntered.await(2, TimeUnit.SECONDS)).isTrue();
            starter.start();
            if (blocked != AgentRunEvent.Type.STOP_REQUESTED)
                assertThat(observerEntered.await(2, TimeUnit.SECONDS)).isTrue();
            stopAt.set(System.nanoTime());
            stopper.start();
            assertThat(stopReturned.await(200, TimeUnit.MILLISECONDS))
                    .as("阻塞观察器不能使停止控制超过固定200ms").isTrue();
            assertThat(interruptSeen.await(Math.max(0,
                    200_000_000L - (System.nanoTime() - stopAt.get())), TimeUnit.NANOSECONDS)).isTrue();
            assertThat(interruptAt.get() - stopAt.get()).isLessThanOrEqualTo(200_000_000L);
            assertThat(observerEntered.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(context.requestStop(AgentStopReason.RUN_TIMEOUT)).isFalse();
            assertThat(context.snapshot().terminated()).isFalse();
            assertThat(releaseObserver.getCount()).isEqualTo(1);
            assertThat(context.stopReason()).isEqualTo(AgentStopReason.USER_REQUEST);
        } finally {
            // 无论RED/GREEN，都真实保持观察器十秒；释放之后才检验线程退出。
            long remaining = observerAt.get() + TimeUnit.SECONDS.toNanos(10) - System.nanoTime();
            if (observerAt.get() != 0 && remaining > 0) TimeUnit.NANOSECONDS.sleep(remaining);
            releaseObserver.countDown();
            worker.interrupt();
            starter.join(2000);
            stopper.join(2000);
            worker.join(2000);
            assertThat(starter.isAlive() || stopper.isAlive() || worker.isAlive()).isFalse();
        }
    }

    @ParameterizedTest
    @EnumSource(value = AgentRunEvent.Type.class, names = {"TOOL_CALL_PROPOSED", "TOOL_CALL_RESULT", "ERROR_RECORDED", "RUN_TERMINATED"})
    void publicationDoesNotWaitForBlockedCallback(AgentRunEvent.Type blocked) throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var at = new AtomicLong();
        var observed = new AtomicReference<AgentRunEvent>();
        var context = AgentRunContext.create("发布隔离", e -> {
            if (e.type() == blocked) {
                at.set(System.nanoTime()); observed.set(e); entered.countDown();
                awaitIgnoringInterrupt(release);
            }
        }, owner);
        try {
            context.start(); context.advanceStep();
            long operationAt = System.nanoTime();
            var call = context.proposeToolCall("id", "tool", "{}");
            context.recordToolResult(call, new AgentToolCall.Result(AgentToolCall.Status.SUCCEEDED,
                    "完整结果", AgentToolCall.PayloadFormat.TEXT, null));
            if (blocked == AgentRunEvent.Type.ERROR_RECORDED)
                context.finish(AgentRunState.FAILED, AgentRunSnapshot.EndReason.ERROR,
                        AgentRuntimeError.of(AgentRuntimeError.Category.MODEL_FAILURE, "MODEL_FAILURE"));
            else context.finish(AgentRunState.SUCCEEDED);
            context.cleanupOnce(() -> { }); context.signalTermination();
            context.publishTermination();
            assertThat(System.nanoTime() - operationAt).isLessThanOrEqualTo(200_000_000L);
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(context.snapshot().terminated()).isTrue();
            assertThat(owner.status().inFlightCount()).isEqualTo(1);
            if (blocked == AgentRunEvent.Type.RUN_TERMINATED)
                assertThat(observed.get().runSnapshot()).isEqualTo(context.snapshot());
            AgentRunObservationDispatcherTest.await(() -> context.observationStatus().phase()
                    == AgentRunObservationDispatcher.Phase.DETACHED, 425);
            assertThat(context.observationStatus().inFlight()).isTrue();
            assertThat(context.snapshot().error() == null).isEqualTo(blocked != AgentRunEvent.Type.ERROR_RECORDED);
        } finally {
            if (blocked == AgentRunEvent.Type.RUN_TERMINATED && at.get() != 0) {
                long remaining = at.get() + TimeUnit.SECONDS.toNanos(10) - System.nanoTime();
                if (remaining > 0) TimeUnit.NANOSECONDS.sleep(remaining);
            }
            release.countDown();
        }
    }

    @Test void concurrentFactsKeepSequenceAndUniqueFinalWithoutReentrantDeadlock() throws Exception {
        var events = new CopyOnWriteArrayList<AgentRunEvent>();
        var context = AgentRunContext.create("并发事实", events::add, owner);
        context.start(); context.advanceStep();
        var gate = new CountDownLatch(1);
        var workers = new ArrayList<Thread>();
        for (int i = 0; i < 8; i++) {
            var worker = new Thread(() -> {
                awaitIgnoringInterrupt(gate);
                var call = context.proposeToolCall("duplicate", "tool", "{}");
                context.recordToolResult(call, new AgentToolCall.Result(AgentToolCall.Status.SUCCEEDED,
                        "result", AgentToolCall.PayloadFormat.TEXT, null));
            }); workers.add(worker); worker.start();
        }
        gate.countDown();
        for (var worker : workers) { worker.join(2000); assertThat(worker.isAlive()).isFalse(); }
        context.requestStop(AgentStopReason.USER_REQUEST); context.finish(AgentRunState.CANCELLED);
        context.cleanupOnce(() -> { }); context.signalTermination();
        var publishers = new ArrayList<Thread>();
        for (int i = 0; i < 4; i++) { var thread = new Thread(context::publishTermination); publishers.add(thread); thread.start(); }
        for (var thread : publishers) { thread.join(2000); assertThat(thread.isAlive()).isFalse(); }
        AgentRunObservationDispatcherTest.await(() -> context.observationStatus().phase()
                == AgentRunObservationDispatcher.Phase.DRAINED, 1000);
        assertThat(events).hasSize(20);
        assertThat(events).extracting(AgentRunEvent::sequence)
                .containsExactlyElementsOf(java.util.stream.LongStream.rangeClosed(1, 20).boxed().toList());
        context.proposeToolCall("late", "tool", "{}");
        assertThat(context.observationStatus().lastProducedSequence()).isEqualTo(20);
        assertThat(context.observationStatus().dropCount()).isZero();
    }

    @Test void cleanupBoundaryStillPreventsPrematurePublication() throws Exception {
        var context = AgentRunContext.create("清理边界");
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        context.start(); context.finish(AgentRunState.SUCCEEDED);
        var cleaner = new Thread(() -> context.cleanupOnce(() -> {
            entered.countDown(); awaitIgnoringInterrupt(release);
        }));
        cleaner.start();
        try {
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            org.assertj.core.api.Assertions.assertThatThrownBy(context::signalTermination)
                    .isInstanceOf(IllegalStateException.class);
            assertThat(context.termination().isDone()).isFalse();
            org.assertj.core.api.Assertions.assertThatThrownBy(context::publishTermination)
                    .isInstanceOf(IllegalStateException.class);
        } finally { release.countDown(); cleaner.join(2000); }
        assertThat(cleaner.isAlive()).isFalse();
        context.signalTermination(); assertThat(context.publishTermination()).isTrue();
    }

    static void awaitIgnoringInterrupt(CountDownLatch release) {
        while (release.getCount() != 0) {
            try {
                release.await();
            } catch (InterruptedException ignored) {
                // 任意观察器可以忽略中断，不能靠Future取消宣称物理退出。
            }
        }
    }
}
