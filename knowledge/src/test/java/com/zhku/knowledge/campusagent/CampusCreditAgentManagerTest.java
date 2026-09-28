package com.zhku.knowledge.campusagent;

import com.zhku.agent.agent.runtime.AgentRunState;
import com.zhku.agent.agent.runtime.AgentStopReason;
import com.zhku.knowledge.conversation.elective.ElectiveRetakeReplyType;
import com.zhku.knowledge.routing.AnswerRoute;
import com.zhku.knowledge.tools.CampusToolRegistry;
import com.zhku.knowledge.tools.CourseCreditSummaryTool;
import com.zhku.knowledge.web.LocalPreviewConversationFacade;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class CampusCreditAgentManagerTest {
    @Test
    void cancellationIsNotTerminationUntilWorkerLeavesAndCleansUp() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        var service = new CampusCreditAgentService(prompt -> {
            entered.countDown();
            while (release.getCount() > 0) {
                try { release.await(10, TimeUnit.MILLISECONDS); }
                catch (InterruptedException ignored) { /* 模拟不响应中断的供应商调用。 */ }
            }
            return new ChatResponse(List.of(new Generation(new AssistantMessage("", java.util.Map.of(), List.of()))));
        }, new CampusToolRegistry(List.of(new CourseCreditSummaryTool())),
                question -> new LocalPreviewConversationFacade.ConversationReply(
                        ElectiveRetakeReplyType.ANSWER, "规则", new LocalPreviewConversationFacade.ConversationAnswer(
                                "每学期公共选修课最高不得超过 4 学分。", "依据", java.util.UUID.randomUUID(),
                                "学生手册", "2025", "第 10 条", "本科生"),
                        AnswerRoute.HIGH_RISK_RULE, false, List.of(), List.of()), 3);
        try (var worker = Executors.newSingleThreadExecutor(); var timer = Executors.newSingleThreadScheduledExecutor()) {
            var manager = new CampusCreditAgentManager(service, worker, timer, Duration.ofSeconds(5));
            var started = manager.start(new CampusCreditAgentService.Request("公选课本学期学分上限？", List.of()));
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            var stopping = manager.cancel(started.runId());
            assertThat(stopping.state()).isEqualTo(AgentRunState.STOP_REQUESTED);
            assertThat(stopping.terminated()).isFalse();
            release.countDown();
            CampusCreditAgentManager.Snapshot ended = null;
            for (int i = 0; i < 100; i++) {
                ended = manager.find(started.runId());
                if (ended.terminated()) break;
                Thread.sleep(10);
            }
            assertThat(ended.state()).isEqualTo(AgentRunState.CANCELLED);
            assertThat(ended.stopReason()).isEqualTo(AgentStopReason.USER_REQUEST);
            assertThat(ended.terminated()).isTrue();
        } finally {
            release.countDown();
        }
    }

    @Test
    void executorRejectionHasFailedTerminalState() {
        var service = new CampusCreditAgentService(prompt -> { throw new AssertionError("不应调用模型"); },
                new CampusToolRegistry(List.of(new CourseCreditSummaryTool())),
                question -> { throw new AssertionError("不应查询政策"); }, 3);
        try (var timer = Executors.newSingleThreadScheduledExecutor()) {
            var manager = new CampusCreditAgentManager(service,
                    task -> { throw new java.util.concurrent.RejectedExecutionException(); },
                    timer, Duration.ofSeconds(1));
            var snapshot = manager.start(new CampusCreditAgentService.Request("公选课学分", List.of()));
            assertThat(snapshot.state()).isEqualTo(AgentRunState.FAILED);
            assertThat(snapshot.failureCategory()).isEqualTo("EXECUTOR_REJECTED");
            assertThat(snapshot.terminated()).isTrue();
        }
    }

    @Test
    void timedOutRunWaitsForBlockedModelBeforeTermination() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        var service = new CampusCreditAgentService(prompt -> {
            entered.countDown();
            while (release.getCount() > 0) {
                try { release.await(10, TimeUnit.MILLISECONDS); }
                catch (InterruptedException ignored) { /* 供应商阻塞调用可能忽略中断。 */ }
            }
            return new ChatResponse(List.of(new Generation(new AssistantMessage("", java.util.Map.of(), List.of()))));
        }, new CampusToolRegistry(List.of(new CourseCreditSummaryTool())),
                question -> new LocalPreviewConversationFacade.ConversationReply(
                        ElectiveRetakeReplyType.ANSWER, "规则", new LocalPreviewConversationFacade.ConversationAnswer(
                                "每学期公共选修课最高不得超过 4 学分。", "依据", java.util.UUID.randomUUID(),
                                "学生手册", "2025", "第 10 条", "本科生"),
                        AnswerRoute.HIGH_RISK_RULE, false, List.of(), List.of()), 3);
        try (var worker = Executors.newSingleThreadExecutor(); var timer = Executors.newSingleThreadScheduledExecutor()) {
            var manager = new CampusCreditAgentManager(service, worker, timer, Duration.ofMillis(30));
            var started = manager.start(new CampusCreditAgentService.Request("公选课本学期学分上限？", List.of()));
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            CampusCreditAgentManager.Snapshot stopping = null;
            for (int i = 0; i < 100; i++) {
                stopping = manager.find(started.runId());
                if (stopping.state() == AgentRunState.STOP_REQUESTED) break;
                Thread.sleep(10);
            }
            assertThat(stopping.state()).isEqualTo(AgentRunState.STOP_REQUESTED);
            assertThat(stopping.terminated()).isFalse();
            release.countDown();
            CampusCreditAgentManager.Snapshot ended = null;
            for (int i = 0; i < 100; i++) {
                ended = manager.find(started.runId());
                if (ended.terminated()) break;
                Thread.sleep(10);
            }
            assertThat(ended.state()).isEqualTo(AgentRunState.CANCELLED);
            assertThat(ended.stopReason()).isEqualTo(AgentStopReason.RUN_TIMEOUT);
            assertThat(ended.terminated()).isTrue();
        } finally {
            release.countDown();
        }
    }

    @Test
    void simultaneousRunsKeepSeparateIdsAndResults() throws Exception {
        CountDownLatch entered = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        var service = new CampusCreditAgentService(prompt -> {
            entered.countDown();
            try { release.await(2, TimeUnit.SECONDS); }
            catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
            return new ChatResponse(List.of(new Generation(new AssistantMessage("", java.util.Map.of(), List.of()))));
        }, new CampusToolRegistry(List.of(new CourseCreditSummaryTool())),
                question -> new LocalPreviewConversationFacade.ConversationReply(
                        ElectiveRetakeReplyType.ANSWER, "规则", new LocalPreviewConversationFacade.ConversationAnswer(
                                "每学期公共选修课最高不得超过 4 学分。", "依据", java.util.UUID.randomUUID(),
                                "学生手册", "2025", "第 10 条", "本科生"),
                        AnswerRoute.HIGH_RISK_RULE, false, List.of(), List.of()), 3);
        try (var worker = Executors.newFixedThreadPool(2); var timer = Executors.newSingleThreadScheduledExecutor()) {
            var manager = new CampusCreditAgentManager(service, worker, timer, Duration.ofSeconds(5));
            var a = manager.start(new CampusCreditAgentService.Request("公选课甲本学期学分上限？", List.of()));
            var b = manager.start(new CampusCreditAgentService.Request("公选课乙本学期学分上限？", List.of()));
            assertThat(a.runId()).isNotEqualTo(b.runId());
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            release.countDown();
            for (int i = 0; i < 100 && (!manager.find(a.runId()).terminated()
                    || !manager.find(b.runId()).terminated()); i++) Thread.sleep(10);
            assertThat(manager.find(a.runId()).status()).isEqualTo(CampusCreditAgentService.Status.NEED_CLARIFICATION);
            assertThat(manager.find(b.runId()).status()).isEqualTo(CampusCreditAgentService.Status.NEED_CLARIFICATION);
        } finally {
            release.countDown();
        }
    }
}
