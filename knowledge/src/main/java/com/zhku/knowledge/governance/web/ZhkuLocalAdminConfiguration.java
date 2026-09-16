package com.zhku.knowledge.governance.web;

import com.zhku.knowledge.governance.CandidateReviewService;
import com.zhku.knowledge.governance.JdbcKnowledgeGovernanceRepository;
import com.zhku.knowledge.governance.KnowledgeGovernancePublisher;
import com.zhku.knowledge.governance.KnowledgeGovernanceRepository;
import com.zhku.knowledge.governance.KnowledgeSubmissionService;
import com.zhku.knowledge.governance.LocalKnowledgeFileStore;
import com.zhku.knowledge.ingestion.PdfPageTextExtractor;
import com.zhku.knowledge.publication.LocalKnowledgePublisherFactory;
import com.zhku.knowledge.publication.ReviewedKnowledgePublisher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.file.Path;
import java.time.Clock;

/** 本机管理员服务的受控装配，不进入通用 Agent 应用。 */
@Configuration(proxyBeanMethods = false)
@Profile("zhku-local-admin")
@ConditionalOnProperty(
        prefix = "zhku.local-admin",
        name = "enabled",
        havingValue = "true"
)
public class ZhkuLocalAdminConfiguration {

    @Bean
    DriverManagerDataSource zhkuLocalAdminDataSource() {
        return LocalKnowledgePublisherFactory.createDataSource();
    }

    @Bean
    KnowledgeGovernanceRepository zhkuLocalAdminRepository(
            DriverManagerDataSource dataSource
    ) {
        return new JdbcKnowledgeGovernanceRepository(dataSource);
    }

    @Bean
    LocalKnowledgeFileStore zhkuLocalAdminFileStore(
            @Value("${zhku.local-admin.storage-directory:data/local-admin}")
            String storageDirectory
    ) {
        return new LocalKnowledgeFileStore(Path.of(storageDirectory));
    }

    @Bean
    KnowledgeSubmissionService zhkuLocalAdminSubmissionService(
            KnowledgeGovernanceRepository repository,
            LocalKnowledgeFileStore fileStore
    ) {
        return new KnowledgeSubmissionService(
                repository,
                fileStore,
                new PdfPageTextExtractor(),
                Clock.systemUTC()
        );
    }

    @Bean
    CandidateReviewService zhkuLocalAdminReviewService(
            KnowledgeGovernanceRepository repository
    ) {
        return new CandidateReviewService(repository, Clock.systemUTC());
    }

    @Bean
    KnowledgeGovernancePublisher zhkuLocalAdminPublisher(
            KnowledgeGovernanceRepository repository,
            DriverManagerDataSource dataSource
    ) {
        return new KnowledgeGovernancePublisher(
                repository,
                new ReviewedKnowledgePublisher(dataSource),
                Clock.systemUTC()
        );
    }

    @Bean
    LoopbackOnlyWebServerCustomizer loopbackOnlyWebServerCustomizer() {
        return new LoopbackOnlyWebServerCustomizer();
    }
}
