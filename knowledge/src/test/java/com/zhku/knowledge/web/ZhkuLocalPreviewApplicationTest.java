package com.zhku.knowledge.web;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/** 独立本机预览启动入口的组件隔离测试。 */
class ZhkuLocalPreviewApplicationTest {

    @Test
    void shouldCreatePreviewContextWithoutGenericAgentOrChatModel() {
        new ApplicationContextRunner()
                .withUserConfiguration(ZhkuLocalPreviewApplication.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(org.springframework.util.ClassUtils.isPresent(
                            "com.zhku.agent.agent.DefaultAgentFactory", getClass().getClassLoader())).isFalse();
                    assertThat(org.springframework.util.ClassUtils.isPresent(
                            "org.springframework.ai.chat.model.ChatModel", getClass().getClassLoader())).isFalse();
                });
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
