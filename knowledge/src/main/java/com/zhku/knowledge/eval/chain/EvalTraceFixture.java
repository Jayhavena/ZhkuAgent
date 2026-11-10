package com.zhku.knowledge.eval.chain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zhku.knowledge.ruleagent.RuleAgentRunTrace;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 归档证据投影出的白名单 Trace 夹具。
 *
 * <p>只有枚举、标识和有界列表可以进入夹具；工具观察里的候选原文 excerpt、issue 的用户可见文案、
 * 请求/响应原文一律丢弃。夹具是 CI 重放的唯一输入，因此这里不提供“宽松模式”：
 * 任何越界字段都直接失败，避免把未脱敏内容带进回归资产。
 */
public record EvalTraceFixture(int schemaVersion, String fixtureId, String caseId, String title,
        String projection, UUID taskId, UUID turnId, UUID runId, long version,
        boolean exceptionalFailure, List<SourceArtifact> sourceArtifacts, List<ActionSpec> actions,
        TerminalSpec terminal, List<String> losses, List<String> notes) {

    public static final int SCHEMA_VERSION = 1;
    public static final String FULL_EVENT_SEQUENCE = "FULL_EVENT_SEQUENCE";
    public static final String MODEL_DECISION_SEQUENCE_ONLY = "MODEL_DECISION_SEQUENCE_ONLY";
    public static final String TERMINAL_ONLY = "TERMINAL_ONLY";
    private static final List<String> PROJECTIONS =
            List.of(FULL_EVENT_SEQUENCE, MODEL_DECISION_SEQUENCE_ONLY, TERMINAL_ONLY);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public EvalTraceFixture {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException("不支持的夹具 schemaVersion: " + schemaVersion);
        }
        if (fixtureId == null || fixtureId.isBlank()) throw new IllegalArgumentException("fixtureId 不能为空");
        if (caseId == null || !caseId.matches("[A-Za-z0-9_-]{1,64}")) {
            throw new IllegalArgumentException("夹具 caseId 无效");
        }
        Objects.requireNonNull(taskId);
        Objects.requireNonNull(turnId);
        Objects.requireNonNull(runId);
        if (!PROJECTIONS.contains(projection)) {
            throw new IllegalArgumentException("未知投影类型: " + projection);
        }
        sourceArtifacts = List.copyOf(sourceArtifacts);
        actions = List.copyOf(actions);
        losses = List.copyOf(losses);
        notes = List.copyOf(notes);
        if (TERMINAL_ONLY.equals(projection) && !actions.isEmpty()) {
            throw new IllegalArgumentException("TERMINAL_ONLY 夹具不得携带动作序列");
        }
        Objects.requireNonNull(terminal);
        int expectedStep = 1;
        for (ActionSpec action : actions) {
            if (action.step() != expectedStep++) {
                throw new IllegalArgumentException("夹具动作 step 必须从 1 连续递增");
            }
        }
    }

    /** 投影为现有 Grader 直接消费的 Trace；actionId 用固定前缀派生，只保证唯一。 */
    public RuleAgentRunTrace toTrace() {
        var mapped = actions.stream()
                .map(a -> new RuleAgentRunTrace.Action("fixture-action-" + a.step(), a.type(), a.step(),
                        a.modelAttempt(),
                        a.observation() == null ? null
                                : new RuleAgentRunTrace.Observation(a.observation().status(),
                                        a.observation().dataLevel(), a.observation().reasonCode()),
                        a.reasonCode(), a.publicationReason()))
                .toList();
        return new RuleAgentRunTrace(taskId, turnId, runId, version, terminal.taskStatus(),
                terminal.runState(), exceptionalFailure, false, mapped, terminal.toResultView());
    }

    public static EvalTraceFixture load(Path file, Path repoRoot) throws IOException {
        JsonNode root = MAPPER.readTree(Files.readString(file, StandardCharsets.UTF_8));
        var fixture = fromJson(root);
        fixture.verifySources(repoRoot);
        return fixture;
    }

    public static EvalTraceFixture fromJson(JsonNode root) {
        var terminalNode = root.path("terminal");
        // 归档夹具（E13/R1 与采集器写出）都把 coverage/items/evidence 放在 terminal.result 下，
        // 也兼容采集器早期写出的扁平 terminal；两种形态都不能丢字段，否则 Grader 会拿到空结果。
        var resultNode = terminalNode.has("result") ? terminalNode.path("result") : terminalNode;
        String coverage = resultNode.path("coverage").asText("");
        if (coverage.isBlank()) {
            throw new IllegalArgumentException("夹具终态缺少 coverage");
        }
        var terminal = new TerminalSpec(terminalNode.path("taskStatus").asText(),
                terminalNode.path("runState").asText(), coverage,
                readIssues(resultNode),
                readList(resultNode.path("items"), EvalTraceFixture::itemFrom),
                readList(resultNode.path("evidence"), EvalTraceFixture::evidenceFrom),
                readList(resultNode.path("evidenceRelations"), EvalTraceFixture::relationFrom));
        return new EvalTraceFixture(root.path("schemaVersion").asInt(),
                root.path("fixtureId").asText(), root.path("caseId").asText(),
                root.path("title").asText(), root.path("projection").asText(),
                UUID.fromString(root.path("taskId").asText()),
                UUID.fromString(root.path("turnId").asText()),
                UUID.fromString(root.path("runId").asText()),
                root.path("version").asLong(), root.path("exceptionalFailure").asBoolean(false),
                readList(root.path("sourceArtifacts"), EvalTraceFixture::sourceFrom),
                readList(root.path("actions"), EvalTraceFixture::actionFrom), terminal,
                readStrings(root.path("losses")), readStrings(root.path("notes")));
    }

    /** 证据文件存在时逐一对齐 SHA-256；文件缺失不阻断跨平台重放，但会失去该条来源校验。 */
    private void verifySources(Path repoRoot) throws IOException {
        if (repoRoot == null) return;
        for (SourceArtifact artifact : sourceArtifacts) {
            Path file = repoRoot.resolve(artifact.path());
            if (!Files.isRegularFile(file)) continue;
            String actual = sha256(file);
            if (!actual.equalsIgnoreCase(artifact.sha256())) {
                throw new IllegalStateException("夹具来源已被篡改: " + artifact.path()
                        + " 期望 " + artifact.sha256() + " 实际 " + actual);
            }
        }
    }

    public ObjectNode toJson() {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("schemaVersion", schemaVersion);
        root.put("fixtureId", fixtureId);
        root.put("caseId", caseId);
        root.put("title", title);
        root.put("projection", projection);
        root.put("taskId", taskId.toString());
        root.put("turnId", turnId.toString());
        root.put("runId", runId.toString());
        root.put("version", version);
        root.put("exceptionalFailure", exceptionalFailure);
        ArrayNode sources = root.putArray("sourceArtifacts");
        sourceArtifacts.forEach(s -> sources.addObject().put("role", s.role())
                .put("path", s.path()).put("sha256", s.sha256()));
        ArrayNode actionArray = root.putArray("actions");
        actions.forEach(a -> {
            ObjectNode node = actionArray.addObject();
            node.put("type", a.type());
            node.put("step", a.step());
            node.put("modelAttempt", a.modelAttempt());
            if (a.observation() == null) {
                node.putNull("observation");
            } else {
                node.putObject("observation").put("status", a.observation().status())
                        .put("dataLevel", a.observation().dataLevel())
                        .put("reasonCode", a.observation().reasonCode());
            }
            node.put("reasonCode", a.reasonCode());
            node.put("publicationReason", a.publicationReason());
        });
        ObjectNode terminalNode = root.putObject("terminal");
        terminalNode.put("taskStatus", terminal.taskStatus());
        terminalNode.put("runState", terminal.runState());
        terminalNode.put("coverage", terminal.coverage());
        ArrayNode issues = terminalNode.putArray("issues");
        terminal.issues().forEach(i -> issues.addObject().put("kind", i.kind()).put("reasonCode", i.reasonCode()));
        ArrayNode items = terminalNode.putArray("items");
        terminal.items().forEach(item -> {
            ArrayNode claims = items.addObject().putArray("claims");
            item.claims().forEach(c -> claims.addObject().put("id", c.id()).put("factId", c.factId()));
        });
        ArrayNode evidence = terminalNode.putArray("evidence");
        terminal.evidence().forEach(e -> evidence.addObject().put("id", e.id())
                .put("sourceId", e.sourceId()).put("chunkId", e.chunkId()).put("revision", e.revision()));
        ArrayNode relations = terminalNode.putArray("evidenceRelations");
        terminal.relations().forEach(r -> relations.addObject().put("targetId", r.targetId())
                .put("evidenceId", r.evidenceId()).put("role", r.role()));
        ArrayNode lossArray = root.putArray("losses");
        losses.forEach(lossArray::add);
        ArrayNode noteArray = root.putArray("notes");
        notes.forEach(noteArray::add);
        return root;
    }

    public void write(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(toJson()) + "\n",
                StandardCharsets.UTF_8);
    }

    /** 与 Runner 投影保持一致的 factId 脱敏；夹具和采集器共用，避免两套哈希口径。 */
    public static String opaqueFactId(String original) {
        if (original == null) return null;
        if (original.startsWith("sha256:")) return original;
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(("rule-agent-fact-id:" + original).getBytes(StandardCharsets.UTF_8));
            return "sha256:" + HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 unavailable", unavailable);
        }
    }

    public static String sha256(Path file) throws IOException {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 unavailable", unavailable);
        }
    }

    private static <T> List<T> readList(JsonNode array, java.util.function.Function<JsonNode, T> mapper) {
        if (array == null || !array.isArray()) return List.of();
        var values = new java.util.ArrayList<T>();
        array.forEach(node -> values.add(mapper.apply(node)));
        return List.copyOf(values);
    }

    private static List<String> readStrings(JsonNode array) {
        if (array == null || !array.isArray()) return List.of();
        var values = new java.util.ArrayList<String>();
        array.forEach(node -> values.add(node.asText()));
        return List.copyOf(values);
    }

    private static List<IssueSpec> readIssues(JsonNode resultNode) {
        if (resultNode.has("issues")) {
            return readList(resultNode.path("issues"), EvalTraceFixture::issueFrom);
        }
        // 归档夹具把 issues 挂在每个 item 下；Grader 需要的是扁平列表。
        var values = new java.util.ArrayList<IssueSpec>();
        resultNode.path("items").forEach(item ->
                values.addAll(readList(item.path("issues"), EvalTraceFixture::issueFrom)));
        return List.copyOf(values);
    }

    private static SourceArtifact sourceFrom(JsonNode node) {
        return new SourceArtifact(node.path("role").asText(), node.path("path").asText(),
                node.path("sha256").asText());
    }

    private static ActionSpec actionFrom(JsonNode node) {
        var observationNode = node.path("observation");
        ObservationSpec observation = observationNode == null || observationNode.isNull()
                || observationNode.isMissingNode() ? null
                        : new ObservationSpec(observationNode.path("status").asText(),
                                observationNode.path("dataLevel").isNull() ? null
                                        : observationNode.path("dataLevel").asText(),
                                observationNode.path("reasonCode").isNull() ? null
                                        : observationNode.path("reasonCode").asText());
        return new ActionSpec(node.path("type").asText(), node.path("step").asInt(),
                node.path("modelAttempt").asInt(),
                observation,
                node.path("reasonCode").isNull() ? null : node.path("reasonCode").asText(),
                node.path("publicationReason").isNull() ? null : node.path("publicationReason").asText());
    }

    private static IssueSpec issueFrom(JsonNode node) {
        return new IssueSpec(node.path("kind").asText(), node.path("reasonCode").asText());
    }

    private static ItemSpec itemFrom(JsonNode node) {
        return new ItemSpec(readList(node.path("claims"), c -> new ClaimSpec(c.path("id").asText(),
                c.path("factId").asText())));
    }

    private static EvidenceSpec evidenceFrom(JsonNode node) {
        return new EvidenceSpec(node.path("id").asText(), node.path("sourceId").asText(),
                node.path("chunkId").asText(), node.path("revision").asText());
    }

    private static RelationSpec relationFrom(JsonNode node) {
        return new RelationSpec(node.path("targetId").asText(), node.path("evidenceId").asText(),
                node.path("role").asText());
    }

    public record SourceArtifact(String role, String path, String sha256) {}

    public record ActionSpec(String type, int step, int modelAttempt, ObservationSpec observation,
            String reasonCode, String publicationReason) {}

    public record ObservationSpec(String status, String dataLevel, String reasonCode) {}

    public record IssueSpec(String kind, String reasonCode) {}

    public record ClaimSpec(String id, String factId) {}

    public record ItemSpec(List<ClaimSpec> claims) {
        public ItemSpec {
            claims = List.copyOf(claims);
        }
    }

    public record EvidenceSpec(String id, String sourceId, String chunkId, String revision) {}

    public record RelationSpec(String targetId, String evidenceId, String role) {}

    public record TerminalSpec(String taskStatus, String runState, String coverage,
            List<IssueSpec> issues, List<ItemSpec> items, List<EvidenceSpec> evidence,
            List<RelationSpec> relations) {
        public TerminalSpec {
            issues = List.copyOf(issues);
            items = List.copyOf(items);
            evidence = List.copyOf(evidence);
            relations = List.copyOf(relations);
        }

        RuleAgentRunTrace.ResultView toResultView() {
            return new RuleAgentRunTrace.ResultView(coverage,
                    evidence.stream().map(e -> new RuleAgentRunTrace.EvidenceView(
                            e.id(), e.sourceId(), e.chunkId(), e.revision())).toList(),
                    relations.stream().map(r -> new RuleAgentRunTrace.RelationView(
                            r.targetId(), r.evidenceId(), r.role())).toList(),
                    items.stream().map(i -> new RuleAgentRunTrace.ItemView(i.claims().stream()
                            .map(c -> new RuleAgentRunTrace.ClaimView(c.id(), c.factId())).toList())).toList(),
                    issues.stream().map(i -> new RuleAgentRunTrace.IssueView(
                            i.kind(), i.reasonCode())).toList());
        }
    }
}
