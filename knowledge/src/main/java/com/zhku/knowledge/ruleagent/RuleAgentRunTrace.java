package com.zhku.knowledge.ruleagent;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.*;
import static com.zhku.knowledge.ruleagent.RuleAgentContract.*;

/**
 * 离线评测专用的运行轨迹投影，绝不保存原始事件对象。
 *
 * <p>设计边界：{@link RuleAgentRunner.Event} 的 observation 内嵌候选原文、证据引用、
 * factId 等敏感内容，完整序列化会把受控资料带出任务边界。这里只保留白名单枚举名、
 * 由 runId 与事件序号派生的 actionId，以及计数/步骤等非敏感元数据；题面、JSON 参数、
 * originalToken、原文、Cookie、密钥、provider 文本一律不进入轨迹。</p>
 *
 * <p>限制：没有 ToolObservation 的事件用 {@link #NOT_OBSERVED} 显式标记，不伪造成功；
 * 单次运行最多保留 {@value #MAX_ACTIONS} 条事件投影，超出时截断并置 {@code truncated}。
 * 该类型只服务评测适配层，不得直接暴露为学生 HTTP 契约。</p>
 */
public record RuleAgentRunTrace(UUID taskId, UUID turnId, UUID runId, long version,
        String taskStatus, String runState, boolean exceptionalFailure, boolean truncated,
        List<Action> actions, ResultView result) {

    public static final int MAX_ACTIONS = 24;
    /** 无真实工具观察时的显式状态，避免被误读为 SUCCEEDED。 */
    public static final String NOT_OBSERVED = "NOT_OBSERVED";

    private static final Set<String> ACTION_TYPES = Set.of(
            "SEARCH_REVIEWED_RULES", "READ_VERIFIED_RULE", "ASK_NECESSARY_CONDITION",
            "FINISH_CHECK", "PUBLICATION_REVIEW", "INVALID");
    private static final Set<String> OBSERVATION_STATUSES = Set.of(
            "SUCCEEDED", "NO_MATCH", "REJECTED", "UNAVAILABLE");
    private static final Set<String> DATA_LEVELS = Set.of(
            "NONE", "CANDIDATE", "VERIFIED_EVIDENCE", "VERIFIED_FACT");
    private static final java.util.regex.Pattern ENUM_NAME =
            java.util.regex.Pattern.compile("[A-Z][A-Z0-9_]{0,63}");
    private static final java.util.regex.Pattern ACTION_ID =
            java.util.regex.Pattern.compile("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}");

    public RuleAgentRunTrace {
        Objects.requireNonNull(taskId);
        Objects.requireNonNull(turnId);
        Objects.requireNonNull(runId);
        if (version < 1 || version > RuleAgentContract.MAX_SAFE_VERSION) {
            throw new IllegalArgumentException("轨迹版本无效");
        }
        taskStatus = enumName(taskStatus);
        runState = enumName(runState);
        actions = List.copyOf(actions);
        Objects.requireNonNull(result);
        if (actions.size() > MAX_ACTIONS) throw new IllegalArgumentException("轨迹事件超出上限");
    }

    /**
     * 从一次已接受发布快照投影轨迹；只读取快照的标识与终态，不读取结果正文。
     * 事件按发生顺序保留，超限部分丢弃并标记 truncated，避免无界内存增长。
     */
    public static RuleAgentRunTrace project(TaskSnapshot snapshot, UUID acceptedTurnId,
            List<RuleAgentRunner.Event> events, boolean exceptionalFailure) {
        Objects.requireNonNull(snapshot);
        Objects.requireNonNull(acceptedTurnId);
        Objects.requireNonNull(events);
        int kept = Math.min(events.size(), MAX_ACTIONS);
        var actions = new ArrayList<Action>(kept);
        for (int index = 0; index < kept; index++) {
            actions.add(project(events.get(index), snapshot.runId(), index + 1));
        }
        return new RuleAgentRunTrace(snapshot.taskId(), acceptedTurnId, snapshot.runId(),
                snapshot.version(), snapshot.taskStatus().name(), snapshot.runState().name(),
                exceptionalFailure, events.size() > kept, actions, projectResult(snapshot.result()));
    }

    /** 评测引擎需要的 action 视图；actionId 由 runId 与序号派生，不含 provider 标识。 */
    public List<ActionView> executionActions() {
        return actions.stream().map(a -> new ActionView(a.actionId(), a.type(), a.step(), a.modelAttempt())).toList();
    }

    /** 评测引擎需要的 observation 视图；无真实观察时状态为 NOT_OBSERVED 且无 dataLevel。 */
    public List<ObservationView> executionObservations() {
        return actions.stream().map(a -> {
            var observation = a.observation();
            return observation == null
                    ? new ObservationView(a.actionId(), NOT_OBSERVED, null, a.reasonCode())
                    : new ObservationView(a.actionId(), observation.status(), observation.dataLevel(),
                            observation.reasonCode());
        }).toList();
    }

    private static Action project(RuleAgentRunner.Event event, UUID runId, int sequence) {
        Objects.requireNonNull(event);
        var raw = event.observation();
        Observation observation = null;
        if (raw != null) {
            var reason = raw.reasonCode() != null ? raw.reasonCode().name()
                    : event.reason() != null ? event.reason().name() : null;
            observation = new Observation(raw.status().name(), raw.dataLevel().name(), reason);
        }
        return new Action(actionId(runId, sequence),
                ACTION_TYPES.contains(event.action()) ? event.action() : "INVALID",
                sequence, event.attempt(), observation,
                event.reason() == null ? null : event.reason().name(),
                event.publicationReason() == null ? null : event.publicationReason().name());
    }

    private static String actionId(UUID runId, int sequence) {
        return UUID.nameUUIDFromBytes(("rule-agent-run:" + runId + ":action:" + sequence)
                .getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static String enumName(String value) {
        if (value == null || !ENUM_NAME.matcher(value).matches()) {
            throw new IllegalArgumentException("轨迹枚举名无效");
        }
        return value;
    }

    private static String optionalEnumName(String value) {
        return value == null ? null : enumName(value);
    }

    /** step is event ordinal (1..N); modelAttempt tracks the LLM decision attempt, not a tool step. */
    public record Action(String actionId, String type, int step, int modelAttempt, Observation observation,
            String reasonCode, String publicationReason) {
        public Action {
            if (actionId == null || !ACTION_ID.matcher(actionId).matches()) {
                throw new IllegalArgumentException("轨迹 actionId 无效");
            }
            if (!ACTION_TYPES.contains(type)) throw new IllegalArgumentException("轨迹动作类型未在白名单");
            if (modelAttempt < 1 || modelAttempt > 10000) throw new IllegalArgumentException("Invalid model attempt");
            if (step < 1 || step > 10000) throw new IllegalArgumentException("轨迹步骤无效");
            reasonCode = optionalEnumName(reasonCode);
            publicationReason = optionalEnumName(publicationReason);
        }
    }

    public record Observation(String status, String dataLevel, String reasonCode) {
        public Observation {
            if (!OBSERVATION_STATUSES.contains(status)) {
                throw new IllegalArgumentException("轨迹观察状态未在白名单");
            }
            if (dataLevel != null && !DATA_LEVELS.contains(dataLevel)) {
                throw new IllegalArgumentException("轨迹数据级别未在白名单");
            }
            reasonCode = optionalEnumName(reasonCode);
        }
    }

    /** Allowlisted T08 result summary: only identifiers, enums and bounded lists. */
    private static ResultView projectResult(ResultEnvelope source) {
        Objects.requireNonNull(source, "Published run must have a result");
        var evidence = source.evidence().stream()
                .map(e -> new EvidenceView(e.id(), e.sourceId().toString(),
                        e.chunkId().toString(), safeRevision(e.revision()))).toList();
        var relations = source.evidenceRelations().stream()
                .map(r -> new RelationView(r.targetId(), r.evidenceId(), r.role().name())).toList();
        var items = source.items().stream()
                .map(q -> new ItemView(q.claims().stream()
                        .map(c -> new ClaimView(c.id(), opaqueFactId(c.factId()))).toList())).toList();
        var issues = source.items().stream().flatMap(q -> q.issues().stream())
                .map(i -> new IssueView(i.kind().name(), i.reasonCode().name())).toList();
        return new ResultView(source.coverage().name(), evidence, relations, items, issues);
    }

    private static String opaqueFactId(String original) {
        try {
            var bytes = MessageDigest.getInstance("SHA-256")
                    .digest(("rule-agent-fact-id:" + original).getBytes(StandardCharsets.UTF_8));
            return "sha256:" + HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 unavailable", unavailable);
        }
    }

    private static String safeRevision(String revision) {
        return revision != null && revision.matches("[A-Za-z0-9_.-]{1,64}") ? revision : "UNKNOWN";
    }

    public record ResultView(String coverage, List<EvidenceView> evidence,
            List<RelationView> evidenceRelations, List<ItemView> items, List<IssueView> issues) {
        public ResultView {
            coverage = enumName(coverage);
            evidence = List.copyOf(evidence);
            evidenceRelations = List.copyOf(evidenceRelations);
            items = List.copyOf(items);
            issues = List.copyOf(issues);
            if (evidence.size() > 8 || evidenceRelations.size() > 64 || items.size() > 4
                    || issues.size() > 32) throw new IllegalArgumentException("Unbounded result projection");
        }
    }
    public record EvidenceView(String id, String sourceId, String chunkId, String revision) {}
    public record RelationView(String targetId, String evidenceId, String role) {}
    public record ItemView(List<ClaimView> claims) {
        public ItemView { claims = List.copyOf(claims); }
    }
    public record ClaimView(String id, String factId) {}
    public record IssueView(String kind, String reasonCode) {}
    public record ActionView(String actionId, String type, int step, int modelAttempt) {}

    public record ObservationView(String actionId, String status, String dataLevel,
            String reasonCode) {}
}
