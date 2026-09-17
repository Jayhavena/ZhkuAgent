package com.zhku.knowledge.rag;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 真实供应商 smoke 测试（**显式 opt-in**，默认不跑）。
 *
 * <p>开启条件（两者都要满足）：环境变量 `DASHSCOPE_API_KEY` 存在 +
 * 系统属性 `zhku.rag.smoke=true`。运行方式：</p>
 * <pre>
 * .\mvnw.cmd -pl knowledge test -Dtest=DashScopeProviderSmokeTest -Dzhku.rag.smoke=true
 * </pre>
 *
 * <p>纪律：密钥只从环境变量读取，**不打印、不入库、不进日志**（本测试只输出模型名/耗时/
 * 是否命中引用等脱敏摘要，且 surefire 输出不进版本库）；端点与模型名与状态板第五节登记
 * 一致（国内站 key 只能配国内站端点）。本测试通过仅代表"该 profile 在该时刻可用"，
 * 不代表任何质量结论。</p>
 */
@EnabledIfEnvironmentVariable(named = "DASHSCOPE_API_KEY", matches = ".+")
@EnabledIfSystemProperty(named = "zhku.rag.smoke", matches = "true")
class DashScopeProviderSmokeTest {

    private static final String PROFILE_NAME = "dashscope";
    private static final String BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1";
    private static final String CHAT_MODEL = "qwen-plus";
    private static final String EMBEDDING_MODEL = "text-embedding-v4";
    private static final int EMBEDDING_DIMENSIONS = 1024;

    @Test
    void shouldCompleteOneRealChatCallWithStructuredOutput() {
        RagProviderProfile profile = new RagProviderProfile(
                PROFILE_NAME, BASE_URL, "DASHSCOPE_API_KEY", CHAT_MODEL, EMBEDDING_MODEL,
                EMBEDDING_DIMENSIONS, java.time.Duration.ofSeconds(30),
                "dashscope-text-embedding-v4-1024");
        ModelChatClient client = SpringAiChatModelClientFactory.create(profile, ApiKeyResolver.systemEnvironment());

        long startedAt = System.nanoTime();
        ModelResponse response = client.call(new ModelRequest(
                GroundedAnswerComposer.SYSTEM_CONSTRAINTS,
                "公共选修课每学期最多修多少学分？只依据给定证据回答。",
                List.of(new ApprovedEvidence(
                        "cit-smoke-1", "第一条 每学期公共选修课最高修读 4 学分。", "冒烟用证据")))
        );
        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000;

        // 只输出脱敏摘要：模型名、耗时、结构是否合法、是否回填了服务端下发的引用标识。
        System.out.println("[SMOKE] provider=" + PROFILE_NAME + ", chatModel=" + CHAT_MODEL
                + ", elapsedMs=" + elapsedMillis
                + ", answered=" + !response.answer().isBlank()
                + ", uncertainty=" + response.uncertainty()
                + ", citedServerId=" + response.citationIds().contains("cit-smoke-1"));

        assertThat(response.answer()).isNotBlank();
        assertThat(response.citationIds()).contains("cit-smoke-1");
        assertThat(response.uncertainty()).isBetween(0.0, 1.0);
    }

    @Test
    void shouldReturnEmbeddingVectorWithProfileDimensions() {
        RagProviderProfile profile = new RagProviderProfile(
                PROFILE_NAME, BASE_URL, "DASHSCOPE_API_KEY", CHAT_MODEL, EMBEDDING_MODEL,
                EMBEDDING_DIMENSIONS, java.time.Duration.ofSeconds(30),
                "dashscope-text-embedding-v4-1024");
        com.zhku.knowledge.retrieval.EmbeddingClient embeddingClient =
                SpringAiEmbeddingModelClientFactory.create(profile, ApiKeyResolver.systemEnvironment());

        long startedAt = System.nanoTime();
        float[] vector = embeddingClient.embed("第一条 每学期公共选修课最高修读 4 学分。");
        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000;

        System.out.println("[SMOKE] provider=" + PROFILE_NAME + ", embeddingModel=" + EMBEDDING_MODEL
                + ", elapsedMs=" + elapsedMillis + ", dimensions=" + vector.length);

        // 维度必须与 profile 声明一致：不一致会被适配层响亮拒绝（禁止混用维度）。
        assertThat(vector).hasSize(EMBEDDING_DIMENSIONS);
    }
}
