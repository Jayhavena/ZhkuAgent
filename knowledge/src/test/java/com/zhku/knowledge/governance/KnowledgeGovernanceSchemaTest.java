package com.zhku.knowledge.governance;

import com.zhku.knowledge.ZhkuDatabaseTestConfig;
import com.zhku.knowledge.ZhkuFlywayTestSupport;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeGovernanceSchemaTest {

    private static final String DATABASE_URL = com.zhku.knowledge.ZhkuDatabaseTestConfig.databaseUrl();
    private static final String DATABASE_USER = System.getProperty(
            "zhku.db.user",
            "zhkuagent"
    );
    private static final String DATABASE_PASSWORD =
            ZhkuDatabaseTestConfig.databasePassword();
    private static final UUID PUBLIC_ELECTIVE_SOURCE_ID = UUID.fromString(
            "2a04fd61-529e-4e6b-8ad6-9666f21a5037"
    );

    @Test
    void shouldProvideGovernanceStagingSchemaAndMapExistingPublicElective()
            throws SQLException {
        assertThat(DATABASE_PASSWORD)
                .as("运行资料治理数据库测试时必须显式提供本机密码")
                .isNotBlank();

        ZhkuFlywayTestSupport.migrate(DATABASE_URL, DATABASE_USER, DATABASE_PASSWORD);

        try (Connection connection = DriverManager.getConnection(
                DATABASE_URL,
                DATABASE_USER,
                DATABASE_PASSWORD
        )) {
            DatabaseMetaData metadata = connection.getMetaData();
            assertThat(tableExists(metadata, "knowledge_submission")).isTrue();
            assertThat(tableExists(metadata, "knowledge_candidate")).isTrue();
            assertThat(tableExists(metadata, "knowledge_review_event")).isTrue();
            assertThat(columnsOf(metadata, "knowledge_source")).contains(
                    "document_family",
                    "knowledge_scope"
            );
            assertPublicElectiveMappingWhenPresent(connection);
        }
    }

    private static void assertPublicElectiveMappingWhenPresent(
            Connection connection
    ) throws SQLException {
        try (var statement = connection.prepareStatement("""
                SELECT document_family, knowledge_scope
                FROM knowledge_source
                WHERE id = ?
                """)) {
            statement.setObject(1, PUBLIC_ELECTIVE_SOURCE_ID);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return;
                }
                assertThat(result.getString("document_family"))
                        .isEqualTo("STUDENT_HANDBOOK");
                assertThat(result.getString("knowledge_scope"))
                        .isEqualTo("PUBLIC_ELECTIVE");
            }
        }
    }

    private static boolean tableExists(
            DatabaseMetaData metadata,
            String tableName
    ) throws SQLException {
        try (ResultSet tables = metadata.getTables(
                null,
                "public",
                tableName,
                new String[]{"TABLE"}
        )) {
            return tables.next();
        }
    }

    private static Set<String> columnsOf(
            DatabaseMetaData metadata,
            String tableName
    ) throws SQLException {
        Set<String> columns = new HashSet<>();
        try (ResultSet result = metadata.getColumns(
                null,
                "public",
                tableName,
                null
        )) {
            while (result.next()) {
                columns.add(result.getString("COLUMN_NAME"));
            }
        }
        return columns;
    }
}
