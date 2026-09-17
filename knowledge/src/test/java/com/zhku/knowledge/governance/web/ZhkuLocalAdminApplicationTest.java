package com.zhku.knowledge.governance.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.server.ConfigurableWebServerFactory;

import java.net.InetAddress;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * 本机治理台的启动与隔离测试。
 *
 * <p><b>守卫精确化（2026-09-17，决策见 SYNC-STATUS 第四节）</b>：原先断言
 * "`org.springframework.ai.chat.model.ChatModel` 类不存在"。阶段 8 按路线图在
 * knowledge 模块引入版本锁定的 Spring AI 后，该前置条件被上层设计取代；
 * 真正要守的是"治理台不夹带 agent 高权限工具"与"未配置 `zhku.rag.*` 不启用模型"。
 * 故拆为两层：agent 智能体/高权限工具类**加强保留**为不可见；
 * 模型相关改为**行为断言**（无模型 bean），不再依赖某个类是否在 classpath。</p>
 */
class ZhkuLocalAdminApplicationTest {

    /** agent 侧智能体与高权限工具：治理台 classpath 内一律不得出现。 */
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
    void shouldCreateAdminContextWithoutGenericAgentOrChatModel() {
        new ApplicationContextRunner()
                .withUserConfiguration(ZhkuLocalAdminApplication.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    for (String className : FORBIDDEN_AGENT_CLASSES) {
                        assertThat(org.springframework.util.ClassUtils.isPresent(
                                className, getClass().getClassLoader()))
                                .as("治理台不得暴露 agent 侧智能体/高权限工具：" + className)
                                .isFalse();
                    }
                    // 行为断言：未配置 zhku.rag.* → 无模型客户端与检索增强服务 bean。
                    assertThat(context).doesNotHaveBean(
                            com.zhku.knowledge.rag.ModelChatClient.class);
                    assertThat(context).doesNotHaveBean(
                            com.zhku.knowledge.rag.RetrievalAugmentedAnswerService.class);
                    assertThat(context).doesNotHaveBean(
                            ZhkuLocalAdminController.class
                    );
                    assertThat(context).doesNotHaveBean(
                            ZhkuLocalAdminExceptionHandler.class
                    );
                });
    }

    @Test
    void shouldForceEmbeddedServerToLoopbackAddress() throws Exception {
        ConfigurableWebServerFactory factory = mock(
                ConfigurableWebServerFactory.class
        );

        new LoopbackOnlyWebServerCustomizer().customize(factory);

        verify(factory).setAddress(InetAddress.getLoopbackAddress());
    }

    @Test
    void shouldConfigureDedicatedAdminPortInItsProfile() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application-zhku-local-admin.yml"));

        Properties properties = yaml.getObject();

        assertThat(properties.get("server.port")).isEqualTo(8124);
    }

    @Test
    void shouldAllowOfficialPdfUploadsUpToFiftyMegabytesOnlyInAdminProfile() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application-zhku-local-admin.yml"));

        Properties properties = yaml.getObject();

        assertThat(properties.get("spring.servlet.multipart.max-file-size"))
                .isEqualTo("50MB");
        assertThat(properties.get("spring.servlet.multipart.max-request-size"))
                .isEqualTo("52MB");
    }
}
