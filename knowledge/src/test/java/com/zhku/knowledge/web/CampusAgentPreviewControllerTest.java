package com.zhku.knowledge.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CampusAgentPreviewControllerTest {
    @Test
    void evictedRunGetAndCancelRemain404() throws Exception {
        var service = new CampusCreditAgentService(prompt -> response(),
                new CampusToolRegistry(List.of(new CourseCreditSummaryTool())), question -> policy(), 3);
        try (var timer = Executors.newSingleThreadScheduledExecutor()) {
            var manager = new CampusCreditAgentManager(service, Runnable::run, timer, Duration.ofSeconds(2));
            var first = manager.start(new CampusCreditAgentService.Request("公选课学分", List.of()));
            for (int i = 0; i < 128; i++)
                manager.start(new CampusCreditAgentService.Request("公选课学分", List.of()));
            var mvc = MockMvcBuilders.standaloneSetup(new CampusAgentPreviewController(manager, new com.zhku.knowledge.campusagent.CampusAgentResultVisibility(candidate ->
                new com.zhku.knowledge.campusagent.archive.CampusRunArchive.Visibility(
                        com.zhku.knowledge.campusagent.archive.CampusRunArchive.VisibilityStatus.VISIBLE,
                        com.zhku.knowledge.campusagent.archive.CampusRunArchive.Reason.NONE)))).build();
            mvc.perform(get("/zhku/local-preview/campus-agent/v1/runs/" + first.runId()))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RUN_NOT_FOUND"));
            mvc.perform(post("/zhku/local-preview/campus-agent/v1/runs/" + first.runId() + "/cancel"))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RUN_NOT_FOUND"));
        }
    }

    @Test
    void rejectedTaskReturns503RatherThanAccepted() throws Exception {
        var service = new CampusCreditAgentService(prompt -> response(),
                new CampusToolRegistry(List.of(new CourseCreditSummaryTool())), question -> policy(), 3);
        try (var timer = Executors.newSingleThreadScheduledExecutor()) {
            var manager = new CampusCreditAgentManager(service,
                    task -> {
                        throw new java.util.concurrent.RejectedExecutionException();
                    }, timer, Duration.ofSeconds(2));
            var mvc = MockMvcBuilders.standaloneSetup(new CampusAgentPreviewController(manager, new com.zhku.knowledge.campusagent.CampusAgentResultVisibility(candidate ->
                new com.zhku.knowledge.campusagent.archive.CampusRunArchive.Visibility(
                        com.zhku.knowledge.campusagent.archive.CampusRunArchive.VisibilityStatus.VISIBLE,
                        com.zhku.knowledge.campusagent.archive.CampusRunArchive.Reason.NONE)))).build();
            mvc.perform(post("/zhku/local-preview/campus-agent/v1/runs")
                    .contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.failureCategory").value("EXECUTOR_REJECTED"));
        }
    }

    @Test
    void queuedModelFailureIsAcceptedJustLikeFastFailure() throws Exception {
        var queue = new java.util.ArrayList<Runnable>();
        var service = new CampusCreditAgentService(prompt -> {
            throw new IllegalStateException("供应商异常");
        },
                new CampusToolRegistry(List.of(new CourseCreditSummaryTool())), question -> policy(), 3);
        try (var timer = Executors.newSingleThreadScheduledExecutor()) {
            var manager = new CampusCreditAgentManager(service, queue::add, timer, Duration.ofSeconds(5));
            var mvc = MockMvcBuilders.standaloneSetup(new CampusAgentPreviewController(manager, new com.zhku.knowledge.campusagent.CampusAgentResultVisibility(candidate ->
                new com.zhku.knowledge.campusagent.archive.CampusRunArchive.Visibility(
                        com.zhku.knowledge.campusagent.archive.CampusRunArchive.VisibilityStatus.VISIBLE,
                        com.zhku.knowledge.campusagent.archive.CampusRunArchive.Reason.NONE)))).build();
            String body = mvc.perform(post("/zhku/local-preview/campus-agent/v1/runs")
                    .contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                    .andExpect(status().isAccepted()).andExpect(jsonPath("$.state").value("CREATED"))
                    .andReturn().getResponse().getContentAsString();
            queue.remove(0).run();
            mvc.perform(get("/zhku/local-preview/campus-agent/v1/runs/"
                    + new ObjectMapper().readTree(body).path("runId").asText()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("FAILED"))
                    .andExpect(jsonPath("$.failureCategory").value("MODEL_FAILURE"));
        } finally {
            queue.forEach(Runnable::run);
        }
    }

    @Test
    void acceptedFastModelFailureStillReturns202() throws Exception {
        var service = new CampusCreditAgentService(prompt -> {
            throw new IllegalStateException("供应商异常");
        },
                new CampusToolRegistry(List.of(new CourseCreditSummaryTool())), question -> policy(), 3);
        try (var timer = Executors.newSingleThreadScheduledExecutor()) {
            var mvc = MockMvcBuilders.standaloneSetup(new CampusAgentPreviewController(
                    new CampusCreditAgentManager(service, Runnable::run, timer, Duration.ofSeconds(2)))).build();
            mvc.perform(post("/zhku/local-preview/campus-agent/v1/runs")
                    .contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                    .andExpect(status().isAccepted()).andExpect(jsonPath("$.state").value("FAILED"))
                    .andExpect(jsonPath("$.failureCategory").value("MODEL_FAILURE"));
        }
    }

    private static final String REQUEST = """
            {"question":"这两门虚构公选课学分合计及每学期上限？","courses":[
              {"courseId":"ART01","category":"公共选修课","credits":1.5},
              {"courseId":"HIS02","category":"公共选修课","credits":2}]}
            """;

    @Test
    void postAndGetExposeGroundedStepsAndTerminalState() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        var service = new CampusCreditAgentService(prompt -> response(calls.incrementAndGet() == 1
                ? call("lookup_public_elective_credit_limit", "{}")
                : call("summarize_course_credits", """
                    {"courses":[{"courseId":"ART01","category":"公共选修课","credits":1.5},
                                {"courseId":"HIS02","category":"公共选修课","credits":2}]}
                    """)), new CampusToolRegistry(List.of(new CourseCreditSummaryTool())),
                question -> policy(), 3);
        try (var timer = Executors.newSingleThreadScheduledExecutor()) {
            var manager = new CampusCreditAgentManager(service, Runnable::run, timer, Duration.ofSeconds(2));
            var mvc = MockMvcBuilders.standaloneSetup(new CampusAgentPreviewController(manager, new com.zhku.knowledge.campusagent.CampusAgentResultVisibility(candidate ->
                new com.zhku.knowledge.campusagent.archive.CampusRunArchive.Visibility(
                        com.zhku.knowledge.campusagent.archive.CampusRunArchive.VisibilityStatus.VISIBLE,
                        com.zhku.knowledge.campusagent.archive.CampusRunArchive.Reason.NONE)))).build();
            String body = mvc.perform(post("/zhku/local-preview/campus-agent/v1/runs")
                    .contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.state").value("SUCCEEDED"))
                    .andExpect(jsonPath("$.terminated").value(true))
                    .andExpect(jsonPath("$.answer").value(org.hamcrest.Matchers.containsString("3.5 学分")))
                    .andExpect(jsonPath("$.source.sourceLocator").value("PDF 第 76—77 页，第 8、10 条"))
                    .andReturn().getResponse().getContentAsString();
            JsonNode payload = new ObjectMapper().readTree(body);
            var fields = new java.util.ArrayList<String>();
            payload.fieldNames().forEachRemaining(fields::add);
            assertThat(fields).containsExactlyInAnyOrder("runId", "state", "stopReason", "terminated", "status",
                    "answer", "failureCategory", "steps", "source");
            assertThat(payload.path("steps").size()).isEqualTo(3);
            mvc.perform(get("/zhku/local-preview/campus-agent/v1/runs/" + payload.path("runId").asText()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.failureCategory").value("NONE"));
        }
    }

    @Test
    void malformedRequestAndUnknownRunUseIndependentV1Errors() throws Exception {
        var service = new CampusCreditAgentService(prompt -> response(),
                new CampusToolRegistry(List.of(new CourseCreditSummaryTool())), question -> policy(), 3);
        try (var timer = Executors.newSingleThreadScheduledExecutor()) {
            var mvc = MockMvcBuilders.standaloneSetup(new CampusAgentPreviewController(
                    new CampusCreditAgentManager(service, Runnable::run, timer, Duration.ofSeconds(2)))).build();
            mvc.perform(post("/zhku/local-preview/campus-agent/v1/runs")
                    .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isBadRequest());
            mvc.perform(get("/zhku/local-preview/campus-agent/v1/runs/" + UUID.randomUUID()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("RUN_NOT_FOUND"));
        }
    }

    private static LocalPreviewConversationFacade.ConversationReply policy() {
        return new LocalPreviewConversationFacade.ConversationReply(ElectiveRetakeReplyType.ANSWER,
                "每学期公共选修课最高不得超过 4 学分。",
                new LocalPreviewConversationFacade.ConversationAnswer(
                        "每学期公共选修课最高不得超过 4 学分。", "依据", UUID.randomUUID(),
                        "本科生学生手册（2025版）", "2025版", "PDF 第 76—77 页，第 8、10 条", "本科生"),
                AnswerRoute.HIGH_RISK_RULE, false, List.of(), List.of());
    }

    private static AssistantMessage.ToolCall call(String name, String args) {
        return new AssistantMessage.ToolCall("call-1", "function", name, args);
    }

    private static ChatResponse response(AssistantMessage.ToolCall... calls) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage("", java.util.Map.of(), List.of(calls)))));
    }
}
