package com.zhku.knowledge.conversation;

import com.zhku.knowledge.ZhkuFlywayTestSupport;

import com.zhku.knowledge.ZhkuDatabaseTestConfig;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** 数据库会话存储的最小结构契约。 */
class ConversationSessionSchemaTest {

    private static final String DATABASE_URL = com.zhku.knowledge.ZhkuDatabaseTestConfig.databaseUrl();
    private static final String DATABASE_USER = System.getProperty("zhku.db.user", "zhkuagent");
    private static final String DATABASE_PASSWORD = ZhkuDatabaseTestConfig.databasePassword();

    @Test
    void shouldProvideVersionedConversationSessionStoreSchema() throws SQLException {
        assertThat(DATABASE_PASSWORD)
                .as("运行数据库契约测试时必须显式提供本机密码")
                .isNotBlank();
        ZhkuFlywayTestSupport.migrate(DATABASE_URL, DATABASE_USER, DATABASE_PASSWORD);

        try (Connection connection = DriverManager.getConnection(
                DATABASE_URL,
                DATABASE_USER,
                DATABASE_PASSWORD
        )) {
            DatabaseMetaData metadata = connection.getMetaData();
            assertThat(tableExists(metadata, "conversation_session")).isTrue();
            assertThat(columnsOf(metadata, "conversation_session")).containsExactlyInAnyOrder(
                    "session_id",
                    "status",
                    "pending_intent",
                    "pending_field",
                    "expires_at",
                    "revision",
                    "owner_user_id"
            );
            assertThat(indexColumnsOf(metadata, "conversation_session"))
                    .contains("expires_at");
        }
    }

    private static boolean tableExists(DatabaseMetaData metadata, String tableName)
            throws SQLException {
        try (ResultSet tables = metadata.getTables(
                null,
                "public",
                tableName,
                new String[]{"TABLE"}
        )) {
            return tables.next();
        }
    }

    private static Set<String> columnsOf(DatabaseMetaData metadata, String tableName)
            throws SQLException {
        Set<String> columns = new HashSet<>();
        try (ResultSet result = metadata.getColumns(null, "public", tableName, null)) {
            while (result.next()) {
                columns.add(result.getString("COLUMN_NAME"));
            }
        }
        return columns;
    }

    private static Set<String> indexColumnsOf(DatabaseMetaData metadata, String tableName)
            throws SQLException {
        Set<String> columns = new HashSet<>();
        try (ResultSet result = metadata.getIndexInfo(
                null,
                "public",
                tableName,
                false,
                false
        )) {
            while (result.next()) {
                String column = result.getString("COLUMN_NAME");
                if (column != null) {
                    columns.add(column);
                }
            }
        }
        return columns;
    }
}
