package com.zhku.knowledge.observability;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhku.agent.agent.runtime.*;
import com.zhku.agent.agent.runtime.contract.*;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.LoggerFactory;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static com.zhku.knowledge.observability.ObservationEventV1.*;

class ObservationProjectionTest {
    static final UUID RUN = UUID.fromString("00000000-0000-4000-8000-000000000101");
    static final String RAW = "SYNTHETIC_PROMPT_REPLY_ANSWER_TOOL_HEADER_SECRET_HASH";
    final ObjectMapper json = new ObjectMapper();

    static AgentRunEvent step(UUID run, long sequence) {
        return new AgentRunEvent(run, sequence, Instant.EPOCH, AgentRunEvent.Type.STEP_STARTED, 1, null, null, null);
    }
    static AgentRunEvent terminal(UUID run, long sequence, AgentRunState state) {
        var reason = state == AgentRunState.CANCELLED ? AgentStopReason.USER_REQUEST : AgentStopReason.NONE;
        var end = switch (state) {
            case SUCCEEDED -> AgentRunSnapshot.EndReason.COMPLETED;
            case CANCELLED -> AgentRunSnapshot.EndReason.STOPPED;
            case INCOMPLETE -> AgentRunSnapshot.EndReason.STEP_LIMIT;
            case FAILED -> AgentRunSnapshot.EndReason.ERROR;
            default -> throw new IllegalArgumentException();
        };
        var error = state == AgentRunState.FAILED ? AgentRuntimeError.of(AgentRuntimeError.Category.MODEL_FAILURE, RAW) : null;
        return new AgentRunEvent(run, sequence, Instant.EPOCH, AgentRunEvent.Type.RUN_TERMINATED, null, null,
                new AgentRunSnapshot(run, state, reason, 1, true, end, error), null);
    }
    static AgentRunEvent source(AgentRunEvent.Type type) {
        var error = AgentRuntimeError.of(AgentRuntimeError.Category.TOOL_FAILURE, RAW);
        var call = new AgentToolCall(RUN, RAW, RAW, 1, RAW, "{\"Authorization\":\"" + RAW + "\"}", null);
        return switch (type) {
            case STEP_STARTED -> step(RUN, 1);
            case TOOL_CALL_PROPOSED -> new AgentRunEvent(RUN, 1, Instant.EPOCH, type, 1, call, null, null);
            case TOOL_CALL_RESULT -> new AgentRunEvent(RUN, 1, Instant.EPOCH, type, 1,
                    call.withResult(new AgentToolCall.Result(AgentToolCall.Status.SUCCEEDED, RAW, AgentToolCall.PayloadFormat.TEXT, null)), null, null);
            case ERROR_RECORDED -> new AgentRunEvent(RUN, 1, Instant.EPOCH, type, null, null, null, error);
            case RUN_TERMINATED -> terminal(RUN, 1, AgentRunState.SUCCEEDED);
            case RUN_STARTED, STOP_REQUESTED -> new AgentRunEvent(RUN, 1, Instant.EPOCH, type, null, null,
                    new AgentRunSnapshot(RUN, type == AgentRunEvent.Type.RUN_STARTED ? AgentRunState.RUNNING : AgentRunState.STOP_REQUESTED,
                            type == AgentRunEvent.Type.RUN_STARTED ? AgentStopReason.NONE : AgentStopReason.USER_REQUEST,
                            0, false, null, null), null);
        };
    }

    @ParameterizedTest @EnumSource(AgentRunEvent.Type.class)
    void mapsSevenKindsWithoutContentOrCorrelation(AgentRunEvent.Type type) throws Exception {
        var projected = ObservationProjector.anonymous(source(type));
        String expectedKind = switch (type) {
            case TOOL_CALL_PROPOSED -> "TOOL_PROPOSED";
            case TOOL_CALL_RESULT -> "TOOL_RESULT";
            case ERROR_RECORDED -> "ERROR";
            default -> type.name();
        };
        assertThat(projected.kind().name()).isEqualTo(expectedKind);
        assertThat(projected.outcome()).isEqualTo(type == AgentRunEvent.Type.TOOL_CALL_RESULT || type == AgentRunEvent.Type.RUN_TERMINATED ? Outcome.SUCCEEDED : Outcome.OBSERVED);
        assertThat(projected.toolClass()).isEqualTo(type == AgentRunEvent.Type.TOOL_CALL_PROPOSED || type == AgentRunEvent.Type.TOOL_CALL_RESULT ? ToolClass.OTHER : ToolClass.NONE);
        assertThat(projected.inputTokens()).isNull(); assertThat(projected.outputTokens()).isNull();
        assertThat(projected.elapsedMillis()).isNull(); assertThat(projected.estimatedCostMicros()).isNull();
        var tree = json.readTree(json.writeValueAsString(projected));
        var names = new HashSet<String>(); tree.fieldNames().forEachRemaining(names::add);
        assertThat(names).containsExactlyInAnyOrder("schemaVersion", "kind", "outcome", "toolClass", "failureClass", "inputTokens", "outputTokens", "elapsedMillis", "estimatedCostMicros");
        var logger = (Logger) LoggerFactory.getLogger("synthetic-projection");
        var capture = new ListAppender<ILoggingEvent>(); capture.start(); logger.addAppender(capture);
        try {
            logger.info("{}", projected);
            assertThat(json.writeValueAsString(projected) + capture.list.getFirst().getFormattedMessage())
                    .doesNotContain(RAW, RUN.toString(), "1970-01-01", "arguments", "payload", "sequence");
            assertThat(capture.list.getFirst().getThrowableProxy()).isNull();
        } finally { logger.detachAppender(capture); capture.stop(); }
        var diagnostic = ObservationProjector.diagnostic(source(type));
        assertThat(diagnostic.runId()).isEqualTo(RUN); assertThat(diagnostic.sequence()).isEqualTo(1);
        assertThat(diagnostic.terminated()).isEqualTo(type == AgentRunEvent.Type.RUN_TERMINATED);
        assertThat(diagnostic.metadata()).isEqualTo(projected);
        assertThat(Arrays.stream(RunDiagnosticEventV1.class.getRecordComponents()).map(c -> c.getName()))
                .containsExactly("schemaVersion", "runId", "sequence", "terminated", "metadata");
    }

    @ParameterizedTest @EnumSource(AgentToolCall.Status.class)
    void resultOutcomeUsesActualStatus(AgentToolCall.Status status) {
        var error = status == AgentToolCall.Status.FAILED || status == AgentToolCall.Status.REJECTED
                ? AgentRuntimeError.of(AgentRuntimeError.Category.TOOL_FAILURE, RAW) : null;
        var call = new AgentToolCall(RUN, RAW, RAW, 1, RAW, RAW,
                new AgentToolCall.Result(status, RAW, AgentToolCall.PayloadFormat.TEXT, error));
        var result = ObservationProjector.anonymous(new AgentRunEvent(RUN, 1, Instant.EPOCH,
                AgentRunEvent.Type.TOOL_CALL_RESULT, 1, call, null, null));
        assertThat(result.outcome().name()).isEqualTo(status.name());
        assertThat(result.failureClass()).isEqualTo(error == null ? FailureClass.NONE : FailureClass.TOOL);
    }

    @ParameterizedTest @EnumSource(value = AgentRunState.class, names = {"SUCCEEDED", "INCOMPLETE", "CANCELLED", "FAILED"})
    void terminalOutcomeUsesSnapshot(AgentRunState state) {
        assertThat(ObservationProjector.anonymous(terminal(RUN, 1, state)).outcome().name()).isEqualTo(state.name());
    }

    @ParameterizedTest @EnumSource(AgentRuntimeError.Category.class)
    void failureCategoryNeverCopiesLegalLookingCode(AgentRuntimeError.Category category) {
        var event = new AgentRunEvent(RUN, 1, Instant.EPOCH, AgentRunEvent.Type.ERROR_RECORDED, null, null, null,
                AgentRuntimeError.of(category, RAW));
        assertThat(ObservationProjector.anonymous(event).failureClass().name()).isEqualTo(switch (category) {
            case MODEL_FAILURE -> "MODEL"; case TOOL_FAILURE -> "TOOL"; case EXECUTOR_REJECTED -> "EXECUTOR";
            case SEND_FAILURE -> "SEND"; case CLEANUP_FAILURE -> "CLEANUP"; case EXECUTION_FAILURE -> "EXECUTION";
            case INTERNAL_FAILURE -> "INTERNAL"; case DOMAIN_FAILURE -> "DOMAIN";
        });
    }

    @Test void validatesVersionAndMetricsWhilePreservingUnknownAndMeasuredZero() throws Exception {
        var unknown = new ObservationEventV1(1, Kind.ERROR, Outcome.UNKNOWN, ToolClass.OTHER, FailureClass.OTHER, null, null, null, null);
        var zero = new ObservationEventV1(1, Kind.ERROR, Outcome.UNKNOWN, ToolClass.OTHER, FailureClass.OTHER, 0L, 0L, 0L, 0L);
        assertThat(json.readTree(json.writeValueAsString(unknown)).get("inputTokens").isNull()).isTrue();
        assertThat(json.readTree(json.writeValueAsString(zero)).get("inputTokens").longValue()).isZero();
        for (int metric = 0; metric < 4; metric++) {
            Long[] values = {null, null, null, null}; values[metric] = -1L;
            assertThatThrownBy(() -> new ObservationEventV1(1, Kind.ERROR, Outcome.UNKNOWN, ToolClass.NONE, FailureClass.NONE,
                    values[0], values[1], values[2], values[3])).isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> new ObservationEventV1(2, Kind.ERROR, Outcome.UNKNOWN, ToolClass.NONE, FailureClass.NONE, null, null, null, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void legalLookingToolNamesAndNestedResultsNeverBecomeLabels() throws Exception {
        String hash = "0123456789abcdef".repeat(4);
        for (String name : new String[]{"user_00000000_0000_4000_8000_000000000101", null}) {
            var call = new AgentToolCall(RUN, RUN.toString(), hash, 1, name,
                    "{\"Cookie\":\"" + RAW + "\"}", new AgentToolCall.Result(AgentToolCall.Status.SUCCEEDED,
                    "{\"nested\":{\"answer\":\"" + RAW + "\",\"hash\":\"" + hash + "\"}}", AgentToolCall.PayloadFormat.JSON, null));
            var projected = ObservationProjector.anonymous(new AgentRunEvent(RUN, 1, Instant.EPOCH,
                    AgentRunEvent.Type.TOOL_CALL_RESULT, 1, call, null, null));
            assertThat(projected.toolClass()).isEqualTo(ToolClass.OTHER);
            assertThat(json.writeValueAsString(projected) + projected).doesNotContain(RAW, hash, RUN.toString(), "user_00000000");
        }
        assertThat(ObservationProjector.diagnostic(step(RUN, 1)).toString()).doesNotContain(RUN.toString(), "sequence=1");
    }

    @Test void fixtureIsNewDevelopmentSyntheticData() throws Exception {
        try (var input = getClass().getResourceAsStream("/observability/metadata-v1-synthetic.json")) {
            var fixture = json.readTree(input);
            assertThat(fixture.get("classification").asText()).isEqualTo("DEVELOPMENT_SYNTHETIC");
            assertThat(fixture.get("marker").asText()).isEqualTo(RAW);
        }
    }
}
