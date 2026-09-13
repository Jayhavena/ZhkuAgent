package com.yupi.yuaiagent.zhku.knowledge.governance.web;

import com.yupi.yuaiagent.agent.YuManusFactory;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.server.ConfigurableWebServerFactory;

import java.net.InetAddress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ZhkuLocalAdminApplicationTest {

    @Test
    void shouldCreateAdminContextWithoutGenericAgentOrChatModel() {
        new ApplicationContextRunner()
                .withUserConfiguration(ZhkuLocalAdminApplication.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(YuManusFactory.class);
                    assertThat(context).doesNotHaveBean(ChatModel.class);
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
}
