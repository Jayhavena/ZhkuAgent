package com.zhku.knowledge.campusagent;

import com.zhku.knowledge.conversation.elective.ElectiveRetakeReplyType;
import com.zhku.knowledge.routing.AnswerRoute;
import com.zhku.knowledge.tools.CampusToolRegistry;
import com.zhku.knowledge.tools.CourseCreditSummaryTool;
import com.zhku.knowledge.web.LocalPreviewConversationFacade;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.model.tool.ToolCallingChatOptions;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class CampusCreditAgentServiceTest {
    private static final String COURSES = """
            {"courses":[{"courseId":"ART01","category":"公共选修课","credits":1.5},
                        {"courseId":"HIS02","category":"公共选修课","credits":2}]}
            """;

    @Test
    void modelSelectsPolicyAndCalculationBeforeGroundedAnswer() {
        AtomicInteger calls = new AtomicInteger();
        ChatModel model = prompt -> {
            ToolCallingChatOptions options = (ToolCallingChatOptions) prompt.getOptions();
            assertThat(options.getInternalToolExecutionEnabled()).isFalse();
            assertThat(options.getToolCallbacks()).extracting(callback -> callback.getToolDefinition().name())
                    .containsExactlyInAnyOrder("lookup_public_elective_credit_limit", "summarize_course_credits");
            return response(calls.incrementAndGet() == 1
                    ? call("lookup_public_elective_credit_limit", "{}")
                    : call("summarize_course_credits", COURSES));
        };
        var result = service(model, policy()).run(request());
        assertThat(result.status()).isEqualTo(CampusCreditAgentService.Status.ANSWERED);
        assertThat(result.answer()).contains("3.5", "4 学分", "学生手册", "第 8、10 条", "不能确认");
        assertThat(result.steps()).extracting(CampusCreditAgentService.Step::name)
                .contains("POLICY_ROUTE", "lookup_public_elective_credit_limit", "summarize_course_credits");
        assertThat(result.terminated()).isTrue();
    }

    @Test
    void missingCourseInputLeadsToClarificationWithoutClaimingCredits() {
        AtomicInteger policyCalls = new AtomicInteger();
        var service = new CampusCreditAgentService(prompt -> response(),
                new CampusToolRegistry(List.of(new CourseCreditSummaryTool())),
                question -> { policyCalls.incrementAndGet(); return policy(); }, 3);
        var result = service.run(
                new CampusCreditAgentService.Request("公选课本学期上限是多少？帮我算合计", List.of()));
        assertThat(result.status()).isEqualTo(CampusCreditAgentService.Status.NEED_CLARIFICATION);
        assertThat(result.answer()).contains("课程", "学分").doesNotContain("合计 0");
        assertThat(policyCalls.get()).isZero();
    }

    @Test
    void absentPublishedPolicyPreventsCalculation() {
        AtomicInteger calls = new AtomicInteger();
        var result = service(prompt -> { calls.incrementAndGet();
                    return response(call("lookup_public_elective_credit_limit", "{}")); },
                new LocalPreviewConversationFacade.ConversationReply(
                        ElectiveRetakeReplyType.EVIDENCE_UNAVAILABLE, "资料不足", null,
                        AnswerRoute.INSUFFICIENT_EVIDENCE, true, List.of(), List.of()))
                .run(request());
        assertThat(result.status()).isEqualTo(CampusCreditAgentService.Status.HANDOFF);
        assertThat(result.answer()).contains("人工").doesNotContain("4 学分");
        assertThat(calls.get()).isEqualTo(1);
        assertThat(result.steps()).extracting(CampusCreditAgentService.Step::name)
                .doesNotContain("summarize_course_credits");
    }

    @Test
    void illegalToolParametersAreRejected() {
        var result = service(prompt -> response(call("summarize_course_credits",
                "{\"courses\":[{\"courseId\":\"ART01\",\"category\":\"公共选修课\",\"credits\":-1}]}")),
                policy()).run(request());
        assertThat(result.status()).isEqualTo(CampusCreditAgentService.Status.HANDOFF);
        assertThat(result.failureCategory()).isEqualTo("INVALID_TOOL_ARGUMENT");
        assertThat(result.answer()).doesNotContain("合计");
    }

    @Test
    void validCalculationCannotRunBeforePolicyLookup() {
        var result = service(prompt -> response(call("summarize_course_credits", COURSES)), policy())
                .run(request());
        assertThat(result.failureCategory()).isEqualTo("POLICY_REQUIRED");
        assertThat(result.steps()).extracting(CampusCreditAgentService.Step::name)
                .doesNotContain("summarize_course_credits");
    }

    @Test
    void unknownToolAndModelFailureDoNotInventAnswer() {
        var unknown = service(prompt -> response(call("terminal", "{}")), policy()).run(request());
        assertThat(unknown.failureCategory()).isEqualTo("UNKNOWN_TOOL");
        var failed = service(prompt -> { throw new IllegalStateException("secret provider text"); },
                policy()).run(request());
        assertThat(failed.failureCategory()).isEqualTo("MODEL_FAILURE");
        assertThat(failed.answer()).doesNotContain("secret provider text", "4 学分");
    }

    @Test
    void fabricatedCourseCreditsAndMultipleCallsAreRejectedBeforeCalculation() {
        String fabricated = COURSES.replace("1.5", "2.5");
        var mismatch = service(prompt -> response(call("summarize_course_credits", fabricated)), policy())
                .run(request());
        assertThat(mismatch.failureCategory()).isEqualTo("INVALID_TOOL_ARGUMENT");
        var multiple = service(prompt -> response(call("lookup_public_elective_credit_limit", "{}"),
                call("summarize_course_credits", COURSES)), policy()).run(request());
        assertThat(multiple.failureCategory()).isEqualTo("TOOL_CALL_LIMIT");
        assertThat(multiple.steps()).extracting(CampusCreditAgentService.Step::name)
                .doesNotContain("summarize_course_credits");
    }

    @Test
    void policyFailureIsClassifiedWithoutLeakingException() {
        var service = new CampusCreditAgentService(prompt -> response(
                call("lookup_public_elective_credit_limit", "{}")),
                new CampusToolRegistry(List.of(new CourseCreditSummaryTool())),
                question -> { throw new IllegalStateException("private database detail"); }, 3);
        var result = service.run(request());
        assertThat(result.failureCategory()).isEqualTo("RETRIEVAL_FAILURE");
        assertThat(result.answer()).doesNotContain("private database detail");
    }

    private static CampusCreditAgentService.Request request() {
        return new CampusCreditAgentService.Request("这两门虚构公选课共多少学分，本学期制度上限是多少？",
                List.of(new CampusCreditAgentService.CourseInput("ART01", "公共选修课", new BigDecimal("1.5")),
                        new CampusCreditAgentService.CourseInput("HIS02", "公共选修课", new BigDecimal("2"))));
    }

    private static LocalPreviewConversationFacade.ConversationReply policy() {
        return new LocalPreviewConversationFacade.ConversationReply(
                ElectiveRetakeReplyType.ANSWER, "每学期公共选修课最高不得超过 4 学分。",
                new LocalPreviewConversationFacade.ConversationAnswer(
                        "每学期公共选修课最高不得超过 4 学分。", "以培养方案为准", UUID.randomUUID(),
                        "本科生学生手册（2025版）", "2025版", "PDF 第 76—77 页，第 8、10 条", "本校本科生"),
                AnswerRoute.HIGH_RISK_RULE, false, List.of(), List.of());
    }

    private static CampusCreditAgentService service(ChatModel model,
            LocalPreviewConversationFacade.ConversationReply policy) {
        return new CampusCreditAgentService(model,
                new CampusToolRegistry(List.of(new CourseCreditSummaryTool())), question -> policy, 3);
    }

    private static AssistantMessage.ToolCall call(String name, String args) {
        return new AssistantMessage.ToolCall("call-1", "function", name, args);
    }

    private static ChatResponse response(AssistantMessage.ToolCall... calls) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage("", java.util.Map.of(), List.of(calls)))));
    }
}
