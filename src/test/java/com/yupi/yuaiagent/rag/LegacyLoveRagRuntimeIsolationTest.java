package com.yupi.yuaiagent.rag;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 验证旧恋爱 RAG 链路在默认配置下不会被 Spring 创建。
 *
 * <p>三个断言分别覆盖语料读取、本地向量库和云端检索顾问。它们均验证真实容器中的
 * Bean 注册结果，避免仅靠删除前端入口而让旧语料继续消耗启动资源。</p>
 */
class LegacyLoveRagRuntimeIsolationTest {

    @Test
    void defaultRuntimeShouldNotRegisterLoveDocumentLoader() {
        new ApplicationContextRunner()
                .withUserConfiguration(
                        LoveAppDocumentLoader.class,
                        DocumentLoaderTestDependencies.class
                )
                .run(context ->
                        assertThat(context).doesNotHaveBean(LoveAppDocumentLoader.class)
                );
    }

    @Test
    void defaultRuntimeShouldNotRegisterLoveVectorStore() {
        new ApplicationContextRunner()
                .withUserConfiguration(
                        LoveAppVectorStoreConfig.class,
                        VectorStoreTestDependencies.class
                )
                .run(context ->
                        assertThat(context).doesNotHaveBean("loveAppVectorStore")
                );
    }

    @Test
    void defaultRuntimeShouldNotRegisterLoveCloudAdvisor() {
        new ApplicationContextRunner()
                .withPropertyValues("spring.ai.dashscope.api-key=test-key")
                .withUserConfiguration(LoveAppRagCloudAdvisorConfig.class)
                .run(context ->
                        assertThat(context).doesNotHaveBean("loveAppRagCloudAdvisor")
                );
    }

    @Configuration(proxyBeanMethods = false)
    static class DocumentLoaderTestDependencies {

        @Bean
        ResourcePatternResolver resourcePatternResolver() {
            return new PathMatchingResourcePatternResolver();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class VectorStoreTestDependencies {

        @Bean
        LoveAppDocumentLoader loveAppDocumentLoader() {
            LoveAppDocumentLoader loader = mock(LoveAppDocumentLoader.class);
            when(loader.loadMarkdowns()).thenReturn(
                    List.of(new Document("legacy-love-test-document"))
            );
            return loader;
        }

        @Bean
        MyTokenTextSplitter myTokenTextSplitter() {
            return new MyTokenTextSplitter();
        }

        @Bean
        MyKeywordEnricher myKeywordEnricher() {
            MyKeywordEnricher enricher = mock(MyKeywordEnricher.class);
            when(enricher.enrichDocuments(anyList()))
                    .thenAnswer(invocation -> invocation.getArgument(0, List.class));
            return enricher;
        }

        @Bean
        EmbeddingModel dashscopeEmbeddingModel() {
            EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
            when(embeddingModel.embed(any(Document.class)))
                    .thenReturn(new float[]{0.1F, 0.2F, 0.3F});
            return embeddingModel;
        }

        /**
         * Mockito 的 MyKeywordEnricher 替身仍会保留父类的 @Resource 元数据；
         * 此模型只用于完成测试容器装配，不会发生任何模型调用。
         */
        @Bean
        ChatModel dashscopeChatModel() {
            return mock(ChatModel.class);
        }
    }
}
