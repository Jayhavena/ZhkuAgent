package com.zhku.knowledge.web;

import com.zhku.knowledge.conversation.ConversationStore;
import com.zhku.knowledge.conversation.InMemoryConversationStore;
import com.zhku.knowledge.conversation.elective.ElectiveRetakeConversationService;
import com.zhku.knowledge.conversation.elective.JdbcElectiveRetakeEvidenceFinder;
import com.zhku.knowledge.conversation.publicelective.CreditLimitEvidenceFinder;
import com.zhku.knowledge.conversation.publicelective.JdbcCreditLimitEvidenceFinder;
import com.zhku.knowledge.conversation.publicelective.PublicElectiveConversationRouter;
import com.zhku.knowledge.conversation.publicelective.PublicElectiveCreditLimitService;
import com.zhku.knowledge.notice.AcademicTerm;
import com.zhku.knowledge.notice.CourseSelectionNoticeService;
import com.zhku.knowledge.notice.CurrentNoticeFinder;
import com.zhku.knowledge.notice.JdbcNoticeRepository;
import com.zhku.knowledge.notice.NoticeRepository;
import com.zhku.knowledge.answer.GroundedAnswerService;
import com.zhku.knowledge.publication.ReviewedKnowledgePublisher;
import java.time.Clock;
import java.time.Duration;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 本机问答预览所需的内部资料服务配置。
 *
 * <p>服务只在专用 Profile 和显式开关同时开启时创建，防止默认应用启动时连接仅供本地验证的资料库。</p>
 */
@Configuration(proxyBeanMethods = false)
@Profile("zhku-local-preview")
@ConditionalOnProperty(
        prefix = "zhku.local-preview",
        name = "enabled",
        havingValue = "true"
)
public class ZhkuLocalPreviewConfiguration {

    private static final Duration CONVERSATION_TTL = Duration.ofMinutes(10);
    private static final Logger LOG = LoggerFactory.getLogger(
            ZhkuLocalPreviewConfiguration.class
    );

    @Bean
    GroundedAnswerService zhkuLocalPreviewAnswerService(Environment environment) {
        ReviewedKnowledgePublisher publisher = new ReviewedKnowledgePublisher(
                localPreviewDataSource(environment)
        );
        return new GroundedAnswerService(publisher::searchPublished);
    }

    @Bean
    Clock zhkuLocalPreviewClock() {
        return Clock.systemUTC();
    }

    @Bean
    ConversationStore zhkuLocalPreviewConversationStore() {
        return new InMemoryConversationStore();
    }

    @Bean
    JdbcElectiveRetakeEvidenceFinder zhkuLocalPreviewElectiveRetakeEvidenceFinder(Environment environment) {
        return new JdbcElectiveRetakeEvidenceFinder(localPreviewDataSource(environment));
    }

    @Bean
    ElectiveRetakeConversationService zhkuLocalPreviewElectiveRetakeConversationService(
            ConversationStore zhkuLocalPreviewConversationStore,
            JdbcElectiveRetakeEvidenceFinder zhkuLocalPreviewElectiveRetakeEvidenceFinder,
            Clock zhkuLocalPreviewClock) {
        return new ElectiveRetakeConversationService(
                zhkuLocalPreviewConversationStore,
                zhkuLocalPreviewElectiveRetakeEvidenceFinder,
                zhkuLocalPreviewClock,
                CONVERSATION_TTL);
    }

    @Bean
    CreditLimitEvidenceFinder zhkuLocalPreviewCreditLimitEvidenceFinder(Environment environment) {
        return new JdbcCreditLimitEvidenceFinder(localPreviewDataSource(environment));
    }

    @Bean
    PublicElectiveCreditLimitService zhkuLocalPreviewCreditLimitService(
            CreditLimitEvidenceFinder zhkuLocalPreviewCreditLimitEvidenceFinder) {
        return new PublicElectiveCreditLimitService(zhkuLocalPreviewCreditLimitEvidenceFinder);
    }

    @Bean
    NoticeRepository zhkuLocalPreviewNoticeRepository(Environment environment) {
        return new JdbcNoticeRepository(localPreviewDataSource(environment));
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "zhku.local-preview",
            name = {"current-academic-term", "course-selection-official-query-url"}
    )
    CourseSelectionNoticeService zhkuLocalPreviewCourseSelectionNoticeService(
            NoticeRepository zhkuLocalPreviewNoticeRepository,
            Clock zhkuLocalPreviewClock,
            @Value("${zhku.local-preview.current-academic-term}")
            String currentAcademicTerm,
            @Value("${zhku.local-preview.course-selection-official-query-url}")
            String officialQueryUrl
    ) {
        // 启动即记录受控动态范围，避免页面静默退化到静态规则却难以排查。
        LOG.info("已启用当前选课通知问答，学期为 {}", currentAcademicTerm);
        return new CourseSelectionNoticeService(
                new CurrentNoticeFinder(
                        zhkuLocalPreviewNoticeRepository,
                        zhkuLocalPreviewClock
                ),
                new AcademicTerm(currentAcademicTerm),
                officialQueryUrl
        );
    }

    @Bean
    PublicElectiveConversationRouter zhkuLocalPreviewConversationRouter(
            ConversationStore zhkuLocalPreviewConversationStore,
            ElectiveRetakeConversationService zhkuLocalPreviewElectiveRetakeConversationService,
            PublicElectiveCreditLimitService zhkuLocalPreviewCreditLimitService,
            ObjectProvider<CourseSelectionNoticeService>
                    zhkuLocalPreviewCourseSelectionNoticeService,
            Clock zhkuLocalPreviewClock) {
        return new PublicElectiveConversationRouter(
                zhkuLocalPreviewConversationStore,
                zhkuLocalPreviewElectiveRetakeConversationService,
                zhkuLocalPreviewCreditLimitService,
                zhkuLocalPreviewCourseSelectionNoticeService.getIfAvailable(),
                zhkuLocalPreviewClock);
    }

    @Bean
    LocalPreviewAnswerChain zhkuLocalPreviewAnswerChain(
            ConversationStore zhkuLocalPreviewConversationStore,
            PublicElectiveConversationRouter zhkuLocalPreviewConversationRouter,
            ObjectProvider<CourseSelectionNoticeService> zhkuLocalPreviewCourseSelectionNoticeService,
            ObjectProvider<com.zhku.knowledge.rag.RetrievalAugmentedAnswerService>
                    zhkuLocalPreviewRetrievalService,
            Clock zhkuLocalPreviewClock) {
        // 检索增强级可缺省：未配置 zhku.rag.* 时为 null，该级整体跳过，链路退化为既有四级行为。
        return new LocalPreviewAnswerChain(
                zhkuLocalPreviewConversationStore,
                zhkuLocalPreviewConversationRouter,
                zhkuLocalPreviewCourseSelectionNoticeService.getIfAvailable(),
                zhkuLocalPreviewRetrievalService.getIfAvailable(),
                zhkuLocalPreviewClock);
    }

    @Bean
    LocalPreviewConversationFacade zhkuLocalPreviewConversationFacade(
            ConversationStore zhkuLocalPreviewConversationStore,
            LocalPreviewAnswerChain zhkuLocalPreviewAnswerChain,
            Clock zhkuLocalPreviewClock) {
        return new LocalPreviewConversationFacade(
                zhkuLocalPreviewConversationStore,
                zhkuLocalPreviewAnswerChain,
                zhkuLocalPreviewClock,
                CONVERSATION_TTL);
    }

    /**
     * 预览容器从 Spring Environment 获取密码，既可读取 OS 环境变量，也能让夹具注入临时值。
     * CLI 仍使用 LocalKnowledgePublisherFactory 的环境变量 fail-fast，避免两条入口放宽密码纪律。
     */
    private static DriverManagerDataSource localPreviewDataSource(Environment environment) {
        String password = environment.getProperty("ZHKU_DB_PASSWORD");
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("缺少数据库密码：请设置环境变量 ZHKU_DB_PASSWORD");
        }
        return new DriverManagerDataSource(
                environment.getProperty(
                        "zhku.db.url", "jdbc:postgresql://127.0.0.1:54329/zhkuagent"
                ),
                environment.getProperty("zhku.db.user", "zhkuagent"),
                password
        );
    }
}
