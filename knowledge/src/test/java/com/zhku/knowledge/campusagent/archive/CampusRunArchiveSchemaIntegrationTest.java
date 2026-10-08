package com.zhku.knowledge.campusagent.archive;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class CampusRunArchiveSchemaIntegrationTest {
    @BeforeAll
    static void migrate() {
        ArchiveTestDatabase.migrate();
    }

    @Test
    void realMigratedTablesAndIndexesExist() throws Exception {
        try (var c = ArchiveTestDatabase.dataSource().getConnection(); var s = c.createStatement()) {
            try (var r = s.executeQuery("SELECT tablename FROM pg_tables WHERE schemaname='public'")) {
                var tables = new java.util.HashSet<String>();
                while (r.next())
                    tables.add(r.getString(1));
                assertThat(tables).contains("campus_agent_runtime_owner", "campus_agent_run_archive");
            }
            try (var r = s.executeQuery("SELECT count(*) FROM pg_indexes WHERE tablename='campus_agent_run_archive'")) {
                r.next();
                assertThat(r.getInt(1)).isGreaterThanOrEqualTo(4);
            }
        }
    }

    @Test
    void finalizedWithoutExecutionFactsIsRejectedByDatabase() throws Exception {
        try (var c = ArchiveTestDatabase.dataSource().getConnection(); var p = c.prepareStatement("""
                INSERT INTO campus_agent_run_archive
                (run_id,owner_instance_id,owner_epoch,task_kind,archive_status,schema_version,writer_version)
                VALUES (?, ?, 1, 'CAMPUS_CREDIT_SUMMARY', 'FINALIZED', 1, 'schema-test')
                """)) {
            p.setObject(1, UUID.randomUUID());
            p.setObject(2, UUID.randomUUID());
            assertThatThrownBy(p::executeUpdate).isInstanceOf(SQLException.class)
                    .extracting(e -> ((SQLException) e).getSQLState()).isEqualTo("23514");
        }
    }

    @Test
    void admittedCannotContainResultOrTerminationFacts() throws Exception {
        try (var c = ArchiveTestDatabase.dataSource().getConnection(); var p = c.prepareStatement("""
                INSERT INTO campus_agent_run_archive
                (run_id,owner_instance_id,owner_epoch,archive_status,schema_version,writer_version,result_payload)
                VALUES (?, ?, 1, 'ADMITTED', 1, 'schema-test', '{}')
                """)) {
            p.setObject(1, UUID.randomUUID());
            p.setObject(2, UUID.randomUUID());
            assertThatThrownBy(p::executeUpdate).isInstanceOf(SQLException.class)
                    .extracting(e -> ((SQLException) e).getSQLState()).isEqualTo("23514");
        }
    }

    @Test
    void migrationIsRepeatableAndHighestVersionIs21() throws Exception {
        ArchiveTestDatabase.migrate();
        try (var c = ArchiveTestDatabase.dataSource().getConnection();
                var s = c.createStatement();
                var r = s.executeQuery(
                        "SELECT max(version::integer) FROM flyway_schema_history WHERE success AND version IS NOT NULL")) {
            r.next();
            assertThat(r.getInt(1)).isEqualTo(21);
        }
    }
}
