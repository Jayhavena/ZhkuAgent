package com.yupi.yuaiagent.zhku;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 仲园 Agent 知识库的最小数据契约测试。
 *
 * <p>该测试在本机开发数据库执行版本化结构迁移，但不写入或清理任何资料。
 * 它确保后续问答至少有“资料来源—资料片段—发布状态”这一条可追溯链路。</p>
 */
class KnowledgeSchemaTest {

    // 允许命令行覆盖连接信息；默认值只服务于本机 Docker 开发环境。
    private static final String DATABASE_URL = System.getProperty(
            "zhku.db.url",
            "jdbc:postgresql://127.0.0.1:54329/zhkuagent"
    );

    private static final String DATABASE_USER = System.getProperty(
            "zhku.db.user",
            "zhkuagent"
    );

    private static final String DATABASE_PASSWORD =
            ZhkuDatabaseTestConfig.databasePassword();

    @Test
    void shouldProvideTraceableKnowledgeSourceAndChunkSchema()
            throws SQLException {
        assertThat(DATABASE_PASSWORD)
                .as("运行数据库契约测试时必须显式提供本机密码")
                .isNotBlank();

        // 测试通过真实迁移而非手工建表，确保提交到仓库的结构脚本本身可用。
        Flyway.configure()
                .dataSource(
                        DATABASE_URL,
                        DATABASE_USER,
                        DATABASE_PASSWORD
                )
                .locations("classpath:db/migration")
                .load()
                .migrate();

        try (Connection connection = DriverManager.getConnection(
                DATABASE_URL,
                DATABASE_USER,
                DATABASE_PASSWORD
        )) {
            // 读取数据库元数据验证外部可观察的契约，不依赖表实现细节或模拟对象。
            DatabaseMetaData metadata = connection.getMetaData();

            assertThat(tableExists(metadata, "knowledge_source"))
                    .as("知识库必须保存可审核的资料来源")
                    .isTrue();
            assertThat(tableExists(metadata, "knowledge_chunk"))
                    .as("知识库必须保存可引用的资料片段")
                    .isTrue();

            Set<String> sourceColumns = columnsOf(
                    metadata,
                    "knowledge_source"
            );
            assertThat(sourceColumns).contains(
                    "id",
                    "title",
                    "source_version",
                    "publication_status",
                    "published_at",
                    "visibility_scope",
                    "publication_scope",
                    "review_notice",
                    "sharing_rights_confirmed"
            );

            Set<String> chunkColumns = columnsOf(
                    metadata,
                    "knowledge_chunk"
            );
            assertThat(chunkColumns).contains(
                    "id",
                    "source_id",
                    "page_number",
                    "content",
                    "chunk_index"
            );
            assertThat(hasForeignKeyTo(
                    metadata,
                    "knowledge_chunk",
                    "knowledge_source"
            )).as("资料片段必须关联到其来源")
                    .isTrue();
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

    private static boolean hasForeignKeyTo(
            DatabaseMetaData metadata,
            String foreignKeyTable,
            String primaryKeyTable
    ) throws SQLException {
        try (ResultSet foreignKeys = metadata.getImportedKeys(
                null,
                "public",
                foreignKeyTable
        )) {
            while (foreignKeys.next()) {
                if (primaryKeyTable.equals(
                        foreignKeys.getString("PKTABLE_NAME")
                )) {
                    return true;
                }
            }
        }
        return false;
    }
}
