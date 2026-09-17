package com.zhku.knowledge.rag;

import com.zhku.knowledge.retrieval.EmbeddingClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RAG 装配的开关语义测试（守卫行为断言的依据）。
 *
 * <p>要守住的性质：**未配置 `zhku.rag.*` 时链路第四级整体不存在**（无模型客户端、
 * 无检索增强服务 bean），因此不可能发生模型调用；一旦开启，则要求显式给出
 * 激活 profile，配置不全宁可启动失败也不静默降级。</p>
 */
class RagProviderConfigurationTest {

    private static final String[] PROFILE_KEYS = {
            "zhku.rag.profiles.stub.base-url=https://api.example.invalid",
            "zhku.rag.profiles.stub.api-key-env-var=ZHKU_RAG_TEST_KEY",
            "zhku.rag.profiles.stub.chat-model=stub-chat",
            "zhku.rag.profiles.stub.embedding-model=stub-embed",
            "zhku.rag.profiles.stub.embedding-dimensions=1536",
            "zhku.rag.profiles.stub.timeout-seconds=5",
            "zhku.rag.profiles.stub.index-profile=index-stub"
    };

    @Test
    void shouldNotCreateModelBeansWhenSwitchAbsent() {
        new ApplicationContextRunner()
                .withUserConfiguration(RagProviderConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(ModelChatClient.class);
                    assertThat(context).doesNotHaveBean(EmbeddingClient.class);
                    assertThat(context).doesNotHaveBean(RetrievalAugmentedAnswerService.class);
                });
    }

    @Test
    void shouldNotCreateModelBeansWhenSwitchExplicitlyDisabled() {
        new ApplicationContextRunner()
                .withUserConfiguration(RagProviderConfiguration.class)
                .withPropertyValues("zhku.rag.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(ModelChatClient.class);
                    assertThat(context).doesNotHaveBean(RetrievalAugmentedAnswerService.class);
                });
    }

    @Test
    void shouldFailFastWhenEnabledWithoutActiveProfile() {
        new ApplicationContextRunner()
                .withUserConfiguration(RagProviderConfiguration.class)
                .withPropertyValues("zhku.rag.enabled=true")
                .run(context -> {
                    // 开了开关却不给 profile：宁可启动失败，也不要"开了却静默不生效"。
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining("zhku.rag.active-profile");
                });
    }

    @Test
    void shouldCreateModelBeansWhenEnabledAndConfigured() {
        new ApplicationContextRunner()
                .withAllowBeanDefinitionOverriding(true)
                .withUserConfiguration(RagProviderConfiguration.class, TestApiKeyConfiguration.class)
                .withPropertyValues(concatProfileKeys("zhku.rag.enabled=true", "zhku.rag.active-profile=stub"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ModelChatClient.class);
                    assertThat(context).hasSingleBean(EmbeddingClient.class);
                    assertThat(context).hasSingleBean(RetrievalAugmentedAnswerService.class);
                    assertThat(context.getBean(EmbeddingClient.class).dimensions()).isEqualTo(1536);
                });
    }

    private static String[] concatProfileKeys(String... extra) {
        String[] all = new String[extra.length + PROFILE_KEYS.length];
        System.arraycopy(extra, 0, all, 0, extra.length);
        System.arraycopy(PROFILE_KEYS, 0, all, extra.length, PROFILE_KEYS.length);
        return all;
    }

    /** 测试用密钥解析器：与生产 bean 同名以便覆盖，避免依赖真实环境变量（密钥仍不进配置）。 */
    @Configuration(proxyBeanMethods = false)
    static class TestApiKeyConfiguration {

        @Bean
        ApiKeyResolver zhkuRagApiKeyResolver() {
            return new ApiKeyResolver(Map.of("ZHKU_RAG_TEST_KEY", "sk-test-only"));
        }
    }
}
