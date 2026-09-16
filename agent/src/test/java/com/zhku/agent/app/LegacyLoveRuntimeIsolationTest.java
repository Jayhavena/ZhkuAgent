package com.zhku.agent.app;

import com.zhku.agent.rag.QueryRewriter;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 恋爱领域在默认运行配置中必须完全退出 Spring 容器。
 *
 * <p>测试为旧组件提供最小替身依赖，使 RED 阶段能够准确暴露“LoveApp 被注册”
 * 这一问题，而不是因为缺少外部模型或工具配置而启动失败。</p>
 */
class LegacyLoveRuntimeIsolationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(LoveApp.class, LoveAppTestDependencies.class);

    @Test
    void defaultRuntimeShouldNotRegisterLoveApp() {
        contextRunner.run(context ->
                assertThat(context).doesNotHaveBean(LoveApp.class)
        );
    }

    @Configuration(proxyBeanMethods = false)
    static class LoveAppTestDependencies {

        @Bean
        ChatModel dashscopeChatModel() {
            return mock(ChatModel.class);
        }

        @Bean(name = "loveAppVectorStore")
        VectorStore loveAppVectorStore() {
            return mock(VectorStore.class);
        }

        @Bean(name = "pgVectorVectorStore")
        VectorStore pgVectorVectorStore() {
            return mock(VectorStore.class);
        }

        @Bean(name = "loveAppRagCloudAdvisor")
        Advisor loveAppRagCloudAdvisor() {
            return mock(Advisor.class);
        }

        @Bean
        QueryRewriter queryRewriter() {
            return mock(QueryRewriter.class);
        }

        @Bean(name = "allTools")
        ToolCallback[] allTools() {
            return new ToolCallback[0];
        }

        @Bean
        ToolCallbackProvider toolCallbackProvider() {
            return mock(ToolCallbackProvider.class);
        }
    }
}
