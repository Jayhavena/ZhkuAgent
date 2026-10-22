package com.zhku.knowledge.observability;

import com.zhku.agent.agent.runtime.*;
import com.zhku.agent.agent.runtime.contract.*;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.assertj.core.api.Assertions.*;
import static com.zhku.knowledge.observability.ObservationProjectionTest.*;
import static com.zhku.knowledge.observability.ObservationSink.Delivery.*;

class ObservationDeliveryTest {
    ObservationEventV1 metadata() { return ObservationProjector.anonymous(step(RUN, 1)); }

    @Test void noopDisablesDeliveryWithoutRetainingEvent() {
        assertThat(ObservationSink.noop().offer(metadata())).isEqualTo(DISABLED);
        var session = new RunObservationSession(RUN, ObservationSink.noop());
        session.accept(step(RUN, 1)); session.close();
        assertThat(session.status().producedCount()).isEqualTo(1);
        assertThat(session.status().incomplete()).isTrue();
    }

    @Test void bufferRejectsThirtyThirdAndDrainsIndependentCopy() {
        var buffer = new BoundedObservationBuffer();
        for (int i = 0; i < 32; i++) assertThat(buffer.offer(metadata())).isEqualTo(ACCEPTED);
        assertThat(buffer.offer(metadata())).isEqualTo(FULL);
        assertThat(buffer.dropCount()).isEqualTo(1);
        var copy = buffer.drain(); assertThat(copy).hasSize(32);
        assertThat(buffer.drain()).isEmpty();
        buffer.offer(metadata()); buffer.close();
        assertThat(buffer.drain()).isEmpty(); assertThat(copy).hasSize(32);
        assertThat(buffer.offer(metadata())).isEqualTo(DISABLED);
        assertThatThrownBy(() -> new BoundedObservationBuffer(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BoundedObservationBuffer(33)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void concurrentOffersFinishAndRespectCapacity() throws Exception {
        var buffer = new BoundedObservationBuffer(1);
        var gate = new CountDownLatch(1); var failures = new AtomicReference<Throwable>();
        var accepted = new AtomicInteger(); var threads = new ArrayList<Thread>();
        for (int i = 0; i < 8; i++) {
            var thread = new Thread(() -> {
                try { gate.await(); for (int n = 0; n < 100; n++) if (buffer.offer(metadata()) == ACCEPTED) accepted.incrementAndGet(); }
                catch (Throwable failure) { failures.set(failure); }
            }); threads.add(thread); thread.start();
        }
        try { gate.countDown(); for (var thread : threads) thread.join(2000); }
        finally { gate.countDown(); for (var thread : threads) { thread.join(2000); assertThat(thread.isAlive()).isFalse(); } buffer.close(); }
        assertThat(failures.get()).isNull(); assertThat(accepted.get()).isEqualTo(1);
        assertThat(buffer.dropCount()).isEqualTo(799);
    }

    @Test void contendedOfferReturnsBeforeLockIsReleased() throws Exception {
        var buffer = new BoundedObservationBuffer();
        var field = BoundedObservationBuffer.class.getDeclaredField("lock"); field.setAccessible(true);
        var lock = (java.util.concurrent.locks.ReentrantLock) field.get(buffer);
        var done = new CountDownLatch(1); var delivery = new AtomicReference<ObservationSink.Delivery>();
        var thread = new Thread(() -> { try { delivery.set(buffer.offer(metadata())); } finally { done.countDown(); } });
        lock.lock();
        try {
            thread.start();
            assertThat(done.await(500, TimeUnit.MILLISECONDS)).as("锁未释放时 offer 必须返回").isTrue();
            assertThat(delivery.get()).isEqualTo(FULL); assertThat(buffer.dropCount()).isEqualTo(1);
        } finally { lock.unlock(); thread.join(2000); assertThat(thread.isAlive()).isFalse(); buffer.close(); }
    }

    @Test void sinkFailureNeverRecursesOrChangesSourceFacts() {
        var calls = new AtomicInteger();
        var session = new RunObservationSession(RUN, event -> { calls.incrementAndGet(); throw new IllegalStateException(RAW); });
        var source = terminal(RUN, 1, AgentRunState.CANCELLED);
        session.accept(source);
        assertThat(calls.get()).isEqualTo(1); assertThat(session.status().deliveryFailureCount()).isEqualTo(1);
        assertThat(session.status().incomplete()).isTrue(); assertThat(session.status().terminatedObserved()).isTrue();
        assertThat(source.runSnapshot().state()).isEqualTo(AgentRunState.CANCELLED);
        assertThat(source.runSnapshot().terminated()).isTrue();
        assertThat(session.status().toString()).doesNotContain(RAW);
    }

    @Test void fullAndFailedDeliveryAreExplicitDegradation() {
        var buffer = new BoundedObservationBuffer(1); var buffered = new RunObservationSession(RUN, buffer);
        buffered.accept(step(RUN, 1)); buffered.accept(terminal(RUN, 2, AgentRunState.SUCCEEDED));
        assertThat(buffer.dropCount()).isEqualTo(1); assertThat(buffered.status().dropCount()).isEqualTo(1);
        assertThat(buffered.status().incomplete()).isTrue(); assertThat(buffered.status().terminatedObserved()).isTrue();
        assertThat(buffer.drain()).extracting(ObservationEventV1::kind).containsExactly(ObservationEventV1.Kind.STEP_STARTED);
        for (var delivery : List.of(FULL, FAILED, DISABLED)) {
            var session = new RunObservationSession(RUN, event -> delivery);
            session.accept(terminal(RUN, 1, AgentRunState.SUCCEEDED)); session.close();
            assertThat(session.status().incomplete()).isTrue();
            assertThat(session.status().dropCount()).isEqualTo(delivery == FAILED ? 0 : 1);
            assertThat(session.status().deliveryFailureCount()).isEqualTo(delivery == FAILED ? 1 : 0);
        }
    }

    @Test void gapsDuplicatesCrossRunAndRepeatedTerminalCannotManufactureCompletion() {
        var buffer = new BoundedObservationBuffer(); var session = new RunObservationSession(RUN, buffer);
        session.accept(step(RUN, 2)); session.accept(step(RUN, 2)); session.accept(step(RUN, 1));
        session.accept(step(UUID.fromString("00000000-0000-4000-8000-000000000102"), 3));
        session.accept(terminal(RUN, 4, AgentRunState.SUCCEEDED));
        session.accept(terminal(RUN, 5, AgentRunState.SUCCEEDED));
        session.close();
        assertThat(session.status().producedCount()).isEqualTo(2);
        assertThat(session.status().duplicateCount()).isEqualTo(3);
        assertThat(session.status().incomplete()).isTrue(); assertThat(session.status().terminatedObserved()).isTrue();
        assertThat(buffer.drain()).extracting(ObservationEventV1::kind)
                .containsExactly(ObservationEventV1.Kind.STEP_STARTED, ObservationEventV1.Kind.RUN_TERMINATED);
    }

    @Test void stopIsNotTerminationAndCloseReleasesSink() throws Exception {
        var buffer = new BoundedObservationBuffer(); var session = new RunObservationSession(RUN, buffer);
        session.accept(source(AgentRunEvent.Type.RUN_STARTED));
        var stop = source(AgentRunEvent.Type.STOP_REQUESTED);
        session.accept(new AgentRunEvent(RUN, 2, stop.occurredAt(), stop.type(), null, null, stop.runSnapshot(), null));
        assertThat(session.status().terminatedObserved()).isFalse(); session.close();
        assertThat(session.status().closed()).isTrue(); assertThat(session.status().incomplete()).isTrue();
        session.accept(terminal(RUN, 3, AgentRunState.CANCELLED));
        assertThat(buffer.drain()).hasSize(2);
        for (Field field : RunObservationSession.class.getDeclaredFields()) {
            if (ObservationSink.class.isAssignableFrom(field.getType())) { field.setAccessible(true); assertThat(field.get(session)).isNull(); }
            assertThat(field.getType()).isNotEqualTo(AgentRunEvent.class);
            assertThat(Collection.class.isAssignableFrom(field.getType())).isFalse();
        }
    }

    @Test void consecutiveActualTerminalClosesCompleteAndReleasesSink() {
        var buffer = new BoundedObservationBuffer(); var session = new RunObservationSession(RUN, buffer);
        session.accept(source(AgentRunEvent.Type.RUN_STARTED));
        session.accept(terminal(RUN, 2, AgentRunState.SUCCEEDED));
        assertThat(session.status().closed()).isTrue(); assertThat(session.status().incomplete()).isFalse();
        session.accept(step(RUN, 3)); session.close();
        assertThat(buffer.drain()).hasSize(2);
    }

    @Test void dispatcherIsolatesBlockedSinkAndWorkersPhysicallyExit() throws Exception {
        var owner = AgentRunObservationDispatcher.createOwned();
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var returned = new CountDownLatch(1); var sessionRef = new AtomicReference<RunObservationSession>();
        var context = AgentRunContext.create("DEVELOPMENT_SYNTHETIC", event -> sessionRef.get().accept(event), owner);
        var session = new RunObservationSession(context.runId(), event -> {
            entered.countDown();
            try { release.await(); } catch (InterruptedException error) { Thread.currentThread().interrupt(); }
            finally { returned.countDown(); }
            return ACCEPTED;
        }); sessionRef.set(session);
        try {
            context.start(); assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            assertThatCode(() -> { context.requestStop(AgentStopReason.USER_REQUEST); context.finish(AgentRunState.CANCELLED);
                context.cleanupOnce(() -> {}); context.signalTermination(); context.publishTermination(); }).doesNotThrowAnyException();
            assertThat(context.snapshot().terminated()).isTrue();
            assertThat(context.snapshot().state()).isEqualTo(AgentRunState.CANCELLED);
            assertThat(returned.getCount()).isEqualTo(1);
        } finally {
            release.countDown(); assertThat(returned.await(2, TimeUnit.SECONDS)).isTrue();
            owner.close(Duration.ofSeconds(1));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (owner.status().liveThreadCount() != 0 && System.nanoTime() < deadline) Thread.sleep(1);
            assertThat(owner.status().liveThreadCount()).isZero(); session.close();
        }
    }
}
