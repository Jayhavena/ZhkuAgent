package com.zhku.knowledge.campusagent.archive;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.zhku.agent.agent.runtime.AgentRunState;
import com.zhku.agent.agent.runtime.AgentStopReason;
import com.zhku.agent.agent.runtime.contract.AgentRunSnapshot.EndReason;
import com.zhku.agent.agent.runtime.contract.AgentRuntimeError.Category;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static com.zhku.knowledge.campusagent.archive.CampusRunArchive.*;
import static org.assertj.core.api.Assertions.*;

class CampusRunArchiveContractTest {
    private final UUID id = UUID.randomUUID();
    private final Instant now = Instant.EPOCH;

    @Test
    void historicalInterruptedHasNoExecutionTerminationClaim() throws Exception {
        var archive = new CampusRunArchive(id, 1, "milestone-3a-preview", ArchiveStatus.INTERRUPTED,
                new OwnerToken(UUID.randomUUID(), 1), 1, now, null, now, now.plusSeconds(604800),
                null, null, null, "PREVIOUS_OWNER_RESULT_UNCONFIRMED");
        var json = new ObjectMapper().registerModule(new JavaTimeModule()).writeValueAsString(archive);
        assertThat(json).doesNotContain("terminated", "USER_REQUEST", "CANCELLED", "steps", "prompt");
        assertThat(archive.execution()).isNull();
    }

    @Test
    void rejectsNonterminalAndInconsistentExecutionFacts() {
        assertThatThrownBy(() -> facts(AgentRunState.RUNNING, AgentStopReason.NONE, null, 0, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> facts(AgentRunState.SUCCEEDED, AgentStopReason.NONE, EndReason.COMPLETED,
                0, Category.MODEL_FAILURE, "MODEL_FAILURE")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> facts(AgentRunState.CANCELLED, AgentStopReason.NONE, EndReason.STOPPED,
                0, null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> facts(AgentRunState.FAILED, AgentStopReason.NONE, EndReason.ERROR,
                0, null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> facts(AgentRunState.SUCCEEDED, AgentStopReason.NONE, EndReason.COMPLETED,
                -1, null, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void allowsAllExistingExecutionOutcomes() {
        assertThat(facts(AgentRunState.SUCCEEDED, AgentStopReason.NONE, EndReason.COMPLETED, 2, null, null).state())
                .isEqualTo(AgentRunState.SUCCEEDED);
        for (var end : new EndReason[] { EndReason.STEP_LIMIT, EndReason.BLOCKED, EndReason.DOMAIN_DECISION })
            assertThat(facts(AgentRunState.INCOMPLETE, AgentStopReason.NONE, end, 2, null, null).endReason())
                    .isEqualTo(end);
        for (var stop : new AgentStopReason[] { AgentStopReason.USER_REQUEST, AgentStopReason.RUN_TIMEOUT })
            assertThat(facts(AgentRunState.CANCELLED, stop, EndReason.STOPPED, 2, null, null).stopReason())
                    .isEqualTo(stop);
        assertThat(facts(AgentRunState.FAILED, AgentStopReason.NONE, EndReason.ERROR, 2,
                Category.TOOL_FAILURE, "TOOL_FAILURE").errorCategory()).isEqualTo(Category.TOOL_FAILURE);
    }

    @Test
    void rejectsBadIdentityAndRevision() {
        assertThatThrownBy(() -> new OwnerToken(id, -1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OwnerToken(null, 1)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Admission(id, new OwnerToken(id, 1), -1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Admission(null, new OwnerToken(id, 1), 0))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void budgetsAndRetentionAreBounded() {
        assertThat(Limits.defaults().maxRecords()).isEqualTo(10000);
        for (var operation : new Duration[] { Duration.ZERO, Duration.ofSeconds(-1), Duration.ofSeconds(3) })
            assertThatThrownBy(() -> new Limits(operation, Duration.ofSeconds(5), Duration.ofSeconds(5),
                    Duration.ofDays(7), 10000)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Limits(Duration.ofSeconds(1), Duration.ofSeconds(6), Duration.ofSeconds(5),
                Duration.ofDays(7), 10000)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Limits(Duration.ofSeconds(1), Duration.ofSeconds(5), Duration.ofSeconds(6),
                Duration.ofDays(7), 10000)).isInstanceOf(IllegalArgumentException.class);
        for (int count : new int[] { 0, 10001 })
            assertThatThrownBy(() -> new Limits(Duration.ofSeconds(1), Duration.ofSeconds(5), Duration.ofSeconds(5),
                    Duration.ofDays(7), count)).isInstanceOf(IllegalArgumentException.class);
        for (var retention : new Duration[] { Duration.ZERO, Duration.ofDays(31) })
            assertThatThrownBy(() -> new Limits(Duration.ofSeconds(1), Duration.ofSeconds(5), Duration.ofSeconds(5),
                    retention, 1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void invalidArchiveStateDoesNotImplyFinished() {
        assertThatThrownBy(() -> new CampusRunArchive(id, 1, "milestone-3a-preview", ArchiveStatus.FINALIZED,
                new OwnerToken(id, 1), 1, now, null, null, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CampusRunArchive(id, 1, "milestone-3a-preview", ArchiveStatus.ADMITTED,
                new OwnerToken(id, 1), 0, now, now, null, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fixedErrorsHaveNoExternalCause() {
        for (var code : Code.values()) {
            var error = new Failure(code);
            assertThat(error.code()).isEqualTo(code);
            assertThat(error.getCause()).isNull();
            assertThat(error.safeMessage()).isNotBlank().doesNotContain("jdbc", "password");
        }
    }

    private FinalFacts facts(AgentRunState state, AgentStopReason stop, EndReason end, int step,
            Category category, String code) {
        return new FinalFacts(id, state, stop, end, step, category, code, null, "NONE", CleanupOutcome.SUCCESS, now);
    }
}
