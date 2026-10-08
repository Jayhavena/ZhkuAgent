package com.zhku.knowledge.web;

import com.zhku.knowledge.campusagent.archive.*;
import com.zhku.knowledge.publication.LocalKnowledgePublisherFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.StandardEnvironment;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CampusRunArchiveConfigurationTest {
    private ApplicationContextRunner runner(boolean profile) {
        return new ApplicationContextRunner().withInitializer(c -> {
            if (profile)
                c.getEnvironment().setActiveProfiles("zhku-local-preview");
            // 单元测试不能意外继承本机已配置的库地址或凭据。
            c.getEnvironment().getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
            c.getEnvironment().getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        }).withUserConfiguration(CampusRunArchiveConfiguration.class);
    }

    @Test
    void disabledArchiveOrCampusOrProfileNeverCreatesStorage() {
        try (var factory = mockStatic(LocalKnowledgePublisherFactory.class)) {
            runner(true).withPropertyValues("zhku.campus-agent.enabled=true").run(c -> assertThat(c)
                    .doesNotHaveBean(CampusRunArchiveStore.class).doesNotHaveBean(CampusRunArchiveOwner.class));
            runner(true).withPropertyValues("zhku.campus-agent.enabled=false", "zhku.campus-agent.archive.enabled=true")
                    .run(c -> assertThat(c).doesNotHaveBean(CampusRunArchiveStore.class));
            runner(false).withPropertyValues("zhku.campus-agent.enabled=true", "zhku.campus-agent.archive.enabled=true")
                    .run(c -> assertThat(c).doesNotHaveBean(CampusRunArchiveOwner.class));
            factory.verifyNoInteractions();
        }
    }

    @Test
    void archiveGuardCannotBeDisabledByTurningOffOriginalPreviewConfiguration() {
        try (var factory = mockStatic(LocalKnowledgePublisherFactory.class)) {
            for (var address : new String[] { "0.0.0.0", "localhost", "" }) {
                runner(true)
                        .withPropertyValues("zhku.campus-agent.enabled=true", "zhku.campus-agent.archive.enabled=true",
                                "zhku.local-preview.enabled=false", "server.address=" + address,
                                "zhku.db.url=jdbc:postgresql://127.0.0.1:55433/zhkuagent_run_archive_e2e")
                        .run(c -> {
                            assertThat(c).hasFailed();
                            assertThat(c.getStartupFailure()).hasRootCauseInstanceOf(IllegalArgumentException.class);
                        });
            }
            factory.verifyNoInteractions();
        }
    }

    @Test
    void explicitSafeDatabaseTargetIsRequiredBeforeAnyConnection() {
        try (var factory = mockStatic(LocalKnowledgePublisherFactory.class)) {
            for (var url : new String[] { "", "jdbc:postgresql://name:secret@127.0.0.1:55433/test",
                    "jdbc:postgresql://127.0.0.1:55433/test?password=synthetic",
                    "jdbc:postgresql://127.0.0.1:55433/test?socketTimeout=999" }) {
                runner(true)
                        .withPropertyValues("zhku.campus-agent.enabled=true", "zhku.campus-agent.archive.enabled=true",
                                "server.address=127.0.0.1", "zhku.db.url=" + url)
                        .run(c -> {
                            assertThat(c).hasFailed();
                            assertThat(c.getStartupFailure()).hasRootCauseInstanceOf(IllegalArgumentException.class);
                        });
            }
            factory.verifyNoInteractions();
        }
    }

    @Test
    void invalidRetentionOrCapacityFailsBeforeStorageConstruction() {
        try (var factory = mockStatic(LocalKnowledgePublisherFactory.class)) {
            for (var property : new String[] { "zhku.campus-agent.archive.retention-days=0",
                    "zhku.campus-agent.archive.retention-days=31",
                    "zhku.campus-agent.archive.max-records=0", "zhku.campus-agent.archive.max-records=10001" }) {
                runner(true)
                        .withPropertyValues("zhku.campus-agent.enabled=true", "zhku.campus-agent.archive.enabled=true",
                                "server.address=127.0.0.1",
                                "zhku.db.url=jdbc:postgresql://127.0.0.1:55433/zhkuagent_run_archive_e2e", property)
                        .run(c -> {
                            assertThat(c).hasFailed();
                            assertThat(c.getStartupFailure()).hasRootCauseInstanceOf(IllegalArgumentException.class);
                        });
            }
            factory.verifyNoInteractions();
        }
    }
}
