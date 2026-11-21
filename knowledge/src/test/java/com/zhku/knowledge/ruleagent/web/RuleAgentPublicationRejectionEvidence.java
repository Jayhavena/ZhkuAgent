package com.zhku.knowledge.ruleagent.web;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zhku.agent.agent.runtime.AgentRunState;
import com.zhku.knowledge.ruleagent.RuleAgentRunner;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import static com.zhku.knowledge.ruleagent.RuleAgentContract.*;

/**
 * 仅测试classpath：导出内存事件里的发布精确拒绝码，不改变公开结果契约。
 * 只导出动作、尝试次数与原因码，不携带请求正文、模型回答或凭据。
 */
public final class RuleAgentPublicationRejectionEvidence {
    private RuleAgentPublicationRejectionEvidence() {}

    @FunctionalInterface
    public interface Journal { void append(ObjectNode node) throws IOException; }

    /** 磁盘失败在宿主内收敛，不能由 TaskStore 泛化成 TASK_EXECUTION_UNAVAILABLE。 */
    public static RuleAgentRunner.Outcome persist(RuleAgentRunner.Outcome outcome, Journal journal) {
        try {
            for (var node : nodes(outcome)) journal.append(node);
            return outcome;
        } catch (IOException failure) {
            var before = outcome.snapshot();
            var result = before.result();
            var issue = new Issue("publication-audit", IssueKind.PROCESSING_FAILURE, ReasonCode.AUDIT_UNAVAILABLE,
                    "发布核验诊断未能持久化，本轮结果不发布。", "请先恢复审计写入，不自动重试。");
            var failed = new ResultEnvelope(result.publishedVersion(), result.originatingRunId(),
                    result.catalogueVersion(), result.referenceRevision(), Coverage.NONE,
                    result.items().stream().map(item -> new Subquestion(item.id(), Coverage.NONE,
                            List.of(), List.of(issue), List.of())).toList(), List.of(), List.of());
            // 该路径只处理 Gate 已拒绝的运行，runtime 本来就是 FAILED；不把正常结果伪造为新运行。
            var old = before.execution();
            var execution = new Execution(old.mode(), old.modelAttemptCount(), old.toolStepCount(),
                    old.providerResponseReceived(), AnswerSource.NONE, old.replayCoverage());
            var snapshot = new TaskSnapshot(before.schemaVersion(), before.taskId(), before.turnId(), before.runId(),
                    before.version(), TaskStatus.FAILED, AgentRunState.FAILED, true, failed, null, execution);
            return new RuleAgentRunner.Outcome(snapshot, outcome.events());
        }
    }

    public static List<ObjectNode> nodes(RuleAgentRunner.Outcome outcome) {
        var result = new ArrayList<ObjectNode>();
        for (var event : outcome.events()) {
            if (event.publicationReason() == null) continue;
            result.add(RuleAgentThreeQuestionPackage.JSON.createObjectNode()
                    .put("attempt", event.attempt())
                    .put("action", event.action())
                    .put("reason", event.reason() == null ? "NONE" : event.reason().name())
                    .put("publicationReason", event.publicationReason().name()));
        }
        return result;
    }
}
