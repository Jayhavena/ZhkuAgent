package com.zhku.knowledge.campusagent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhku.agent.agent.runtime.AgentRunContext;
import com.zhku.agent.agent.runtime.AgentRunObservationDispatcher;
import com.zhku.agent.agent.runtime.AgentRunState;
import com.zhku.agent.agent.runtime.AgentStopReason;
import com.zhku.agent.agent.runtime.contract.AgentRunEvent;
import com.zhku.agent.agent.runtime.contract.AgentRunSnapshot;
import com.zhku.agent.agent.runtime.contract.AgentRunSnapshot.EndReason;
import com.zhku.agent.agent.runtime.contract.AgentRuntimeError;
import com.zhku.agent.agent.runtime.contract.AgentToolCall;
import com.zhku.knowledge.conversation.elective.ElectiveRetakeReplyType;
import com.zhku.knowledge.routing.AnswerRoute;
import com.zhku.knowledge.tools.CampusTool;
import com.zhku.knowledge.tools.CampusToolDescriptor;
import com.zhku.knowledge.tools.CampusToolRegistry;
import com.zhku.knowledge.tools.CampusToolResult;
import com.zhku.knowledge.tools.CourseCreditSummaryTool;
import com.zhku.knowledge.web.CampusAgentPreviewController;
import com.zhku.knowledge.web.LocalPreviewConversationFacade;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 组合验证原宿主和共享契约，不用测试需要的观察入口改造生产主循环。 */
class CampusRuntimeIntegrationTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String BASE = "/zhku/local-preview/campus-agent/v1/runs";
    private static final String LOOKUP = "lookup_public_elective_credit_limit";
    private static final String CALCULATE = "summarize_course_credits";
    private static final String COURSES = """
            {"courses":[{"courseId":"ART01","category":"公共选修课","credits":1.5},
                        {"courseId":"HIS02","category":"公共选修课","credits":2}]}
            """;

    @Test
    void controllerGoldenRunCorrelatesToolsEventsFinalSnapshotAndUnchangedHttp() throws Exception {
        AtomicInteger modelCalls = new AtomicInteger();
        var calculator = new CountingCalculator(false);
        try (var h = new Harness(prompt -> response(call(modelCalls.incrementAndGet() == 1 ? LOOKUP : CALCULATE,
                modelCalls.get() == 1 ? "{}" : COURSES)), q -> policy(), calculator)) {
            var accepted = h.start(request());
            UUID id = UUID.fromString(accepted.path("runId").asText());
            assertThat(accepted.path("state").asText()).isEqualTo("CREATED");
            assertThat(accepted.path("terminated").asBoolean()).isFalse();
            h.runQueued();
            var http = h.find(id);
            assertHttp(http, "SUCCEEDED", "ANSWERED", "NONE");
            assertThat(http.path("answer").asText()).contains("3.5 学分", "4 学分", "不能确认", "第 8、10 条");
            assertThat(http.path("source").path("sourceVersion").asText()).isEqualTo("2025版");
            assertThat(http.path("steps")).hasSize(3);
            h.probe.assertPublished(id, AgentRunState.SUCCEEDED, EndReason.COMPLETED, null);
            assertThat(h.probe.events).extracting(AgentRunEvent::type).containsExactly(
                    AgentRunEvent.Type.RUN_STARTED, AgentRunEvent.Type.STEP_STARTED,
                    AgentRunEvent.Type.TOOL_CALL_PROPOSED, AgentRunEvent.Type.TOOL_CALL_RESULT,
                    AgentRunEvent.Type.STEP_STARTED, AgentRunEvent.Type.TOOL_CALL_PROPOSED,
                    AgentRunEvent.Type.TOOL_CALL_RESULT, AgentRunEvent.Type.RUN_TERMINATED);
            var proposed = h.probe.events.stream().filter(e -> e.type() == AgentRunEvent.Type.TOOL_CALL_PROPOSED)
                    .map(AgentRunEvent::toolCall).toList();
            var results = h.probe.events.stream().filter(e -> e.type() == AgentRunEvent.Type.TOOL_CALL_RESULT)
                    .map(AgentRunEvent::toolCall).toList();
            assertThat(proposed).extracting(AgentToolCall::callId).doesNotHaveDuplicates();
            assertThat(proposed).extracting(AgentToolCall::providerCallId).containsOnly("same-provider-id");
            assertThat(proposed).extracting(AgentToolCall::toolName).containsExactly(LOOKUP, CALCULATE);
            assertThat(results).extracting(AgentToolCall::callId).containsExactly(proposed.get(0).callId(),
                    proposed.get(1).callId());
            assertThat(results).allSatisfy(c -> {
                assertThat(c.runId()).isEqualTo(id);
                assertThat(c.result().status()).isEqualTo(AgentToolCall.Status.SUCCEEDED);
                assertThat(c.result().error()).isNull();
                assertThat(c.result().payloadFormat()).isEqualTo(AgentToolCall.PayloadFormat.JSON);
            });
            assertThat(results).extracting(AgentToolCall::step).containsExactly(1, 2);
            assertThat(JSON.readTree(results.get(1).result().payload()).path("boundaryMessage").asText()).isNotBlank();
            assertThat(calculator.executions.get()).isEqualTo(1);
            assertThat(h.context(id).termination().isDone()).isTrue();
            assertThat(h.context(id).snapshot()).isEqualTo(h.probe.finalEvent().runSnapshot());
        }
    }

    @Test
    void cleanupSignalPrecedesManagerResultAndFinalPublication() throws Exception {
        var beforePublish = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        Clock clock = new Clock() {
            public ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            public Clock withZone(ZoneId zone) {
                return this;
            }

            public Instant instant() {
                if (Thread.currentThread().getName().equals("milestone2-publisher")) {
                    beforePublish.countDown();
                    await(release);
                }
                return Instant.EPOCH;
            }
        };
        try (var h = new Harness(prompt -> response(), q -> policy(), new CountingCalculator(false), clock)) {
            UUID id = UUID.fromString(
                    h.start(new CampusCreditAgentService.Request("请补充课程", List.of())).path("runId").asText());
            Thread worker = h.startWorker("milestone2-publisher");
            try {
                assertThat(beforePublish.await(5, TimeUnit.SECONDS)).isTrue();
                AgentRunContext context = h.context(id);
                // Future 只能在清理收敛后完成；此处不增加生产 getter 或清理钩子。
                assertThat(context.termination().isDone()).isTrue();
                assertThat(context.snapshot().state()).isEqualTo(AgentRunState.INCOMPLETE);
                assertThat(context.snapshot().terminated()).isFalse();
                var waiting = h.find(id);
                assertThat(waiting.path("terminated").asBoolean()).isFalse();
                assertThat(waiting.path("status").isNull()).isTrue();
                assertThat(h.probe.events).noneMatch(e -> e.type() == AgentRunEvent.Type.RUN_TERMINATED);
                System.out.println("M2_BARRIER runId=" + id + " state=" + context.snapshot().state()
                        + " cleanupSignalDone=" + context.termination().isDone()
                        + " sharedTerminated=" + context.snapshot().terminated()
                        + " httpTerminated=" + waiting.path("terminated").asBoolean());
            } finally {
                release.countDown();
                worker.join(5000);
            }
            assertThat(worker.isAlive()).isFalse();
            h.probe.assertPublished(id, AgentRunState.INCOMPLETE, EndReason.DOMAIN_DECISION, null);
            assertHttp(h.find(id), "INCOMPLETE", "NEED_CLARIFICATION", "NONE");
        }
    }

    @Test
    void clarificationIsIncompleteWithoutRuntimeErrorInHttpAndFinalEvent() throws Exception {
        try (var h = new Harness(prompt -> response(), q -> policy(), new CountingCalculator(false))) {
            UUID id = UUID.fromString(
                    h.start(new CampusCreditAgentService.Request("合计多少", List.of())).path("runId").asText());
            h.runQueued();
            assertHttp(h.find(id), "INCOMPLETE", "NEED_CLARIFICATION", "NONE");
            h.probe.assertPublished(id, AgentRunState.INCOMPLETE, EndReason.DOMAIN_DECISION, null);
            assertThat(h.probe.events).noneMatch(e -> e.type() == AgentRunEvent.Type.ERROR_RECORDED
                    || e.type() == AgentRunEvent.Type.TOOL_CALL_PROPOSED);
        }
    }

    @Test
    void ineligiblePolicyHandsOffWithoutCalculationOrRuntimeFailure() throws Exception {
        var unavailable = new LocalPreviewConversationFacade.ConversationReply(
                ElectiveRetakeReplyType.EVIDENCE_UNAVAILABLE,
                "资料不足", null, AnswerRoute.INSUFFICIENT_EVIDENCE, true, List.of(), List.of());
        var forcedHandoff = new LocalPreviewConversationFacade.ConversationReply(ElectiveRetakeReplyType.ANSWER,
                "需人工核实", policy().answer(), AnswerRoute.HIGH_RISK_RULE, true, List.of(), List.of());
        var incompleteSource = new LocalPreviewConversationFacade.ConversationReply(ElectiveRetakeReplyType.ANSWER,
                "来源不完整", new LocalPreviewConversationFacade.ConversationAnswer("4 学分", "边界", UUID.randomUUID(),
                        "学生手册", "2025版", "", "本科生"),
                AnswerRoute.HIGH_RISK_RULE, false, List.of(), List.of());
        // 这里验证宿主对领域决定的消费；真实资料可见性由原临时库验收验证，不能把替身称为数据库证据。
        for (var decision : List.of(unavailable, forcedHandoff, incompleteSource)) {
            var calculator = new CountingCalculator(false);
            try (var h = new Harness(prompt -> response(call(LOOKUP, "{}")), q -> decision, calculator)) {
                UUID id = UUID.fromString(h.start(request()).path("runId").asText());
                h.runQueued();
                assertHttp(h.find(id), "INCOMPLETE", "HANDOFF", "INSUFFICIENT_EVIDENCE");
                assertThat(h.find(id).path("answer").asText()).contains("人工核实").doesNotContain("3.5", "4 学分");
                h.probe.assertPublished(id, AgentRunState.INCOMPLETE, EndReason.DOMAIN_DECISION, null);
                assertThat(calculator.executions.get()).isZero();
                assertThat(h.probe.events.stream().filter(e -> e.type() == AgentRunEvent.Type.TOOL_CALL_RESULT)
                        .map(e -> e.toolCall().result().status())).containsExactly(AgentToolCall.Status.SUCCEEDED);
            }
        }
    }

    @Test
    void modelAndPolicyFailuresCorrelateSafeErrorWithExistingHttpCategory() throws Exception {
        for (boolean policyFailure : List.of(false, true)) {
            ChatModel model = policyFailure ? prompt -> response(call(LOOKUP, "{}"))
                    : prompt -> {
                        throw new IllegalStateException("SECRET-M2");
                    };
            try (var h = new Harness(model, q -> {
                throw new IllegalStateException("SECRET-M2");
            }, new CountingCalculator(false))) {
                UUID id = UUID.fromString(h.start(request()).path("runId").asText());
                h.runQueued();
                String code = policyFailure ? "RETRIEVAL_FAILURE" : "MODEL_FAILURE";
                var http = h.find(id);
                assertHttp(http, "FAILED", "HANDOFF", code);
                h.probe.assertPublished(id, AgentRunState.FAILED, EndReason.ERROR, code);
                assertThat(h.probe.finalEvent().runSnapshot().error().category()).isEqualTo(policyFailure
                        ? AgentRuntimeError.Category.DOMAIN_FAILURE
                        : AgentRuntimeError.Category.MODEL_FAILURE);
                assertThat(h.probe.events.toString()).doesNotContain("SECRET-M2");
                assertThat(http.toString()).doesNotContain("SECRET-M2");
            }
        }
    }

    @Test
    void knowledgeGuardsRejectUnknownReorderedFabricatedAndMultipleCalls() throws Exception {
        var proposals = List.of(List.of(call("not_allowed", "{}")), List.of(call(CALCULATE, COURSES)),
                List.of(call(CALCULATE, COURSES.replace("1.5", "9"))),
                List.of(call(LOOKUP, "{}"), call(CALCULATE, COURSES)));
        var codes = List.of("UNKNOWN_TOOL", "POLICY_REQUIRED", "INVALID_TOOL_ARGUMENT", "TOOL_CALL_LIMIT");
        for (int i = 0; i < proposals.size(); i++) {
            var calls = proposals.get(i);
            var calculator = new CountingCalculator(false);
            try (var h = new Harness(prompt -> response(calls.toArray(AssistantMessage.ToolCall[]::new)), q -> policy(),
                    calculator)) {
                UUID id = UUID.fromString(h.start(request()).path("runId").asText());
                h.runQueued();
                assertHttp(h.find(id), "FAILED", "HANDOFF", codes.get(i));
                h.probe.assertPublished(id, AgentRunState.FAILED, EndReason.ERROR, codes.get(i));
                assertThat(calculator.executions.get()).isZero();
                assertThat(h.probe.events.stream().filter(e -> e.type() == AgentRunEvent.Type.TOOL_CALL_RESULT))
                        .hasSize(calls.size())
                        .allMatch(e -> e.toolCall().result().status() == AgentToolCall.Status.REJECTED);
            }
        }
    }

    @Test
    void calculatorFailureIsObservedAsToolFailureAndNeverAsCompleted() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        var calculator = new CountingCalculator(true);
        try (var h = new Harness(
                prompt -> response(calls.incrementAndGet() == 1 ? call(LOOKUP, "{}") : call(CALCULATE, COURSES)),
                q -> policy(), calculator)) {
            UUID id = UUID.fromString(h.start(request()).path("runId").asText());
            h.runQueued();
            assertHttp(h.find(id), "FAILED", "HANDOFF", "TOOL_FAILURE");
            h.probe.assertPublished(id, AgentRunState.FAILED, EndReason.ERROR, "TOOL_FAILURE");
            assertThat(h.probe.events.stream().filter(e -> e.type() == AgentRunEvent.Type.TOOL_CALL_RESULT)
                    .map(e -> e.toolCall().result().status()))
                    .containsExactly(AgentToolCall.Status.SUCCEEDED, AgentToolCall.Status.FAILED);
            assertThat(h.probe.events.toString()).doesNotContain("SECRET-M2");
            assertThat(calculator.executions.get()).isEqualTo(1);
        }
    }

    @Test
    void userCancellationWaitsForBlockedModelAndPublishesReadableCancelledResult() throws Exception {
        verifyBlockedStop(false);
    }

    @Test
    void timeoutKeepsItsReasonWhenUserAlsoCancelsAndWaitsForBlockedModel() throws Exception {
        verifyBlockedStop(true);
    }

    private void verifyBlockedStop(boolean timeout) throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var h = new Harness(prompt -> {
            entered.countDown();
            // 模拟不响应中断的外部模型，验证停止请求不等于底层调用已停止。
            while (release.getCount() != 0) {
                try {
                    release.await();
                } catch (InterruptedException ignored) {
                }
            }
            return response();
        }, q -> policy(), new CountingCalculator(false))) {
            UUID id = UUID.fromString(h.start(request()).path("runId").asText());
            Thread worker = h.startWorker("milestone2-blocked-model");
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                if (timeout)
                    h.timer.fireTimeout();
                var stopping = h.cancel(id);
                assertThat(stopping.path("state").asText()).isEqualTo("STOP_REQUESTED");
                assertThat(stopping.path("terminated").asBoolean()).isFalse();
                assertThat(h.context(id).termination().isDone()).isFalse();
                assertThat(h.probe.events).noneMatch(e -> e.type() == AgentRunEvent.Type.RUN_TERMINATED);
            } finally {
                release.countDown();
                worker.join(5000);
            }
            assertThat(worker.isAlive()).isFalse();
            var http = h.find(id);
            assertHttp(http, "CANCELLED", "CANCELLED", "CANCELLED");
            AgentStopReason reason = timeout ? AgentStopReason.RUN_TIMEOUT : AgentStopReason.USER_REQUEST;
            assertThat(http.path("stopReason").asText()).isEqualTo(reason.name());
            h.probe.assertPublished(id, AgentRunState.CANCELLED, EndReason.STOPPED, null);
            assertThat(h.probe.finalEvent().runSnapshot().stopReason()).isEqualTo(reason);
            assertThat(h.probe.events).extracting(AgentRunEvent::type).containsExactly(AgentRunEvent.Type.RUN_STARTED,
                    AgentRunEvent.Type.STEP_STARTED, AgentRunEvent.Type.STOP_REQUESTED,
                    AgentRunEvent.Type.RUN_TERMINATED);
        }
    }

    @Test
    void queuedCancellationDoesNotStartModelAndStillPublishesFinalRecord() throws Exception {
        try (var h = new Harness(prompt -> {
            throw new AssertionError("排队取消后不能调用模型");
        }, q -> policy(), new CountingCalculator(false))) {
            UUID id = UUID.fromString(h.start(request()).path("runId").asText());
            assertThat(h.cancel(id).path("terminated").asBoolean()).isFalse();
            h.runQueued();
            assertHttp(h.find(id), "CANCELLED", "CANCELLED", "CANCELLED");
            h.probe.assertPublished(id, AgentRunState.CANCELLED, EndReason.STOPPED, null);
            assertThat(h.probe.events).extracting(AgentRunEvent::type)
                    .containsExactly(AgentRunEvent.Type.STOP_REQUESTED, AgentRunEvent.Type.RUN_TERMINATED);
            assertThat(h.probe.finalEvent().runSnapshot().currentStep()).isZero();
        }
    }

    @Test
    void executorAndSchedulerRejectionPublishSafeErrorBefore503ResultReturns() throws Exception {
        for (boolean scheduler : List.of(false, true)) {
            try (var h = new Harness(prompt -> {
                throw new AssertionError("拒绝任务不能调用模型");
            }, q -> policy(), new CountingCalculator(false))) {
                if (scheduler)
                    h.timer.shutdownNow();
                else
                    h.rejectWorker = true;
                var http = h.start(request(), 503);
                UUID id = UUID.fromString(http.path("runId").asText());
                assertThat(http.path("state").asText()).isEqualTo("FAILED");
                assertThat(http.path("failureCategory").asText()).isEqualTo("EXECUTOR_REJECTED");
                h.probe.assertPublished(id, AgentRunState.FAILED, EndReason.ERROR, "EXECUTOR_REJECTED");
                assertThat(h.probe.events).extracting(AgentRunEvent::type)
                        .containsExactly(AgentRunEvent.Type.ERROR_RECORDED, AgentRunEvent.Type.RUN_TERMINATED);
            }
        }
    }

    @Test
    void directServiceEntryPublishesFinalFactAndSeparatelyDeliversCallback() throws Exception {
        var owner = AgentRunObservationDispatcher.createOwned();
        List<AgentRunEvent> events = new CopyOnWriteArrayList<>();
        var finalReturned = new CountDownLatch(1);
        try {
            var service = new CampusCreditAgentService(prompt -> response(),
                    new CampusToolRegistry(List.of(new CourseCreditSummaryTool())),
                    q -> policy(), 3, e -> {
                        events.add(e);
                        if (e.type() == AgentRunEvent.Type.RUN_TERMINATED) finalReturned.countDown();
                    }, owner);
            var result = service.run(new CampusCreditAgentService.Request("缺课程", List.of()));
            assertThat(result.terminated()).isTrue();
            assertThat(result.state()).isEqualTo(AgentRunState.INCOMPLETE);
            // 返回证明事实已发布；观察回调另等屏障，不能沿用同步交付假设。
            assertThat(finalReturned.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(events.getLast().type()).isEqualTo(AgentRunEvent.Type.RUN_TERMINATED);
            assertThat(events.getLast().runSnapshot().runId()).isEqualTo(result.runId());
            assertThat(events.getLast().runSnapshot().terminated()).isTrue();
            assertThat(events.getLast().runSnapshot().endReason()).isEqualTo(EndReason.DOMAIN_DECISION);
        } finally {
            owner.close();
            long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (owner.status().liveThreadCount() != 0 && System.nanoTime() < until) Thread.sleep(1);
            assertThat(owner.status().liveThreadCount()).isZero();
        }
    }

    private static void assertHttp(JsonNode http, String state, String businessStatus, String category) {
        var fields = new ArrayList<String>();
        http.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrder("runId", "state", "stopReason", "terminated", "status", "answer",
                "failureCategory", "steps", "source");
        assertThat(http.path("state").asText()).isEqualTo(state);
        assertThat(http.path("status").asText()).isEqualTo(businessStatus);
        assertThat(http.path("failureCategory").asText()).isEqualTo(category);
        assertThat(http.path("terminated").asBoolean()).isTrue();
    }

    private static final class Probe implements Consumer<AgentRunEvent> {
        final List<AgentRunEvent> events = new CopyOnWriteArrayList<>();
        final AtomicReference<CampusCreditAgentManager.Snapshot> readInFinalCallback = new AtomicReference<>();
        final CountDownLatch finalReturned = new CountDownLatch(1);
        final AtomicReference<Throwable> callbackFailure = new AtomicReference<>();
        CampusCreditAgentManager manager;

        public void accept(AgentRunEvent event) {
            events.add(event);
            if (event.type() == AgentRunEvent.Type.RUN_TERMINATED) {
                // Runtime 会隔离 RuntimeException；测试必须另存失败并在回调外断言，不能让失败被吞掉。
                try {
                    readInFinalCallback.set(manager.find(event.runId()));
                } catch (Throwable failure) {
                    callbackFailure.set(failure);
                } finally { finalReturned.countDown(); }
            }
        }

        AgentRunEvent finalEvent() {
            return events.getLast();
        }

        void assertPublished(UUID id, AgentRunState state, EndReason reason, String errorCode) {
            try { assertThat(finalReturned.await(1, TimeUnit.SECONDS)).isTrue(); }
            catch (InterruptedException failure) { throw new AssertionError(failure); }
            assertThat(callbackFailure.get()).isNull();
            assertThat(events).allSatisfy(e -> assertThat(e.runId()).isEqualTo(id));
            for (int i = 0; i < events.size(); i++)
                assertThat(events.get(i).sequence()).isEqualTo(i + 1L);
            assertThat(events.stream().filter(e -> e.type() == AgentRunEvent.Type.RUN_TERMINATED)).hasSize(1);
            AgentRunSnapshot snapshot = finalEvent().runSnapshot();
            assertThat(snapshot.state()).isEqualTo(state);
            assertThat(snapshot.endReason()).isEqualTo(reason);
            assertThat(snapshot.terminated()).isTrue();
            if (errorCode == null)
                assertThat(snapshot.error()).isNull();
            else {
                assertThat(snapshot.error().code()).isEqualTo(errorCode);
                assertThat(events.stream().filter(e -> e.type() == AgentRunEvent.Type.ERROR_RECORDED)
                        .map(AgentRunEvent::error))
                        .containsExactly(snapshot.error());
            }
            var observed = readInFinalCallback.get();
            assertThat(observed).isNotNull().isEqualTo(manager.find(id));
            assertThat(observed.state()).isEqualTo(state);
            assertThat(observed.terminated()).isTrue();
            if (!"EXECUTOR_REJECTED".equals(errorCode)) {
                assertThat(observed.status()).isNotNull();
                assertThat(observed.answer()).isNotBlank();
                assertThat(observed.failureCategory()).isNotNull();
            }
            // 只输出虚构任务的运行元数据，不记录问题、工具参数、资料载荷或异常原文。
            System.out.println("M2_TRACE runId=" + id + " events="
                    + events.stream().map(e -> e.sequence() + ":" + e.type()).toList()
                    + " state=" + snapshot.state() + " end=" + snapshot.endReason() + " stop=" + snapshot.stopReason()
                    + " error=" + (snapshot.error() == null ? "NONE" : snapshot.error().code())
                    + " sharedTerminated=" + snapshot.terminated() + " callbackHttpTerminated=" + observed.terminated()
                    + " callbackStatus=" + observed.status());
        }
    }

    private static final class Harness implements AutoCloseable {
        final List<Runnable> queue = new ArrayList<>();
        final ManualTimer timer = new ManualTimer();
        final AgentRunObservationDispatcher observations = AgentRunObservationDispatcher.createOwned();
        final Probe probe = new Probe();
        final CampusCreditAgentManager manager;
        final MockMvc mvc;
        boolean rejectWorker;

        Harness(ChatModel model, Function<String, LocalPreviewConversationFacade.ConversationReply> policy,
                CampusTool calculator) {
            this(model, policy, calculator, Clock.systemUTC());
        }

        Harness(ChatModel model, Function<String, LocalPreviewConversationFacade.ConversationReply> policy,
                CampusTool calculator, Clock clock) {
            var service = new CampusCreditAgentService(model, new CampusToolRegistry(List.of(calculator)), policy, 3,
                    probe, observations);
            manager = new CampusCreditAgentManager(service, task -> {
                if (rejectWorker)
                    throw new RejectedExecutionException();
                queue.add(task);
            }, timer, Duration.ofSeconds(30), 128, Duration.ofMinutes(10), clock);
            probe.manager = manager;
            mvc = MockMvcBuilders.standaloneSetup(new CampusAgentPreviewController(manager, new com.zhku.knowledge.campusagent.CampusAgentResultVisibility(candidate ->
                new com.zhku.knowledge.campusagent.archive.CampusRunArchive.Visibility(
                        com.zhku.knowledge.campusagent.archive.CampusRunArchive.VisibilityStatus.VISIBLE,
                        com.zhku.knowledge.campusagent.archive.CampusRunArchive.Reason.NONE)))).build();
        }

        JsonNode start(CampusCreditAgentService.Request request) throws Exception {
            return start(request, 202);
        }

        JsonNode start(CampusCreditAgentService.Request request, int expectedStatus) throws Exception {
            return JSON.readTree(mvc
                    .perform(
                            post(BASE).contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsBytes(request)))
                    .andExpect(status().is(expectedStatus)).andReturn().getResponse().getContentAsString());
        }

        JsonNode find(UUID id) throws Exception {
            return JSON.readTree(mvc.perform(get(BASE + "/" + id)).andExpect(status().isOk()).andReturn().getResponse()
                    .getContentAsString());
        }

        JsonNode cancel(UUID id) throws Exception {
            return JSON.readTree(mvc.perform(post(BASE + "/" + id + "/cancel")).andExpect(status().isOk()).andReturn()
                    .getResponse().getContentAsString());
        }

        void runQueued() {
            queue.remove(0).run();
        }

        Thread startWorker(String name) {
            var worker = new Thread(queue.remove(0), name);
            worker.start();
            return worker;
        }

        AgentRunContext context(UUID id) throws ReflectiveOperationException {
            // 仅为观察清理与发布之间的现有屏障读取 Context，不增加测试专用生产 API。
            var runsField = CampusCreditAgentManager.class.getDeclaredField("runs");
            runsField.setAccessible(true);
            Object managed = ((Map<?, ?>) runsField.get(manager)).get(id);
            var contextField = managed.getClass().getDeclaredField("context");
            contextField.setAccessible(true);
            return (AgentRunContext) contextField.get(managed);
        }

        public void close() throws InterruptedException {
            observations.close();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (observations.status().liveThreadCount() != 0 && System.nanoTime() < deadline) Thread.sleep(1);
            assertThat(observations.status().liveThreadCount()).isZero();
            timer.shutdownNow();
            assertThat(timer.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static final class ManualTimer extends ScheduledThreadPoolExecutor {
        private Runnable timeout;

        ManualTimer() {
            super(1);
            setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        }

        @Override
        public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
            timeout = command;
            // 验证相同生产 timeout Runnable，主动触发以避免用墙钟等待制造脆弱时序。
            return super.schedule(command, 1, TimeUnit.DAYS);
        }

        @Override
        public <V> ScheduledFuture<V> schedule(Callable<V> command, long delay, TimeUnit unit) {
            // requestStop 返回 boolean，表达式 lambda 会选择 Callable 重载，不能漏掉真实调度路径。
            timeout = () -> {
                try {
                    command.call();
                } catch (Exception exception) {
                    throw new IllegalStateException(exception);
                }
            };
            return super.schedule(command, 1, TimeUnit.DAYS);
        }

        void fireTimeout() {
            timeout.run();
        }
    }

    private static final class CountingCalculator implements CampusTool {
        final AtomicInteger executions = new AtomicInteger();
        final CourseCreditSummaryTool delegate = new CourseCreditSummaryTool();
        final boolean fail;

        CountingCalculator(boolean fail) {
            this.fail = fail;
        }

        public CampusToolDescriptor descriptor() {
            return delegate.descriptor();
        }

        public CampusToolResult execute(String arguments) {
            executions.incrementAndGet();
            if (fail)
                throw new IllegalStateException("SECRET-M2");
            return delegate.execute(arguments);
        }
    }

    private static CampusCreditAgentService.Request request() {
        return new CampusCreditAgentService.Request("虚构公选课合计及上限？", List.of(
                new CampusCreditAgentService.CourseInput("ART01", "公共选修课", new BigDecimal("1.5")),
                new CampusCreditAgentService.CourseInput("HIS02", "公共选修课", new BigDecimal("2"))));
    }

    private static LocalPreviewConversationFacade.ConversationReply policy() {
        return new LocalPreviewConversationFacade.ConversationReply(ElectiveRetakeReplyType.ANSWER, "每学期最高 4 学分",
                new LocalPreviewConversationFacade.ConversationAnswer("每学期最高 4 学分", "以培养方案为准",
                        UUID.fromString("00000000-0000-0000-0000-000000000004"), "学生手册", "2025版", "第 8、10 条", "本科生"),
                AnswerRoute.HIGH_RISK_RULE, false, List.of(), List.of());
    }

    private static AssistantMessage.ToolCall call(String name, String arguments) {
        return new AssistantMessage.ToolCall("same-provider-id", "function", name, arguments);
    }

    private static ChatResponse response(AssistantMessage.ToolCall... calls) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage("", Map.of(), List.of(calls)))));
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS))
                throw new AssertionError("等待发布屏障超时");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }
}
