package com.zhku.knowledge.controlled.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhku.agent.agent.runtime.AgentRunContext;
import com.zhku.agent.agent.runtime.AgentStopReason;
import com.zhku.knowledge.campusagent.CampusCreditAgentService;
import com.zhku.knowledge.conversation.elective.ElectiveRetakeReplyType;
import com.zhku.knowledge.rag.ApiKeyResolver;
import com.zhku.knowledge.rag.RagProviderProfile;
import com.zhku.knowledge.routing.AnswerRoute;
import com.zhku.knowledge.tools.CampusToolRegistry;
import com.zhku.knowledge.tools.CourseCreditSummaryTool;
import com.zhku.knowledge.tools.modelcall.CourseCreditChatModelFactory;
import com.zhku.knowledge.web.LocalPreviewConversationFacade;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.springframework.ai.chat.model.ChatModel;

/** 显式供应商诊断：真实Service/模型，合成政策替身；不代表受控HTTP或数据库验收。 */
public final class ControlledModelDecisionDiagnostic {
    private static final ObjectMapper JSON = new ObjectMapper();
    public static void main(String[] args) throws Exception {
        if (!Arrays.equals(args, new String[]{"--live-diagnostic"})) throw new IllegalArgumentException();
        var rows = new ArrayList<Map<String, Object>>();
        var report = new LinkedHashMap<String, Object>();
        report.put("scope", "real model and Service / synthetic policy fixture / no DB or HTTP");
        var profile = new RagProviderProfile("dashscope", "https://dashscope.aliyuncs.com/compatible-mode/v1",
                "DASHSCOPE_API_KEY", "qwen-plus", "text-embedding-v4", 1024, Duration.ofSeconds(30),
                "dashscope-text-embedding-v4-1024");
        var context = AgentRunContext.create("合成诊断");
        try (var transport = CourseCreditChatModelFactory.open(profile, ApiKeyResolver.systemEnvironment());
                var timer = Executors.newSingleThreadScheduledExecutor()) {
            timer.schedule(() -> context.requestStop(AgentStopReason.RUN_TIMEOUT), 30, TimeUnit.SECONDS);
            ChatModel observed = prompt -> {
                // 只允许这一任务的两次决策；不保存供应商原文、字段值或完整Prompt。
                if (rows.size() >= 2) throw new IllegalStateException();
                var response = transport.model().call(prompt);
                var row = new LinkedHashMap<String, Object>();
                row.put("decision", rows.size() + 1);
                var calls = response.getResult().getOutput().getToolCalls();
                row.put("callCount", calls.size());
                if (calls.size() == 1) {
                    var call = calls.getFirst();
                    String name = call.name();
                    row.put("tool", name.equals("lookup_public_elective_credit_limit") ? "LOOKUP"
                            : name.equals("summarize_course_credits") ? "CALCULATE" : "OTHER");
                    try {
                        var root = JSON.readTree(call.arguments());
                        row.put("rootObject", root.isObject()); row.put("rootFieldCount", root.size());
                        row.put("lookupEmptyObject", root.isObject() && root.isEmpty());
                        row.put("calculatorParserAccepts", CourseCreditSummaryTool.acceptsArguments(call.arguments()));
                        var courses = root.path("courses"); row.put("coursesArray", courses.isArray());
                        row.put("courseCount", courses.size());
                        var items = new ArrayList<Map<String, Object>>();
                        for (var item : courses) {
                            var entry = new LinkedHashMap<String, Object>(); entry.put("fieldCount", item.size());
                            entry.put("idText", item.path("courseId").isTextual());
                            entry.put("categoryText", item.path("category").isTextual());
                            entry.put("creditsNumber", item.path("credits").isNumber());
                            items.add(entry);
                        }
                        row.put("itemStructures", items);
                    } catch (Exception invalid) { row.put("jsonParseable", false); }
                }
                rows.add(row); return response;
            };
            var answer = new LocalPreviewConversationFacade.ConversationAnswer(
                    "每学期公共选修课最高不得超过 4 学分", "合成规则", ControlledWebDemoSetupCli.CREDIT_SOURCE,
                    "合成演示学分规则", "web-demo-v1", "合成片段第1条", "仅用于合成演示");
            var policy = new LocalPreviewConversationFacade.ConversationReply(ElectiveRetakeReplyType.ANSWER,
                    "合成规则", answer, AnswerRoute.HIGH_RISK_RULE, false, List.of(), List.of());
            var service = new CampusCreditAgentService(observed, new CampusToolRegistry(List.of(new CourseCreditSummaryTool())), q -> policy, 2);
            var result = service.run(context, new CampusCreditAgentService.Request(
                    "这两门虚构公共选修课学分合计以及每学期上限是多少？", List.of(
                            new CampusCreditAgentService.CourseInput("ART01", "公共选修课", new BigDecimal("1.5")),
                            new CampusCreditAgentService.CourseInput("HIS02", "公共选修课", new BigDecimal("2")))));
            context.publishTermination();
            report.put("state", result.state().name()); report.put("failureCategory", result.failureCategory());
            timer.shutdownNow();
        }
        report.put("decisions", rows);
        JSON.writerWithDefaultPrettyPrinter().writeValue(Path.of(System.getenv("ZHKU_DIAGNOSTIC_OUTPUT")).toFile(), report);
        System.out.println("DIAGNOSTIC_COMPLETE decisions=" + rows.size());
    }
}
