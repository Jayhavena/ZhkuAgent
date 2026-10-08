package com.zhku.knowledge.governance;

import com.zhku.knowledge.ZhkuDatabaseTestConfig;
import com.zhku.knowledge.ZhkuFlywayTestSupport;
import com.zhku.knowledge.conversation.ConversationStore;
import com.zhku.knowledge.conversation.InMemoryConversationStore;
import com.zhku.knowledge.conversation.elective.ElectiveRetakeConversationService;
import com.zhku.knowledge.conversation.elective.ElectiveRetakeReplyType;
import com.zhku.knowledge.conversation.publicelective.PublicElectiveConversationRouter;
import com.zhku.knowledge.conversation.publicelective.PublicElectiveCreditLimitService;
import com.zhku.knowledge.rag.AnswerEvidenceGuard;
import com.zhku.knowledge.rag.CitationAuditRecord;
import com.zhku.knowledge.rag.CitationIdFactory;
import com.zhku.knowledge.rag.ModelCallTimeoutException;
import com.zhku.knowledge.rag.ModelResponse;
import com.zhku.knowledge.rag.RetrievalAugmentedAnswerService;
import com.zhku.knowledge.retrieval.ChunkCandidateRetriever;
import com.zhku.knowledge.retrieval.EmbeddingClient;
import com.zhku.knowledge.retrieval.HybridCandidateFuser;
import com.zhku.knowledge.retrieval.HybridRetrievalService;
import com.zhku.knowledge.retrieval.JdbcVectorCandidateRetriever;
import com.zhku.knowledge.retrieval.RetrievalEligibilityFilter;
import com.zhku.knowledge.retrieval.RetrievedChunk;
import com.zhku.knowledge.retrieval.RetrieverUnavailableException;
import com.zhku.knowledge.routing.AnswerRoute;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 阶段 10 的端到端降级核对：从回答门面观察学生回复，再从 V17 核对同一轮的匿名遥测。
 *
 * <p>这些用例只读调用检索/生成组件。若链路把模型异常泄漏成回答、漏记遥测、写入自由文本，
 * 或把模型降级误标成检索未命中，用户可见回复或数据库断言会失败。</p>
 */
class GracefulDegradationTest {

    private static final String DATABASE_URL = com.zhku.knowledge.ZhkuDatabaseTestConfig.databaseUrl();
    private static final String DATABASE_USER = System.getProperty("zhku.db.user", "zhkuagent");
    private static final String DATABASE_PASSWORD = ZhkuDatabaseTestConfig.databasePassword();
    private static final LocalDate TEST_DATE = LocalDate.of(1993, 3, 3);
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("1993-03-03T12:00:00Z"), ZoneOffset.UTC);
    private static final Duration TTL = Duration.ofMinutes(10);
    private static final String MODEL_MARKER = "模型拼凑出来的内容-不应下发";
    private static final Set<String> FAILURE_CATEGORIES =
            Set.of("NONE", "RETRIEVAL_MISS", "MODEL_DEGRADED");
    private static final Set<String> DIMENSION_COLUMNS = Set.of(
            "route", "retrieval_profile", "generator_profile", "handoff_required",
            "failure_category", "latency_bucket", "estimated_cost_bucket", "event_date");

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
    void shouldSafelyRefuseAndRecordEnumWhenBothRetrievalPathsAreUnavailable() {
        ChunkCandidateRetriever unavailable = (query, limit) -> {
            throw new RetrieverUnavailableException("受控检索故障文本");
        };
        RetrievalAugmentedAnswerService retrieval = retrievalService(
                unavailable, unavailable, request -> {
                    throw new AssertionError("无候选时不得调用模型");
                });

        assertDegradedAnswer(retrieval, "RETRIEVAL_MISS");
    }

    @Test
    void shouldSafelyRefuseAndRecordEnumWhenGenerationSideIsNotAssembled() {
        // 本机未装配 RAG/生成侧时，回答链仍须安全落到第五级拒答与人工渠道。
        assertDegradedAnswer(null, "RETRIEVAL_MISS");
    }

    @Test
    void shouldSafelyRefuseAndClassifyModelTimeoutAsModelDegraded() {
        int[] modelCalls = {0};
        ChunkCandidateRetriever available = (query, limit) -> List.of(candidate());
        RetrievalAugmentedAnswerService retrieval = retrievalService(
                available, (query, limit) -> List.of(), request -> {
                    modelCalls[0]++;
                    throw new ModelCallTimeoutException(MODEL_MARKER);
                });

        assertDegradedAnswer(retrieval, "MODEL_DEGRADED");
        assertThat(modelCalls[0]).isEqualTo(1);
    }

    @Test
    void shouldSafelyRefuseAndRecordEnumWhenIndexProfileDoesNotMatch() {
        ChunkCandidateRetriever noLexicalCandidates = (query, limit) -> List.of();
        ChunkCandidateRetriever mismatchedProfile = new JdbcVectorCandidateRetriever(
                dataSource,
                new RetrievalEligibilityFilter(CLOCK),
                "profile-that-is-not-in-the-index",
                null,
                new EmbeddingClient() {
                    @Override
                    public float[] embed(String text) {
                        return new float[]{0.25f};
                    }

                    @Override
                    public int dimensions() {
                        return 1;
                    }
                });
        RetrievalAugmentedAnswerService retrieval = retrievalService(
                noLexicalCandidates, mismatchedProfile, request -> {
                    throw new AssertionError("无合格候选时不得调用模型");
                });

        assertDegradedAnswer(retrieval, "RETRIEVAL_MISS");
    }

    private void assertDegradedAnswer(
            RetrievalAugmentedAnswerService retrieval, String expectedFailureCategory) {
        LocalPreviewConversationFacade facade = facade(retrieval);

        LocalPreviewConversationFacade.ConversationReply reply = facade.sendMessage(
                facade.createConversation().sessionId(), "公共选修课的选课调整怎么办理？");

        assertThat(reply.type()).isEqualTo(ElectiveRetakeReplyType.OUT_OF_SCOPE);
        assertThat(reply.route()).isEqualTo(AnswerRoute.INSUFFICIENT_EVIDENCE);
        assertThat(reply.handoffRequired()).isTrue();
        assertThat(reply.answer()).isNull();
        assertThat(reply.message()).isNotBlank().matches(".*\\p{IsHan}.*");
        assertThat(reply.message()).doesNotContain(MODEL_MARKER, "controlled-retrieval-failure-text");
        assertThat(reply.citationIds()).isEmpty();

        assertThat(telemetry.countByEventDate(TEST_DATE)).isEqualTo(1);
        Map<String, Object> row = storedDimensionRow();
        assertThat(row.keySet()).containsExactlyInAnyOrderElementsOf(DIMENSION_COLUMNS);
        assertThat(row.get("failure_category")).isIn(FAILURE_CATEGORIES);
        assertThat(row.get("failure_category")).isEqualTo(expectedFailureCategory);
        assertThat(row.get("event_date")).isEqualTo(TEST_DATE);
        assertThat(row.get("route")).isEqualTo("INSUFFICIENT_EVIDENCE");
        assertThat(row.get("handoff_required")).isEqualTo(true);
        assertTelemetrySchemaHasNoSensitiveColumns();
    }

    private LocalPreviewConversationFacade facade(RetrievalAugmentedAnswerService retrieval) {
        ConversationStore store = new InMemoryConversationStore();
        ElectiveRetakeConversationService retakeService = new ElectiveRetakeConversationService(
                store, courseType -> Optional.empty(), CLOCK, TTL);
        PublicElectiveCreditLimitService creditLimitService =
                new PublicElectiveCreditLimitService(Optional::empty);
        // 调整规则证据不足会在第三级短路；此夹具专测第四级检索与生成的四种降级。
        PublicElectiveConversationRouter router = new PublicElectiveConversationRouter(
                store, retakeService, creditLimitService, null, CLOCK);
        LocalPreviewAnswerChain answerChain = new LocalPreviewAnswerChain(
                store, router, null, retrieval, CLOCK);
        return new LocalPreviewConversationFacade(store, answerChain, CLOCK, TTL,
                null, null, telemetry);
    }

    private RetrievalAugmentedAnswerService retrievalService(
            ChunkCandidateRetriever lexical,
            ChunkCandidateRetriever vector,
            com.zhku.knowledge.rag.ModelChatClient modelClient) {
        HybridRetrievalService hybrid = new HybridRetrievalService(new HybridCandidateFuser());
        List<CitationAuditRecord> audits = new ArrayList<>();
        CitationIdFactory citationIds = () -> "graceful-degradation-citation";
        return new RetrievalAugmentedAnswerService(hybrid, lexical, vector, modelClient,
                new AnswerEvidenceGuard(), citationIds, audits::add, CLOCK, 3);
    }

    private static RetrievedChunk candidate() {
        return new RetrievedChunk(
                UUID.fromString("bfe37144-490a-4e96-b572-42b79439916a"),
                "公共选修课调整应以教务部门发布的通知为准。",
                "受控降级测试资料", "测试页 1", 1, "v1", "b".repeat(64));
    }

    private Map<String, Object> storedDimensionRow() {
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
                for (String column : DIMENSION_COLUMNS) {
                    Object value = "event_date".equals(column)
                            ? result.getObject(column, LocalDate.class)
                            : result.getObject(column);
                    row.put(column, value);
                }
                return row;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("读取降级遥测夹具失败", exception);
        }
    }

    private void assertTelemetrySchemaHasNoSensitiveColumns() {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT column_name
                     FROM information_schema.columns
                     WHERE table_schema = 'public' AND table_name = 'anonymous_telemetry_event'
                     """);
             ResultSet result = statement.executeQuery()) {
            List<String> columns = new ArrayList<>();
            while (result.next()) {
                columns.add(result.getString("column_name"));
            }
            assertThat(columns).contains("route", "failure_category", "event_date");
            assertThat(columns).doesNotContain("message", "question", "answer", "student_id",
                    "name", "contact", "api_key", "anonymous_session_hash", "dedupe_key");
        } catch (SQLException exception) {
            throw new IllegalStateException("检查遥测隐私列失败", exception);
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
            throw new IllegalStateException("清理降级遥测夹具失败", exception);
        }
    }
}
