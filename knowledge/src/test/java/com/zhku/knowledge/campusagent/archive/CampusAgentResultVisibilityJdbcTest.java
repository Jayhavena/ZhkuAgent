package com.zhku.knowledge.campusagent.archive;

import com.zhku.agent.agent.runtime.AgentRunState;
import com.zhku.knowledge.campusagent.CampusAgentResultVisibility;
import com.zhku.knowledge.campusagent.CampusCreditAgentManager;
import com.zhku.knowledge.campusagent.CampusCreditAgentService;
import com.zhku.knowledge.conversation.elective.ElectiveRetakeReplyType;
import com.zhku.knowledge.routing.AnswerRoute;
import com.zhku.knowledge.tools.CampusToolRegistry;
import com.zhku.knowledge.tools.CourseCreditSummaryTool;
import com.zhku.knowledge.web.CampusAgentPreviewController;
import com.zhku.knowledge.web.LocalPreviewConversationFacade;
import org.junit.jupiter.api.BeforeAll;
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
import java.util.concurrent.atomic.AtomicInteger;

import static com.zhku.knowledge.campusagent.archive.CampusRunArchive.*;
import static org.assertj.core.api.Assertions.assertThat;

/** 只使用 ArchiveTestDatabase 验证过的临时库；模型和输入均为合成替身。 */
class CampusAgentResultVisibilityJdbcTest {
    @BeforeAll
    static void migrate() {
        ArchiveTestDatabase.migrate();
    }

    @Test
    void withdrawalAfterLookupHidesFirstOnlineResponseAndPersistedHistory() throws Exception {
        exercise(true);
    }

    @Test
    void completedOnlineAndHistoryRecheckAllCurrentSourceConditions() throws Exception {
        exercise(false);
    }

    private static void exercise(boolean revokeDuringCalculation) throws Exception {
        var dataSource = ArchiveTestDatabase.dataSource();
        UUID source;
        try (var c = dataSource.getConnection()) {
            source = CampusRunHistoryVisibilityIntegrationTest.insertSource(c);
        }
        UUID run = null;
        try (var owner = new CampusRunArchiveOwner(dataSource, Limits.defaults());
                var timer = Executors.newSingleThreadScheduledExecutor()) {
            owner.start();
            try (var archive = new JdbcCampusRunArchiveStore(dataSource, owner, Limits.defaults(), new CampusRunHistoryVisibility())) {
                var calls = new AtomicInteger();
                var service = new CampusCreditAgentService(prompt -> {
                    int n = calls.incrementAndGet();
                    // V2 禁止未发布资料保持 STUDENT_VISIBLE；撤回必须同时降为内部可见。
                    if (n == 2 && revokeDuringCalculation) update(source,
                            "visibility_scope='INTERNAL_ONLY',publication_status='ARCHIVED'");
                    var call = new AssistantMessage.ToolCall("c" + n, "function",
                            n == 1 ? "lookup_public_elective_credit_limit" : "summarize_course_credits",
                            n == 1 ? "{}" : "{\"courses\":[{\"courseId\":\"C1\",\"category\":\"公共选修课\",\"credits\":3.5}]}");
                    return new ChatResponse(List.of(new Generation(new AssistantMessage("", Map.of(), List.of(call)))));
                }, new CampusToolRegistry(List.of(new CourseCreditSummaryTool())), question ->
                        new LocalPreviewConversationFacade.ConversationReply(ElectiveRetakeReplyType.ANSWER,
                                "合成政策", CampusRunHistoryVisibilityIntegrationTest.candidate(source).source(),
                                AnswerRoute.HIGH_RISK_RULE, false, List.of(), List.of()), 3);
                var manager = new CampusCreditAgentManager(service, Runnable::run, timer, Duration.ofSeconds(30), archive);
                var controller = new CampusAgentPreviewController(manager, new CampusAgentResultVisibility(dataSource));
                var history = new CampusRunHistoryService(archive);
                var request = new CampusCreditAgentService.Request("合成学分",
                        List.of(new CampusCreditAgentService.CourseInput("C1", "公共选修课", new BigDecimal("3.5"))));
                var first = (CampusCreditAgentManager.Snapshot) controller.start(request).getBody();
                run = first.runId();
                assertThat(calls.get()).isEqualTo(2);
                assertThat(first.state()).isEqualTo(AgentRunState.SUCCEEDED);
                assertThat(first.failureCategory()).isEqualTo("NONE");
                assertThat(history.find(run).execution().state()).isEqualTo(AgentRunState.SUCCEEDED);
                if (revokeDuringCalculation) {
                    assertHidden(first);
                    assertThat(history.find(run).result()).isNull();
                } else {
                    assertThat(first.answer()).contains("3.5 学分");
                    assertThat(first.source().sourceId()).isEqualTo(source);
                    assertThat(history.find(run).result()).isNotNull();
                    // 分享许可撤回也须与 INTERNAL_ONLY 一起提交，不能用违反数据库约束的状态验收门禁。
                    for (var change : new String[]{"visibility_scope='INTERNAL_ONLY',publication_status='ARCHIVED'",
                            "visibility_scope='INTERNAL_ONLY'", "visibility_scope='INTERNAL_ONLY',sharing_rights_confirmed=false",
                            "source_version='synthetic-v2'", "publication_scope='另一范围'"}) {
                        update(source, "publication_status='PUBLISHED',visibility_scope='STUDENT_VISIBLE',sharing_rights_confirmed=true,"
                                + "source_version='synthetic-v1',publication_scope='合成本科生'");
                        update(source, change);
                        assertHidden((CampusCreditAgentManager.Snapshot) controller.find(run).getBody());
                        assertHidden((CampusCreditAgentManager.Snapshot) controller.cancel(run).getBody());
                        var detail = history.find(run);
                        assertThat(detail.result()).isNull();
                        assertThat(detail.resultVisibility().status()).isEqualTo(VisibilityStatus.HIDDEN);
                        assertThat(detail.execution().state()).isEqualTo(AgentRunState.SUCCEEDED);
                        assertThat(detail.provenance()).isEqualTo("REFERENCE_ONLY");
                    }
                }
                manager.close();
            }
        } finally {
            try (var c = dataSource.getConnection()) {
                if (run != null) {
                    try (var p = c.prepareStatement("DELETE FROM campus_agent_run_archive WHERE run_id=?")) {
                        p.setObject(1, run);
                        p.executeUpdate();
                    }
                }
                CampusRunHistoryVisibilityIntegrationTest.deleteSource(c, source);
            }
        }
    }

    private static void assertHidden(CampusCreditAgentManager.Snapshot snapshot) {
        assertThat(snapshot.state()).isEqualTo(AgentRunState.SUCCEEDED);
        assertThat(snapshot.terminated()).isTrue();
        assertThat(snapshot.failureCategory()).isEqualTo("NONE");
        assertThat(snapshot.answer()).contains("不可展示").doesNotContain("3.5 学分", "4 学分", "合成第1条");
        assertThat(snapshot.steps()).isEmpty();
        assertThat(snapshot.source()).isNull();
    }

    private static void update(UUID source, String assignment) {
        try (var c = ArchiveTestDatabase.dataSource().getConnection();
                var p = c.prepareStatement("UPDATE knowledge_source SET " + assignment + " WHERE id=?")) {
            p.setObject(1, source);
            assertThat(p.executeUpdate()).isEqualTo(1);
        } catch (Exception failure) {
            throw new IllegalStateException("合成资料修改失败");
        }
    }
}
