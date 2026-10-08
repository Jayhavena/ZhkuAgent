package com.zhku.knowledge;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * `ZhkuFlywayTestSupport` 的验收：**乱序迁移可被补应用**，且这确由该设置带来。
 *
 * <p>用临时目录里的脚本 + 专用 schema 模拟真实情形：先跑 V1、V3（模拟"高版本先入库"），
 * 之后补一个 V2（模拟"低版本后补"）。这正是本项目踩过的坑（V16 先应用、V15 后补，
 * 导致所有入口校验失败），所以这里把它固化成回归。</p>
 *
 * <p>同时给一个**反向对照**：不开该设置时，同样的场景必须报校验失败——
 * 否则"能通过"就可能只是没触发检查，而不是设置真的起作用。</p>
 */
class ZhkuFlywayTestSupportTest {

    private static final String DATABASE_URL = com.zhku.knowledge.ZhkuDatabaseTestConfig.databaseUrl();
    private static final String DATABASE_USER = System.getProperty(
            "zhku.db.user", "zhkuagent");
    private static final String DATABASE_PASSWORD = ZhkuDatabaseTestConfig.databasePassword();

    private static final String SUPPORT_SCHEMA = "zhku_flyway_support_test";
    private static final String CONTROL_SCHEMA = "zhku_flyway_control_test";

    @TempDir
    Path migrationDirectory;

    @BeforeEach
    void setUp() throws SQLException {
        dropSchemas();
    }

    @AfterEach
    void tearDown() throws SQLException {
        dropSchemas();
    }

    @Test
    void shouldApplyLowerVersionedMigrationAfterHigherOneWhenOutOfOrderEnabled() throws Exception {
        writeMigration("V1__base.sql", "CREATE TABLE support_probe (id integer primary key);");
        writeMigration("V3__later.sql", "ALTER TABLE support_probe ADD COLUMN note text;");
        Flyway flyway = ZhkuFlywayTestSupport.flywayIn(
                DATABASE_URL, DATABASE_USER, DATABASE_PASSWORD, location(), SUPPORT_SCHEMA);
        flyway.migrate();

        // 模拟"低版本后补"：此刻最高版本已是 3。
        writeMigration("V2__middle.sql", "CREATE TABLE support_probe_middle (id integer primary key);");
        flyway.migrate();

        assertThat(appliedVersions(flyway)).contains("1", "2", "3");
    }

    @Test
    void shouldRejectTheSameOutOfOrderScenarioWhenSettingIsOff() throws Exception {
        writeMigration("V1__base.sql", "CREATE TABLE support_probe (id integer primary key);");
        writeMigration("V3__later.sql", "ALTER TABLE support_probe ADD COLUMN note text;");
        Flyway withoutOutOfOrder = Flyway.configure()
                .dataSource(DATABASE_URL, DATABASE_USER, DATABASE_PASSWORD)
                .locations(location())
                .schemas(CONTROL_SCHEMA)
                .defaultSchema(CONTROL_SCHEMA)
                .load();
        withoutOutOfOrder.migrate();

        writeMigration("V2__middle.sql", "CREATE TABLE support_probe_middle (id integer primary key);");

        // 反向对照：不开设置就必须失败——证明上一条用例的通过来自该设置，而不是"没检查"。
        assertThatThrownBy(withoutOutOfOrder::migrate).isInstanceOf(FlywayException.class);
    }

    private String location() {
        return "filesystem:" + migrationDirectory.toAbsolutePath().toString().replace('\\', '/');
    }

    private void writeMigration(String fileName, String sql) throws Exception {
        Files.writeString(migrationDirectory.resolve(fileName), sql);
    }

    private static String[] appliedVersions(Flyway flyway) {
        return Arrays.stream(flyway.info().applied())
                .map(info -> info.getVersion() == null ? "null" : info.getVersion().getVersion())
                .toArray(String[]::new);
    }

    private static void dropSchemas() throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                DATABASE_URL, DATABASE_USER, DATABASE_PASSWORD);
             Statement statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS " + SUPPORT_SCHEMA + " CASCADE");
            statement.execute("DROP SCHEMA IF EXISTS " + CONTROL_SCHEMA + " CASCADE");
        }
    }
}
