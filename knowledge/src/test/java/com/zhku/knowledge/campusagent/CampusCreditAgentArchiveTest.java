package com.zhku.knowledge.campusagent;

import com.zhku.agent.agent.runtime.*;
import com.zhku.agent.agent.runtime.contract.AgentRunEvent;
import com.zhku.knowledge.campusagent.archive.CampusRunArchive;
import com.zhku.knowledge.campusagent.archive.CampusRunArchiveStore;
import com.zhku.knowledge.tools.*;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static com.zhku.knowledge.campusagent.archive.CampusRunArchive.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 存储替身验证宿主边界；不能代替 JDBC 或真正重启测试。 */
class CampusCreditAgentArchiveTest {
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

    private final CampusRunArchiveStore store = mock(CampusRunArchiveStore.class);
    private final AtomicBoolean accepting = new AtomicBoolean(true);
    private final AtomicInteger calls = new AtomicInteger();
    private CampusRunArchive.FinalFacts finalFacts;

    private void ready() {
        when(store.isAccepting()).thenAnswer(i -> accepting.get());
        when(store.admit(any()))
                .thenAnswer(i -> new Admission(i.getArgument(0), new OwnerToken(UUID.randomUUID(), 1), 0));
        when(store.finalizeRun(any(), any(), any())).thenAnswer(i -> {
            finalFacts = i.getArgument(1);
            return Finalization.CONFIRMED;
        });
        doAnswer(i -> {
            accepting.set(false);
            return null;
        }).when(store).stopAdmission();
    }

    private CampusCreditAgentService service(ChatModel model, java.util.function.Consumer<AgentRunEvent> observer) {
        return new CampusCreditAgentService(model, new CampusToolRegistry(List.of(new CourseCreditSummaryTool())),
                q -> {
                    throw new AssertionError("No policy lookup expected");
                }, 3, observer, observations);
    }

    private ChatResponse clarification() {
        calls.incrementAndGet();
        return new ChatResponse(List.of(new Generation(new AssistantMessage("补充课程"))));
    }

    private CampusCreditAgentService.Request request() {
        return new CampusCreditAgentService.Request("合成追问", List.of());
    }

    @Test
    void finalStoreExceptionDoesNotTurnClarificationIntoModelFailure() {
        ready();
        when(store.finalizeRun(any(), any(), any())).thenThrow(new IllegalStateException("SYNTHETIC_PRIVATE_TEXT"));
        try (var timer = Executors.newSingleThreadScheduledExecutor()) {
            var manager = new CampusCreditAgentManager(service(p -> clarification(), e -> {
            }), Runnable::run, timer, Duration.ofSeconds(30), store);
            var result = manager.start(request());
            assertThat(result.terminated()).isTrue();
            assertThat(result.state()).isEqualTo(AgentRunState.INCOMPLETE);
            assertThat(result.failureCategory()).isEqualTo("NONE");
            assertThat(calls.get()).isEqualTo(1);
            verify(store).stopAdmission();
        }
    }

    @Test
    void unconfirmedFinalsPublishActualResultAndPauseAdmission() {
        for (var outcome : List.of(Finalization.UNCONFIRMED, Finalization.OWNER_LOST)) {
            reset(store);
            ready();
            accepting.set(true);
            when(store.finalizeRun(any(), any(), any())).thenReturn(outcome);
            try (var timer = new ManualTimer()) {
                var manager = new CampusCreditAgentManager(service(p -> clarification(), e -> {
                }), Runnable::run, timer, Duration.ofSeconds(30), store);
                var result = manager.start(request());
                assertThat(result.terminated()).isTrue();
                assertThat(result.state()).isEqualTo(AgentRunState.INCOMPLETE);
                assertThat(result.failureCategory()).isEqualTo("NONE");
                verify(store).stopAdmission();
                assertThatThrownBy(() -> manager.start(request())).isInstanceOf(Failure.class);
            }
        }
    }

    @Test
    void normalAnswerRepeatedLookupPolicyHandoffAndModelToolErrorsKeepDistinctFacts() {
        for (var scenario : List.of("ANSWERED", "REPEATED_LOOKUP", "POLICY_HANDOFF", "MODEL_FAILURE", "UNKNOWN_TOOL",
                "TOOL_FAILURE")) {
            reset(store);
            ready();
            accepting.set(true);
            var count = new AtomicInteger();
            var payload = new AtomicReference<PayloadCandidate>();
            doAnswer(i -> {
                finalFacts = i.getArgument(1);
                payload.set(i.getArgument(2));
                return Finalization.CONFIRMED;
            }).when(store).finalizeRun(any(), any(), any());
            ChatModel model = p -> {
                if (scenario.equals("MODEL_FAILURE"))
                    throw new IllegalStateException("SYNTHETIC_PRIVATE_TEXT");
                String name = scenario.equals("UNKNOWN_TOOL") ? "not_allowed"
                        : count.incrementAndGet() == 1 || scenario.equals("REPEATED_LOOKUP")
                                ? "lookup_public_elective_credit_limit"
                                : "summarize_course_credits";
                String arguments = name.equals("summarize_course_credits")
                        ? "{\"courses\":[{\"courseId\":\"ART01\",\"category\":\"公共选修课\",\"credits\":1.5}]}"
                        : "{}";
                return new ChatResponse(List.of(new Generation(new AssistantMessage("", Map.of(),
                        List.of(new AssistantMessage.ToolCall("synthetic", "function", name, arguments))))));
            };
            var real = new CourseCreditSummaryTool();
            CampusTool tool = new CampusTool() {
                public CampusToolDescriptor descriptor() {
                    return real.descriptor();
                }

                public CampusToolResult execute(String arguments) {
                    if (scenario.equals("TOOL_FAILURE"))
                        throw new IllegalStateException("SYNTHETIC_PRIVATE_TEXT");
                    return real.execute(arguments);
                }
            };
            var source = new com.zhku.knowledge.web.LocalPreviewConversationFacade.ConversationAnswer("每学期最高4学分",
                    "合成依据", UUID.randomUUID(), "合成资料", "v1", "第1条", "合成本科生");
            var service = new CampusCreditAgentService(model, new CampusToolRegistry(List.of(tool)),
                    q -> new com.zhku.knowledge.web.LocalPreviewConversationFacade.ConversationReply(
                            scenario.equals("POLICY_HANDOFF")
                                    ? com.zhku.knowledge.conversation.elective.ElectiveRetakeReplyType.EVIDENCE_UNAVAILABLE
                                    : com.zhku.knowledge.conversation.elective.ElectiveRetakeReplyType.ANSWER,
                            "合成裁决", scenario.equals("POLICY_HANDOFF") ? null : source,
                            com.zhku.knowledge.routing.AnswerRoute.HIGH_RISK_RULE,
                            scenario.equals("POLICY_HANDOFF"), List.of(), List.of()),
                    2);
            try (var timer = new ManualTimer()) {
                var manager = new CampusCreditAgentManager(service, Runnable::run, timer, Duration.ofSeconds(30),
                        store);
                var result = manager.start(new CampusCreditAgentService.Request("本学期公共选修学分汇总", List.of(
                        new CampusCreditAgentService.CourseInput("ART01", "公共选修课", new java.math.BigDecimal("1.5")))));
                assertThat(finalFacts).as(scenario).isNotNull();
                assertThat(finalFacts.state()).isEqualTo(result.state());
                assertThat(finalFacts.failureCategory()).isEqualTo(result.failureCategory());
                if (scenario.equals("ANSWERED")) {
                    assertThat(finalFacts.state()).isEqualTo(AgentRunState.SUCCEEDED);
                    assertThat(payload.get()).isNotNull();
                    assertThat(payload.get().source()).isEqualTo(source);
                } else {
                    assertThat(payload.get()).isNull();
                    if (scenario.equals("POLICY_HANDOFF")) {
                        assertThat(finalFacts.state()).isEqualTo(AgentRunState.INCOMPLETE);
                        assertThat(finalFacts.errorCategory()).isNull();
                    } else {
                        assertThat(finalFacts.state()).isEqualTo(AgentRunState.FAILED);
                        assertThat(finalFacts.errorCategory().name())
                                .isEqualTo(scenario.equals("MODEL_FAILURE") ? "MODEL_FAILURE" : "TOOL_FAILURE");
                    }
                }
            }
        }
    }

    @Test
    void stepLimitCaptureIsContractVerificationRatherThanARealServiceExecution() {
        var context = AgentRunContext.create("合成契约验证");
        context.start();
        context.advanceStep();
        context.finish(AgentRunState.INCOMPLETE,
                com.zhku.agent.agent.runtime.contract.AgentRunSnapshot.EndReason.STEP_LIMIT, null);
        context.cleanupOnce(() -> {
        });
        context.signalTermination();
        var result = new CampusCreditAgentService.Result(context.runId(), AgentRunState.INCOMPLETE,
                AgentStopReason.NONE, true,
                CampusCreditAgentService.Status.HANDOFF, "固定契约结果", "STEP_LIMIT", List.of(), null);
        var facts = CampusRunArchive.capture(context, result, false, Instant.EPOCH);
        assertThat(facts.state()).isEqualTo(AgentRunState.INCOMPLETE);
        assertThat(facts.endReason())
                .isEqualTo(com.zhku.agent.agent.runtime.contract.AgentRunSnapshot.EndReason.STEP_LIMIT);
        assertThat(facts.errorCategory()).isNull();
    }

    @Test
    void admissionFailureAndUnknownCommitDoNotExecuteAndReleaseCapacity() {
        for (var code : List.of(Code.ARCHIVE_UNAVAILABLE, Code.ARCHIVE_ADMISSION_UNCONFIRMED)) {
            reset(store);
            ready();
            calls.set(0);
            doThrow(new Failure(code))
                    .doAnswer(i -> new Admission(i.getArgument(0), new OwnerToken(UUID.randomUUID(), 1), 0)).when(store)
                    .admit(any());
            try (var timer = Executors.newSingleThreadScheduledExecutor()) {
                var manager = new CampusCreditAgentManager(service(p -> clarification(), e -> {
                }), Runnable::run, timer,
                        Duration.ofSeconds(30), 1, Duration.ofMinutes(10), Clock.systemUTC(), store);
                assertThatThrownBy(() -> manager.start(request())).isInstanceOf(Failure.class);
                assertThat(calls.get()).isZero();
                assertThat(manager.start(request()).terminated()).isTrue();
                assertThat(calls.get()).isEqualTo(1);
            }
        }
    }

    @Test
    void gateClosingAfterAdmissionCommitPreventsDispatch() {
        ready();
        doAnswer(i -> {
            accepting.set(false);
            return new Admission(i.getArgument(0), new OwnerToken(UUID.randomUUID(), 1), 0);
        }).when(store).admit(any());
        try (var timer = Executors.newSingleThreadScheduledExecutor()) {
            var manager = new CampusCreditAgentManager(service(p -> clarification(), e -> {
            }), Runnable::run, timer, Duration.ofSeconds(30), store);
            assertThatThrownBy(() -> manager.start(request())).isInstanceOf(Failure.class)
                    .extracting(e -> ((Failure) e).code()).isEqualTo(Code.ARCHIVE_NOT_READY);
            assertThat(calls.get()).isZero();
            verify(store, never()).finalizeRun(any(), any(), any());
        }
    }

    @Test
    void cleanupFuturePrecedesArchiveAndFinalEventReadsCompletePublishedResult() throws Exception {
        ready();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var queued = new AtomicReference<Runnable>();
        var managerRef = new AtomicReference<CampusCreditAgentManager>();
        var finalRead = new AtomicReference<CampusCreditAgentManager.Snapshot>();
        var finalReturned = new CountDownLatch(1);
        var callbackFailure = new AtomicReference<Throwable>();
        var events = new CopyOnWriteArrayList<AgentRunEvent>();
        when(store.finalizeRun(any(), any(), any())).thenAnswer(i -> {
            entered.countDown();
            assertThat(release.await(3, TimeUnit.SECONDS)).isTrue();
            finalFacts = i.getArgument(1);
            return Finalization.CONFIRMED;
        });
        try (var timer = Executors.newSingleThreadScheduledExecutor()) {
            var service = service(p -> clarification(), e -> {
                events.add(e);
                if (e.type() == AgentRunEvent.Type.RUN_TERMINATED) {
                    try {
                        finalRead.set(managerRef.get().find(e.runId()));
                    } catch (Throwable failure) {
                        callbackFailure.set(failure);
                    } finally { finalReturned.countDown(); }
                }
            });
            var manager = new CampusCreditAgentManager(service, queued::set, timer, Duration.ofSeconds(30), store);
            managerRef.set(manager);
            var started = manager.start(request());
            var context = context(manager, started.runId());
            var futureRead = new AtomicReference<CampusCreditAgentManager.Snapshot>();
            context.termination().thenRun(() -> futureRead.set(manager.find(started.runId())));
            var worker = new Thread(queued.get(), "m3a-publication");
            worker.start();
            try {
                assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
                assertThat(context.termination().isDone()).isTrue();
                assertThat(futureRead.get().terminated()).isFalse();
                assertThat(manager.find(started.runId()).status()).isNull();
                assertThat(events).noneMatch(e -> e.type() == AgentRunEvent.Type.RUN_TERMINATED);
            } finally {
                release.countDown();
                worker.join(3000);
            }
            assertThat(worker.isAlive()).isFalse();
            assertThat(finalReturned.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(callbackFailure.get()).isNull();
            assertThat(finalRead.get().terminated()).isTrue();
            assertThat(finalRead.get().status()).isEqualTo(CampusCreditAgentService.Status.NEED_CLARIFICATION);
            assertThat(events.stream().filter(e -> e.type() == AgentRunEvent.Type.RUN_TERMINATED)).hasSize(1);
            assertThat(finalFacts.state()).isEqualTo(AgentRunState.INCOMPLETE);
        } finally {
            release.countDown();
        }
    }

    @Test
    void userCancelAndTaskTimeoutTemporarilyClearThenRestoreInterruptForFinalArchive() throws Exception {
        for (boolean timeout : List.of(false, true)) {
            reset(store);
            ready();
            var entered = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            var queued = new AtomicReference<Runnable>();
            var interruptedAfter = new AtomicBoolean();
            when(store.finalizeRun(any(), any(), any())).thenAnswer(i -> {
                assertThat(Thread.currentThread().isInterrupted()).isFalse();
                finalFacts = i.getArgument(1);
                return Finalization.CONFIRMED;
            });
            try (var timer = new ManualTimer()) {
                var service = service(p -> {
                    entered.countDown();
                    while (release.getCount() != 0)
                        try {
                            release.await();
                        } catch (InterruptedException ignored) {
                        }
                    Thread.currentThread().interrupt();
                    return new ChatResponse(List.of(new Generation(new AssistantMessage(""))));
                }, e -> {
                });
                var manager = new CampusCreditAgentManager(service, queued::set, timer, Duration.ofSeconds(30), store);
                var started = manager.start(request());
                var worker = new Thread(() -> {
                    queued.get().run();
                    interruptedAfter.set(Thread.currentThread().isInterrupted());
                });
                worker.start();
                try {
                    assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
                    if (timeout)
                        timer.fire();
                    else
                        manager.cancel(started.runId());
                    assertThat(manager.find(started.runId()).terminated()).isFalse();
                } finally {
                    release.countDown();
                    worker.join(3000);
                }
                assertThat(worker.isAlive()).isFalse();
                assertThat(interruptedAfter.get()).isTrue();
                var result = manager.find(started.runId());
                assertThat(result.state()).isEqualTo(AgentRunState.CANCELLED);
                assertThat(result.stopReason())
                        .isEqualTo(timeout ? AgentStopReason.RUN_TIMEOUT : AgentStopReason.USER_REQUEST);
                assertThat(finalFacts).isNotNull();
                assertThat(finalFacts.state()).isEqualTo(AgentRunState.CANCELLED);
                assertThat(finalFacts.stopReason()).isEqualTo(result.stopReason());
            } finally {
                release.countDown();
            }
        }
    }

    @Test
    void executorAndTimerRejectionsArchiveFailedFactsWithoutBusinessResult() {
        for (boolean timerReject : List.of(false, true)) {
            reset(store);
            ready();
            try (var timer = Executors.newSingleThreadScheduledExecutor()) {
                if (timerReject)
                    timer.shutdown();
                var manager = new CampusCreditAgentManager(service(p -> {
                    throw new AssertionError("Must not execute");
                }, e -> {
                }),
                        r -> {
                            throw new RejectedExecutionException();
                        }, timer, Duration.ofSeconds(30), store);
                var result = manager.start(request());
                assertThat(result.terminated()).isTrue();
                assertThat(result.failureCategory()).isEqualTo("EXECUTOR_REJECTED");
                assertThat(finalFacts).isNotNull();
                assertThat(finalFacts.state()).isEqualTo(AgentRunState.FAILED);
                assertThat(finalFacts.businessStatus()).isNull();
                assertThat(finalFacts.errorCategory().name()).isEqualTo("EXECUTOR_REJECTED");
            }
        }
    }

    @Test
    void closeWithZeroDrainSealsArchiveWithoutFakingStopAndLateResultStillPublishes() throws Exception {
        ready();
        var queued = new AtomicReference<Runnable>();
        try (var timer = new ManualTimer()) {
            var manager = new CampusCreditAgentManager(service(p -> clarification(), e -> {
            }), queued::set, timer, Duration.ofSeconds(30), store);
            var started = manager.start(request());
            manager.close(Duration.ZERO);
            assertThat(manager.find(started.runId()).stopReason()).isEqualTo(AgentStopReason.NONE);
            verify(store).sealWrites();
            verify(store).close(any(Duration.class));
            assertThatThrownBy(() -> manager.start(request())).isInstanceOf(Failure.class);
            when(store.finalizeRun(any(), any(), any())).thenReturn(Finalization.UNCONFIRMED);
            queued.get().run();
            assertThat(manager.find(started.runId()).terminated()).isTrue();
            assertThat(manager.find(started.runId()).state()).isEqualTo(AgentRunState.INCOMPLETE);
            assertThat(manager.find(started.runId()).stopReason()).isEqualTo(AgentStopReason.NONE);
        }
    }

    private static AgentRunContext context(CampusCreditAgentManager manager, UUID id) throws Exception {
        var field = CampusCreditAgentManager.class.getDeclaredField("runs");
        field.setAccessible(true);
        var managed = ((Map<?, ?>) field.get(manager)).get(id);
        var context = managed.getClass().getDeclaredField("context");
        context.setAccessible(true);
        return (AgentRunContext) context.get(managed);
    }

    private static final class ManualTimer extends ScheduledThreadPoolExecutor {
        private Runnable timeout;

        ManualTimer() {
            super(1);
            setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        }

        @Override
        public ScheduledFuture<?> schedule(Runnable task, long delay, TimeUnit unit) {
            timeout = task;
            return super.schedule(task, 1, TimeUnit.DAYS);
        }

        @Override
        public <V> ScheduledFuture<V> schedule(Callable<V> task, long delay, TimeUnit unit) {
            timeout = () -> {
                try {
                    task.call();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            };
            return super.schedule(task, 1, TimeUnit.DAYS);
        }

        void fire() {
            timeout.run();
        }

        @Override
        public void close() {
            shutdownNow();
        }
    }
}
