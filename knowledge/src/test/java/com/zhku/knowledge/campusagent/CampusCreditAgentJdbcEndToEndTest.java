package com.zhku.knowledge.campusagent;

import com.zhku.knowledge.ZhkuDatabaseTestConfig;
import com.zhku.knowledge.ZhkuFlywayTestSupport;
import com.zhku.knowledge.conversation.InMemoryConversationStore;
import com.zhku.knowledge.conversation.elective.ElectiveRetakeConversationService;
import com.zhku.knowledge.conversation.elective.JdbcElectiveRetakeEvidenceFinder;
import com.zhku.knowledge.conversation.publicelective.JdbcCreditLimitEvidenceFinder;
import com.zhku.knowledge.conversation.publicelective.PublicElectiveConversationRouter;
import com.zhku.knowledge.conversation.publicelective.PublicElectiveCreditLimitService;
import com.zhku.knowledge.publication.ReviewedKnowledgeJsonReader;
import com.zhku.knowledge.publication.ReviewedKnowledgePublisher;
import com.zhku.knowledge.tools.CampusToolRegistry;
import com.zhku.knowledge.tools.CourseCreditSummaryTool;
import com.zhku.knowledge.web.CampusAgentPreviewController;
import com.zhku.knowledge.web.LocalPreviewAnswerChain;
import com.zhku.knowledge.web.LocalPreviewConversationFacade;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.http.MediaType;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.Path;
import java.sql.Connection;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 临时 PostgreSQL 中验证审核发布、五级规则链、受控工具和 HTTP 回答的真实数据边界。 */
class CampusCreditAgentJdbcEndToEndTest {
    private static final String URL = com.zhku.knowledge.ZhkuDatabaseTestConfig.databaseUrl();
    private static final String USER = System.getProperty("zhku.db.user", "zhkuagent");
    private static final String PASSWORD = ZhkuDatabaseTestConfig.databasePassword();
    private static final String REQUEST = """
            {"question":"这两门虚构公选课学分合计及每学期上限？","courses":[
              {"courseId":"ART01","category":"公共选修课","credits":1.5},
              {"courseId":"HIS02","category":"公共选修课","credits":2}]}
            """;
    private static final String COURSE_ARGUMENTS = """
            {"courses":[{"courseId":"ART01","category":"公共选修课","credits":1.5},
                        {"courseId":"HIS02","category":"公共选修课","credits":2}]}
            """;

    @Test
    void publishedEvidenceAnswersAndInvisibleEvidenceHandsOff() throws Exception {
        assertThat(URL).doesNotContain(":54329/");
        assertThat(PASSWORD).isNotBlank();
        ZhkuFlywayTestSupport.migrate(URL, USER, PASSWORD);
        var dataSource = new DriverManagerDataSource(URL, USER, PASSWORD);
        var publication = new ReviewedKnowledgeJsonReader().read(Path.of(
                "data/curation/2025版学生手册2025.9.2-公共选修单学期学分上限审核知识片段.json"));
        new ReviewedKnowledgePublisher(dataSource).publishReplacingScope(
                publication, "STUDENT_HANDBOOK", "PUBLIC_ELECTIVE_CREDIT_LIMIT");

        var store = new InMemoryConversationStore();
        Clock clock = Clock.systemUTC();
        Duration ttl = Duration.ofMinutes(10);
        var router = new PublicElectiveConversationRouter(store,
                new ElectiveRetakeConversationService(store,
                        new JdbcElectiveRetakeEvidenceFinder(dataSource), clock, ttl),
                new PublicElectiveCreditLimitService(new JdbcCreditLimitEvidenceFinder(dataSource)), clock);
        var facade = new LocalPreviewConversationFacade(store,
                new LocalPreviewAnswerChain(store, router, null, null, clock), clock, ttl);

        try (var timer = Executors.newSingleThreadScheduledExecutor()) {
            AtomicInteger modelCalls = new AtomicInteger();
            MockMvc mvc = mvc(facade, modelCalls, timer);
            mvc.perform(post("/zhku/local-preview/campus-agent/v1/runs")
                            .contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.state").value("SUCCEEDED"))
                    .andExpect(jsonPath("$.terminated").value(true))
                    .andExpect(jsonPath("$.answer").value(org.hamcrest.Matchers.containsString("合计 3.5 学分")))
                    .andExpect(jsonPath("$.answer").value(org.hamcrest.Matchers.containsString("4 学分")))
                    .andExpect(jsonPath("$.source.sourceId").value(publication.sourceId().toString()))
                    .andExpect(jsonPath("$.source.sourceLocator").value("PDF 第 76—77 页，第 8、10 条"));
            assertThat(modelCalls.get()).isEqualTo(2);

            // 权限撤回后用同一批数据库资料再次请求，检索层必须拒绝，不能沿用上次答案。
            try (Connection connection = dataSource.getConnection();
                 var statement = connection.prepareStatement(
                         "UPDATE knowledge_source SET visibility_scope = 'INTERNAL_ONLY' WHERE id = ?")) {
                statement.setObject(1, publication.sourceId());
                assertThat(statement.executeUpdate()).isEqualTo(1);
            }
            AtomicInteger hiddenCalls = new AtomicInteger();
            mvc(facade, hiddenCalls, timer)
                    .perform(post("/zhku/local-preview/campus-agent/v1/runs")
                            .contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.status").value("HANDOFF"))
                    .andExpect(jsonPath("$.failureCategory").value("INSUFFICIENT_EVIDENCE"))
                    .andExpect(jsonPath("$.source").doesNotExist())
                    .andExpect(jsonPath("$.answer").value(org.hamcrest.Matchers.not(
                            org.hamcrest.Matchers.containsString("4 学分"))));
            assertThat(hiddenCalls.get()).isEqualTo(1);
        }
    }

    private static MockMvc mvc(LocalPreviewConversationFacade facade, AtomicInteger calls,
            java.util.concurrent.ScheduledExecutorService timer) {
        var model = (org.springframework.ai.chat.model.ChatModel) prompt -> response(calls.incrementAndGet() == 1
                ? call("lookup_public_elective_credit_limit", "{}")
                : call("summarize_course_credits", COURSE_ARGUMENTS));
        var service = new CampusCreditAgentService(model,
                new CampusToolRegistry(List.of(new CourseCreditSummaryTool())), question -> {
                    UUID sessionId = facade.createConversation().sessionId();
                    return facade.sendMessage(sessionId, question);
                }, 3);
        var manager = new CampusCreditAgentManager(service, Runnable::run, timer, Duration.ofSeconds(5));
        return MockMvcBuilders.standaloneSetup(new CampusAgentPreviewController(manager,
                new com.zhku.knowledge.campusagent.CampusAgentResultVisibility(
                        new DriverManagerDataSource(URL, USER, PASSWORD)))).build();
    }

    private static AssistantMessage.ToolCall call(String name, String arguments) {
        return new AssistantMessage.ToolCall("call-1", "function", name, arguments);
    }

    private static ChatResponse response(AssistantMessage.ToolCall call) {
        return new ChatResponse(List.of(new Generation(
                new AssistantMessage("", java.util.Map.of(), List.of(call)))));
    }
}
