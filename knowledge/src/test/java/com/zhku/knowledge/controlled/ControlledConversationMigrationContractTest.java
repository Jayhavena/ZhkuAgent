package com.zhku.knowledge.controlled;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;
import org.flywaydb.core.api.MigrationState;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.configuration.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 允许后续迁移不能变成跳过V20或跨库借用迁移许可；这里只验装配，真库另由进程测试验证。 */
class ControlledConversationMigrationContractTest {
    private final DataSource source = mock(DataSource.class);
    private final Flyway flyway = mock(Flyway.class);
    private final Configuration configuration = mock(Configuration.class);
    private final MigrationInfoService info = mock(MigrationInfoService.class);

    private MockEnvironment environment() {
        var env = new MockEnvironment().withProperty("zhku.controlled-preview.enabled", "true")
                .withProperty("zhku.campus-agent.enabled", "true")
                .withProperty("zhku.campus-agent.archive.enabled", "true")
                .withProperty("zhku.controlled-preview.conversations.enabled", "true")
                .withProperty("server.address", "127.0.0.1")
                .withProperty("zhku.db.url", "jdbc:postgresql://127.0.0.1:55437/zhkuagent_run_archive_e2e")
                .withProperty("zhku.db.username", "zhkuagent");
        env.setActiveProfiles("zhku-controlled-preview");
        return env;
    }

    private MigrationInfo migration(String version, MigrationState state) {
        var migration = mock(MigrationInfo.class);
        when(migration.getVersion()).thenReturn(MigrationVersion.fromVersion(version));
        when(migration.getState()).thenReturn(state);
        return migration;
    }

    private void setup(String currentVersion, MigrationInfo... applied) {
        var current = currentVersion == null ? null : migration(currentVersion, MigrationState.SUCCESS);
        when(flyway.getConfiguration()).thenReturn(configuration);
        when(configuration.getDataSource()).thenReturn(source);
        when(flyway.info()).thenReturn(info);
        when(info.current()).thenReturn(current);
        when(info.applied()).thenReturn(applied);
    }

    private void accepts() {
        try (var access = new ControlledConversationConfiguration().conversationJdbcAccess(source, flyway, environment())) {
            assertThat(access.status().inFlight()).isZero();
            assertThat(access.status().liveThreads()).isZero();
        }
        verifyNoInteractions(source);
    }

    private void rejects() {
        assertThatThrownBy(() -> new ControlledConversationConfiguration().conversationJdbcAccess(source, flyway, environment()))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(source);
    }

    @Test void acceptsSuccessfullyAppliedV20() { setup("20", migration("20", MigrationState.SUCCESS)); accepts(); }
    @Test void acceptsV21WithSuccessfullyAppliedV20() {
        setup("21", migration("20", MigrationState.SUCCESS), migration("21", MigrationState.SUCCESS)); accepts();
    }
    @Test void rejectsMissingCurrentMigration() { setup(null); rejects(); }
    @Test void rejectsCurrentBelowV20() { setup("19", migration("19", MigrationState.SUCCESS)); rejects(); }
    @Test void rejectsV20WithoutSuccessfulAppliedV20() { setup("20"); rejects(); }
    @Test void rejectsFailedV20EvenIfCurrentSaysV20() { setup("20", migration("20", MigrationState.FAILED)); rejects(); }
    @Test void rejectsV21WhichSkippedV20() { setup("21", migration("21", MigrationState.SUCCESS)); rejects(); }
    @Test void rejectsMigrationOnDifferentDataSource() {
        setup("21", migration("20", MigrationState.SUCCESS));
        when(configuration.getDataSource()).thenReturn(mock(DataSource.class)); rejects();
    }
}
