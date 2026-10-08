package com.zhku.knowledge.campusagent;

import com.zhku.agent.agent.runtime.*;
import com.zhku.agent.agent.runtime.contract.*;
import com.zhku.agent.agent.runtime.contract.AgentRunSnapshot.EndReason;
import com.zhku.knowledge.tools.*;
import com.zhku.knowledge.web.LocalPreviewConversationFacade;
import com.zhku.knowledge.conversation.elective.ElectiveRetakeReplyType;
import com.zhku.knowledge.routing.AnswerRoute;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.*;
import static org.assertj.core.api.Assertions.*;

class CampusRuntimeContractTest {
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
    void malformedProposalsKeepOriginalGuardCategories() {
        var inputs = List.of(
                List.of(call("lookup_public_elective_credit_limit", null)),
                List.of(call(" ", "{}")),
                List.of(call(null, null)),
                List.of(call("lookup_public_elective_credit_limit", null), call("summarize_course_credits", null)));
        var expected = List.of("INVALID_TOOL_ARGUMENT", "UNKNOWN_TOOL", "UNKNOWN_TOOL", "TOOL_CALL_LIMIT");
        for (int i = 0; i < inputs.size(); i++) {
            var calls = inputs.get(i);
            List<AgentRunEvent> events = new CopyOnWriteArrayList<>();
            var context = AgentRunContext.create("任务", events::add, observations);
            var result = service(
                    prompt -> new ChatResponse(List.of(new Generation(new AssistantMessage("", Map.of(), calls)))),
                    q -> policy())
                    .run(context, request());
            assertThat(result.failureCategory()).isEqualTo(expected.get(i));
            awaitObserved(context);
            assertThat(events.stream().filter(e -> e.type() == AgentRunEvent.Type.TOOL_CALL_RESULT))
                    .hasSize(calls.size())
                    .allMatch(e -> e.toolCall().result().status() == AgentToolCall.Status.REJECTED);
        }
    }

    private static final String COURSES = "{\"courses\":[{\"courseId\":\"ART01\",\"category\":\"公共选修课\",\"credits\":1.5},{\"courseId\":\"HIS02\",\"category\":\"公共选修课\",\"credits\":2}]}";

    @Test
    void goldenTaskPreservesPolicyBoundaryAndCorrelatesRepeatedProviderIds() {
        List<AgentRunEvent> events = new CopyOnWriteArrayList<>();
        var context = AgentRunContext.create("任务", events::add, observations);
        AtomicInteger number = new AtomicInteger();
        var result = service(prompt -> response(number.incrementAndGet() == 1
                ? call("lookup_public_elective_credit_limit", "{}")
                : call("summarize_course_credits", COURSES)), q -> policy())
                .run(context, request());
        assertThat(result.answer()).contains("3.5", "4 学分", "不能确认", "第 8、10 条");
        awaitObserved(context);
        var calls = events.stream().filter(e -> e.type() == AgentRunEvent.Type.TOOL_CALL_RESULT)
                .map(AgentRunEvent::toolCall).toList();
        assertThat(calls).hasSize(2);
        assertThat(calls).extracting(AgentToolCall::callId).doesNotHaveDuplicates();
        assertThat(calls).extracting(AgentToolCall::providerCallId).containsOnly("duplicate");
        assertThat(calls.getLast().result().payload()).contains("boundaryMessage", "totalCredits");
        assertThat(context.snapshot().endReason()).isEqualTo(EndReason.COMPLETED);
        // 传入 Context 的宿主负责发布；服务清理后不能抢先发送 Manager 的最终事件。
        assertThat(events).noneMatch(e -> e.type() == AgentRunEvent.Type.RUN_TERMINATED);
        assertThat(context.snapshot().terminated()).isFalse();
    }

    @Test
    void clarificationAndInsufficientEvidenceAreDomainDecisionsWithoutRuntimeError() {
        for (boolean missingInput : List.of(true, false)) {
            var context = AgentRunContext.create("任务");
            var service = service(
                    prompt -> missingInput ? new ChatResponse(List.of(new Generation(new AssistantMessage("请补充"))))
                            : response(call("lookup_public_elective_credit_limit", "{}")),
                    q -> new LocalPreviewConversationFacade.ConversationReply(
                            ElectiveRetakeReplyType.EVIDENCE_UNAVAILABLE, "无证据", null,
                            AnswerRoute.INSUFFICIENT_EVIDENCE, true, List.of(), List.of()));
            service.run(context, missingInput ? new CampusCreditAgentService.Request("问题", List.of()) : request());
            assertThat(context.snapshot().state()).isEqualTo(AgentRunState.INCOMPLETE);
            assertThat(context.snapshot().endReason()).isEqualTo(EndReason.DOMAIN_DECISION);
            assertThat(context.snapshot().error()).isNull();
        }
    }

    @Test
    void toolGuardRejectsWithoutPretendingExecution() {
        List<AgentRunEvent> events = new CopyOnWriteArrayList<>();
        var context = AgentRunContext.create("任务", events::add, observations);
        service(prompt -> response(call("summarize_course_credits", COURSES)), q -> policy()).run(context, request());
        assertThat(context.snapshot().error().code()).isEqualTo("POLICY_REQUIRED");
        awaitObserved(context);
        assertThat(events).anySatisfy(event -> {
            assertThat(event.type()).isEqualTo(AgentRunEvent.Type.TOOL_CALL_RESULT);
            assertThat(event.toolCall().result().status()).isEqualTo(AgentToolCall.Status.REJECTED);
        });
        assertThat(events).noneMatch(e -> e.toolCall() != null && e.toolCall().result() != null
                && e.toolCall().result().status() == AgentToolCall.Status.SUCCEEDED);
    }

    @Test
    void modelAndPolicyFailuresHaveDifferentSafeErrors() {
        var modelContext = AgentRunContext.create("任务");
        service(prompt -> {
            throw new IllegalStateException("SECRET");
        }, q -> policy()).run(modelContext, request());
        assertThat(modelContext.snapshot().error().category()).isEqualTo(AgentRuntimeError.Category.MODEL_FAILURE);
        var policyContext = AgentRunContext.create("任务");
        service(prompt -> response(call("lookup_public_elective_credit_limit", "{}")), q -> {
            throw new IllegalStateException("SECRET");
        })
                .run(policyContext, request());
        assertThat(policyContext.snapshot().error().category()).isEqualTo(AgentRuntimeError.Category.DOMAIN_FAILURE);
        assertThat(policyContext.snapshot().error().code()).isEqualTo("RETRIEVAL_FAILURE");
        assertThat(policyContext.snapshot().toString()).doesNotContain("SECRET");
    }

    @Test
    void managerPublishesFinalEventAfterResultAndHandlesExecutorRejection() throws Exception {
        for (boolean rejected : List.of(false, true)) {
            List<AgentRunEvent> events = new CopyOnWriteArrayList<>();
            var service = observedService(
                    prompt -> new ChatResponse(List.of(new Generation(new AssistantMessage("请补充")))), events::add);
            try (var timer = Executors.newSingleThreadScheduledExecutor()) {
                var manager = new CampusCreditAgentManager(service, rejected ? command -> {
                    throw new RejectedExecutionException();
                }
                        : Runnable::run, timer, Duration.ofSeconds(5));
                var result = manager.start(new CampusCreditAgentService.Request("问题", List.of()));
                assertThat(result.terminated()).isTrue();
                awaitFinal(events);
                assertThat(events.getLast().type()).isEqualTo(AgentRunEvent.Type.RUN_TERMINATED);
                assertThat(events.getLast().runSnapshot().terminated()).isTrue();
                assertThat(events.getLast().runSnapshot().state()).isEqualTo(result.state());
                if (rejected)
                    assertThat(events.getLast().runSnapshot().error().category())
                            .isEqualTo(AgentRuntimeError.Category.EXECUTOR_REJECTED);
            }
        }
    }

    @Test
    void publicationBarrierDoesNotEmitPrematureFinalEvent() throws Exception {
        List<AgentRunEvent> events = new CopyOnWriteArrayList<>();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var clock = new Clock() {
            public ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            public Clock withZone(ZoneId zone) {
                return this;
            }

            public Instant instant() {
                if (Thread.currentThread().getName().equals("contract-publisher")) {
                    entered.countDown();
                    try {
                        if (!release.await(5, TimeUnit.SECONDS))
                            throw new AssertionError("发布等待超时");
                    } catch (InterruptedException e) {
                        throw new AssertionError(e);
                    }
                }
                return Instant.EPOCH;
            }
        };
        List<Runnable> queue = new CopyOnWriteArrayList<>();
        try (var timer = Executors.newSingleThreadScheduledExecutor()) {
            var manager = new CampusCreditAgentManager(
                    observedService(prompt -> new ChatResponse(List.of(new Generation(new AssistantMessage("请补充")))),
                            events::add),
                    queue::add, timer, Duration.ofSeconds(30), 2, Duration.ofMinutes(10), clock);
            var accepted = manager.start(new CampusCreditAgentService.Request("问题", List.of()));
            Thread worker = new Thread(queue.remove(0), "contract-publisher");
            worker.start();
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(manager.find(accepted.runId()).terminated()).isFalse();
                assertThat(events).noneMatch(e -> e.type() == AgentRunEvent.Type.RUN_TERMINATED);
            } finally {
                release.countDown();
                worker.join(5000);
            }
            assertThat(worker.isAlive()).isFalse();
            awaitFinal(events);
            assertThat(events.getLast().runSnapshot().terminated()).isTrue();
        } finally {
            release.countDown();
        }
    }

    private void awaitFinal(List<AgentRunEvent> events) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        while (System.nanoTime() < deadline) {
            if (events.stream().anyMatch(e -> e.type() == AgentRunEvent.Type.RUN_TERMINATED)
                    && observations.status().inFlightCount() == 0) return;
            Thread.onSpinWait();
        }
        throw new AssertionError("final交付超过固定1000ms");
    }

    private CampusCreditAgentService observedService(ChatModel model, Consumer<AgentRunEvent> observer)
            throws Exception {
        assertThat(Arrays.stream(CampusCreditAgentService.class.getConstructors()).map(c -> c.getParameterCount()))
                .contains(5);
        return CampusCreditAgentService.class
                .getConstructor(ChatModel.class, CampusToolRegistry.class, Function.class, int.class, Consumer.class, AgentRunObservationDispatcher.class)
                .newInstance(model, new CampusToolRegistry(List.of(new CourseCreditSummaryTool())),
                        (Function<String, LocalPreviewConversationFacade.ConversationReply>) q -> policy(), 3,
                        observer, observations);
    }

    private static CampusCreditAgentService service(ChatModel model,
            Function<String, LocalPreviewConversationFacade.ConversationReply> policy) {
        return new CampusCreditAgentService(model, new CampusToolRegistry(List.of(new CourseCreditSummaryTool())),
                policy, 3);
    }

    private static CampusCreditAgentService.Request request() {
        return new CampusCreditAgentService.Request("公选课合计及每学期上限？", List.of(
                new CampusCreditAgentService.CourseInput("ART01", "公共选修课", new BigDecimal("1.5")),
                new CampusCreditAgentService.CourseInput("HIS02", "公共选修课", new BigDecimal("2"))));
    }

    private static LocalPreviewConversationFacade.ConversationReply policy() {
        return new LocalPreviewConversationFacade.ConversationReply(ElectiveRetakeReplyType.ANSWER, "每学期最高 4 学分",
                new LocalPreviewConversationFacade.ConversationAnswer("每学期最高 4 学分", "以培养方案为准", UUID.randomUUID(),
                        "学生手册", "2025版", "第 8、10 条", "本科生"),
                AnswerRoute.HIGH_RISK_RULE, false, List.of(), List.of());
    }

    private static AssistantMessage.ToolCall call(String name, String args) {
        return new AssistantMessage.ToolCall("duplicate", "function", name, args);
    }

    private static ChatResponse response(AssistantMessage.ToolCall call) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage("", Map.of(), List.of(call)))));
    }
}
