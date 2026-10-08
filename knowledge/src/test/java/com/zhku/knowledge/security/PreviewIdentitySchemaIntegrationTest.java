package com.zhku.knowledge.security;

import com.zhku.knowledge.campusagent.archive.ArchiveTestDatabase;
import java.sql.SQLException;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

class PreviewIdentitySchemaIntegrationTest {
    @BeforeAll static void migrate() { ArchiveTestDatabase.migrate(); }

    @Test void realV18UpgradePreservesAnonymousRunAndForbidsLaterClaiming() throws Exception {
        String schema = "identity_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        var source = ArchiveTestDatabase.dataSource();
        Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration").target("18").outOfOrder(false).load().migrate();
        UUID run = UUID.randomUUID();
        try (var c = source.getConnection()) {
            c.setSchema(schema);
            try (var p = c.prepareStatement("INSERT INTO campus_agent_run_archive "
                    + "(run_id,owner_instance_id,owner_epoch,archive_status,schema_version,writer_version) VALUES (?,?,0,'ADMITTED',1,'upgrade-test')")) {
                p.setObject(1, run); p.setObject(2, UUID.randomUUID()); p.executeUpdate();
            }
        }
        Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration").outOfOrder(false).load().migrate();
        try (var c = source.getConnection()) {
            c.setSchema(schema);
            try (var p = c.prepareStatement("SELECT owner_user_id FROM campus_agent_run_archive WHERE run_id=?")) {
                p.setObject(1, run);
                try (var row = p.executeQuery()) { assertThat(row.next()).isTrue(); assertThat(row.getObject(1)).isNull(); }
            }
            UUID user = UUID.randomUUID();
            char[] password = UUID.randomUUID().toString().replace("-", "").toCharArray();
            try { PreviewIdentityRepositoryIntegrationTest.insertIdentity(c, user, "user-upgrade", password); }
            finally { java.util.Arrays.fill(password, '\0'); }
            try (var p = c.prepareStatement("UPDATE campus_agent_run_archive SET owner_user_id=? WHERE run_id=?")) {
                p.setObject(1, user); p.setObject(2, run);
                assertThat(sqlState(p::executeUpdate)).isEqualTo("23514");
            }
        } catch (SQLException failure) { throw PreviewIdentityRepositoryIntegrationTest.safeSqlFailure(failure); }
        // 专用临时容器整体退出回收该隔离schema；不DROP、不改Flyway历史。
    }

    @Test void identityChangeRequiresExactlyOneRevisionIncrement() throws Exception {
        UUID id = UUID.randomUUID();
        char[] password = UUID.randomUUID().toString().replace("-", "").toCharArray();
        try (var c = ArchiveTestDatabase.dataSource().getConnection()) {
            try { PreviewIdentityRepositoryIntegrationTest.insertIdentity(c, id, "user-revision", password); }
            finally { java.util.Arrays.fill(password, '\0'); }
            try (var p = c.prepareStatement("UPDATE preview_identity SET enabled=false WHERE user_id=?")) {
                p.setObject(1, id); assertThat(sqlState(p::executeUpdate)).isEqualTo("23514");
            }
            try (var p = c.prepareStatement("UPDATE preview_identity SET auth_revision=-1 WHERE user_id=?")) {
                p.setObject(1, id); assertThat(sqlState(p::executeUpdate)).isEqualTo("23514");
            }
            try (var p = c.prepareStatement("UPDATE preview_identity SET user_id=? WHERE user_id=?")) {
                p.setObject(1, UUID.randomUUID()); p.setObject(2, id);
                assertThat(sqlState(p::executeUpdate)).isEqualTo("23514");
            }
        } catch (SQLException failure) { throw PreviewIdentityRepositoryIntegrationTest.safeSqlFailure(failure); }
    }

    @FunctionalInterface private interface Sql { int run() throws SQLException; }
    private static String sqlState(Sql sql) {
        try { sql.run(); return "UNEXPECTED_SUCCESS"; }
        catch (SQLException failure) { return failure.getSQLState(); }
    }
}
