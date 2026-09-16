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

class ZhkuLocalAdminApplicationTest {

    @Test
    void shouldCreateAdminContextWithoutGenericAgentOrChatModel() {
        new ApplicationContextRunner()
                .withUserConfiguration(ZhkuLocalAdminApplication.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(org.springframework.util.ClassUtils.isPresent(
                            "com.zhku.agent.agent.DefaultAgentFactory", getClass().getClassLoader())).isFalse();
                    assertThat(org.springframework.util.ClassUtils.isPresent(
                            "org.springframework.ai.chat.model.ChatModel", getClass().getClassLoader())).isFalse();
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
