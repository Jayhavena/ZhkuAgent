package com.zhku.knowledge.telemetry;

import com.zhku.knowledge.ZhkuDatabaseTestConfig;
import com.zhku.knowledge.ZhkuFlywayTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 匿名遥测写入路径：**只允许白名单维度入表**，其余一律拒绝（含反向断言）。
 *
 * <p>本用例的验收重点不是"能写进去"，而是"写不进不该写的"：自由文本、原问答、身份信息、
 * 密钥、逐条散列都必须被拒；被拒时**一行都不能落库**（失败必须无副作用）。</p>
 */
class JdbcAnonymousTelemetryRepositoryTest {

    private static final String DATABASE_URL = com.zhku.knowledge.ZhkuDatabaseTestConfig.databaseUrl();
    private static final String DATABASE_USER = System.getProperty(
            "zhku.db.user", "zhkuagent");
    private static final String DATABASE_PASSWORD = ZhkuDatabaseTestConfig.databasePassword();

    /** 夹具隔离：用专门的日期做清理锚点，不与任何演示/他人数据共用标识。 */
    private static final LocalDate TEST_DATE = LocalDate.of(1990, 1, 1);

    private DataSource dataSource;
    private JdbcAnonymousTelemetryRepository repository;

    @BeforeEach
    void setUp() {
        ZhkuFlywayTestSupport.migrate(DATABASE_URL, DATABASE_USER, DATABASE_PASSWORD);
        dataSource = new DriverManagerDataSource(DATABASE_URL, DATABASE_USER, DATABASE_PASSWORD);
        repository = new JdbcAnonymousTelemetryRepository(dataSource);
        deleteTestRows();
    }

    @AfterEach
    void tearDown() {
        deleteTestRows();
    }

    @Test
    void shouldRecordWhitelistedDimensionsOnly() {
        Map<String, Object> fields = validFields();
        fields.put("failureCategory", "NONE");

        repository.record(fields);

        assertThat(repository.countByEventDate(TEST_DATE)).isEqualTo(1);
        assertThat(storedRouteOfFirstRow()).isEqualTo("HIGH_RISK_RULE");
    }

    @Test
    void shouldRejectFieldsOutsideWhitelist() {
        // 反向断言：自由文本 / 原问答 / 身份信息 / 密钥 / 逐条散列——一个都不许进。
        for (String forbidden : new String[]{
                "message", "question", "answer", "studentId", "name", "contact",
                "apiKey", "anonymousSessionHash"}) {
            Map<String, Object> fields = validFields();
            fields.put(forbidden, "公共选修课怎么办理？学号 20230001");

            assertThatThrownBy(() -> repository.record(fields))
                    .as("字段 %s 必须被拒", forbidden)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining(forbidden);
        }
        assertThat(repository.countByEventDate(TEST_DATE)).isZero();
    }

    @Test
    void shouldRejectFreeTextValueInAllowedField() {
        Map<String, Object> fields = validFields();
        fields.put("route", "公共选修课怎么办理");

        assertThatThrownBy(() -> repository.record(fields))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("格式不合法");
        assertThat(repository.countByEventDate(TEST_DATE)).isZero();
    }

    @Test
    void shouldRejectMissingRequiredDimension() {
        Map<String, Object> fields = validFields();
        fields.remove("eventDate");

        assertThatThrownBy(() -> repository.record(fields))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("eventDate");
        assertThat(repository.countByEventDate(TEST_DATE)).isZero();
    }

    @Test
    void shouldRejectFreeTextAtDatabaseLevelToo() throws SQLException {
        // 第二道闸：即使绕过应用层直接写库，表上的 CHECK 也必须挡住自由文本。
        assertThatThrownBy(() -> insertRawRoute("公共选修课怎么办理"))
                .isInstanceOf(SQLException.class);
        assertThat(repository.countByEventDate(TEST_DATE)).isZero();
    }

    private Map<String, Object> validFields() {
        Map<String, Object> fields = new HashMap<>();
        fields.put("route", "HIGH_RISK_RULE");
        fields.put("retrievalProfile", "dashscope-text-embedding-v4-1024");
        fields.put("generatorProfile", "dashscope-qwen-plus");
        fields.put("handoffRequired", false);
        fields.put("latencyBucket", "S_1_3");
        fields.put("estimatedCostBucket", "LT_1K");
        fields.put("eventDate", TEST_DATE.toString());
        return fields;
    }

    private String storedRouteOfFirstRow() {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT route FROM anonymous_telemetry_event WHERE event_date = ?")) {
            statement.setObject(1, TEST_DATE);
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getString("route");
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("读取遥测行失败", exception);
        }
    }

    private void insertRawRoute(String route) throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                DATABASE_URL, DATABASE_USER, DATABASE_PASSWORD);
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO anonymous_telemetry_event
                         (id, route, handoff_required, latency_bucket, event_date)
                     VALUES (gen_random_uuid(), ?, FALSE, 'S_1_3', ?)
                     """)) {
            statement.setString(1, route);
            statement.setObject(2, TEST_DATE);
            statement.executeUpdate();
        }
    }

    private void deleteTestRows() {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM anonymous_telemetry_event WHERE event_date = ?")) {
            statement.setObject(1, TEST_DATE);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("清理遥测夹具失败", exception);
        }
    }
}
