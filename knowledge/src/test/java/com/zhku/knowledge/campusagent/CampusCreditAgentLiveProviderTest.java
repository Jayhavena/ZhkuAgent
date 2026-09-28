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
import com.zhku.knowledge.rag.ApiKeyResolver;
import com.zhku.knowledge.rag.RagProviderProfile;
import com.zhku.knowledge.tools.CampusToolRegistry;
import com.zhku.knowledge.tools.CourseCreditSummaryTool;
import com.zhku.knowledge.tools.modelcall.CourseCreditChatModelFactory;
import com.zhku.knowledge.web.CampusAgentPreviewController;
import com.zhku.knowledge.web.LocalPreviewAnswerChain;
import com.zhku.knowledge.web.LocalPreviewConversationFacade;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.http.MediaType;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 仅在显式选择时验证真实模型选工具；审核资料只写入专用临时 PostgreSQL。 */
@EnabledIfEnvironmentVariable(named = "DASHSCOPE_API_KEY", matches = ".+")
@EnabledIfSystemProperty(named = "zhku.campus.live", matches = "true")
class CampusCreditAgentLiveProviderTest {

    @Test
    void liveModelSelectsApprovedToolsAndAnswersFromPublishedRule() throws Exception {
        String url = System.getProperty("zhku.db.url");
        String user = System.getProperty("zhku.db.user", "zhkuagent");
        // 真供应商测试会发布资料；只接受临时库的固定库名和本机地址，拒绝误写演示库。
        assertThat(url).matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/zhkuagent_e2e");
        assertThat(url).doesNotContain(":54329/");
        String password = ZhkuDatabaseTestConfig.databasePassword();
        ZhkuFlywayTestSupport.migrate(url, user, password);
        var dataSource = new DriverManagerDataSource(url, user, password);
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
        var profile = new RagProviderProfile("dashscope",
                "https://dashscope.aliyuncs.com/compatible-mode/v1", "DASHSCOPE_API_KEY",
                "qwen-plus", "text-embedding-v4", 1024, Duration.ofSeconds(30),
                "dashscope-text-embedding-v4-1024");
        var model = CourseCreditChatModelFactory.create(profile, ApiKeyResolver.systemEnvironment());

        try (var timer = Executors.newSingleThreadScheduledExecutor()) {
            var service = new CampusCreditAgentService(model,
                    new CampusToolRegistry(List.of(new CourseCreditSummaryTool())), question -> {
                        UUID sessionId = facade.createConversation().sessionId();
                        return facade.sendMessage(sessionId, question);
                    }, 3);
            var manager = new CampusCreditAgentManager(service, Runnable::run, timer, Duration.ofSeconds(60));
            var mvc = MockMvcBuilders.standaloneSetup(new CampusAgentPreviewController(manager)).build();

            mvc.perform(post("/zhku/local-preview/campus-agent/v1/runs")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"question":"这两门虚构公选课学分合计及每学期上限？","courses":[
                                      {"courseId":"ART01","category":"公共选修课","credits":1.5},
                                      {"courseId":"HIS02","category":"公共选修课","credits":2}]}
                                    """))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.state").value("SUCCEEDED"))
                    .andExpect(jsonPath("$.terminated").value(true))
                    .andExpect(jsonPath("$.answer").value(org.hamcrest.Matchers.containsString("合计 3.5 学分")))
                    .andExpect(jsonPath("$.answer").value(org.hamcrest.Matchers.containsString("4 学分")))
                    .andExpect(jsonPath("$.source.sourceId").value(publication.sourceId().toString()))
                    .andExpect(jsonPath("$.source.sourceLocator").value("PDF 第 76—77 页，第 8、10 条"));
        }
    }
}
