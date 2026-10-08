package com.zhku.knowledge.campusagent;

import com.zhku.knowledge.conversation.elective.ElectiveRetakeReplyType;
import com.zhku.knowledge.routing.AnswerRoute;
import com.zhku.knowledge.tools.CampusToolRegistry;
import com.zhku.knowledge.tools.CourseCreditSummaryTool;
import com.zhku.knowledge.web.LocalPreviewConversationFacade;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.prompt.Prompt;
import static org.assertj.core.api.Assertions.assertThat;

/** 只验证模型输入中的服务端进度反馈；替身响应不能证明真实供应商会遵循提示。 */
class CampusCreditModelProgressTest {
    @Test void successfulLookupIsReportedAsExecutedBeforeNextDecision() {
        var prompts = new ArrayList<Prompt>();
        ChatModel model = prompt -> {
            prompts.add(prompt);
            return response(prompts.size() == 1 ? "lookup_public_elective_credit_limit" : "summarize_course_credits",
                    prompts.size() == 1 ? "{}" : "{\"courses\":[{\"courseId\":\"ART01\",\"category\":\"公共选修课\",\"credits\":1.5}]}");
        };
        var answer = new LocalPreviewConversationFacade.ConversationAnswer("每学期最高4学分", "合成规则",
                UUID.randomUUID(), "合成演示学分规则", "web-demo-v1", "合成片段第1条", "仅用于合成演示");
        var service = new CampusCreditAgentService(model, new CampusToolRegistry(List.of(new CourseCreditSummaryTool())),
                q -> new LocalPreviewConversationFacade.ConversationReply(ElectiveRetakeReplyType.ANSWER,
                        "合成规则", answer, AnswerRoute.HIGH_RISK_RULE, false, List.of(), List.of()), 3);
        var result = service.run(new CampusCreditAgentService.Request("合成课程学分和上限？", List.of(
                new CampusCreditAgentService.CourseInput("ART01", "公共选修课", new BigDecimal("1.5")))));
        assertThat(result.state().name()).isEqualTo("SUCCEEDED");
        assertThat(result.source()).isEqualTo(answer);
        assertThat(prompts).hasSize(2);
        // 检查真正送入模型的上下文边界，不能只把政策正文当作已执行事实。
        assertThat(prompts.get(0).getContents().contains("服务端已执行工具")).isFalse();
        String next = prompts.get(1).getContents();
        // 失败时只输出boolean，不让完整Prompt进入XML或控制台。
        assertThat(next.contains("服务端已执行工具 lookup_public_elective_credit_limit({})")
                && next.contains("不要再次查询") && next.contains("summarize_course_credits")
                && next.contains("ART01") && next.contains("1.5")).isTrue();
        assertThat(result.steps()).extracting(CampusCreditAgentService.Step::name).containsExactly(
                "POLICY_ROUTE", "lookup_public_elective_credit_limit", "summarize_course_credits");
    }
    private static ChatResponse response(String name, String arguments) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage("", Map.of(), List.of(
                new AssistantMessage.ToolCall(UUID.randomUUID().toString(), "function", name, arguments))))));
    }
}
