package com.zhku.knowledge.web;

import com.zhku.agent.agent.runtime.AgentRunState;
import com.zhku.knowledge.campusagent.CampusAgentResultVisibility;
import com.zhku.knowledge.campusagent.archive.CampusRunArchive;
import com.zhku.knowledge.campusagent.CampusCreditAgentManager;
import com.zhku.knowledge.campusagent.CampusCreditAgentService;
import com.zhku.knowledge.conversation.elective.ElectiveRetakeReplyType;
import com.zhku.knowledge.routing.AnswerRoute;
import com.zhku.knowledge.tools.CampusToolRegistry;
import com.zhku.knowledge.tools.CourseCreditSummaryTool;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class CampusAgentResultVisibilityTest {
    private static final UUID SOURCE_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final CampusCreditAgentService.Request REQUEST = new CampusCreditAgentService.Request("合成公选课",
            List.of(new CampusCreditAgentService.CourseInput("C1", "公共选修课", new BigDecimal("3.5"))));

    @Test
    void sourceWithdrawnAfterLookupIsHiddenOnFirstResponseWithoutChangingExecution() throws Exception {
        var visible = new AtomicBoolean(true);
        try (var fixture = new Fixture(visible, () -> visible.set(false))) {
            var response = fixture.controller.start(REQUEST);
            var body = (CampusCreditAgentManager.Snapshot) response.getBody();
            assertThat(visible.get()).isFalse();
            assertThat(body.state()).isEqualTo(AgentRunState.SUCCEEDED);
            assertThat(body.terminated()).isTrue();
            assertThat(body.failureCategory()).isEqualTo("NONE");
            // 完整业务结果仍在宿主内；对外副本必须经过当下许可，不改写执行事实。
            assertThat(fixture.manager.find(body.runId()).source()).isNotNull();
            assertThat(body.source()).isNull();
            assertThat(body.steps()).isEmpty();
            assertThat(body.answer()).doesNotContain("PRIVATE_POLICY", "第SECRET条", "3.5 学分");
        }
    }

    @Test
    void pollingAndCancelRecheckCurrentPermissionAndKeepNineFields() throws Exception {
        var visible = new AtomicBoolean(true);
        try (var fixture = new Fixture(visible, () -> {})) {
            var allowed = (CampusCreditAgentManager.Snapshot) fixture.controller.start(REQUEST).getBody();
            assertThat(allowed.answer()).contains("PRIVATE_POLICY", "3.5 学分");
            assertThat(allowed.steps()).hasSize(3);
            visible.set(false);
            for (var response : List.of(fixture.controller.find(allowed.runId()), fixture.controller.cancel(allowed.runId()))) {
                var hidden = (CampusCreditAgentManager.Snapshot) response.getBody();
                assertHidden(hidden);
                assertThat(hidden.state()).isEqualTo(allowed.state());
                assertThat(hidden.stopReason()).isEqualTo(allowed.stopReason());
                assertThat(hidden.status()).isEqualTo(allowed.status());
                assertThat(hidden.failureCategory()).isEqualTo(allowed.failureCategory());
                var fields = new java.util.ArrayList<String>();
                new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(hidden).fieldNames().forEachRemaining(fields::add);
                assertThat(fields).containsExactlyInAnyOrder("runId", "state", "stopReason", "terminated", "status",
                        "answer", "failureCategory", "steps", "source");
            }
            visible.set(true);
            var restored = (CampusCreditAgentManager.Snapshot) fixture.controller.find(allowed.runId()).getBody();
            assertThat(restored.source()).isEqualTo(allowed.source());
            assertThat(restored.answer()).isEqualTo(allowed.answer());
        }
    }

    @Test
    void unavailableNullPermissionAndUnwiredControllerFailClosed() throws Exception {
        try (var fixture = new Fixture(new AtomicBoolean(true), () -> {})) {
            var completed = (CampusCreditAgentManager.Snapshot) fixture.controller.start(REQUEST).getBody();
            for (var gate : List.of(new CampusAgentResultVisibility(candidate -> { throw new IllegalStateException("合成失败"); }),
                    new CampusAgentResultVisibility(candidate -> null))) {
                var controller = new CampusAgentPreviewController(fixture.manager, gate);
                assertHidden((CampusCreditAgentManager.Snapshot) controller.find(completed.runId()).getBody());
            }
            var unwired = new CampusAgentPreviewController(fixture.manager);
            assertHidden((CampusCreditAgentManager.Snapshot) unwired.find(completed.runId()).getBody());
            assertThat(fixture.manager.find(completed.runId())).isEqualTo(completed);
        }
    }

    @Test
    void missingSourceSuppressesPolicyStepsWithoutChangingFailedExecution() throws Exception {
        try (var fixture = new Fixture(new AtomicBoolean(true), () -> {})) {
            var completed = (CampusCreditAgentManager.Snapshot) fixture.controller.start(REQUEST).getBody();
            var failed = new CampusCreditAgentManager.Snapshot(completed.runId(), AgentRunState.FAILED,
                    completed.stopReason(), true, CampusCreditAgentService.Status.HANDOFF, "PRIVATE_POLICY",
                    "MODEL_FAILURE", completed.steps(), null);
            var gate = new CampusAgentResultVisibility(candidate -> { throw new AssertionError("缺来源不能授权"); });
            var hidden = gate.filter(failed);
            assertHidden(hidden);
            assertThat(hidden.state()).isEqualTo(AgentRunState.FAILED);
            assertThat(hidden.failureCategory()).isEqualTo("MODEL_FAILURE");
        }
    }

    private static void assertHidden(CampusCreditAgentManager.Snapshot body) {
        assertThat(body.terminated()).isTrue();
        assertThat(body.answer()).contains("不可展示", "人工核实").doesNotContain("PRIVATE_POLICY", "第SECRET条", "3.5 学分");
        assertThat(body.source()).isNull();
        assertThat(body.steps()).isEmpty();
    }

    private static final class Fixture implements AutoCloseable {
        private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
        private final CampusCreditAgentManager manager;
        private final CampusAgentPreviewController controller;

        private Fixture(AtomicBoolean visible, Runnable beforeCalculation) {
            var calls = new AtomicInteger();
            var service = new CampusCreditAgentService(prompt -> {
                int n = calls.incrementAndGet();
                if (n == 2) beforeCalculation.run();
                var call = new AssistantMessage.ToolCall("c" + n, "function",
                        n == 1 ? "lookup_public_elective_credit_limit" : "summarize_course_credits",
                        n == 1 ? "{}" : "{\"courses\":[{\"courseId\":\"C1\",\"category\":\"公共选修课\",\"credits\":3.5}]}");
                return new ChatResponse(List.of(new Generation(new AssistantMessage("", Map.of(), List.of(call)))));
            }, new CampusToolRegistry(List.of(new CourseCreditSummaryTool())), question ->
                    new LocalPreviewConversationFacade.ConversationReply(ElectiveRetakeReplyType.ANSWER,
                            "PRIVATE_POLICY", new LocalPreviewConversationFacade.ConversationAnswer(
                                    "PRIVATE_POLICY", "合成依据", SOURCE_ID, "PRIVATE_POLICY_TITLE", "synthetic-v1",
                                    "第SECRET条", "合成本科生"), AnswerRoute.HIGH_RISK_RULE, false, List.of(), List.of()), 3);
            manager = new CampusCreditAgentManager(service, Runnable::run, timer, Duration.ofSeconds(30));
            controller = new CampusAgentPreviewController(manager, new CampusAgentResultVisibility(candidate ->
                    new CampusRunArchive.Visibility(visible.get() ? CampusRunArchive.VisibilityStatus.VISIBLE
                            : CampusRunArchive.VisibilityStatus.HIDDEN, visible.get() ? CampusRunArchive.Reason.NONE
                            : CampusRunArchive.Reason.SOURCE_NOT_VISIBLE)));
        }

        @Override
        public void close() throws Exception {
            timer.shutdownNow();
            assertThat(timer.awaitTermination(2, TimeUnit.SECONDS)).isTrue();
        }
    }
}
