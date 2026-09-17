package com.zhku.knowledge.rag;

import com.zhku.knowledge.retrieval.EmbeddingClient;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;

/**
 * 按 profile 构建 Spring AI embedding 客户端。
 *
 * <p>维度以 profile 声明为准并要求模型按该维度返回；不一致由
 * {@link com.zhku.knowledge.retrieval.EmbeddingClient} 适配层响亮失败，
 * 避免不同维度的向量混入同一索引 profile。</p>
 */
public final class SpringAiEmbeddingModelClientFactory {

    private SpringAiEmbeddingModelClientFactory() {
    }

    public static EmbeddingClient create(RagProviderProfile profile, ApiKeyResolver apiKeyResolver) {
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(profile.baseUrl())
                .apiKey(apiKeyResolver.requireApiKey(profile))
                .build();
        OpenAiEmbeddingModel embeddingModel = new OpenAiEmbeddingModel(
                api,
                MetadataMode.NONE,
                OpenAiEmbeddingOptions.builder()
                        .model(profile.embeddingModel())
                        .dimensions(profile.embeddingDimensions())
                        .build()
        );
        return new com.zhku.knowledge.rag.SpringAiEmbeddingModelClient(
                embeddingModel, profile.embeddingDimensions());
    }
}
