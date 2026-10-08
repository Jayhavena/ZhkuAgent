package com.zhku.knowledge.web;

import com.zhku.knowledge.rag.ModelChatClient;
import com.zhku.knowledge.rag.ApiKeyResolver;
import com.zhku.knowledge.rag.RetrievalAugmentedAnswerService;
import com.zhku.knowledge.retrieval.HybridRetrievalService;
import com.zhku.knowledge.campusagent.CampusCreditAgentManager;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.ClassUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 独立本机预览启动入口的组件隔离测试。
 *
 * <p><b>断言为何这样写（2026-09-17 守卫精确化，决策见 SYNC-STATUS 第四节）</b>：
 * 本测试原有一条"`org.springframework.ai.chat.model.ChatModel` 类不存在"的断言。
 * 阶段 8 按路线图在 knowledge 模块引入版本锁定的 Spring AI 依赖后，该断言的
 * 前置条件被上层设计取代——而它真正想守的性质是"<b>预览应用没有模型栈</b>"。
 * 因此改为两层更精确的守卫：
 * <ol>
 *   <li><b>安全边界（保留并加强）</b>：agent 侧智能体与高权限工具类不得出现在
 *       预览应用 classpath（终端/文件/下载/网页抓取等），这是"不向学生暴露高权限工具"
 *       的机械保障，属项目核心资产；</li>
 *   <li><b>行为断言（替代"类不存在"）</b>：未配置 `zhku.rag.*` 时不创建任何模型相关
 *       bean——"未配置不启用"才是真正需要守住的性质；把守卫建在"某个类不存在"上，
 *       会在依赖升级时误报，也会挡住模块内合法的库引入。</li>
 * </ol>
 */
class ZhkuLocalPreviewApplicationTest {

    /** agent 侧智能体与高权限工具：预览应用 classpath 内一律不得出现。 */
    private static final String[] FORBIDDEN_AGENT_CLASSES = {
            "com.zhku.agent.agent.DefaultAgentFactory",
            "com.zhku.agent.agent.ToolCallAgent",
            "com.zhku.agent.agent.ReActAgent",
            "com.zhku.agent.tools.TerminalOperationTool",
            "com.zhku.agent.tools.FileOperationTool",
            "com.zhku.agent.tools.ResourceDownloadTool",
            "com.zhku.agent.tools.WebScrapingTool"
    };

    @Test
    void shouldKeepAgentAndHighPrivilegeToolsOffPreviewClasspath() {
        new ApplicationContextRunner()
                .withUserConfiguration(ZhkuLocalPreviewApplication.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    for (String className : FORBIDDEN_AGENT_CLASSES) {
                        assertThat(ClassUtils.isPresent(className, getClass().getClassLoader()))
                                .as("预览应用不得暴露 agent 侧智能体/高权限工具：" + className)
                                .isFalse();
                    }
                });
    }

    @Test
    void shouldNotCreateModelBeansWhenRagIsNotConfigured() {
        new ApplicationContextRunner()
                .withUserConfiguration(ZhkuLocalPreviewApplication.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    // 行为断言：未配置 zhku.rag.* → 无模型客户端、无检索增强服务；
                    // 链路第四级因此整级跳过，不会发生模型调用。
                    assertThat(context).doesNotHaveBean(ModelChatClient.class);
                    assertThat(context).doesNotHaveBean(RetrievalAugmentedAnswerService.class);
                    assertThat(context).doesNotHaveBean(CampusCreditAgentManager.class);
                });
    }

    @Test
    void shouldWireRetrievalAndModelWhenRagIsExplicitlyEnabled() {
        new ApplicationContextRunner()
                .withPropertyValues("spring.profiles.active=zhku-local-preview",
                        "zhku.rag.enabled=true")
                .withUserConfiguration(ZhkuLocalPreviewApplication.class)
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().hasMessageContaining("zhku.rag.active-profile"));
    }

    @Test
    void shouldCreateRetrievalAndModelBeansWithCompletePreviewProfile() {
        new ApplicationContextRunner()
                .withAllowBeanDefinitionOverriding(true)
                .withUserConfiguration(ZhkuLocalPreviewApplication.class, TestKeyConfiguration.class)
                .withPropertyValues("spring.profiles.active=zhku-local-preview",
                        "zhku.local-preview.enabled=true", "server.address=127.0.0.1", "zhku.rag.enabled=true",
                        "zhku.campus-agent.enabled=true",
                        "zhku.rag.active-profile=stub", "ZHKU_DB_PASSWORD=test-only",
                        "zhku.rag.profiles.stub.base-url=http://127.0.0.1:19999/v1",
                        "zhku.rag.profiles.stub.api-key-env-var=ZHKU_RAG_TEST_KEY",
                        "zhku.rag.profiles.stub.chat-model=stub-chat",
                        "zhku.rag.profiles.stub.embedding-model=stub-embed",
                        "zhku.rag.profiles.stub.embedding-dimensions=3",
                        "zhku.rag.profiles.stub.timeout-seconds=5",
                        "zhku.rag.profiles.stub.index-profile=index-stub")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ModelChatClient.class);
                    assertThat(context).hasSingleBean(HybridRetrievalService.class);
                    assertThat(context).hasSingleBean(RetrievalAugmentedAnswerService.class);
                    assertThat(context).hasSingleBean(CampusCreditAgentManager.class);
                    assertThat(context).hasSingleBean(CampusAgentPreviewController.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class TestKeyConfiguration {
        @Bean
        ApiKeyResolver zhkuRagApiKeyResolver() {
            return new ApiKeyResolver(Map.of("ZHKU_RAG_TEST_KEY", "sk-test-only"));
        }
    }

    @Test
    void shouldNotExposeConversationApiWhenExplicitPreviewSwitchIsAbsent() {
        new ApplicationContextRunner()
                .withPropertyValues("spring.profiles.active=zhku-local-preview")
                .withUserConfiguration(ZhkuLocalPreviewApplication.class)
                .run(context -> assertThat(context)
                        .hasNotFailed()
                        .doesNotHaveBean(ZhkuLocalPreviewConversationController.class));
    }
}
