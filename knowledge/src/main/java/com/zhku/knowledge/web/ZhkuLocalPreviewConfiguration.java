package com.zhku.knowledge.web;

import com.zhku.knowledge.conversation.ConversationStore;
import com.zhku.knowledge.conversation.InMemoryConversationStore;
import com.zhku.knowledge.conversation.elective.ElectiveRetakeConversationService;
import com.zhku.knowledge.conversation.elective.JdbcElectiveRetakeEvidenceFinder;
import com.zhku.knowledge.conversation.publicelective.CreditLimitEvidenceFinder;
import com.zhku.knowledge.conversation.publicelective.JdbcCreditLimitEvidenceFinder;
import com.zhku.knowledge.conversation.publicelective.PublicElectiveConversationRouter;
import com.zhku.knowledge.conversation.publicelective.PublicElectiveCreditLimitService;
import com.zhku.knowledge.answer.GroundedAnswerService;
import com.zhku.knowledge.publication.LocalKnowledgePublisherFactory;
import com.zhku.knowledge.publication.ReviewedKnowledgePublisher;
import java.time.Clock;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

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

    @Bean
    GroundedAnswerService zhkuLocalPreviewAnswerService() {
        ReviewedKnowledgePublisher publisher = LocalKnowledgePublisherFactory.create();
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
    JdbcElectiveRetakeEvidenceFinder zhkuLocalPreviewElectiveRetakeEvidenceFinder() {
        return new JdbcElectiveRetakeEvidenceFinder(LocalKnowledgePublisherFactory.createDataSource());
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
    CreditLimitEvidenceFinder zhkuLocalPreviewCreditLimitEvidenceFinder() {
        return new JdbcCreditLimitEvidenceFinder(LocalKnowledgePublisherFactory.createDataSource());
    }

    @Bean
    PublicElectiveCreditLimitService zhkuLocalPreviewCreditLimitService(
            CreditLimitEvidenceFinder zhkuLocalPreviewCreditLimitEvidenceFinder) {
        return new PublicElectiveCreditLimitService(zhkuLocalPreviewCreditLimitEvidenceFinder);
    }

    @Bean
    PublicElectiveConversationRouter zhkuLocalPreviewConversationRouter(
            ConversationStore zhkuLocalPreviewConversationStore,
            ElectiveRetakeConversationService zhkuLocalPreviewElectiveRetakeConversationService,
            PublicElectiveCreditLimitService zhkuLocalPreviewCreditLimitService,
            Clock zhkuLocalPreviewClock) {
        return new PublicElectiveConversationRouter(
                zhkuLocalPreviewConversationStore,
                zhkuLocalPreviewElectiveRetakeConversationService,
                zhkuLocalPreviewCreditLimitService,
                zhkuLocalPreviewClock);
    }

    @Bean
    LocalPreviewConversationFacade zhkuLocalPreviewConversationFacade(
            ConversationStore zhkuLocalPreviewConversationStore,
            PublicElectiveConversationRouter zhkuLocalPreviewConversationRouter,
            Clock zhkuLocalPreviewClock) {
        return new LocalPreviewConversationFacade(
                zhkuLocalPreviewConversationStore,
                zhkuLocalPreviewConversationRouter,
                zhkuLocalPreviewClock,
                CONVERSATION_TTL);
    }
}
