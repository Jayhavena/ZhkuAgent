package com.zhku.knowledge.rag;

import com.zhku.knowledge.retrieval.ChunkCandidateRetriever;
import com.zhku.knowledge.retrieval.EmbeddingClient;
import com.zhku.knowledge.retrieval.HybridCandidateFuser;
import com.zhku.knowledge.retrieval.HybridRetrievalService;
import com.zhku.knowledge.retrieval.JdbcLexicalCandidateRetriever;
import com.zhku.knowledge.retrieval.JdbcVectorCandidateRetriever;
import com.zhku.knowledge.retrieval.RetrievalEligibilityFilter;
import com.zhku.knowledge.publication.LocalKnowledgePublisherFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;

/**
 * 受控检索增强（RAG）的装配：**只在显式开启时创建模型相关 bean**。
 *
 * <p>守卫语义（由隔离测试的行为断言守护）：未配置 `zhku.rag.*` 时本类整体不生效，
 * 上下文里不会出现模型客户端或检索增强服务 bean，链路第四级自动跳过——
 * "未配置不启用"是真正需要守住的性质；把守卫写成"某类不存在"会在
 * 依赖升级时误报，也会挡住模块内合法的库引入。</p>
 *
 * <p>密钥只从环境变量读取（见 {@link ApiKeyResolver}），不落配置、不进日志。</p>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "zhku.rag", name = "enabled", havingValue = "true")
public class RagProviderConfiguration {

    private static final int DEFAULT_TOP_K = 5;

    /** 独立于预览 Profile 的时钟：RAG 开关不应依赖别的 Configuration 是否装配。 */
    @Bean
    @ConditionalOnMissingBean(Clock.class)
    Clock zhkuRagClock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean(ApiKeyResolver.class)
    ApiKeyResolver zhkuRagApiKeyResolver() {
        // 生产装配从进程环境读密钥；测试与更严格的部署可提供自己的实现覆盖本 bean。
        return ApiKeyResolver.systemEnvironment();
    }

    @Bean
    RagProviderProfile zhkuRagActiveProfile(Environment environment) {
        RagProviderProfileResolver resolver = new RagProviderProfileResolver(propertiesOf(environment));
        // 开启但没给激活 profile：启动即失败，避免"开了但静默不生效"。
        return resolver.activeProfile().orElseThrow(() -> new IllegalStateException(
                "已启用 zhku.rag.enabled 但未配置有效的 zhku.rag.active-profile"));
    }

    @Bean
    ModelChatClient zhkuRagChatClient(RagProviderProfile zhkuRagActiveProfile, ApiKeyResolver zhkuRagApiKeyResolver) {
        return SpringAiChatModelClientFactory.create(zhkuRagActiveProfile, zhkuRagApiKeyResolver);
    }

    @Bean
    EmbeddingClient zhkuRagEmbeddingClient(
            RagProviderProfile zhkuRagActiveProfile, ApiKeyResolver zhkuRagApiKeyResolver) {
        return SpringAiEmbeddingModelClientFactory.create(zhkuRagActiveProfile, zhkuRagApiKeyResolver);
    }

    @Bean
    RetrievalEligibilityFilter zhkuRagEligibilityFilter(Clock zhkuLocalPreviewClock) {
        return new RetrievalEligibilityFilter(zhkuLocalPreviewClock);
    }

    @Bean(name = "zhkuRagLexicalRetriever")
    ChunkCandidateRetriever zhkuRagLexicalRetriever(
            RetrievalEligibilityFilter zhkuRagEligibilityFilter,
            RagProviderProfile zhkuRagActiveProfile,
            Environment environment) {
        return new JdbcLexicalCandidateRetriever(
                LocalKnowledgePublisherFactory.createDataSource(),
                zhkuRagEligibilityFilter,
                zhkuRagActiveProfile.indexProfile(),
                scopeOf(environment));
    }

    @Bean(name = "zhkuRagVectorRetriever")
    ChunkCandidateRetriever zhkuRagVectorRetriever(
            RetrievalEligibilityFilter zhkuRagEligibilityFilter,
            RagProviderProfile zhkuRagActiveProfile,
            EmbeddingClient zhkuRagEmbeddingClient,
            Environment environment) {
        return new JdbcVectorCandidateRetriever(
                LocalKnowledgePublisherFactory.createDataSource(),
                zhkuRagEligibilityFilter,
                zhkuRagActiveProfile.indexProfile(),
                scopeOf(environment),
                zhkuRagEmbeddingClient);
    }

    @Bean
    RetrievalAugmentedAnswerService zhkuRagAnswerService(
            HybridRetrievalService zhkuRagHybridRetrievalService,
            @Qualifier("zhkuRagLexicalRetriever") ChunkCandidateRetriever zhkuRagLexicalRetriever,
            @Qualifier("zhkuRagVectorRetriever") ChunkCandidateRetriever zhkuRagVectorRetriever,
            ModelChatClient zhkuRagChatClient,
            Clock zhkuLocalPreviewClock,
            Environment environment) {
        return new RetrievalAugmentedAnswerService(
                zhkuRagHybridRetrievalService,
                zhkuRagLexicalRetriever,
                zhkuRagVectorRetriever,
                zhkuRagChatClient,
                new AnswerEvidenceGuard(),
                new RandomCitationIdFactory(),
                new JdbcCitationAuditRepository(LocalKnowledgePublisherFactory.createDataSource()),
                zhkuLocalPreviewClock,
                topKOf(environment));
    }

    @Bean
    HybridRetrievalService zhkuRagHybridRetrievalService() {
        return new HybridRetrievalService(new HybridCandidateFuser());
    }

    /** 知识范围过滤；未配置表示不限定（如全校通用资料）。 */
    private static String scopeOf(Environment environment) {
        String scope = environment.getProperty("zhku.rag.retrieval-scope");
        return scope == null || scope.isBlank() ? null : scope.strip();
    }

    private static int topKOf(Environment environment) {
        String value = environment.getProperty("zhku.rag.top-k");
        if (value == null || value.isBlank()) {
            return DEFAULT_TOP_K;
        }
        try {
            return Integer.parseInt(value.strip());
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("zhku.rag.top-k 必须为整数：" + value, exception);
        }
    }

    /** 把 Spring 的 Environment 收窄为解析器需要的键值视图。 */
    private static Map<String, String> propertiesOf(Environment environment) {
        Map<String, String> properties = new HashMap<>();
        for (String key : new String[]{
                "zhku.rag.enabled", "zhku.rag.active-profile",
                "zhku.rag.retrieval-scope", "zhku.rag.top-k"}) {
            properties.put(key, environment.getProperty(key, ""));
        }
        // 逐 profile 的键名带 profile 名，只能在运行时枚举：这里扫描已知前缀。
        for (PropertySource<?> source : ((ConfigurableEnvironment) environment).getPropertySources()) {
            if (source instanceof EnumerablePropertySource<?> enumerable) {
                for (String name : enumerable.getPropertyNames()) {
                    if (name.startsWith("zhku.rag.profiles.")) {
                        properties.put(name, String.valueOf(environment.getProperty(name, "")));
                    }
                }
            }
        }
        return properties;
    }
}
