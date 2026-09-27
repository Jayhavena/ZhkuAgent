package com.zhku.knowledge.eval;

import com.zhku.knowledge.ZhkuDatabaseTestConfig;
import com.zhku.knowledge.conversation.ConversationStore;
import com.zhku.knowledge.conversation.InMemoryConversationStore;
import com.zhku.knowledge.conversation.elective.ElectiveRetakeConversationService;
import com.zhku.knowledge.conversation.elective.ElectiveRetakeReplyType;
import com.zhku.knowledge.conversation.elective.JdbcElectiveRetakeEvidenceFinder;
import com.zhku.knowledge.conversation.publicelective.JdbcCreditLimitEvidenceFinder;
import com.zhku.knowledge.conversation.publicelective.PublicElectiveConversationRouter;
import com.zhku.knowledge.conversation.publicelective.PublicElectiveCreditLimitService;
import com.zhku.knowledge.routing.AnswerRoute;
import com.zhku.knowledge.web.LocalPreviewAnswerChain;
import com.zhku.knowledge.web.LocalPreviewConversationFacade;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/** 共享演示库只读复验：问题必须经真实 JDBC 证据、规则路由与预览门面作答。 */
class PublicElectiveRetakeSharedCorpusTest {

    @Test
    void shouldAnswerExplicitPublicElectiveRetakeFromApprovedCorpus() throws Exception {
        String url = System.getProperty("zhku.db.url", "jdbc:postgresql://127.0.0.1:54329/zhkuagent");
        String user = System.getProperty("zhku.db.user", "zhkuagent");
        String password = ZhkuDatabaseTestConfig.databasePassword();
        EvaluationDataset dataset = new EvaluationDatasetLoader().loadDataset(
                Paths.get("src", "test", "resources", "eval", "datasets", "five-level-routing-v1.json"));
        assertThat(dataset.corpusFingerprint()).isEqualTo(
                "[EVAL-CORPUS] 学生手册2025版(PDF条级定位) + 当期选课通知2026-2027-1 @ 共享演示库");

        DataSource dataSource = new DriverManagerDataSource(url, user, password);
        ConversationStore store = new InMemoryConversationStore();
        Clock clock = Clock.systemUTC();
        Duration ttl = Duration.ofMinutes(10);
        ElectiveRetakeConversationService retake = new ElectiveRetakeConversationService(
                store, new JdbcElectiveRetakeEvidenceFinder(dataSource), clock, ttl);
        PublicElectiveConversationRouter router = new PublicElectiveConversationRouter(
                store, retake,
                new PublicElectiveCreditLimitService(new JdbcCreditLimitEvidenceFinder(dataSource)),
                clock);
        LocalPreviewConversationFacade facade = new LocalPreviewConversationFacade(
                store, new LocalPreviewAnswerChain(store, router, null, null, clock), clock, ttl);

        LocalPreviewConversationFacade.ConversationReply reply = facade.sendMessage(
                facade.createConversation().sessionId(), "我公选课不及格了要重修怎么办");

        assertThat(reply.route()).isEqualTo(AnswerRoute.HIGH_RISK_RULE);
        assertThat(reply.type()).isEqualTo(ElectiveRetakeReplyType.ANSWER);
        assertThat(reply.answer().conclusion()).isEqualTo("公共选修课成绩不合格，不安排重修。");
        assertThat(reply.answer().sourceLocator()).isEqualTo("PDF 第 77 页，第 18 条");
        assertThat(reply.handoffRequired()).isFalse();
    }
}
