package com.zhku.knowledge.observability;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhku.agent.agent.runtime.*;
import com.zhku.agent.agent.runtime.contract.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.lang.reflect.Modifier;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.*;
import static com.zhku.knowledge.observability.ObservationSink.Delivery.*;

/** 仅合成 Runtime 契约；不装配 T08、HTTP、供应商或审计服务。 */
class AnonymousRuntimeObserverContractTest {
    private static final UUID FIRST = UUID.fromString("00000000-0000-4000-8000-000000000201");
    private static final UUID SECOND = UUID.fromString("00000000-0000-4000-8000-000000000202");
    private static final String PRIVATE = "SYNTHETIC_PRIVATE_CONTENT";
    private static final String HASH = "a1b2c3d4".repeat(8);

    private static AgentRunEvent event(UUID run, long sequence, AgentRunEvent.Type type) {
        var call = new AgentToolCall(run, PRIVATE, HASH, 1, PRIVATE,
                "{\"arguments\":\"" + PRIVATE + "\"}", null);
        var error = AgentRuntimeError.of(AgentRuntimeError.Category.MODEL_FAILURE, PRIVATE);
        return switch (type) {
            case STEP_STARTED -> new AgentRunEvent(run, sequence, Instant.EPOCH, type, 1, null, null, null);
            case TOOL_CALL_PROPOSED -> new AgentRunEvent(run, sequence, Instant.EPOCH, type, 1, call, null, null);
            case TOOL_CALL_RESULT -> new AgentRunEvent(run, sequence, Instant.EPOCH, type, 1,
                    call.withResult(new AgentToolCall.Result(AgentToolCall.Status.SUCCEEDED,
                            PRIVATE + HASH, AgentToolCall.PayloadFormat.TEXT, null)), null, null);
            case ERROR_RECORDED -> new AgentRunEvent(run, sequence, Instant.EPOCH, type, null, null, null, error);
            case RUN_STARTED, STOP_REQUESTED, RUN_TERMINATED -> {
                boolean start = type == AgentRunEvent.Type.RUN_STARTED;
                boolean terminal = type == AgentRunEvent.Type.RUN_TERMINATED;
                yield new AgentRunEvent(run, sequence, Instant.EPOCH, type, null, null,
                        new AgentRunSnapshot(run, start ? AgentRunState.RUNNING : terminal ? AgentRunState.CANCELLED : AgentRunState.STOP_REQUESTED,
                                start ? AgentStopReason.NONE : AgentStopReason.USER_REQUEST, 0, terminal,
                                terminal ? AgentRunSnapshot.EndReason.STOPPED : null, null), null);
            }
        };
    }

    private static void anonymousOnly(ObservationEventV1 output) throws Exception {
        String encoded = new ObjectMapper().writeValueAsString(output) + output;
        // 断言只输出布尔值，失败报告也不得回显注入的敏感标记。
        assertThat(List.of(PRIVATE, HASH, FIRST.toString(), SECOND.toString(), "arguments", "sequence", "runId")
                .stream().noneMatch(encoded::contains)).as("anonymous output excludes private fields").isTrue();
        assertThat(output.inputTokens()).isNull();
        assertThat(output.outputTokens()).isNull();
        assertThat(output.elapsedMillis()).isNull();
        assertThat(output.estimatedCostMicros()).isNull();
    }

    @ParameterizedTest @EnumSource(AgentRunEvent.Type.class)
    void canonicalProjectionOfAllSevenKinds(AgentRunEvent.Type type) throws Exception {
        var delivered = new ArrayList<ObservationEventV1>();
        var observer = new AnonymousRuntimeObserver(value -> { delivered.add(value); return ACCEPTED; });
        var source = event(FIRST, 1, type);
        observer.accept(source);
        assertThat(delivered).containsExactly(ObservationProjector.anonymous(source));
        assertThat(delivered.getFirst().kind().name()).isEqualTo(switch (type) {
            case TOOL_CALL_PROPOSED -> "TOOL_PROPOSED";
            case TOOL_CALL_RESULT -> "TOOL_RESULT";
            case ERROR_RECORDED -> "ERROR";
            default -> type.name();
        });
        anonymousOnly(delivered.getFirst());
        assertThat(observer.status().acceptedCount()).isEqualTo(1);
    }

    @Test void rejectsMismatchedSourceWithoutInventingMetrics() throws Exception {
        var sink = new BoundedObservationBuffer();
        var observer = new AnonymousRuntimeObserver(sink);
        assertThatThrownBy(() -> new AgentRunEvent(FIRST, 1, Instant.EPOCH,
                AgentRunEvent.Type.TOOL_CALL_RESULT, 1, null, null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AgentRunEvent(FIRST, 1, Instant.EPOCH,
                AgentRunEvent.Type.RUN_TERMINATED, null, null,
                event(SECOND, 1, AgentRunEvent.Type.RUN_TERMINATED).runSnapshot(), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(sink.drain()).isEmpty();
        observer.accept(event(FIRST, 1, AgentRunEvent.Type.STEP_STARTED));
        anonymousOnly(sink.drain().getFirst());
        sink.close();
    }

    @Test void noopAndNonNullConstructorAreSafeDefaults() {
        assertThatThrownBy(() -> new AnonymousRuntimeObserver(null)).isInstanceOf(NullPointerException.class);
        var observer = AnonymousRuntimeObserver.noop();
        Consumer<AgentRunEvent> compatible = observer;
        compatible.accept(event(FIRST, 1, AgentRunEvent.Type.RUN_STARTED));
        assertThat(observer.status().acceptedCount()).isZero();
        assertThat(observer.status().dropCount()).isEqualTo(1);
        assertThat(observer.status().failureCount()).isZero();
    }

    @Test void boundedFullDisabledFailedAndNullReceiptsRemainExplicit() {
        var buffer = new BoundedObservationBuffer(1);
        var observer = new AnonymousRuntimeObserver(buffer);
        observer.accept(event(FIRST, 1, AgentRunEvent.Type.RUN_STARTED));
        observer.accept(event(FIRST, 2, AgentRunEvent.Type.RUN_TERMINATED));
        assertThat(buffer.dropCount()).isEqualTo(1);
        assertThat(buffer.drain()).extracting(ObservationEventV1::kind)
                .containsExactly(ObservationEventV1.Kind.RUN_STARTED);
        buffer.close();
        observer.accept(event(SECOND, 1, AgentRunEvent.Type.RUN_STARTED));
        assertThat(observer.status()).isEqualTo(new AnonymousRuntimeObserver.Status(1, 2, 0));
        for (ObservationSink sink : List.<ObservationSink>of(value -> FAILED, value -> null)) {
            var failed = new AnonymousRuntimeObserver(sink);
            failed.accept(event(FIRST, 1, AgentRunEvent.Type.RUN_TERMINATED));
            assertThat(failed.status()).isEqualTo(new AnonymousRuntimeObserver.Status(0, 0, 1));
        }
    }

    @Test void projectorAndSinkExceptionsAreIsolatedWithoutRetainingThrowable() {
        var calls = new AtomicInteger();
        var observer = new AnonymousRuntimeObserver(value -> {
            calls.incrementAndGet(); throw new IllegalStateException(PRIVATE, new RuntimeException(HASH));
        });
        assertThatCode(() -> observer.accept(null)).doesNotThrowAnyException();
        assertThat(calls.get()).isZero();
        var source = event(FIRST, 1, AgentRunEvent.Type.RUN_TERMINATED);
        assertThatCode(() -> observer.accept(source)).doesNotThrowAnyException();
        assertThat(calls.get()).isEqualTo(1);
        assertThat(observer.status()).isEqualTo(new AnonymousRuntimeObserver.Status(0, 0, 2));
        assertThat(source.runSnapshot().state()).isEqualTo(AgentRunState.CANCELLED);
        assertThat(observer.status().toString().contains(PRIVATE)).isFalse();
    }

    @Test void onlyFixedAggregateCountersAndSinkAreRetainedAndCountersSaturate() throws Exception {
        var observer = new AnonymousRuntimeObserver(value -> ACCEPTED);
        int counters = 0;
        for (var field : AnonymousRuntimeObserver.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            assertThat(field.getType() == ObservationSink.class || field.getType() == AtomicLong.class).isTrue();
            if (field.getType() == AtomicLong.class) {
                counters++;
                field.setAccessible(true);
                ((AtomicLong) field.get(observer)).set(Long.MAX_VALUE);
            }
        }
        assertThat(counters).isEqualTo(3);
        observer.accept(event(FIRST, 1, AgentRunEvent.Type.RUN_STARTED));
        observer.accept(null);
        assertThat(observer.status()).isEqualTo(new AnonymousRuntimeObserver.Status(Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE));
    }

    @Test void perRunSessionsRemainIsolatedAndReleaseTheirSink() throws Exception {
        var one = new BoundedObservationBuffer(); var two = new BoundedObservationBuffer();
        try (var first = new RunObservationSession(FIRST, one); var second = new RunObservationSession(SECOND, two)) {
            first.accept(event(FIRST, 1, AgentRunEvent.Type.RUN_STARTED));
            second.accept(event(FIRST, 1, AgentRunEvent.Type.RUN_STARTED));
            assertThat(two.drain()).isEmpty();
            assertThat(second.status().incomplete()).isTrue();
            second.accept(event(SECOND, 1, AgentRunEvent.Type.RUN_STARTED));
            first.accept(event(FIRST, 3, AgentRunEvent.Type.STOP_REQUESTED));
            first.accept(event(FIRST, 3, AgentRunEvent.Type.STOP_REQUESTED));
            assertThat(first.status().terminatedObserved()).isFalse();
            first.accept(event(FIRST, 4, AgentRunEvent.Type.RUN_TERMINATED));
            assertThat(first.status().duplicateCount()).isEqualTo(1);
            assertThat(first.status().incomplete()).isTrue();
            assertThat(first.status().closed()).isTrue();
            assertThat(second.status().terminatedObserved()).isFalse();
            second.accept(event(SECOND, 2, AgentRunEvent.Type.RUN_TERMINATED));
            assertThat(second.status().closed()).isTrue();
            var sinkField = RunObservationSession.class.getDeclaredField("sink"); sinkField.setAccessible(true);
            assertThat(sinkField.get(first)).isNull(); assertThat(sinkField.get(second)).isNull();
            for (var output : one.drain()) anonymousOnly(output);
            for (var output : two.drain()) anonymousOnly(output);
        } finally { one.close(); two.close(); }
    }

    @Test void oneConsumerAcceptsTwoActualSyntheticContextsWithoutRunState() throws Exception {
        var owner = AgentRunObservationDispatcher.createOwned();
        var outputs = new CopyOnWriteArrayList<ObservationEventV1>();
        var observer = new AnonymousRuntimeObserver(value -> { outputs.add(value); return ACCEPTED; });
        try {
            var first = AgentRunContext.create(FIRST, PRIVATE, observer, owner);
            var second = AgentRunContext.create(SECOND, PRIVATE + HASH, observer, owner);
            first.start(); second.start();
            first.requestStop(AgentStopReason.USER_REQUEST);
            finish(first, AgentRunState.CANCELLED);
            finish(second, AgentRunState.SUCCEEDED);
            await(() -> first.observationStatus().phase() == AgentRunObservationDispatcher.Phase.DRAINED
                    && second.observationStatus().phase() == AgentRunObservationDispatcher.Phase.DRAINED);
            assertThat(outputs).hasSize(5);
            assertThat(outputs.stream().filter(value -> value.kind() == ObservationEventV1.Kind.RUN_STARTED).count()).isEqualTo(2);
            assertThat(outputs.stream().filter(value -> value.kind() == ObservationEventV1.Kind.RUN_TERMINATED).count()).isEqualTo(2);
            for (var output : outputs) anonymousOnly(output);
            assertThat(observer.status().acceptedCount()).isEqualTo(5);
        } finally { closeAndVerify(owner); }
    }

    @Test void throwingRawObserverDoesNotChangeSyntheticFailureOrCleanup() throws Exception {
        var owner = AgentRunObservationDispatcher.createOwned(); var cleaned = new AtomicInteger();
        try {
            var context = AgentRunContext.create(FIRST, PRIVATE, value -> { throw new IllegalStateException(PRIVATE); }, owner);
            context.start(); context.finish(AgentRunState.FAILED);
            context.cleanupOnce(cleaned::incrementAndGet); context.signalTermination(); context.publishTermination();
            await(() -> context.observationStatus().phase() == AgentRunObservationDispatcher.Phase.DRAINED);
            assertThat(context.observationStatus().exceptionCount()).isGreaterThanOrEqualTo(1);
            assertThat(context.termination().join()).isEqualTo(AgentRunState.FAILED);
            assertThat(cleaned.get()).isEqualTo(1);
        } finally { closeAndVerify(owner); }
    }

    @Test void timeoutDetachAndRunTerminationDoNotProvePhysicalCallbackExit() throws Exception {
        var owner = AgentRunObservationDispatcher.createOwned();
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1); var returned = new CountDownLatch(1);
        var callbackThread = new AtomicReference<Thread>(); var cleaned = new AtomicInteger();
        var observer = new AnonymousRuntimeObserver(value -> {
            callbackThread.set(Thread.currentThread()); entered.countDown();
            try { release.await(); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            finally { returned.countDown(); }
            return ACCEPTED;
        });
        var context = AgentRunContext.create(FIRST, PRIVATE, observer, owner);
        try {
            context.start(); assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            context.requestStop(AgentStopReason.USER_REQUEST);
            assertThat(context.state()).isEqualTo(AgentRunState.STOP_REQUESTED);
            assertThat(context.snapshot().terminated()).isFalse(); assertThat(context.termination().isDone()).isFalse();
            context.finish(AgentRunState.CANCELLED); context.cleanupOnce(cleaned::incrementAndGet);
            context.signalTermination(); context.publishTermination();
            await(() -> context.observationStatus().detachReason() == AgentRunObservationDispatcher.DetachReason.CALLBACK_TIMEOUT);
            assertThat(context.termination().join()).isEqualTo(AgentRunState.CANCELLED);
            assertThat(cleaned.get()).isEqualTo(1);
            assertThat(context.observationStatus().dropCount()).isGreaterThanOrEqualTo(1);
            owner.close(Duration.ZERO);
            assertThat(owner.status().phase()).isEqualTo(AgentRunObservationDispatcher.OwnerPhase.CLOSED);
            assertThat(returned.getCount()).isEqualTo(1);
            assertThat(callbackThread.get().isAlive()).isTrue();
            assertThat(owner.status().liveThreadCount()).isGreaterThan(0);
            assertThat(observer.status().acceptedCount()).isZero();
        } finally {
            release.countDown();
            try {
                assertThat(returned.await(2, TimeUnit.SECONDS)).isTrue();
                var thread = callbackThread.get();
                if (thread != null) { thread.join(2000); assertThat(thread.isAlive()).isFalse(); }
            } finally { closeAndVerify(owner); }
        }
    }

    private static void finish(AgentRunContext context, AgentRunState state) {
        context.finish(state); context.cleanupOnce(() -> {}); context.signalTermination(); context.publishTermination();
    }

    private static void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(1);
        assertThat(condition.getAsBoolean()).as("bounded synthetic condition").isTrue();
    }

    private static void closeAndVerify(AgentRunObservationDispatcher owner) throws InterruptedException {
        owner.close(Duration.ofSeconds(1));
        await(() -> owner.status().liveThreadCount() == 0);
        assertThat(owner.status().registeredCount()).isZero();
        assertThat(owner.status().ticketCount()).isZero();
        assertThat(owner.status().inFlightCount()).isZero();
    }
}
