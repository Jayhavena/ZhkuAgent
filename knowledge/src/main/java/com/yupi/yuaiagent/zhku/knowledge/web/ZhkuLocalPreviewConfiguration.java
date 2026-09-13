package com.yupi.yuaiagent.zhku.knowledge.web;

import com.yupi.yuaiagent.zhku.knowledge.answer.GroundedAnswerService;
import com.yupi.yuaiagent.zhku.knowledge.publication.LocalKnowledgePublisherFactory;
import com.yupi.yuaiagent.zhku.knowledge.publication.ReviewedKnowledgePublisher;
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

    @Bean
    GroundedAnswerService zhkuLocalPreviewAnswerService() {
        ReviewedKnowledgePublisher publisher = LocalKnowledgePublisherFactory.create();
        return new GroundedAnswerService(publisher::searchPublished);
    }
}
