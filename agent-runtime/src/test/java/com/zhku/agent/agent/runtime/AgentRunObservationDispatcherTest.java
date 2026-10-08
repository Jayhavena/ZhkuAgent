package com.zhku.agent.agent.runtime;

import com.zhku.agent.agent.runtime.contract.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.*;

class AgentRunObservationDispatcherTest {
    AgentRunObservationDispatcher owner;
    final AtomicInteger observerInterrupts = new AtomicInteger();
    final CountDownLatch release = new CountDownLatch(1);

    @BeforeEach void open() { owner = AgentRunObservationDispatcher.createOwned(); }
    @AfterEach void close() throws Exception {
        release.countDown();
        owner.close();
        await(() -> owner.status().liveThreadCount() == 0, 2000);
    }

    static void await(BooleanSupplier condition, long milliseconds) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(milliseconds);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(1);
        assertThat(condition.getAsBoolean()).as("固定预算 %dms", milliseconds).isTrue();
    }

    void occupyWorkers() throws Exception { occupyWorkers(4); }
    void occupyWorkers(int count) throws Exception {
        var entered = new CountDownLatch(count);
        for (int i = 0; i < count; i++) AgentRunContext.create("占用", e -> {
            entered.countDown();
            while (release.getCount() != 0) try { release.await(); }
            catch (InterruptedException ignored) { observerInterrupts.incrementAndGet(); }
        }, owner).start();
        assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
    }

    AgentRunEvent step(UUID id, long sequence) {
        return new AgentRunEvent(id, sequence, Instant.EPOCH, AgentRunEvent.Type.STEP_STARTED,
                1, null, null, null);
    }

    AgentRunEvent tool(UUID id, long sequence, int argumentLength, int resultLength) {
        var call = new AgentToolCall(id, "c", null, 1, "t", "x".repeat(argumentLength),
                new AgentToolCall.Result(AgentToolCall.Status.SUCCEEDED, "r".repeat(resultLength),
                        AgentToolCall.PayloadFormat.TEXT, null));
        return new AgentRunEvent(id, sequence, Instant.EPOCH, AgentRunEvent.Type.TOOL_CALL_RESULT,
                1, call, null, null);
    }

    @Test void ordinaryQueueDropsOldestAndFinalHasSeparateSlot() throws Exception {
        occupyWorkers();
        UUID id = UUID.randomUUID();
        var events = new CopyOnWriteArrayList<AgentRunEvent>();
        var subscription = owner.subscribe(id, events::add);
        for (int i = 1; i <= 33; i++) owner.offer(subscription, step(id, i));
        var snapshot = new AgentRunSnapshot(id, AgentRunState.SUCCEEDED, AgentStopReason.NONE,
                1, true, AgentRunSnapshot.EndReason.COMPLETED, null);
        owner.offer(subscription, new AgentRunEvent(id, 34, Instant.EPOCH,
                AgentRunEvent.Type.RUN_TERMINATED, null, null, snapshot, null));
        var status = owner.observationStatus(subscription);
        assertThat(status.normalPending()).isEqualTo(32);
        assertThat(status.finalPending()).isEqualTo(1);
        assertThat(status.dropCount()).isEqualTo(1);
        assertThat(status.firstDroppedSequence()).isEqualTo(1);
        assertThat(status.lastDropReason()).isEqualTo(AgentRunObservationDispatcher.DropReason.OVERFLOW);
        assertThat(owner.status().ticketCount()).isLessThanOrEqualTo(32);
        release.countDown();
        await(() -> owner.observationStatus(subscription).phase() == AgentRunObservationDispatcher.Phase.DRAINED, 1000);
        assertThat(events).extracting(AgentRunEvent::sequence)
                .containsExactlyElementsOf(java.util.stream.LongStream.rangeClosed(2, 34).boxed().toList());
    }

    @Test void payloadBudgetCountsCombinedFieldsWithoutChangingResult() throws Exception {
        occupyWorkers();
        UUID id = UUID.randomUUID();
        var events = new CopyOnWriteArrayList<AgentRunEvent>();
        var subscription = owner.subscribe(id, events::add);
        var accepted = tool(id, 1, 4000, 4190); // c+t+参数+结果=8192 UTF-16单元。
        var dropped = tool(id, 2, 4000, 4191);
        owner.offer(subscription, accepted);
        owner.offer(subscription, dropped);
        owner.offer(subscription, step(id, 3));
        assertThat(owner.observationStatus(subscription).normalPending()).isEqualTo(2);
        assertThat(owner.observationStatus(subscription).dropCount()).isEqualTo(1);
        assertThat(owner.observationStatus(subscription).lastDropReason())
                .isEqualTo(AgentRunObservationDispatcher.DropReason.OVERSIZED);
        assertThat(dropped.toolCall().result().payload()).hasSize(4191);
        release.countDown();
        await(() -> owner.observationStatus(subscription).lastReturnedSequence() == 3, 1000);
        assertThat(events).extracting(AgentRunEvent::sequence).containsExactly(1L, 3L);
        assertThat(events.getFirst()).isSameAs(accepted);
    }

    @Test void registrationIsLazyBoundedAndSaturationOnlyDetachesDiagnostics() throws Exception {
        for (int i = 0; i < 64; i++) {
            var unused = AgentRunContext.create("未准入", e -> { }, owner);
            assertThat(unused.observationStatus().phase()).isEqualTo(AgentRunObservationDispatcher.Phase.NEW);
        }
        assertThat(owner.status().registeredCount()).isZero();
        occupyWorkers();
        await(() -> owner.status().registeredCount() == 0, 425);
        for (int i = 0; i < 32; i++) AgentRunContext.create("排队", e -> { }, owner).start();
        var saturated = AgentRunContext.create("业务仍运行", e -> { }, owner);
        assertThat(saturated.start()).isTrue();
        assertThat(saturated.state()).isEqualTo(AgentRunState.RUNNING);
        assertThat(saturated.observationStatus().detachReason())
                .isEqualTo(AgentRunObservationDispatcher.DetachReason.SATURATED);
        assertThat(owner.status().registeredCount()).isEqualTo(32);
        assertThat(owner.status().ticketCount()).isEqualTo(32);
        release.countDown();
        await(() -> owner.status().inFlightCount() == 0 && owner.status().ticketCount() == 0, 1000);
        // 只消费普通事件不会假称运行结束；close回收这些无final的登记。
        owner.close();
        assertThat(owner.status().registeredCount()).isZero();
    }

    @Test void sequentialFinalsReleaseRegistryAndExceptionsDoNotBecomeBusinessFailures() throws Exception {
        for (int i = 0; i < 64; i++) {
            var context = AgentRunContext.create("正常", e -> {
                if (e.type() == AgentRunEvent.Type.RUN_STARTED) throw new IllegalStateException("SECRET");
            }, owner);
            context.start();
            context.finish(AgentRunState.SUCCEEDED);
            context.cleanupOnce(() -> { });
            context.signalTermination();
            context.publishTermination();
            await(() -> context.observationStatus().phase() == AgentRunObservationDispatcher.Phase.DRAINED, 1000);
            assertThat(context.observationStatus().exceptionCount()).isEqualTo(1);
            assertThat(context.observationStatus().normalReturnCount()).isEqualTo(1);
            assertThat(context.snapshot().state()).isEqualTo(AgentRunState.SUCCEEDED);
            assertThat(context.observationStatus().toString()).doesNotContain("SECRET");
        }
        assertThat(owner.status().registeredCount()).isZero();
        var error = AgentRunContext.create("Error隔离", e -> { throw new AssertionError("SECRET"); }, owner);
        error.start();
        error.finish(AgentRunState.INCOMPLETE, AgentRunSnapshot.EndReason.DOMAIN_DECISION, null);
        error.cleanupOnce(() -> { }); error.signalTermination(); error.publishTermination();
        await(() -> error.observationStatus().detachReason() == AgentRunObservationDispatcher.DetachReason.OBSERVER_ERROR, 1000);
        await(() -> !error.observationStatus().inFlight(), 1000);
        assertThat(error.snapshot().state()).isEqualTo(AgentRunState.INCOMPLETE);
    }

    @Test void finalDeadlineDetachesButDoesNotReleaseBlockedPhysicalSlots() throws Exception {
        occupyWorkers();
        var context = AgentRunContext.create("无法交付final", e -> { }, owner);
        context.start(); context.finish(AgentRunState.SUCCEEDED);
        context.cleanupOnce(() -> { }); context.signalTermination();
        long at = System.nanoTime();
        context.publishTermination();
        await(() -> context.observationStatus().phase() == AgentRunObservationDispatcher.Phase.DETACHED,
                Math.max(0, 1225 - TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - at)));
        assertThat(context.observationStatus().detachReason()).isEqualTo(AgentRunObservationDispatcher.DetachReason.FINAL_TIMEOUT);
        assertThat(context.observationStatus().normalPending()).isZero();
        assertThat(context.observationStatus().finalPending()).isZero();
        assertThat(context.snapshot().terminated()).isTrue();
        assertThat(owner.status().inFlightCount()).isEqualTo(4);
        assertThat(owner.status().liveThreadCount()).isEqualTo(5);
    }

    @Test void boundedCloseCannotCreateReplacementUntilRealThreadExit() throws Exception {
        occupyWorkers();
        long at = System.nanoTime();
        owner.close(Duration.ofSeconds(1));
        assertThat(System.nanoTime() - at).isLessThanOrEqualTo(1_200_000_000L);
        assertThat(owner.status().phase()).isEqualTo(AgentRunObservationDispatcher.OwnerPhase.CLOSED);
        assertThat(owner.status().inFlightCount()).isEqualTo(4);
        await(() -> owner.status().liveThreadCount() == 4, 425);
        assertThat(observerInterrupts.get()).isZero();
        assertThatThrownBy(AgentRunObservationDispatcher::createOwned).isInstanceOf(IllegalStateException.class);
        var late = AgentRunContext.create("关闭后", e -> { }, owner);
        late.start();
        assertThat(late.observationStatus().detachReason()).isEqualTo(AgentRunObservationDispatcher.DetachReason.OWNER_CLOSED);
        release.countDown();
        await(() -> owner.status().liveThreadCount() == 0, 2000);
        try (var replacement = AgentRunObservationDispatcher.createOwned()) {
            assertThat(replacement.status().liveThreadCount()).isEqualTo(5);
        }
    }

    @Test void callbackCanReenterAndSelfInterruptDoesNotLeakToAnotherRun() throws Exception {
        occupyWorkers(3); // 唯一空闲worker，明确验证同一线程复用而非偶然换线程。
        var callbackThreads = new CopyOnWriteArrayList<Thread>();
        var contextRef = new AtomicReference<AgentRunContext>();
        var interruptFlags = new CopyOnWriteArrayList<Boolean>();
        var context = AgentRunContext.create("重入", e -> {
            callbackThreads.add(Thread.currentThread());
            interruptFlags.add(Thread.currentThread().isInterrupted());
            contextRef.get().snapshot();
            if (e.type() == AgentRunEvent.Type.RUN_STARTED) {
                contextRef.get().requestStop(AgentStopReason.USER_REQUEST);
                Thread.currentThread().interrupt();
            }
        }, owner);
        contextRef.set(context); context.start();
        await(() -> context.observationStatus().lastReturnedSequence() == 2, 1000);
        assertThat(interruptFlags).containsExactly(false, false);
        assertThat(context.stopReason()).isEqualTo(AgentStopReason.USER_REQUEST);
        var other = AgentRunContext.create("另一Run", e -> {
            callbackThreads.add(Thread.currentThread());
            assertThat(Thread.currentThread().isInterrupted()).isFalse();
        }, owner);
        other.start();
        await(() -> other.observationStatus().lastReturnedSequence() == 1, 1000);
        assertThat(other.observationStatus().exceptionCount()).isZero();
        assertThat(callbackThreads).hasSize(3).allMatch(t -> t == callbackThreads.getFirst());
        assertThat(observerInterrupts.get()).isZero();
    }

    @Test void closeOfferAndRepeatedCloseRacePreserveFactsAndFiniteResources() throws Exception {
        var entered = new CountDownLatch(1); var gate = new CountDownLatch(1);
        var failure = new AtomicReference<Throwable>();
        var context = AgentRunContext.create("关闭竞争", e -> {
            entered.countDown(); AgentRunObservationIsolationTest.awaitIgnoringInterrupt(release);
        }, owner);
        context.start(); context.advanceStep();
        assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
        var writer = new Thread(() -> {
            AgentRunObservationIsolationTest.awaitIgnoringInterrupt(gate);
            try {
                for (int i = 0; i < 40; i++) context.proposeToolCall("id", "tool", "{}");
                context.finish(AgentRunState.SUCCEEDED); context.cleanupOnce(() -> { });
                context.signalTermination(); context.publishTermination();
            } catch (Throwable error) { failure.set(error); }
        });
        var closer = new Thread(() -> { AgentRunObservationIsolationTest.awaitIgnoringInterrupt(gate); owner.close(); });
        writer.start(); closer.start(); long at = System.nanoTime(); gate.countDown();
        owner.close(Duration.ZERO);
        writer.join(2000); closer.join(Math.max(1, 1200 - TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - at)));
        assertThat(System.nanoTime() - at).isLessThanOrEqualTo(1_200_000_000L);
        assertThat(writer.isAlive() || closer.isAlive()).isFalse();
        assertThat(failure.get()).isNull();
        assertThat(context.snapshot().state()).isEqualTo(AgentRunState.SUCCEEDED);
        assertThat(context.snapshot().terminated()).isTrue();
        assertThat(owner.status().registeredCount()).isZero();
        assertThat(owner.status().ticketCount()).isZero();
        assertThat(owner.status().inFlightCount()).isEqualTo(1);
        assertThat(context.observationStatus().dropCount()).isLessThanOrEqualTo(42);
        assertThat(observerInterrupts.get()).isZero();
    }

    @Test void interruptedClosingCallerDoesNotAbandonOriginalDrainDeadline() throws Exception {
        occupyWorkers();
        var returned = new CountDownLatch(1); var restored = new AtomicBoolean();
        long at = System.nanoTime();
        var closer = new Thread(() -> {
            owner.close(); restored.set(Thread.currentThread().isInterrupted()); returned.countDown();
        });
        closer.start();
        try {
            await(() -> owner.status().phase() == AgentRunObservationDispatcher.OwnerPhase.DRAINING, 2000);
            closer.interrupt(); assertThat(returned.await(200, TimeUnit.MILLISECONDS)).isTrue();
            assertThat(restored.get()).isTrue();
            await(() -> owner.status().phase() == AgentRunObservationDispatcher.OwnerPhase.CLOSED,
                    Math.max(0, 1225 - TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - at)));
            assertThat(owner.status().inFlightCount()).isEqualTo(4);
            assertThat(observerInterrupts.get()).isZero();
        } finally { closer.join(2000); assertThat(closer.isAlive()).isFalse(); }
    }

    @Test void concurrentOwnedFactoriesGrantOnlyOnePhysicalQuota() throws Exception {
        owner.close(); await(() -> owner.status().liveThreadCount() == 0, 2000);
        var gate = new CountDownLatch(1); var done = new CountDownLatch(2);
        var winner = new AtomicReference<AgentRunObservationDispatcher>();
        var rejected = new AtomicInteger(); var failure = new AtomicReference<Throwable>();
        var creators = new ArrayList<Thread>();
        for (int i = 0; i < 2; i++) {
            var thread = new Thread(() -> {
                AgentRunObservationIsolationTest.awaitIgnoringInterrupt(gate);
                try {
                    var value = AgentRunObservationDispatcher.createOwned();
                    if (!winner.compareAndSet(null, value)) { value.close(); failure.set(new AssertionError("双重额度")); }
                } catch (IllegalStateException expected) { rejected.incrementAndGet(); }
                catch (Throwable error) { failure.set(error); }
                finally { done.countDown(); }
            }); creators.add(thread); thread.start();
        }
        gate.countDown();
        try { assertThat(done.await(2, TimeUnit.SECONDS)).isTrue(); }
        finally {
            for (var thread : creators) { thread.join(2000); assertThat(thread.isAlive()).isFalse(); }
            if (winner.get() != null) owner = winner.get();
        }
        assertThat(failure.get()).isNull(); assertThat(rejected.get()).isEqualTo(1);
        assertThat(owner.status().initializationComplete()).isTrue();
        assertThat(owner.status().liveThreadCount()).isEqualTo(5);
    }

    @Test void defaultOwnerIsLazyUniqueAndShutdownHookActuallyCloses(@TempDir Path directory) throws Exception {
        for (String mode : List.of("NONE", "DEFAULT")) {
            Path witness = directory.resolve(mode + ".txt");
            var child = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java.exe").toString(),
                    "-cp", System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")),
                    AgentRunDefaultObservationOwnerProbe.class.getName(), mode, witness.toString())
                    .redirectErrorStream(true).redirectOutput(directory.resolve(mode + ".log").toFile()).start();
            try {
                assertThat(child.waitFor(10, TimeUnit.SECONDS)).isTrue();
                assertThat(child.exitValue()).isZero();
                assertThat(Files.readString(witness)).isEqualTo(mode.equals("NONE") ? "DISABLED/0" : "CLOSED/0/UNIQUE");
            } finally {
                if (child.isAlive()) { child.destroyForcibly(); assertThat(child.waitFor(2, TimeUnit.SECONDS)).isTrue(); }
            }
        }
    }
}
