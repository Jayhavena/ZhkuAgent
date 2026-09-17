package com.zhku.knowledge.rag;

import com.zhku.knowledge.retrieval.EmbeddingClient;
import org.springframework.ai.embedding.EmbeddingModel;

import java.util.Objects;

/**
 * Spring AI embedding 适配器（计划任务 3 的 Embedding 侧）。
 *
 * <p>维度以 profile 声明为准并在此断言：Spring AI 的维度取自模型/选项，
 * 若与 profile 不一致必须立即失败——索引任务与召回都依赖"同 profile 同维度"。</p>
 */
public final class SpringAiEmbeddingModelClient implements EmbeddingClient {

    private final EmbeddingModel embeddingModel;
    private final int dimensions;

    public SpringAiEmbeddingModelClient(EmbeddingModel embeddingModel, int dimensions) {
        this.embeddingModel = Objects.requireNonNull(embeddingModel, "embedding 模型不能为空");
        if (dimensions <= 0) {
            throw new IllegalArgumentException("embedding 维度必须为正数");
        }
        this.dimensions = dimensions;
    }

    @Override
    public float[] embed(String text) {
        Objects.requireNonNull(text, "待向量化文本不能为空");
        float[] vector;
        try {
            vector = embeddingModel.embed(text);
        } catch (RuntimeException exception) {
            throw new ModelCallFailedException("embedding 调用失败", exception);
        }
        if (vector == null || vector.length == 0) {
            throw new ModelOutputFormatException("embedding 返回空向量");
        }
        if (vector.length != dimensions) {
            // 禁止混用维度：宁可整轮失败，也不把不同维度的向量写进同一 profile。
            throw new IllegalStateException(
                    "embedding 维度与 profile 声明不符：声明 " + dimensions + "，实际 " + vector.length);
        }
        return vector;
    }

    @Override
    public int dimensions() {
        return dimensions;
    }
}
