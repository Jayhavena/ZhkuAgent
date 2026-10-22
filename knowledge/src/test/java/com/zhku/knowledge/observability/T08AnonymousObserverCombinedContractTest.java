package com.zhku.knowledge.ruleagent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhku.agent.agent.runtime.AgentRunContext;
import com.zhku.agent.agent.runtime.AgentRunObservationDispatcher;
import com.zhku.agent.agent.runtime.contract.AgentRunEvent;
import com.zhku.agent.agent.runtime.contract.AgentToolCall;
import com.zhku.knowledge.observability.AnonymousRuntimeObserver;
import com.zhku.knowledge.observability.BoundedObservationBuffer;
import com.zhku.knowledge.observability.ObservationEventV1;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(15)
class T08AnonymousObserverCombinedContractTest {
    private static final String PRIVATE = "SYNTHETIC_PRIVATE_CONTENT_02C";
    private static final String HASH = "a1b2c3d4".repeat(8);

    private final RuleAgentTaskStoreTest fixture = new RuleAgentTaskStoreTest();
    private final List<AgentRunContext> contexts = new CopyOnWriteArrayList<>();
    private final List<ObservationEventV1> output = new CopyOnWriteArrayList<>();
    private AgentRunObservationDispatcher dispatcher;
    private BoundedObservationBuffer buffer;
    private AnonymousRuntimeObserver observer;

    @AfterEach
    void closeOwnedResources() throws Exception {
        try {
            if (dispatcher != null) {
                dispatcher.close(Duration.ofSeconds(1));
                await(() -> dispatcher.status().liveThreadCount() == 0);
                assertThat(dispatcher.status().phase())
                        .isEqualTo(AgentRunObservationDispatcher.OwnerPhase.CLOSED);
                assertThat(dispatcher.status().registeredCount()).isZero();
                assertThat(dispatcher.status().inFlightCount()).isZero();
                assertThat(dispatcher.status().ticketCount()).isZero();
            }
        } finally {
            if (buffer != null) buffer.close();
        }
    }

    private void setupBufferObserver() {
        buffer = new BoundedObservationBuffer(32);
        observer = new AnonymousRuntimeObserver(buffer);
        dispatcher = AgentRunObservationDispatcher.createOwned();
    }

    private RuleAgentTaskStore.Factory factory() {
        return (owner, task) -> {
            var delegate = fixture.work(owner, task);
            return new RuleAgentTaskStore.Work() {
                @Override
                public RuleAgentRunner.Outcome run(AgentRunContext context, RuleAgentRunner.Input input) {
                    contexts.add(context);
                    return delegate.run(context, input);
                }

                @Override
                public boolean revalidate(RuleAgentContract.ResultEnvelope previous) {
                    return delegate.revalidate(previous);
                }
            };
        };
    }

    private RuleAgentTaskStore store() {
        // 实际走 T08 六参数入口；dispatcher 归测试宿主管理，TaskStore 不得关闭它。
        return new RuleAgentTaskStore(fixture.queued::add, factory(), fixture.clock,
                RuleAgentTaskStore.DEFAULT_LIMITS, observer, dispatcher);
    }

    private static void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(5);
        assertThat(condition.getAsBoolean()).as("bounded synthetic condition").isTrue();
    }

    private void awaitAndCollect(int contextIndex) throws Exception {
        await(() -> contexts.size() > contextIndex
                && contexts.get(contextIndex).observationStatus().phase()
                == AgentRunObservationDispatcher.Phase.DRAINED);
        output.addAll(buffer.drain());
    }

    @Test
    void sixParameterTaskStoreSharesAnonymousProjectionAcrossIdempotentClarificationRuns() throws Exception {
        setupBufferObserver();
        UUID createKey = UUID.randomUUID();
        var request = new RuleAgentContract.CreateTask("课程类别是什么？", createKey);

        try (var store = store()) {
            var first = store.create(fixture.owner, request);
            var duplicateCreate = store.create(fixture.owner, request);
            assertThat(duplicateCreate.duplicate()).isTrue();
            assertThat(duplicateCreate.runId()).isEqualTo(first.runId());
            assertThat(fixture.queued).hasSize(1);

            fixture.drain();
            awaitAndCollect(0);
            var waiting = store.snapshot(fixture.owner, first.taskId());
            assertThat(waiting.taskStatus()).isEqualTo(RuleAgentContract.TaskStatus.WAITING_USER);

            UUID messageKey = UUID.randomUUID();
            var message = fixture.reply(waiting, messageKey);
            var second = store.message(fixture.owner, first.taskId(), message);
            var duplicateMessage = store.message(fixture.owner, first.taskId(), message);
            assertThat(duplicateMessage.duplicate()).isTrue();
            assertThat(duplicateMessage.runId()).isEqualTo(second.runId());
            assertThat(second.taskId()).isEqualTo(first.taskId());
            assertThat(second.runId()).isNotEqualTo(first.runId());
            assertThat(fixture.queued).hasSize(1);

            fixture.drain();
            awaitAndCollect(1);

            assertThat(store.snapshot(fixture.owner, first.taskId()).taskStatus())
                    .isEqualTo(RuleAgentContract.TaskStatus.RESOLVED);
            assertThat(store.traceHistory(fixture.owner, first.taskId())).hasSize(2);
            assertThat(output.stream().filter(e -> e.kind() == ObservationEventV1.Kind.RUN_STARTED)).hasSize(2);
            assertThat(output.stream().filter(e -> e.kind() == ObservationEventV1.Kind.RUN_TERMINATED)).hasSize(2);
            assertThat(observer.status().dropCount()).isZero();
            assertThat(observer.status().failureCount()).isZero();
            assertThat(observer.status().acceptedCount()).isEqualTo(output.size());
            assertThat(buffer.dropCount()).isZero();

            for (var event : output) {
                assertThat(event.schemaVersion()).isEqualTo(1);
                assertThat(event.inputTokens()).isNull();
                assertThat(event.outputTokens()).isNull();
                assertThat(event.elapsedMillis()).isNull();
                assertThat(event.estimatedCostMicros()).isNull();
            }
            String encoded = new ObjectMapper().writeValueAsString(output) + output;
            // 只输出布尔断言；如果脱敏回归失败，不让测试报告回显注入的标记或 ID。
            assertThat(List.of(PRIVATE, HASH, first.taskId().toString(), first.turnId().toString(),
                    first.runId().toString(), second.runId().toString(), createKey.toString(),
                    messageKey.toString(), "arguments", "sequence", "runId", "turnId", "taskId")
                    .stream().noneMatch(encoded::contains))
                    .as("integrated anonymous sink excludes private content and identifiers").isTrue();
        }
    }

    @Test
    void sinkFailureIsIsolatedFromT08TaskPublication() throws Exception {
        dispatcher = AgentRunObservationDispatcher.createOwned();
        observer = new AnonymousRuntimeObserver(event -> {
            throw new IllegalStateException(PRIVATE);
        });
        try (var store = store()) {
            var accepted = store.create(fixture.owner, new RuleAgentContract.CreateTask("课程类别是什么？", UUID.randomUUID()));
            fixture.drain();
            await(() -> contexts.size() == 1
                    && contexts.getFirst().observationStatus().phase()
                    == AgentRunObservationDispatcher.Phase.DRAINED);

            var snapshot = store.snapshot(fixture.owner, accepted.taskId());
            assertThat(snapshot.taskStatus()).isEqualTo(RuleAgentContract.TaskStatus.WAITING_USER);
            assertThat(store.traceHistory(fixture.owner, accepted.taskId())).hasSize(1);
            assertThat(store.activeJobs()).isZero();
            assertThat(observer.status().acceptedCount()).isZero();
            assertThat(observer.status().failureCount()).isPositive();
            assertThat(snapshot.runId()).isEqualTo(accepted.runId());
        }
    }

    @Test
    void sensitiveSyntheticRuntimeEventIsReducedToAnonymousV1() throws Exception {
        try (var localBuffer = new BoundedObservationBuffer(2)) {
            var adapter = new AnonymousRuntimeObserver(localBuffer);
            UUID runId = UUID.randomUUID();
            var rawCall = new AgentToolCall(runId, PRIVATE, HASH, 1, PRIVATE,
                    "{\"arguments\":\"" + PRIVATE + "\",\"evidenceId\":\"" + HASH + "\"}", null);
            var raw = new AgentRunEvent(runId, 1, Instant.EPOCH,
                    AgentRunEvent.Type.TOOL_CALL_PROPOSED, 1, rawCall, null, null);

            adapter.accept(raw);
            var projected = localBuffer.drain().getFirst();
            assertThat(projected.schemaVersion()).isEqualTo(1);
            assertThat(projected.inputTokens()).isNull();
            assertThat(projected.outputTokens()).isNull();
            assertThat(projected.elapsedMillis()).isNull();
            assertThat(projected.estimatedCostMicros()).isNull();
            String encoded = new ObjectMapper().writeValueAsString(projected) + projected;
            assertThat(List.of(PRIVATE, HASH, runId.toString(), "arguments", "evidenceId", "sequence", "runId")
                    .stream().noneMatch(encoded::contains))
                    .as("raw synthetic runtime event is reduced to fixed anonymous metadata").isTrue();
            assertThat(adapter.status().acceptedCount()).isEqualTo(1);
            assertThat(adapter.status().dropCount()).isZero();
            assertThat(adapter.status().failureCount()).isZero();
        }
    }
}
