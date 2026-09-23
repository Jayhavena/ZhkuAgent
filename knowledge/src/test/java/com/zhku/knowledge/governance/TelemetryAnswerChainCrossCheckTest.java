package com.zhku.knowledge.governance;

import com.zhku.knowledge.ZhkuDatabaseTestConfig;
import com.zhku.knowledge.ZhkuFlywayTestSupport;
import com.zhku.knowledge.conversation.ConversationStore;
import com.zhku.knowledge.conversation.InMemoryConversationStore;
import com.zhku.knowledge.conversation.elective.ElectiveRetakeConversationService;
import com.zhku.knowledge.conversation.publicelective.AdjustmentEvidence;
import com.zhku.knowledge.conversation.publicelective.PublicElectiveAdjustmentService;
import com.zhku.knowledge.conversation.publicelective.PublicElectiveConversationRouter;
import com.zhku.knowledge.conversation.publicelective.PublicElectiveCreditLimitService;
import com.zhku.knowledge.telemetry.JdbcAnonymousTelemetryRepository;
import com.zhku.knowledge.web.LocalPreviewAnswerChain;
import com.zhku.knowledge.web.LocalPreviewConversationFacade;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 线 B 对线 A 遥测接线的独立黑盒核对。
 *
 * <p>这不是仓库单测：真实问答必须穿过门面、路由和写库端口，才能证明回答链路没有把遥测
 * 白名单停留在库层。若移除门面的 {@code telemetry.record}、将未知会话也记录，或越过白名单
 * 扩展落库字段，本类的可见数据库结果都会失败。</p>
 */
class TelemetryAnswerChainCrossCheckTest {

    private static final String DATABASE_URL = System.getProperty(
            "zhku.db.url", "jdbc:postgresql://127.0.0.1:54329/zhkuagent");
    private static final String DATABASE_USER = System.getProperty("zhku.db.user", "zhkuagent");
    private static final String DATABASE_PASSWORD = ZhkuDatabaseTestConfig.databasePassword();
    private static final LocalDate TEST_DATE = LocalDate.of(1992, 2, 2);
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("1992-02-02T12:00:00Z"), ZoneOffset.UTC);
    private static final Duration TTL = Duration.ofMinutes(10);
    private static final Pattern CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");

    private DataSource dataSource;
    private JdbcAnonymousTelemetryRepository telemetry;

    @BeforeEach
    void setUp() {
        ZhkuFlywayTestSupport.migrate(DATABASE_URL, DATABASE_USER, DATABASE_PASSWORD);
        dataSource = new DriverManagerDataSource(DATABASE_URL, DATABASE_USER, DATABASE_PASSWORD);
        telemetry = new JdbcAnonymousTelemetryRepository(dataSource);
        deleteFixtureRows();
    }

    @AfterEach
    void tearDown() {
        deleteFixtureRows();
    }

    @Test
    void shouldPersistExactlyOneWhitelistedTelemetryRowForARealAnswer() {
        LocalPreviewConversationFacade facade = facadeWithJdbcTelemetry();

        facade.sendMessage(facade.createConversation().sessionId(), "公共选修课选课调整怎么办理");

        // 真实回答链路只应增加一行；读取列集合本身就是导出白名单，禁止把原问答或身份带出表。
        assertThat(telemetry.countByEventDate(TEST_DATE)).isEqualTo(1);
        Map<String, Object> row = storedFixtureRow();
        assertThat(row).containsOnlyKeys(
                "route", "retrievalProfile", "generatorProfile", "handoffRequired",
                "failureCategory", "latencyBucket", "estimatedCostBucket", "eventDate");
        assertThat((String) row.get("route")).matches(CODE);
        assertThat((String) row.get("failureCategory")).matches(CODE);
        assertThat((String) row.get("latencyBucket")).matches(CODE);
        assertThat(row.get("handoffRequired")).isEqualTo(false);
        assertThat(row.get("retrievalProfile")).isNull();
        assertThat(row.get("generatorProfile")).isNull();
        assertThat(row.get("estimatedCostBucket")).isNull();
        assertThat(row.get("eventDate")).isEqualTo(TEST_DATE);
    }

    @Test
    void shouldNotPersistTelemetryForAnUnknownSession() {
        LocalPreviewConversationFacade facade = facadeWithJdbcTelemetry();

        facade.sendMessage(UUID.fromString("c3111111-1111-4111-8111-111111111111"), "任意问题");

        // 未知会话是会话错误而非知识事件，落库会污染“未召回/降级/转人工”运营统计。
        assertThat(telemetry.countByEventDate(TEST_DATE)).isZero();
    }

    private LocalPreviewConversationFacade facadeWithJdbcTelemetry() {
        ConversationStore store = new InMemoryConversationStore();
        ElectiveRetakeConversationService retakeService = new ElectiveRetakeConversationService(
                store, courseType -> Optional.empty(), CLOCK, TTL);
        PublicElectiveCreditLimitService creditLimitService =
                new PublicElectiveCreditLimitService(Optional::empty);
        PublicElectiveAdjustmentService adjustmentService = new PublicElectiveAdjustmentService(
                () -> Optional.of(new AdjustmentEvidence(
                        UUID.fromString("72b0f39e-9c69-47f6-b3f2-7662fef2d4bd"),
                        "本科生学生手册（2025版）·公共选修课修读与考核规则",
                        "2025版-公共选修修读与考核-学生可见",
                        "PDF 第 77 页，第 9、13 条",
                        "办理公共选修课选课与修读的全日制普通本科生",
                        "选课、退选、补选和改选应按照每学期《课程选课工作通知》办理。")));
        PublicElectiveConversationRouter router = new PublicElectiveConversationRouter(
                store, retakeService, creditLimitService, null, adjustmentService, CLOCK);
        LocalPreviewAnswerChain chain = new LocalPreviewAnswerChain(store, router, null, null, CLOCK);
        return new LocalPreviewConversationFacade(store, chain, CLOCK, TTL, null, null, telemetry);
    }

    private Map<String, Object> storedFixtureRow() {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT route, retrieval_profile, generator_profile, handoff_required,
                            failure_category, latency_bucket, estimated_cost_bucket, event_date
                     FROM anonymous_telemetry_event
                     WHERE event_date = ?
                     """)) {
            statement.setObject(1, TEST_DATE);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("route", result.getString("route"));
                row.put("retrievalProfile", result.getString("retrieval_profile"));
                row.put("generatorProfile", result.getString("generator_profile"));
                row.put("handoffRequired", result.getBoolean("handoff_required"));
                row.put("failureCategory", result.getString("failure_category"));
                row.put("latencyBucket", result.getString("latency_bucket"));
                row.put("estimatedCostBucket", result.getString("estimated_cost_bucket"));
                row.put("eventDate", result.getObject("event_date", LocalDate.class));
                return row;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("读取遥测交叉核对夹具失败", exception);
        }
    }

    private void deleteFixtureRows() {
        if (dataSource == null) {
            return;
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM anonymous_telemetry_event WHERE event_date = ?")) {
            statement.setObject(1, TEST_DATE);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("清理遥测交叉核对夹具失败", exception);
        }
    }
}
