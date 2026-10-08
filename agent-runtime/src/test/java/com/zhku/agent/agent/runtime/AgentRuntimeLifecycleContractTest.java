package com.zhku.agent.agent.runtime;

import com.zhku.agent.agent.runtime.contract.*;
import com.zhku.agent.agent.runtime.contract.AgentRunSnapshot.EndReason;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

class AgentRuntimeLifecycleContractTest {
    private AgentRunObservationDispatcher observations;
    @org.junit.jupiter.api.BeforeEach
    void openObservations() { observations = AgentRunObservationDispatcher.createOwned(); }
    @org.junit.jupiter.api.AfterEach
    void closeObservations() throws Exception {
        observations.close();
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(2);
        while (observations.status().liveThreadCount() != 0 && System.nanoTime() < deadline) Thread.sleep(1);
        assertThat(observations.status().liveThreadCount()).isZero();
    }
    private static void awaitObserved(AgentRunContext context) {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(1);
        while (System.nanoTime() < deadline) {
            var status = context.observationStatus();
            if (status.lastReturnedSequence() == status.lastProducedSequence() && !status.inFlight()) return;
            Thread.onSpinWait();
        }
        throw new AssertionError("正常观察交付超过固定1000ms");
    }

    @Test
    void lifecycleCannotCommitAheadOfItsOrderedEvent() throws Exception {
        for (boolean stopping : List.of(false, true)) {
            List<AgentRunEvent> events = new CopyOnWriteArrayList<>();
            var context = AgentRunContext.create("任务", events::add, observations);
            if (stopping)
                context.start();
            // 精确卡住事件锁，复现 CAS 已提交但事件还没发布的窗口，避免靠随机循环碰运气。
            var field = AgentRunContext.class.getDeclaredField("eventLock");
            field.setAccessible(true);
            Object lock = field.get(context);
            Thread transition = new Thread(() -> {
                if (stopping)
                    context.requestStop(AgentStopReason.USER_REQUEST);
                else
                    context.start();
            });
            try {
                synchronized (lock) {
                    transition.start();
                    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
                    while (transition.getState() != Thread.State.BLOCKED && System.nanoTime() < deadline)
                        Thread.sleep(1);
                    assertThat(transition.getState()).isEqualTo(Thread.State.BLOCKED);
                    assertThat(context.state()).isEqualTo(stopping ? AgentRunState.RUNNING : AgentRunState.CREATED);
                }
            } finally {
                transition.join(2000);
            }
            assertThat(transition.isAlive()).isFalse();
            if (!stopping)
                context.requestStop(AgentStopReason.USER_REQUEST);
            context.finish(AgentRunState.CANCELLED);
            context.cleanupOnce(() -> {
            });
            context.signalTermination();
            context.publishTermination();
            awaitObserved(context);
            assertThat(events).extracting(AgentRunEvent::type).containsExactly(
                    AgentRunEvent.Type.RUN_STARTED, AgentRunEvent.Type.STOP_REQUESTED,
                    AgentRunEvent.Type.RUN_TERMINATED);
        }
    }

    @Test
    void publicationFollowsCleanupAndIsAtMostOnce() {
        List<AgentRunEvent> events = new CopyOnWriteArrayList<>();
        var context = AgentRunContext.create("任务", events::add, observations);
        context.start();
        context.advanceStep();
        context.finish(AgentRunState.INCOMPLETE, EndReason.STEP_LIMIT, null);
        assertThat(context.snapshot().endReason()).isEqualTo(EndReason.STEP_LIMIT);
        assertThat(context.snapshot().terminated()).isFalse();
        assertThatThrownBy(context::publishTermination).isInstanceOf(IllegalStateException.class);
        context.cleanupOnce(() -> {
        });
        context.signalTermination();
        assertThat(context.snapshot().terminated()).isFalse();
        assertThat(context.publishTermination()).isTrue();
        assertThat(context.publishTermination()).isFalse();
        awaitObserved(context);
        assertThat(events).extracting(AgentRunEvent::type).containsExactly(
                AgentRunEvent.Type.RUN_STARTED, AgentRunEvent.Type.STEP_STARTED, AgentRunEvent.Type.RUN_TERMINATED);
        assertThat(events).extracting(AgentRunEvent::sequence).containsExactly(1L, 2L, 3L);
        assertThat(events.getLast().runSnapshot()).isEqualTo(context.snapshot());
    }

    @Test
    void stopKeepsFirstReasonAndRejectsLateCompletionFacts() {
        var context = AgentRunContext.create("任务");
        context.start();
        context.requestStop(AgentStopReason.RUN_TIMEOUT);
        assertThat(context.requestStop(AgentStopReason.USER_REQUEST)).isFalse();
        assertThat(context.finish(AgentRunState.SUCCEEDED, EndReason.COMPLETED, null)).isFalse();
        context.finish(AgentRunState.CANCELLED, EndReason.STOPPED, null);
        assertThat(context.snapshot().stopReason()).isEqualTo(AgentStopReason.RUN_TIMEOUT);
        assertThat(context.snapshot().error()).isNull();
    }

    @Test
    void concurrentSnapshotsNeverSplitStopStateAndReason() throws Exception {
        try (var worker = Executors.newSingleThreadExecutor()) {
            for (int i = 0; i < 200; i++) {
                var context = AgentRunContext.create("任务");
                context.start();
                var stop = worker.submit(() -> context.requestStop(AgentStopReason.USER_REQUEST));
                for (int j = 0; j < 50; j++) {
                    var snapshot = context.snapshot();
                    assertThat(snapshot.stopReason()).isEqualTo(snapshot.state() == AgentRunState.RUNNING
                            ? AgentStopReason.NONE
                            : AgentStopReason.USER_REQUEST);
                }
                stop.get(2, TimeUnit.SECONDS);
            }
        }
    }

    @Test
    void cleanupFailureOverridesSuccessAndPublishesSafeFinalError() {
        List<AgentRunEvent> events = new CopyOnWriteArrayList<>();
        var context = AgentRunContext.create("任务", events::add, observations);
        context.start();
        context.finish(AgentRunState.SUCCEEDED);
        assertThatThrownBy(() -> context.cleanupOnce(() -> {
            throw new IllegalStateException("SECRET");
        }))
                .isInstanceOf(IllegalStateException.class);
        context.signalTermination();
        context.publishTermination();
        awaitObserved(context);
        assertThat(context.snapshot().state()).isEqualTo(AgentRunState.FAILED);
        assertThat(context.snapshot().error().category()).isEqualTo(AgentRuntimeError.Category.CLEANUP_FAILURE);
        assertThat(events.toString()).doesNotContain("SECRET");
    }

    @Test
    void observerFailureDoesNotChangeBusinessOutcome() {
        var context = AgentRunContext.create("任务", event -> {
            throw new IllegalStateException("SECRET");
        }, observations);
        context.start();
        context.finish(AgentRunState.SUCCEEDED);
        context.cleanupOnce(() -> {
        });
        context.signalTermination();
        context.publishTermination();
        awaitObserved(context);
        assertThat(context.snapshot().state()).isEqualTo(AgentRunState.SUCCEEDED);
    }

    @Test
    void toolResultsAreImmutableCorrelatedAndDoNotCompleteRun() {
        List<AgentRunEvent> events = new CopyOnWriteArrayList<>();
        var context = AgentRunContext.create("任务", events::add, observations);
        context.start();
        context.advanceStep();
        var first = context.proposeToolCall("duplicate", "tool", "{}");
        var second = context.proposeToolCall("duplicate", "tool", "{}");
        assertThat(first.callId()).isNotEqualTo(second.callId());
        assertThat(first.result()).isNull();
        var result = new AgentToolCall.Result(AgentToolCall.Status.SUCCEEDED, "plain text",
                AgentToolCall.PayloadFormat.TEXT, null);
        context.recordToolResult(first, result);
        assertThat(context.snapshot().state()).isEqualTo(AgentRunState.RUNNING);
        assertThat(first.result()).isNull();
        assertThatThrownBy(() -> AgentRunContext.create("其他任务").recordToolResult(first, result))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> first.withResult(result).withResult(result)).isInstanceOf(IllegalStateException.class);
        awaitObserved(context);
        assertThat(events.getLast().toolCall().result().payload()).isEqualTo("plain text");
    }

    @Test
    void contractsRejectInvalidCombinationsAndRawErrorMessages() {
        assertThatThrownBy(() -> new AgentRunSnapshot(UUID.randomUUID(), AgentRunState.SUCCEEDED,
                AgentStopReason.USER_REQUEST, 0, false, EndReason.COMPLETED, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AgentRunSnapshot(UUID.randomUUID(), AgentRunState.RUNNING,
                AgentStopReason.NONE, 0, true, null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AgentRunSnapshot(UUID.randomUUID(), AgentRunState.SUCCEEDED,
                AgentStopReason.NONE, 0, false, EndReason.STEP_LIMIT, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AgentToolCall.Result(AgentToolCall.Status.SUCCEEDED, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                () -> new AgentRuntimeError(AgentRuntimeError.Category.MODEL_FAILURE, "MODEL_FAILURE", "SECRET"))
                .isInstanceOf(IllegalArgumentException.class);
        var error = AgentRuntimeError.of(AgentRuntimeError.Category.TOOL_FAILURE, "INVALID_TOOL_ARGUMENT");
        var rejected = new AgentToolCall.Result(AgentToolCall.Status.REJECTED, "{\"error\":\"INVALID_TOOL_ARGUMENT\"}",
                AgentToolCall.PayloadFormat.JSON, error);
        assertThat(rejected.status()).isEqualTo(AgentToolCall.Status.REJECTED);
    }
}
