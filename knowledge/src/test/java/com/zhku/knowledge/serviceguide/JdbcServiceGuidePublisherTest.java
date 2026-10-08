package com.zhku.knowledge.serviceguide;

import com.zhku.knowledge.ZhkuFlywayTestSupport;

import com.zhku.knowledge.ZhkuDatabaseTestConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 清单发布器：可重复执行 + 整体替换。
 *
 * <p>这两条是"库里就是这一版审核内容"的硬保证：重跑同一份策展结果必须一致；
 * 换版后旧版残留条目必须消失（否则学生会看到上一版已被删掉的步骤或渠道）。</p>
 */
class JdbcServiceGuidePublisherTest {

    private static final String DATABASE_URL = com.zhku.knowledge.ZhkuDatabaseTestConfig.databaseUrl();
    private static final String DATABASE_USER = System.getProperty(
            "zhku.db.user", "zhkuagent");
    private static final String DATABASE_PASSWORD = ZhkuDatabaseTestConfig.databasePassword();

    private static final String IDENTIFIER = "publisher-test-guide";
    private static final String VERSION = "测试版";
    private static final Instant NOW = Instant.parse("2026-09-21T00:00:00Z");

    private DataSource dataSource;
    private JdbcServiceGuidePublisher publisher;
    private JdbcServiceGuideRepository repository;

    @BeforeEach
    void setUp() {
        ZhkuFlywayTestSupport.migrate(DATABASE_URL, DATABASE_USER, DATABASE_PASSWORD);
        dataSource = new DriverManagerDataSource(DATABASE_URL, DATABASE_USER, DATABASE_PASSWORD);
        publisher = new JdbcServiceGuidePublisher(dataSource);
        repository = new JdbcServiceGuideRepository(dataSource);
        deleteFixtures();
    }

    @AfterEach
    void tearDown() {
        deleteFixtures();
    }

    @Test
    void shouldBeIdempotentWhenPublishedTwice() {
        publisher.publish(guide(2));
        publisher.publish(guide(2));

        var found = repository.findPublished(IDENTIFIER, VERSION).orElseThrow();
        assertThat(found.steps()).hasSize(2);
        assertThat(found.channels()).hasSize(1);
        assertThat(found.riskNotices()).hasSize(1);
        assertThat(guideRowCount()).isEqualTo(1);
    }

    @Test
    void shouldReplaceChildrenWhenRepublishedWithFewerSteps() {
        publisher.publish(guide(3));
        publisher.publish(guide(1));

        var found = repository.findPublished(IDENTIFIER, VERSION).orElseThrow();
        // 换版后旧步骤必须消失：逐条 diff 会把这些残留悄悄留在学生可见的卡片里。
        assertThat(found.steps()).extracting(GuideStep::stepOrder).containsExactly(1);
        assertThat(found.steps()).extracting(GuideStep::instruction).containsExactly("步骤 1");
    }

    private ServiceGuide guide(int stepCount) {
        List<GuideStep> steps = java.util.stream.IntStream.rangeClosed(1, stepCount)
                .mapToObj(index -> new GuideStep(index, "步骤 " + index, false))
                .toList();
        return new ServiceGuide(IDENTIFIER, VERSION, "发布器测试清单", "测试范围", steps,
                List.of(), List.of(new OfficialChannel(1, OfficialChannel.ChannelType.SCHOOL_OFFICE,
                        "测试办公室", "测试地点", "测试来源定位", NOW)),
                List.of(new ServiceGuide.RiskNotice(1, "测试提示")),
                null, null, "测试审核说明", true, NOW, null);
    }

    private int guideRowCount() {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM service_guide WHERE guide_identifier = ?")) {
            statement.setString(1, IDENTIFIER);
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getInt(1);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("统计清单行数失败", exception);
        }
    }

    private void deleteFixtures() {
        try (Connection connection = dataSource.getConnection()) {
            for (String table : List.of("service_guide_step", "service_guide_material",
                    "service_guide_channel", "service_guide_risk_notice")) {
                execute(connection, "DELETE FROM " + table + " WHERE guide_id IN ("
                        + "SELECT id FROM service_guide WHERE guide_identifier = ?)");
            }
            execute(connection, "DELETE FROM service_guide WHERE guide_identifier = ?");
        } catch (SQLException exception) {
            throw new IllegalStateException("清理夹具失败", exception);
        }
    }

    private void execute(Connection connection, String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, IDENTIFIER);
            statement.executeUpdate();
        }
    }
}
