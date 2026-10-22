package com.zhku.knowledge.ruleagent;

import com.zhku.agent.agent.runtime.*;
import com.zhku.agent.agent.runtime.contract.AgentRunEvent;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import org.junit.jupiter.api.*;
import static com.zhku.knowledge.ruleagent.RuleAgentContract.*;
import static org.assertj.core.api.Assertions.*;

/** 仅使用现有合成工厂；不装配HTTP、供应商或数据库。 */
@Timeout(15)
class RuleAgentTaskObserverContractTest {
    final RuleAgentTaskStoreTest fixture = new RuleAgentTaskStoreTest();
    final List<AgentRunContext> contexts = new CopyOnWriteArrayList<>();
    final List<AgentRunEvent> events = new CopyOnWriteArrayList<>();
    final AtomicReference<Thread> businessThread = new AtomicReference<>();
    final AtomicReference<Thread> observerThread = new AtomicReference<>();
    AgentRunObservationDispatcher dispatcher;

    @AfterEach void closeOwnedDispatcher() throws Exception {
        if (dispatcher != null) {
            dispatcher.close(Duration.ofSeconds(1));
            await(() -> dispatcher.status().liveThreadCount() == 0);
            assertThat(dispatcher.status().phase()).isEqualTo(AgentRunObservationDispatcher.OwnerPhase.CLOSED);
            assertThat(dispatcher.status().registeredCount()).isZero();
            assertThat(dispatcher.status().inFlightCount()).isZero();
        }
    }

    RuleAgentTaskStore.Factory factory() {
        return (owner, task) -> {
            var delegate = fixture.work(owner, task);
            return new RuleAgentTaskStore.Work() {
                public RuleAgentRunner.Outcome run(AgentRunContext context, RuleAgentRunner.Input input) {
                    contexts.add(context);
                    businessThread.set(Thread.currentThread());
                    return delegate.run(context, input);
                }
                public boolean revalidate(ResultEnvelope previous) { return delegate.revalidate(previous); }
            };
        };
    }

    RuleAgentTaskStore observed(Consumer<AgentRunEvent> observer) {
        dispatcher = AgentRunObservationDispatcher.createOwned();
        return new RuleAgentTaskStore(fixture.queued::add, factory(), fixture.clock,
                RuleAgentTaskStore.DEFAULT_LIMITS, observer, dispatcher);
    }

    void record(AgentRunEvent event) {
        observerThread.set(Thread.currentThread());
        events.add(event);
    }

    RuleAgentTaskStore.Accepted create(RuleAgentTaskStore store) {
        return store.create(fixture.owner, new CreateTask("课程类别是什么？", UUID.randomUUID()));
    }

    List<AgentRunEvent> terminalEvents() {
        return events.stream().filter(e -> e.type() == AgentRunEvent.Type.RUN_TERMINATED).toList();
    }

    static void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(10);
        assertThat(condition.getAsBoolean()).isTrue();
    }

    static Set<Long> observationThreads() {
        var ids = new HashSet<Long>();
        Thread.getAllStackTraces().keySet().stream()
                .filter(t -> t.isAlive() && t.getName().startsWith("agent-observation-"))
                .forEach(t -> ids.add(t.threadId()));
        return ids;
    }

    @Test void defaultOffHasNoSubscriptionOrNewDispatcherThreads() {
        var before = observationThreads();
        try (var store = new RuleAgentTaskStore(fixture.queued::add, factory(), fixture.clock,
                RuleAgentTaskStore.DEFAULT_LIMITS)) {
            var accepted = create(store);
            fixture.drain();
            assertThat(store.snapshot(fixture.owner, accepted.taskId()).terminated()).isTrue();
            assertThat(contexts).hasSize(1);
            assertThat(contexts.getFirst().observationStatus().phase())
                    .isEqualTo(AgentRunObservationDispatcher.Phase.DISABLED);
            assertThat(observationThreads()).isEqualTo(before);
        }
    }

    @Test void optInEmitsActualStartedAndTerminatedForAcceptedRun() throws Exception {
        try (var store = observed(this::record)) {
            var accepted = create(store);
            assertThat(dispatcher.status().registeredCount()).isZero();
            fixture.drain();
            await(() -> terminalEvents().size() == 1);
            assertThat(events).allSatisfy(e -> assertThat(e.runId()).isEqualTo(accepted.runId()));
            assertThat(events.getFirst().type()).isEqualTo(AgentRunEvent.Type.RUN_STARTED);
            var terminal = terminalEvents().getFirst();
            assertThat(terminal.runSnapshot().terminated()).isTrue();
            assertThat(terminal.runSnapshot().state()).isEqualTo(store.snapshot(fixture.owner, accepted.taskId()).runState());
            assertThat(contexts.getFirst().termination()).isCompleted();
            assertThat(store.activeJobs()).isZero();
            assertThat(observerThread.get()).isNotSameAs(businessThread.get());
        }
        assertThat(dispatcher.status().phase()).isEqualTo(AgentRunObservationDispatcher.OwnerPhase.OPEN);
    }

    @Test void duplicateCreateDoesNotCreateOrSubscribeAnotherRun() throws Exception {
        try (var store = observed(this::record)) {
            var request = new CreateTask("课程类别是什么？", UUID.randomUUID());
            var first = store.create(fixture.owner, request);
            var duplicate = store.create(fixture.owner, request);
            assertThat(duplicate.duplicate()).isTrue();
            assertThat(duplicate.runId()).isEqualTo(first.runId());
            assertThat(fixture.queued).hasSize(1);
            fixture.drain();
            await(() -> contexts.getFirst().observationStatus().phase() == AgentRunObservationDispatcher.Phase.DRAINED);
            assertThat(contexts).hasSize(1);
            assertThat(terminalEvents()).hasSize(1);
            assertThat(events.stream().filter(e -> e.type() == AgentRunEvent.Type.RUN_STARTED)).hasSize(1);
        }
    }

    @Test void clarificationKeepsTaskButSeparatesRunsAndDuplicateMessage() throws Exception {
        try (var store = observed(this::record)) {
            var first = create(store);
            fixture.drain();
            await(() -> terminalEvents().size() == 1);
            var oldTerminal = terminalEvents().getFirst();
            var before = store.snapshot(fixture.owner, first.taskId());
            var message = fixture.reply(before, UUID.randomUUID());
            var second = store.message(fixture.owner, first.taskId(), message);
            assertThat(store.message(fixture.owner, first.taskId(), message).runId()).isEqualTo(second.runId());
            assertThat(second.taskId()).isEqualTo(first.taskId());
            assertThat(second.runId()).isNotEqualTo(first.runId());
            assertThat(fixture.queued).hasSize(1);
            fixture.drain();
            await(() -> terminalEvents().size() == 2);
            assertThat(contexts).hasSize(2);
            assertThat(contexts.get(1)).isNotSameAs(contexts.getFirst());
            assertThat(terminalEvents().getFirst()).isSameAs(oldTerminal);
            assertThat(terminalEvents()).extracting(AgentRunEvent::runId).containsExactly(first.runId(), second.runId());
            assertThat(store.snapshot(fixture.owner, first.taskId()).taskStatus()).isEqualTo(TaskStatus.RESOLVED);
            assertThat(store.traceHistory(fixture.owner, first.taskId())).hasSize(2);
        }
    }

    @Test void queuedCancelReceiptPrecedesActualTerminationAfterDrain() throws Exception {
        try (var store = observed(this::record)) {
            var accepted = create(store);
            var cancel = new Cancel(accepted.runId(), accepted.version(), UUID.randomUUID());
            store.cancel(fixture.owner, accepted.taskId(), cancel);
            assertThat(store.cancel(fixture.owner, accepted.taskId(), cancel).duplicate()).isTrue();
            await(() -> events.size() == 1);
            assertThat(events.getFirst().type()).isEqualTo(AgentRunEvent.Type.STOP_REQUESTED);
            assertThat(terminalEvents()).isEmpty();
            assertThat(store.snapshot(fixture.owner, accepted.taskId()).terminated()).isFalse();
            fixture.drain();
            await(() -> terminalEvents().size() == 1);
            assertThat(terminalEvents().getFirst().runId()).isEqualTo(accepted.runId());
            assertThat(terminalEvents().getFirst().runSnapshot().state()).isEqualTo(AgentRunState.CANCELLED);
            assertThat(store.snapshot(fixture.owner, accepted.taskId()).taskStatus()).isEqualTo(TaskStatus.STOPPED);
            assertThat(fixture.decisions).hasValue(0);
            assertThat(store.activeJobs()).isZero();
        }
    }

    @Test void throwingObserverCannotPreventBusinessCompletionOrCleanup() throws Exception {
        try (var store = observed(e -> { throw new IllegalStateException("synthetic observer failure"); })) {
            var accepted = create(store);
            fixture.drain();
            await(() -> contexts.getFirst().observationStatus().exceptionCount() >= 1);
            assertThat(contexts.getFirst().observationStatus().detachReason())
                    .isEqualTo(AgentRunObservationDispatcher.DetachReason.NONE);
            assertThat(contexts.getFirst().termination()).isCompleted();
            assertThat(store.snapshot(fixture.owner, accepted.taskId()).taskStatus()).isEqualTo(TaskStatus.WAITING_USER);
            assertThat(store.activeJobs()).isZero();
            assertThat(store.traceHistory(fixture.owner, accepted.taskId())).hasSize(1);
        }
    }

    @Test void blockingObserverCannotHoldBusinessOrStoreCloseAndPhysicalExitIsSeparate() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var worker = Executors.newSingleThreadExecutor();
        var store = observed(e -> {
            observerThread.set(Thread.currentThread());
            entered.countDown();
            try { if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("release missing"); }
            catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
        });
        try {
            var accepted = create(store);
            worker.submit(fixture::drain).get(3, TimeUnit.SECONDS);
            assertThat(entered.await(3, TimeUnit.SECONDS)).isTrue();
            assertThat(store.snapshot(fixture.owner, accepted.taskId()).terminated()).isTrue();
            assertThat(contexts.getFirst().termination()).isCompleted();
            assertThat(store.activeJobs()).isZero();
            assertThat(observerThread.get()).isNotSameAs(businessThread.get());
            await(() -> contexts.getFirst().observationStatus().detachReason()
                    == AgentRunObservationDispatcher.DetachReason.CALLBACK_TIMEOUT);
            worker.submit(store::close).get(3, TimeUnit.SECONDS);
            assertThat(dispatcher.status().phase()).isEqualTo(AgentRunObservationDispatcher.OwnerPhase.OPEN);
            assertThat(dispatcher.status().inFlightCount()).isEqualTo(1);
            assertThat(dispatcher.status().liveThreadCount()).isPositive();
        } finally {
            release.countDown();
            store.close();
            worker.shutdownNow();
            assertThat(worker.awaitTermination(3, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test void observerFailureCannotTurnMandatoryAuditFailureIntoSuccess() throws Exception {
        dispatcher = AgentRunObservationDispatcher.createOwned();
        var cleaned = new AtomicBoolean();
        var toolsFixture = new RuleAgentRunnerTest();
        var tools = toolsFixture.tools(true, false, false);
        RuleAgentTaskStore.Factory auditFailure = (owner, task) -> new RuleAgentTaskStore.Work() {
            public RuleAgentRunner.Outcome run(AgentRunContext context, RuleAgentRunner.Input input) {
                contexts.add(context);
                var port = new RuleAgentRunner.DecisionPort() {
                    public RuleAgentRunner.Reply decide(RuleAgentRunner.View view, Duration timeout) {
                        throw new RuleAgentProvider.Failure(ReasonCode.AUDIT_UNAVAILABLE, false);
                    }
                    public void close() { cleaned.set(true); }
                };
                return new RuleAgentRunner(port, new RuleActionBroker(tools), fixture.clock).run(context, input);
            }
            public boolean revalidate(ResultEnvelope previous) { return false; }
        };
        try (var store = new RuleAgentTaskStore(fixture.queued::add, auditFailure, fixture.clock,
                RuleAgentTaskStore.DEFAULT_LIMITS, e -> { throw new IllegalStateException("synthetic"); }, dispatcher)) {
            var accepted = create(store);
            fixture.drain();
            await(() -> contexts.getFirst().observationStatus().exceptionCount() >= 1);
            var snapshot = store.snapshot(fixture.owner, accepted.taskId());
            assertThat(snapshot.taskStatus()).isEqualTo(TaskStatus.FAILED);
            assertThat(snapshot.result().items().getFirst().issues()).extracting(Issue::reasonCode)
                    .containsExactly(ReasonCode.AUDIT_UNAVAILABLE);
            assertThat(snapshot.result().items().getFirst().claims()).isEmpty();
            assertThat(cleaned).isTrue();
            assertThat(contexts.getFirst().termination()).isCompleted();
            assertThat(store.activeJobs()).isZero();
        }
    }

    @Test void optInRequiresBothDependencies() {
        dispatcher = AgentRunObservationDispatcher.createOwned();
        assertThatNullPointerException().isThrownBy(() -> new RuleAgentTaskStore(fixture.queued::add,
                factory(), fixture.clock, RuleAgentTaskStore.DEFAULT_LIMITS, null, dispatcher));
        assertThatNullPointerException().isThrownBy(() -> new RuleAgentTaskStore(fixture.queued::add,
                factory(), fixture.clock, RuleAgentTaskStore.DEFAULT_LIMITS, this::record, null));
    }
}
