package com.zhku.knowledge.feedback;

import com.zhku.knowledge.publication.LocalKnowledgePublisherFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.time.Clock;

/**
 * 本机预览的反馈专用装配。
 *
 * <p>它与预览应用使用相同 Profile 和显式开关，避免新增组件扫描后在默认应用中意外
 * 暴露学生反馈入口或创建本地数据库连接配置。</p>
 */
@Configuration(proxyBeanMethods = false)
@Profile("zhku-local-preview")
@ConditionalOnProperty(
        prefix = "zhku.local-preview",
        name = "enabled",
        havingValue = "true"
)
public class FeedbackPreviewConfiguration {

    @Bean
    FeedbackRepository zhkuLocalPreviewFeedbackRepository() {
        return new JdbcFeedbackRepository(LocalKnowledgePublisherFactory.createDataSource());
    }

    @Bean
    AnonymousFeedbackService zhkuLocalPreviewAnonymousFeedbackService(
            FeedbackRepository feedbackRepository
    ) {
        return new AnonymousFeedbackService(feedbackRepository, Clock.systemUTC());
    }
}
