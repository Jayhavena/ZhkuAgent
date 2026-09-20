package com.zhku.knowledge.serviceguide;

import com.zhku.knowledge.ZhkuDatabaseTestConfig;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 办事清单 JDBC 仓储：验证"已发布 + 学生可见"的整链读取与装配。
 *
 * <p>关键不是"能读出来"，而是**不该读出来的读不出来**：内部参考版本、未发布版本
 * 都必须收敛为空——这是"内部状态不进学生回答"在数据访问层的最后一道闸。</p>
 */
class JdbcServiceGuideRepositoryTest {

    private static final String DATABASE_URL = System.getProperty(
            "zhku.db.url", "jdbc:postgresql://127.0.0.1:54329/zhkuagent");
    private static final String DATABASE_USER = System.getProperty(
            "zhku.db.user", "zhkuagent");
    private static final String DATABASE_PASSWORD =
            ZhkuDatabaseTestConfig.databasePassword();

    private static final String IDENTIFIER = "elective-adjust";
    private static final String PUBLISHED_VERSION = "2025版";
    private static final String INTERNAL_VERSION = "内部参考";

    private DataSource dataSource;
    private JdbcServiceGuideRepository repository;

    @BeforeEach
    void setUp() {
        Flyway.configure()
                .dataSource(DATABASE_URL, DATABASE_USER, DATABASE_PASSWORD)
                .locations("classpath:db/migration")
                .load()
                .migrate();
        dataSource = new DriverManagerDataSource(DATABASE_URL, DATABASE_USER, DATABASE_PASSWORD);
        repository = new JdbcServiceGuideRepository(dataSource);
        deleteFixtures();
    }

    @AfterEach
    void tearDown() {
        deleteFixtures();
    }

    @Test
    void shouldAssemblePublishedStudentVisibleGuideWithAllChildren() {
        seedGuide(PUBLISHED_VERSION, true);

        var guide = repository.findPublished(IDENTIFIER, PUBLISHED_VERSION);

        assertThat(guide).isPresent();
        var value = guide.get();
        assertThat(value.title()).isEqualTo("选课调整办理指引");
        assertThat(value.steps()).extracting(GuideStep::stepOrder).containsExactly(1, 2);
        assertThat(value.steps()).extracting(GuideStep::instruction)
                .containsExactly("查看当学期选课通知", "按通知到指定办公室确认");
        assertThat(value.materials()).hasSize(1);
        assertThat(value.channels()).hasSize(2);
        assertThat(value.channels()).extracting(OfficialChannel::channelType)
                .containsExactly(OfficialChannel.ChannelType.SCHOOL_OFFICE,
                        OfficialChannel.ChannelType.PHONE);
        assertThat(value.channels()).extracting(OfficialChannel::sourceLocator)
                .doesNotContainNull();
        assertThat(value.riskNotices()).extracting(ServiceGuide.RiskNotice::noticeText)
                .containsExactly("以当学期官方通知为准");
        assertThat(value.sourceRuleReference()).isEqualTo("PUBLIC_ELECTIVE_RULE#2025版");
    }

    @Test
    void shouldHideInternalVersionFromStudentQuery() {
        // 同一清单的内部参考版本（student_visible=false）：学生侧查询必须查不到。
        seedGuide(INTERNAL_VERSION, false);

        assertThat(repository.findPublished(IDENTIFIER, INTERNAL_VERSION)).isEmpty();
    }

    @Test
    void shouldReturnEmptyForUnknownVersion() {
        seedGuide(PUBLISHED_VERSION, true);

        assertThat(repository.findPublished(IDENTIFIER, "不存在的版本")).isEmpty();
        assertThat(repository.findPublished("未知清单", PUBLISHED_VERSION)).isEmpty();
    }

    private void seedGuide(String version, boolean studentVisible) {
        Instant now = Instant.parse("2026-09-20T00:00:00Z");
        try (Connection connection = dataSource.getConnection()) {
            execute(connection, """
                    INSERT INTO service_guide (id, guide_identifier, guide_version, title,
                        applicable_scope, source_rule_reference, review_notice,
                        student_visible, published_at)
                    VALUES (?, ?, ?, '选课调整办理指引', '全日制本科生',
                        'PUBLIC_ELECTIVE_RULE#2025版', '审核说明', ?, ?)
                    """, statement -> {
                statement.setObject(1, UUID.randomUUID());
                statement.setString(2, IDENTIFIER);
                statement.setString(3, version);
                statement.setBoolean(4, studentVisible);
                statement.setTimestamp(5, Timestamp.from(now));
            });
            UUID guideId = findGuideId(connection, version);
            execute(connection, """
                    INSERT INTO service_guide_step (id, guide_id, step_order, instruction,
                        requires_identity_check)
                    VALUES (?, ?, 1, '查看当学期选课通知', FALSE), (?, ?, 2, '按通知到指定办公室确认', TRUE)
                    """, statement -> {
                statement.setObject(1, UUID.randomUUID());
                statement.setObject(2, guideId);
                statement.setObject(3, UUID.randomUUID());
                statement.setObject(4, guideId);
            });
            execute(connection, """
                    INSERT INTO service_guide_material (id, guide_id, material_order, material_name,
                        required)
                    VALUES (?, ?, 1, '学生证', TRUE)
                    """, statement -> {
                statement.setObject(1, UUID.randomUUID());
                statement.setObject(2, guideId);
            });
            execute(connection, """
                    INSERT INTO service_guide_channel (id, guide_id, channel_order, channel_type,
                        channel_name, channel_value, source_locator, verified_at)
                    VALUES (?, ?, 1, 'SCHOOL_OFFICE', '教务处学籍科', '教务处 201 室',
                        'PDF 第 77 页', ?),
                           (?, ?, 2, 'PHONE', '选课咨询专线', '0755-12345678',
                        'PDF 第 77 页', ?)
                    """, statement -> {
                statement.setObject(1, UUID.randomUUID());
                statement.setObject(2, guideId);
                statement.setTimestamp(3, Timestamp.from(now));
                statement.setObject(4, UUID.randomUUID());
                statement.setObject(5, guideId);
                statement.setTimestamp(6, Timestamp.from(now));
            });
            execute(connection, """
                    INSERT INTO service_guide_risk_notice (id, guide_id, notice_order, notice_text)
                    VALUES (?, ?, 1, '以当学期官方通知为准')
                    """, statement -> {
                statement.setObject(1, UUID.randomUUID());
                statement.setObject(2, guideId);
            });
        } catch (SQLException exception) {
            throw new IllegalStateException("夹具写入失败", exception);
        }
    }

    private UUID findGuideId(Connection connection, String version) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id FROM service_guide WHERE guide_identifier = ? AND guide_version = ?")) {
            statement.setString(1, IDENTIFIER);
            statement.setString(2, version);
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getObject("id", UUID.class);
            }
        }
    }

    private interface StatementBinder {
        void bind(PreparedStatement statement) throws SQLException;
    }

    private void execute(Connection connection, String sql, StatementBinder binder)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            statement.executeUpdate();
        }
    }

    private void deleteFixtures() {
        try (Connection connection = dataSource.getConnection()) {
            execute(connection, """
                    DELETE FROM service_guide_step WHERE guide_id IN (
                        SELECT id FROM service_guide WHERE guide_identifier = ?)
                    """, statement -> statement.setString(1, IDENTIFIER));
            execute(connection, """
                    DELETE FROM service_guide_material WHERE guide_id IN (
                        SELECT id FROM service_guide WHERE guide_identifier = ?)
                    """, statement -> statement.setString(1, IDENTIFIER));
            execute(connection, """
                    DELETE FROM service_guide_channel WHERE guide_id IN (
                        SELECT id FROM service_guide WHERE guide_identifier = ?)
                    """, statement -> statement.setString(1, IDENTIFIER));
            execute(connection, """
                    DELETE FROM service_guide_risk_notice WHERE guide_id IN (
                        SELECT id FROM service_guide WHERE guide_identifier = ?)
                    """, statement -> statement.setString(1, IDENTIFIER));
            execute(connection, "DELETE FROM service_guide WHERE guide_identifier = ?",
                    statement -> statement.setString(1, IDENTIFIER));
        } catch (SQLException exception) {
            throw new IllegalStateException("夹具清理失败", exception);
        }
    }
}
