package com.zhku.knowledge.conversation;

import com.zhku.knowledge.campusagent.archive.ArchiveTestDatabase;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import static org.assertj.core.api.Assertions.*;

/** V19→V20和原约束真库证据；此组不调用owned Store，不提前激活业务入口。 */
@Timeout(30)
class OwnedConversationSessionSchemaTest {
    @org.junit.jupiter.api.BeforeAll static void migratePublicSchema() { ArchiveTestDatabase.migrate(); }
    @Test void v19UpgradeKeepsAllSixFieldsAndLeavesOldSessionUnowned() throws Exception {
        String schema = "conversation_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        var source = ArchiveTestDatabase.dataSource();
        Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration").target("19").load().migrate();
        UUID id = UUID.randomUUID();
        Instant expiry = Instant.parse("2026-10-05T12:00:00Z");
        try (var connection = source.getConnection()) {
            connection.setSchema(schema);
            try (var insert = connection.prepareStatement("INSERT INTO conversation_session "
                    + "(session_id,status,pending_intent,pending_field,expires_at,revision) VALUES (?,'ACTIVE',"
                    + "'ELECTIVE_RETAKE_AFTER_FAILURE','COURSE_TYPE',?,4)")) {
                insert.setObject(1, id); insert.setTimestamp(2, Timestamp.from(expiry)); insert.executeUpdate();
            }
        }
        Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration").load().migrate();
        try (var connection = source.getConnection()) {
            connection.setSchema(schema);
            var columns = new HashSet<String>();
            try (var metadata = connection.getMetaData().getColumns(null, schema, "conversation_session", null)) {
                while (metadata.next()) columns.add(metadata.getString("COLUMN_NAME"));
            }
            assertThat(columns).containsExactlyInAnyOrder("session_id", "status", "pending_intent", "pending_field",
                    "expires_at", "revision", "owner_user_id");
            try (var query = connection.prepareStatement("SELECT * FROM conversation_session WHERE session_id=?")) {
                query.setObject(1, id);
                try (var row = query.executeQuery()) {
                    assertThat(row.next()).isTrue();
                    assertThat(row.getString("status")).isEqualTo("ACTIVE");
                    assertThat(row.getString("pending_intent")).isEqualTo("ELECTIVE_RETAKE_AFTER_FAILURE");
                    assertThat(row.getString("pending_field")).isEqualTo("COURSE_TYPE");
                    assertThat(row.getTimestamp("expires_at").toInstant()).isEqualTo(expiry);
                    assertThat(row.getLong("revision")).isEqualTo(4);
                    assertThat(row.getObject("owner_user_id")).isNull();
                }
            }
        }
        // 专用容器整体销毁隔离schema；不改旧迁移，也不DROP业务表补造验收。
    }

    @Test void ownershipAndIdCannotChangeAndForeignKeyRestrictsDeletion() throws Exception {
        ArchiveTestDatabase.migrate();
        UUID owner = UUID.randomUUID(), another = UUID.randomUUID(), privateId = UUID.randomUUID(), anonymous = UUID.randomUUID();
        try (var connection = ArchiveTestDatabase.dataSource().getConnection()) {
            identity(connection, owner); identity(connection, another);
            insertSession(connection, privateId, owner); insertSession(connection, anonymous, null);
            assertThat(sqlState(connection, "UPDATE conversation_session SET owner_user_id=? WHERE session_id=?", another, privateId))
                    .isEqualTo("23514");
            assertThat(sqlState(connection, "UPDATE conversation_session SET owner_user_id=NULL WHERE session_id=?", privateId))
                    .isEqualTo("23514");
            assertThat(sqlState(connection, "UPDATE conversation_session SET owner_user_id=? WHERE session_id=?", owner, anonymous))
                    .isEqualTo("23514");
            assertThat(sqlState(connection, "UPDATE conversation_session SET session_id=? WHERE session_id=?", UUID.randomUUID(), privateId))
                    .isEqualTo("23514");
            assertThat(sqlState(connection, "DELETE FROM preview_identity WHERE user_id=?", owner)).isEqualTo("23503");
            assertThat(sqlState(connection, "INSERT INTO conversation_session(session_id,status,expires_at,revision,owner_user_id)"
                    + " VALUES (?,'ACTIVE',clock_timestamp(),0,?)", UUID.randomUUID(), UUID.randomUUID())).isEqualTo("23503");
            for (UUID id : List.of(privateId, anonymous)) execute(connection, "DELETE FROM conversation_session WHERE session_id=?", id);
            for (UUID id : List.of(owner, another)) execute(connection, "DELETE FROM preview_identity WHERE user_id=?", id);
        }
    }

    @Test void originalStateConstraintsAndOwnedExpiryIndexRemain() throws Exception {
        ArchiveTestDatabase.migrate();
        try (var connection = ArchiveTestDatabase.dataSource().getConnection()) {
            for (String values : List.of("'BROKEN',NULL,NULL,0", "'ACTIVE',NULL,NULL,-1",
                    "'ACTIVE','ELECTIVE_RETAKE_AFTER_FAILURE',NULL,0", "'CANCELLED','ELECTIVE_RETAKE_AFTER_FAILURE','COURSE_TYPE',0")) {
                assertThat(sqlState(connection, "INSERT INTO conversation_session(session_id,expires_at,status,pending_intent,pending_field,revision)"
                        + " VALUES (?,clock_timestamp()," + values + ")", UUID.randomUUID())).isEqualTo("23514");
            }
            try (var query = connection.prepareStatement("SELECT indexdef FROM pg_indexes WHERE schemaname='public' AND tablename='conversation_session'");
                    var rows = query.executeQuery()) {
                var definitions = new ArrayList<String>(); while (rows.next()) definitions.add(rows.getString(1));
                assertThat(definitions).anyMatch(definition -> definition.contains("(owner_user_id, expires_at, session_id)"));
                assertThat(definitions).anyMatch(definition -> definition.contains("(expires_at)"));
            }
        }
    }

    static void identity(Connection connection, UUID id) throws SQLException {
        String hash = new BCryptPasswordEncoder(10).encode("Synthetic-" + UUID.randomUUID());
        try (var insert = connection.prepareStatement("INSERT INTO preview_identity(user_id,login_name,password_hash,role) VALUES (?,?,?,'USER')")) {
            insert.setObject(1, id); insert.setString(2, "user-" + id.toString().substring(0, 16)); insert.setString(3, hash);
            insert.setQueryTimeout(1); insert.executeUpdate();
        }
    }
    static void insertSession(Connection connection, UUID id, UUID owner) throws SQLException {
        execute(connection, "INSERT INTO conversation_session(session_id,status,expires_at,revision,owner_user_id)"
                + " VALUES (?,'ACTIVE',clock_timestamp()+interval '10 minutes',0,?)", id, owner);
    }
    static int execute(Connection connection, String sql, Object... parameters) throws SQLException {
        try (var query = connection.prepareStatement(sql)) {
            query.setQueryTimeout(1); for (int index = 0; index < parameters.length; index++) query.setObject(index + 1, parameters[index]);
            return query.executeUpdate();
        }
    }
    static String sqlState(Connection connection, String sql, Object... parameters) {
        try { execute(connection, sql, parameters); return "UNEXPECTED_SUCCESS"; }
        catch (SQLException failure) { return failure.getSQLState(); }
    }
}
