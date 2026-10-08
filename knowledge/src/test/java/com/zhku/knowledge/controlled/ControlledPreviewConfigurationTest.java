package com.zhku.knowledge.controlled;

import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ControlledPreviewConfigurationTest {
    private static MockEnvironment valid() {
        var environment = new MockEnvironment().withProperty("zhku.controlled-preview.enabled", "true")
                .withProperty("zhku.campus-agent.enabled", "true")
                .withProperty("zhku.campus-agent.archive.enabled", "true")
                .withProperty("server.address", "127.0.0.1")
                .withProperty("zhku.db.url", "jdbc:postgresql://127.0.0.1:55435/zhkuagent_identity_e2e")
                .withProperty("zhku.db.username", "zhkuagent");
        environment.setActiveProfiles("zhku-controlled-preview");
        return environment;
    }
    @Test void rejectsMixedProfiles() {
        var environment = valid();
        environment.setActiveProfiles("zhku-controlled-preview", "zhku-local-preview");
        assertThatThrownBy(() -> ControlledPreviewConfiguration.validateEnvironment(environment))
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void rejectsAnonymousOrConversationSwitches() {
        var environment = valid().withProperty("zhku.controlled-preview.conversations.enabled", "true");
        assertThatThrownBy(() -> ControlledPreviewConfiguration.validateEnvironment(environment))
                .isInstanceOf(IllegalArgumentException.class);
        final var anonymous = valid().withProperty("zhku.local-preview.enabled", "true");
        assertThatThrownBy(() -> ControlledPreviewConfiguration.validateEnvironment(anonymous))
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void rejectsMissingTargetAndDemonstrationDatabase() {
        var environment = valid().withProperty("zhku.db.url", "");
        assertThatThrownBy(() -> ControlledPreviewConfiguration.validateEnvironment(environment))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ControlledPreviewConfiguration.validateEnvironment(
                valid().withProperty("zhku.db.url", "jdbc:postgresql://127.0.0.1:54329/zhkuagent")))
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void rejectsDatabaseUrlOverridesAndDifferentUsernameAlias() {
        assertThatThrownBy(() -> ControlledPreviewConfiguration.validateEnvironment(valid()
                .withProperty("zhku.db.url", "jdbc:postgresql://127.0.0.1:55435/zhkuagent_identity_e2e?socketTimeout=0")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ControlledPreviewConfiguration.validateEnvironment(valid().withProperty("zhku.db.user", "other")))
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void directConfigurationRejectsBeforeCreatingAnyDatabaseBean() {
        var beans = new AtomicInteger();
        new ApplicationContextRunner().withUserConfiguration(ControlledPreviewConfiguration.class)
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("zhku-controlled-preview"))
                .withPropertyValues("zhku.controlled-preview.enabled=true", "server.address=0.0.0.0")
                .withBean("probeDataSource", DataSource.class, () -> { beans.incrementAndGet(); return mock(DataSource.class); })
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalArgumentException.class);
                    assertThat(beans).hasValue(0);
                });
    }
    @Test void sharedApplicationFactoryIsAvailableWithoutOpeningResources() {
        assertThat(ControlledPreviewApplication.application().getAdditionalProfiles())
                .contains("zhku-controlled-preview");
        ControlledPreviewConfiguration.validateEnvironment(valid());
    }

    @Test void rejectsCookieScopeOrPersistenceOverrides() {
        for (String[] entry : new String[][] {
                {"server.servlet.context-path", "/other"},
                {"server.servlet.session.persistent", "true"},
                {"server.servlet.session.tracking-modes", "cookie,url"},
                {"server.servlet.session.timeout", "60m"},
                {"server.servlet.session.cookie.name", "JSESSIONID"},
                {"server.servlet.session.cookie.path", "/"},
                {"server.servlet.session.cookie.http-only", "false"},
                {"server.servlet.session.cookie.same-site", "none"},
                {"server.servlet.session.cookie.secure", "true"},
                {"server.servlet.session.cookie.domain", "localhost"},
                {"spring.session.store-type", "jdbc"}}) {
            assertThatThrownBy(() -> ControlledPreviewConfiguration.validateEnvironment(
                    valid().withProperty(entry[0], entry[1])))
                    .as("受控登录不接受额外持久化或Cookie作用域: %s", entry[0])
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test void controlledMapperRejectsUnknownIdentityAndBusinessSessionFields() throws Exception {
        var builder=new org.springframework.http.converter.json.Jackson2ObjectMapperBuilder();
        new ControlledPreviewConfiguration().controlledStrictRequests().customize(builder);
        com.fasterxml.jackson.databind.ObjectMapper mapper=builder.build();
        for(String field:new String[]{"userId","role","authRevision","sessionId"}) {
            assertThatThrownBy(()->mapper.readValue("{\"question\":\"公选课学分\",\""+field+"\":\"forged\"}",
                    com.zhku.knowledge.campusagent.CampusCreditAgentService.Request.class))
                    .isInstanceOf(com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException.class);
        }
        assertThat(mapper.readValue("{\"question\":\"公选课学分\",\"courses\":[]}",
                com.zhku.knowledge.campusagent.CampusCreditAgentService.Request.class).courses()).isEmpty();
    }

    @Test void controlledImportReusesPolicyServicesWithoutImportingAnonymousControllers() {
        // 只装配现有领域配置，不扫描web包；受控用户不能因此获得旧匿名入口。
        new ApplicationContextRunner()
                .withUserConfiguration(com.zhku.knowledge.web.ZhkuLocalPreviewConfiguration.class)
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("zhku-controlled-preview"))
                .withPropertyValues("zhku.controlled-preview.enabled=true", "zhku.local-preview.enabled=false",
                        "zhku.campus-agent.enabled=true", "zhku.campus-agent.archive.enabled=true",
                        "server.address=127.0.0.1",
                        "zhku.db.url=jdbc:postgresql://127.0.0.1:55435/zhkuagent_identity_e2e",
                        "zhku.db.username=zhkuagent")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(com.zhku.knowledge.web.LocalPreviewConversationFacade.class);
                    assertThat(context).hasSingleBean(com.zhku.knowledge.conversation.InMemoryConversationStore.class);
                    assertThat(context).hasSingleBean(
                            com.zhku.knowledge.conversation.publicelective.CreditLimitEvidenceFinder.class);
                    assertThat(context).doesNotHaveBean(com.zhku.knowledge.web.CampusAgentPreviewController.class);
                });
    }

    @Test void productionModelIsCheckedBeforeSingletonResourcesAreOpened() {
        var factory = new org.springframework.beans.factory.support.DefaultListableBeanFactory();
        factory.registerBeanDefinition("controlledCampusModelTransport",
                new org.springframework.beans.factory.support.RootBeanDefinition(
                        com.zhku.knowledge.tools.modelcall.CourseCreditModelTransport.class));
        var guard = ControlledPreviewConfiguration.controlledPreviewEarlyGuard(valid());
        assertThatThrownBy(() -> guard.postProcessBeanFactory(factory))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("受控模型配置或环境凭据未通过启动核验");
    }

    @Test void processEnvironmentPasswordIsNotMisclassifiedAsAnExplicitOverride() {
        var environment = valid();
        environment.getPropertySources().addFirst(new org.springframework.core.env.SystemEnvironmentPropertySource(
                "systemEnvironment", java.util.Map.of("ZHKU_DB_PASSWORD", "synthetic-unused-guard-value")));
        assertThat(environment.getProperty("zhku.db.password")).isNotNull();
        assertThatCode(() -> ControlledPreviewConfiguration.validateEnvironment(environment)).doesNotThrowAnyException();
        for (String key : new String[]{"zhku.db.password", "ZHKU_DB_PASSWORD"})
            assertThatThrownBy(() -> ControlledPreviewConfiguration.validateEnvironment(valid().withProperty(key, "synthetic")))
                    .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void missingProductionModelFailsBeforeAnyDataSourceSupplier() {
        var opened = new AtomicInteger();
        new ApplicationContextRunner()
                .withUserConfiguration(ControlledPreviewConfiguration.class, ControlledCampusAgentConfiguration.class)
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("zhku-controlled-preview"))
                .withPropertyValues("zhku.controlled-preview.enabled=true", "zhku.campus-agent.enabled=true",
                        "zhku.campus-agent.archive.enabled=true", "server.address=127.0.0.1",
                        "zhku.db.url=jdbc:postgresql://127.0.0.1:55435/zhkuagent_identity_e2e", "zhku.db.username=zhkuagent")
                .withBean("unusedProbeSource", DataSource.class, () -> { opened.incrementAndGet(); return mock(DataSource.class); })
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).isInstanceOf(IllegalArgumentException.class)
                            .hasMessage("受控模型配置或环境凭据未通过启动核验");
                    assertThat(opened).hasValue(0);
                });
    }
}
