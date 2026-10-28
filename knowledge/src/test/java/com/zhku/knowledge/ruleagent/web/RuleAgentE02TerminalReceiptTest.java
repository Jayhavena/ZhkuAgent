package com.zhku.knowledge.ruleagent.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhku.agent.agent.runtime.AgentRunState;
import com.zhku.knowledge.ruleagent.RuleAgentRunner;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static com.zhku.knowledge.ruleagent.RuleAgentContract.*;
import static org.assertj.core.api.Assertions.*;

/** 合成终态只验证封批和回执，不启动 Provider、网络或真实任务。 */
class RuleAgentE02TerminalReceiptTest {
    @TempDir Path batch;

    @Test void failedAndStoppedKeepOriginalReasonButNeverCountAsPassed() throws Exception {
        var oldBatch = RuleAgentThreeQuestionHostTest.batch;
        var oldScope = RuleAgentThreeQuestionHostTest.e02Scope;
        var oldSingle = RuleAgentThreeQuestionHostTest.single;
        var oldStopped = RuleAgentThreeQuestionHostTest.stopped;
        int oldCalls = RuleAgentThreeQuestionHostTest.calls.get();
        int oldKeyReads = RuleAgentThreeQuestionHostTest.keyReads.get();
        var oldOutcomes = List.copyOf(RuleAgentThreeQuestionHostTest.outcomes);
        try {
            RuleAgentThreeQuestionHostTest.batch = batch;
            RuleAgentThreeQuestionHostTest.single = true;
            RuleAgentThreeQuestionHostTest.calls.set(0);
            RuleAgentThreeQuestionHostTest.keyReads.set(0);
            RuleAgentThreeQuestionHostTest.outcomes.clear();
            var mapper = new ObjectMapper();
            for (var terminal : List.of(TaskStatus.FAILED, TaskStatus.STOPPED)) {
                var plan = mapper.createObjectNode().put("schemaVersion", 1).put("maximumTasks", 1).put("maximumRuns", 1);
                plan.putArray("cases").addObject().put("id", "E02").put("question", "合成问题？")
                        .put("maximumRuns", 1).put("maximumCalls", 5);
                var scope = new RuleAgentE02Scope(plan);
                RuleAgentThreeQuestionHostTest.e02Scope = scope;
                RuleAgentThreeQuestionHostTest.stopped = false;
                var owner = new Owner(UUID.randomUUID(), UUID.randomUUID());
                var task = UUID.randomUUID();
                var run = UUID.randomUUID();
                var input = new RuleAgentRunner.Input(owner, task, 2, "合成问题？",
                        List.of(new RuleAgentRunner.Question("q1", "合成问题？")), Map.of());
                scope.begin(input, input.question(), run);
                var reason = terminal == TaskStatus.FAILED ? ReasonCode.MODEL_RATE_LIMIT : ReasonCode.NO_PROGRESS;
                var issue = new Issue("synthetic", IssueKind.PROCESSING_FAILURE, reason, "合成失败", "人工核查");
                var item = new Subquestion("q1", Coverage.NONE, List.of(), List.of(issue), List.of());
                var result = new ResultEnvelope(2, run, "synthetic", "2025.9.2", Coverage.NONE,
                        List.of(item), List.of(), List.of());
                var execution = new Execution(Mode.ONLINE, 0, 0, false, AnswerSource.NONE, ReplayCoverage.NOT_APPLICABLE);
                var state = terminal == TaskStatus.FAILED ? AgentRunState.FAILED : AgentRunState.INCOMPLETE;
                var snapshot = new TaskSnapshot(SCHEMA_VERSION, task, UUID.randomUUID(), run, 2, terminal,
                        state, true, result, null, execution);
                var outcome = new RuleAgentRunner.Outcome(snapshot, List.of());

                assertThat(RuleAgentThreeQuestionHostTest.failClosedTerminal(outcome)).isTrue();
                assertThat(scope.closed()).isTrue();
                assertThat(RuleAgentThreeQuestionHostTest.stopped).isTrue();
                assertThatThrownBy(() -> scope.begin(input, input.question(), UUID.randomUUID()))
                        .isInstanceOf(IllegalStateException.class);
                assertThatThrownBy(() -> RuleAgentThreeQuestionHostTest.recordE02Outcome(outcome.snapshot()))
                        .isInstanceOf(AssertionError.class);
                assertThat(scope.tasks()).isEqualTo(1);
                assertThat(scope.runs()).isEqualTo(1);
            }
            var lines = Files.readAllLines(batch.resolve("task-results.jsonl"));
            assertThat(lines).hasSize(2);
            for (int i = 0; i < 2; i++) {
                var receipt = mapper.readTree(lines.get(i));
                var snapshot = receipt.path("snapshot");
                assertThat(receipt.path("caseId").asText()).isEqualTo("E02");
                assertThat(snapshot.path("taskStatus").asText()).isEqualTo(i == 0 ? "FAILED" : "STOPPED");
                assertThat(snapshot.path("runState").asText()).isEqualTo(i == 0 ? "FAILED" : "INCOMPLETE");
                assertThat(snapshot.path("result").path("items").get(0).path("issues").get(0).path("reasonCode").asText())
                        .isEqualTo(i == 0 ? "MODEL_RATE_LIMIT" : "NO_PROGRESS");
                // UUID 等对象节点须先按真实回执序列化，再比较落盘后的 JSON 语义。
                assertThat(mapper.readTree(mapper.writeValueAsBytes(RuleAgentThreeQuestionHostTest.outcomes.get(i))))
                        .isEqualTo(receipt);
            }
            assertThat(RuleAgentThreeQuestionHostTest.calls).hasValue(0);
            assertThat(RuleAgentThreeQuestionHostTest.keyReads).hasValue(0);
        } finally {
            RuleAgentThreeQuestionHostTest.batch = oldBatch;
            RuleAgentThreeQuestionHostTest.e02Scope = oldScope;
            RuleAgentThreeQuestionHostTest.single = oldSingle;
            RuleAgentThreeQuestionHostTest.stopped = oldStopped;
            RuleAgentThreeQuestionHostTest.calls.set(oldCalls);
            RuleAgentThreeQuestionHostTest.keyReads.set(oldKeyReads);
            RuleAgentThreeQuestionHostTest.outcomes.clear();
            RuleAgentThreeQuestionHostTest.outcomes.addAll(oldOutcomes);
        }
    }
}
