package com.zhku.knowledge.governance.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhku.knowledge.ZhkuDatabaseTestConfig;
import com.zhku.knowledge.ZhkuFlywayTestSupport;
import com.zhku.knowledge.governance.AnonymousTelemetryAggregateQueryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 遥测读取 API 的 HTTP 契约：只聚合窗口内桶，单条事件地址始终不存在。 */
class TelemetryAdminControllerTest {

    private static final String DATABASE_URL = com.zhku.knowledge.ZhkuDatabaseTestConfig.databaseUrl();
    private static final String DATABASE_USER = System.getProperty("zhku.db.user", "zhkuagent");
    private static final String DATABASE_PASSWORD = ZhkuDatabaseTestConfig.databasePassword();
    private static final LocalDate WINDOW_START = LocalDate.of(2099, 1, 1);
    private static final LocalDate WINDOW_END = LocalDate.of(2099, 1, 30);
    private static final LocalDate OUTSIDE_WINDOW = LocalDate.of(2098, 12, 31);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private DataSource dataSource;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ZhkuFlywayTestSupport.migrate(DATABASE_URL, DATABASE_USER, DATABASE_PASSWORD);
        dataSource = new DriverManagerDataSource(DATABASE_URL, DATABASE_USER, DATABASE_PASSWORD);
        deleteTestRows();
        Clock fixedClock = Clock.fixed(Instant.parse("2099-01-30T12:00:00Z"), ZoneOffset.UTC);
        mockMvc = MockMvcBuilders.standaloneSetup(new TelemetryAdminController(
                new AnonymousTelemetryAggregateQueryService(dataSource), fixedClock
        )).build();
    }

    @AfterEach
    void tearDown() {
        deleteTestRows();
    }

    @Test
    void shouldReturnEmptyAggregationForAnEmptyWindow() throws Exception {
        mockMvc.perform(aggregateRequest("route", WINDOW_START, WINDOW_END))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void shouldAggregateOnlyBucketsInTheRequestedWindowWithFourDecimalRatios() throws Exception {
        insertEvent("TEST_ROUTE_A", false, "NONE", "S_1_3", WINDOW_START);
        insertEvent("TEST_ROUTE_A", false, "NONE", "S_1_3", WINDOW_END);
        insertEvent("TEST_ROUTE_B", true, "RETRIEVAL_MISS", "GE_10S", WINDOW_END);
        insertEvent("TEST_ROUTE_OUTSIDE", false, "NONE", "S_1_3", OUTSIDE_WINDOW);

        mockMvc.perform(aggregateRequest("route", WINDOW_START, WINDOW_END))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].route").value("TEST_ROUTE_A"))
                .andExpect(jsonPath("$[0].count").value(2))
                .andExpect(jsonPath("$[0].ratio").value(0.6667))
                .andExpect(jsonPath("$[1].route").value("TEST_ROUTE_B"))
                .andExpect(jsonPath("$[1].count").value(1))
                .andExpect(jsonPath("$[1].ratio").value(0.3333));
    }

    @Test
    void shouldKeepOnlySelectedDimensionCountAndRatioInResponses() throws Exception {
        insertEvent("TEST_ROUTE_A", false, "NONE", "S_1_3", WINDOW_START);

        MvcResult result = mockMvc.perform(aggregateRequest("route", WINDOW_START, WINDOW_END))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode bucket = objectMapper.readTree(result.getResponse().getContentAsString()).get(0);
        Set<String> fields = new HashSet<>();
        bucket.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrder("route", "count", "ratio");
        assertThat(fields).doesNotContain(
                "message", "question", "answer", "studentId", "name", "contact", "apiKey",
                "anonymousSessionHash", "sessionHash", "deduplicationKey", "id"
        );
    }

    @Test
    void shouldUseThirtyDayWindowWhenDatesAreOmitted() throws Exception {
        insertEvent("TEST_ROUTE_IN_DEFAULT", false, "NONE", "S_1_3", WINDOW_START);
        insertEvent("TEST_ROUTE_BEFORE_DEFAULT", false, "NONE", "S_1_3", OUTSIDE_WINDOW);

        mockMvc.perform(get("/zhku/local-admin/telemetry/aggregates").param("dimension", "route"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].route").value("TEST_ROUTE_IN_DEFAULT"))
                .andExpect(jsonPath("$[0].count").value(1))
                .andExpect(jsonPath("$[1]").doesNotExist());
    }

    @Test
    void shouldNotExposeAnIndividualTelemetryEventRoute() throws Exception {
        mockMvc.perform(get("/zhku/local-admin/telemetry/events/not-an-event-id"))
                .andExpect(status().isNotFound());
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder aggregateRequest(
            String dimension, LocalDate from, LocalDate to
    ) {
        return get("/zhku/local-admin/telemetry/aggregates")
                .param("dimension", dimension)
                .param("from", from.toString())
                .param("to", to.toString());
    }

    private void insertEvent(
            String route,
            boolean handoffRequired,
            String failureCategory,
            String latencyBucket,
            LocalDate eventDate
    ) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO anonymous_telemetry_event
                         (id, route, handoff_required, failure_category, latency_bucket, event_date)
                     VALUES (?, ?, ?, ?, ?, ?)
                     """)) {
            statement.setObject(1, UUID.randomUUID());
            statement.setString(2, route);
            statement.setBoolean(3, handoffRequired);
            statement.setString(4, failureCategory);
            statement.setString(5, latencyBucket);
            statement.setObject(6, eventDate);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("写入遥测聚合夹具失败", exception);
        }
    }

    private void deleteTestRows() {
        if (dataSource == null) {
            return;
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     DELETE FROM anonymous_telemetry_event
                     WHERE event_date BETWEEN ? AND ?
                     """)) {
            statement.setObject(1, OUTSIDE_WINDOW);
            statement.setObject(2, WINDOW_END);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("清理遥测聚合夹具失败", exception);
        }
    }
}
