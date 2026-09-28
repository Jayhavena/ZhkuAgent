package com.zhku.knowledge.retrieval;

import com.zhku.knowledge.publication.LocalKnowledgePublisherFactory;
import com.zhku.knowledge.rag.ApiKeyResolver;
import com.zhku.knowledge.rag.RagProviderProfile;
import com.zhku.knowledge.rag.RagProviderProfileResolver;
import com.zhku.knowledge.rag.SpringAiEmbeddingModelClientFactory;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;

/** 开发者显式执行的已发布资料索引入口；不触碰审核与发布状态。 */
public final class IndexPublishedKnowledgeCli {
    private IndexPublishedKnowledgeCli() { }

    public static void main(String[] args) throws Exception {
        Options options = parseArguments(args);
        if (System.getenv("ZHKU_DB_URL") == null || System.getenv("ZHKU_DB_URL").isBlank()) {
            // 索引会写库；必须明确指向目标库，不能继承演示库默认地址。
            throw new IllegalStateException("索引必须显式设置 ZHKU_DB_URL");
        }
        try {
            RagProviderProfile profile = configuredProfile();
            var dataSource = LocalKnowledgePublisherFactory.createDataSource();
            var profiles = new JdbcEmbeddingProfileStore(dataSource);
            var registered = profiles.find(profile.indexProfile());
            if (registered.isEmpty()) {
                profiles.save(new EmbeddingProfile(profile.indexProfile(), profile.name(),
                        profile.embeddingModel(), profile.embeddingDimensions(), ProfileStatus.BUILDING));
            } else if (!registered.get().provider().equals(profile.name())
                    || !registered.get().model().equals(profile.embeddingModel())
                    || registered.get().dimensions() != profile.embeddingDimensions()) {
                // profile 代表固定向量空间；已登记身份不符时不允许复用旧索引。
                throw new IllegalStateException("索引 profile 与模型配置不一致，请新建 profile");
            }
            EmbeddingClient embedding = SpringAiEmbeddingModelClientFactory.create(
                    profile, ApiKeyResolver.systemEnvironment());
            IndexRunResult result = new RetrievalIndexingService(
                    new JdbcRetrievalIndexRepository(dataSource), embedding,
                    new RetrievalEligibilityFilter(Clock.systemUTC()))
                    .indexProfile(profile.indexProfile(), options.chunkingVersion(), options.scope());
            System.out.println("status=COMPLETED runId=" + result.runId()
                    + " eligible=" + result.candidateChunks() + " newEntries=" + result.indexedEntries());
            System.out.println("profileStatus=" + profiles.find(profile.indexProfile()).orElseThrow().status());
        } catch (RuntimeException exception) {
            // 模型异常可能包含请求内容或端点详情，命令行只输出固定的安全失败类别。
            throw new IllegalStateException("索引失败；请检查 profile、临时数据库及索引运行审计记录");
        }
    }

    static Options parseArguments(String[] args) {
        if (args == null || args.length != 3 || !"--live".equals(args[0])
                || !args[1].startsWith("--scope=") || !args[2].startsWith("--chunking-version=")) {
            throw new IllegalArgumentException(
                    "用法：--live --scope=PUBLIC_ELECTIVE_CREDIT_LIMIT --chunking-version=fact-aware-v1");
        }
        String scope = args[1].substring("--scope=".length());
        String version = args[2].substring("--chunking-version=".length());
        if (!scope.matches("[A-Z_]{1,64}") || !version.matches("[A-Za-z0-9_-]{1,64}")) {
            throw new IllegalArgumentException("知识范围或切分版本非法");
        }
        return new Options(scope, version);
    }

    private static RagProviderProfile configuredProfile() throws Exception {
        Map<String, String> values = new HashMap<>();
        for (PropertySource<?> source : new YamlPropertySourceLoader().load("preview",
                new ClassPathResource("application-zhku-local-preview.yml"))) {
            if (source instanceof EnumerablePropertySource<?> enumerable) {
                for (String name : enumerable.getPropertyNames()) {
                    Object value = enumerable.getProperty(name);
                    if (value != null) values.put(name, value.toString());
                }
            }
        }
        String active = System.getProperty("zhku.rag.active-profile",
                values.get("zhku.rag.active-profile"));
        if (active == null || active.isBlank()) throw new IllegalStateException("未配置模型 profile");
        return new RagProviderProfileResolver(values).profile(active);
    }

    record Options(String scope, String chunkingVersion) { }
}
