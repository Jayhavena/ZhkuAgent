package com.zhku.knowledge.privacy;

import com.zhku.knowledge.ZhkuDatabaseTestConfig;
import com.zhku.knowledge.feedback.FeedbackAggregate;
import com.zhku.knowledge.feedback.FeedbackReason;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/** 隐私边界测试只验证可观察输出，不把原问题、回答或密钥作为遥测内容。 */
class AnonymousTelemetryTest {

    private static final DataSource DATA_SOURCE = new DriverManagerDataSource(
            com.zhku.knowledge.ZhkuDatabaseTestConfig.databaseUrl(),
            System.getProperty("zhku.db.user", "zhkuagent"),
            ZhkuDatabaseTestConfig.databasePassword());

    @Test
    void shouldRejectFreeTextAndSensitiveFieldsFromStructuredTelemetry() {
        assertThatIllegalArgumentException().isThrownBy(() -> PrivacyBoundary.anonymousTelemetry(Map.of(
                "route", "CURRENT_NOTICE",
                "retrievalProfile", "local-public-elective",
                "handoffRequired", false,
                "latencyBucket", "LT_1S",
                "message", "raw-student-question"))).withMessageContaining("不允许");
    }

    @Test
    void shouldRejectRawTextSmuggledIntoAnAllowedTelemetryDimension() {
        assertThatIllegalArgumentException().isThrownBy(() -> PrivacyBoundary.anonymousTelemetry(Map.of(
                "route", "CURRENT_NOTICE",
                "failureCategory", "raw-student-question"))).withMessageContaining("格式");
    }

    @Test
    void shouldKeepOnlyControlledDimensionsInValidStructuredTelemetry() {
        Map<String, Object> telemetry = PrivacyBoundary.anonymousTelemetry(Map.of(
                "route", "CURRENT_NOTICE",
                "retrievalProfile", "local-public-elective",
                "handoffRequired", false,
                "latencyBucket", "LT_1S",
                "eventDate", "2026-09-21"));

        assertThat(telemetry).containsExactlyInAnyOrderEntriesOf(Map.of(
                "route", "CURRENT_NOTICE",
                "retrievalProfile", "local-public-elective",
                "handoffRequired", false,
                "latencyBucket", "LT_1S",
                "eventDate", "2026-09-21"));
    }

    @Test
    void shouldDropSecretsFromConfigurationEcho() {
        Map<String, Object> echo = PrivacyBoundary.configurationEcho(Map.of(
                "provider", "dashscope",
                "model", "qwen-plus",
                "profile", "local-public-elective",
                "status", "READY",
                "apiKey", "test-secret",
                "ZHKU_DB_PASSWORD", "test-password"));

        assertThat(echo).containsExactlyInAnyOrderEntriesOf(Map.of(
                "provider", "dashscope",
                "model", "qwen-plus",
                "profile", "local-public-elective",
                "status", "READY"));
        assertThat(echo.values()).doesNotContain("test-secret", "test-password");
    }

    @Test
    void shouldExportOnlyWhitelistedAggregateFields() {
        Map<String, Object> exported = PrivacyBoundary.aggregateExport(new FeedbackAggregate(
                "course-selection", "2026-1", FeedbackReason.OUTDATED,
                LocalDate.of(2026, 9, 21), 3L));

        assertThat(exported).containsExactlyInAnyOrderEntriesOf(Map.of(
                "guideIdentifier", "course-selection",
                "guideVersion", "2026-1",
                "reason", "OUTDATED",
                "feedbackDate", "2026-09-21",
                "count", 3L));
    }

    @Test
    void shouldKeepRawAndIdentifyingColumnsOutOfAnonymousFeedbackDatabase() throws SQLException {
        try (var connection = DATA_SOURCE.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            assertThat(columnsOf(metadata, "anonymous_feedback_event")).containsExactlyInAnyOrder(
                    "guide_identifier", "guide_version", "feedback_reason",
                    "anonymous_session_hash", "dedupe_key", "created_at", "expires_at");
            assertThat(columnsOf(metadata, "anonymous_feedback_aggregate")).containsExactlyInAnyOrder(
                    "guide_identifier", "guide_version", "feedback_reason", "feedback_date", "feedback_count");
            assertThat(columnsOf(metadata, "anonymous_feedback_event"))
                    .doesNotContain("message", "question", "answer", "name", "student_id", "contact", "password",
                            "api_key");
            assertThat(columnsOf(metadata, "anonymous_feedback_aggregate"))
                    .doesNotContain("message", "question", "answer", "name", "student_id", "contact", "password",
                            "api_key");
        }
    }

    private static Set<String> columnsOf(DatabaseMetaData metadata, String tableName) throws SQLException {
        Set<String> columns = new HashSet<>();
        try (ResultSet result = metadata.getColumns(null, "public", tableName, null)) {
            while (result.next()) {
                columns.add(result.getString("COLUMN_NAME"));
            }
        }
        return columns;
    }
}
