package com.zhku.knowledge.eval.chain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * 把一次真实探针/运行产物目录采集为白名单 Trace 夹具。
 *
 * <p>优先级：已发布终态快照（task-results.json/jsonl）是权威来源；否则退回
 * {@code *-outcome.json} 里的快照，并显式标记“终态未发布”。任何证据缺失都不会被当成
 * “通过”，调用方必须把采集状态写进报告。
 */
public final class EvalEvidenceCollector {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> ACTION_TYPES = Set.of(
            "SEARCH_REVIEWED_RULES", "READ_VERIFIED_RULE", "ASK_NECESSARY_CONDITION",
            "FINISH_CHECK", "PUBLICATION_REVIEW", "INVALID");

    private EvalEvidenceCollector() {}

    public enum Status { COMPLETE, PARTIAL_TERMINAL_FALLBACK, INCOMPLETE }

    public record CollectedRun(UUID runId, String taskStatus, String runState, String coverage,
            String projection, Path fixture) {}

    public record Collection(String bundleId, Status status, List<String> reasons,
            List<CollectedRun> runs, Path directory) {
        public Collection {
            reasons = List.copyOf(reasons);
            runs = List.copyOf(runs);
        }
    }

    public static Collection collect(Path evidenceDirectory, String bundleId, Path outDirectory,
            Path repoRoot) throws IOException {
        Path evidence = evidenceDirectory.toAbsolutePath().normalize();
        if (!Files.isDirectory(evidence)) {
            throw new IllegalArgumentException("证据目录不存在: " + evidence);
        }
        var reasons = new ArrayList<String>();
        var snapshots = new ArrayList<SnapshotEntry>();
        Path terminalFile = findTerminalFile(evidence);
        if (terminalFile != null) {
            snapshots.addAll(readSnapshots(terminalFile));
        }

        Map<UUID, OutcomeEntry> outcomes = readOutcomes(evidence);
        if (terminalFile == null) {
            if (!outcomes.isEmpty()) {
                reasons.add("TERMINAL_SNAPSHOT_NOT_PUBLISHED");
                outcomes.values().forEach(outcome -> snapshots.add(
                        new SnapshotEntry(outcome.snapshot(), outcome.file())));
            } else {
                reasons.add("TERMINAL_SNAPSHOT_MISSING");
            }
        }

        Status status;
        if (snapshots.isEmpty()) {
            status = Status.INCOMPLETE;
        } else if (terminalFile == null) {
            status = Status.PARTIAL_TERMINAL_FALLBACK;
        } else {
            status = Status.COMPLETE;
        }

        Files.createDirectories(outDirectory);
        var runs = new ArrayList<CollectedRun>();
        var usedOutcomes = new LinkedHashMap<Path, String>();
        for (int index = 0; index < snapshots.size(); index++) {
            SnapshotEntry entry = snapshots.get(index);
            JsonNode snapshot = entry.snapshot();
            UUID runId = UUID.fromString(snapshot.path("runId").asText());
            OutcomeEntry outcome = outcomes.get(runId);
            List<EvalTraceFixture.ActionSpec> actions = outcome == null
                    ? List.of()
                    : actionsOf(outcome.events());
            if (outcome != null) usedOutcomes.put(outcome.file(), "RUN_OUTCOME");
            String projection = actions.isEmpty()
                    ? EvalTraceFixture.TERMINAL_ONLY
                    : EvalTraceFixture.FULL_EVENT_SEQUENCE;

            var sources = new ArrayList<EvalTraceFixture.SourceArtifact>();
            if (entry.file() != null) {
                sources.add(new EvalTraceFixture.SourceArtifact("TERMINAL_SNAPSHOT",
                        repoRelative(repoRoot, entry.file()), EvalTraceFixture.sha256(entry.file())));
            }
            if (outcome != null) {
                sources.add(new EvalTraceFixture.SourceArtifact("RUN_OUTCOME",
                        repoRelative(repoRoot, outcome.file()), EvalTraceFixture.sha256(outcome.file())));
            }
            var fixture = new EvalTraceFixture(EvalTraceFixture.SCHEMA_VERSION,
                    "collected-" + runId, "collected-" + runId,
                    "采集自 " + evidence.getFileName() + " / run " + runId,
                    projection, UUID.fromString(snapshot.path("taskId").asText()),
                    UUID.fromString(snapshot.path("turnId").asText()), runId,
                    snapshot.path("version").asLong(), false, sources, actions,
                    terminalOf(snapshot), List.of(
                            "observation.candidates[*].excerpt：检索候选原文不进入夹具",
                            "issues[*].message/nextStep：用户可见文案不进入夹具",
                            "provider 请求/响应原文与宿主日志不进入夹具"),
                    outcome == null
                            ? List.of("该 run 没有 outcome 事件文件，只能重放终态")
                            : List.of("动作来自 outcome.events，观察只保留 status/dataLevel/reasonCode"));
            Path fixtureFile = outDirectory.resolve("fixture-" + runId + ".json");
            fixture.write(fixtureFile);
            runs.add(new CollectedRun(runId, snapshot.path("taskStatus").asText(),
                    snapshot.path("runState").asText(), snapshot.path("result").path("coverage").asText(),
                    projection, fixtureFile));
        }

        if (!outcomes.isEmpty()) {
            outcomes.keySet().stream().filter(id -> runs.stream().noneMatch(r -> r.runId().equals(id)))
                    .forEach(id -> reasons.add("OUTCOME_WITHOUT_TERMINAL_SNAPSHOT:" + id));
        }

        var sourceList = new ArrayList<EvalTraceFixture.SourceArtifact>();
        if (terminalFile != null) {
            sourceList.add(new EvalTraceFixture.SourceArtifact("TERMINAL_SNAPSHOT",
                    repoRelative(repoRoot, terminalFile), EvalTraceFixture.sha256(terminalFile)));
        }
        usedOutcomes.forEach((file, role) -> {
            try {
                sourceList.add(new EvalTraceFixture.SourceArtifact(role,
                        repoRelative(repoRoot, file), EvalTraceFixture.sha256(file)));
            } catch (IOException failure) {
                throw new IllegalStateException("证据文件不可读: " + file, failure);
            }
        });
        Path providerTrace = evidence.resolve("provider-traces.jsonl");
        if (Files.isRegularFile(providerTrace)) {
            sourceList.add(new EvalTraceFixture.SourceArtifact("PROVIDER_TRACE",
                    repoRelative(repoRoot, providerTrace), EvalTraceFixture.sha256(providerTrace)));
        }
        Path probeResult = evidence.resolve("probe-result.json");
        if (Files.isRegularFile(probeResult)) {
            sourceList.add(new EvalTraceFixture.SourceArtifact("PROBE_RESULT",
                    repoRelative(repoRoot, probeResult), EvalTraceFixture.sha256(probeResult)));
        }

        ObjectNode summary = MAPPER.createObjectNode();
        summary.put("schemaVersion", 1);
        summary.put("bundleId", bundleId);
        summary.put("evidenceDirectory", evidence.toString());
        summary.put("status", status.name());
        ArrayNode reasonArray = summary.putArray("reasons");
        reasons.forEach(reasonArray::add);
        ArrayNode fileArray = summary.putArray("artifacts");
        sourceList.forEach(artifact -> fileArray.addObject().put("role", artifact.role())
                .put("path", artifact.path()).put("sha256", artifact.sha256()));
        ArrayNode runArray = summary.putArray("runs");
        runs.forEach(run -> runArray.addObject().put("runId", run.runId().toString())
                .put("taskStatus", run.taskStatus()).put("runState", run.runState())
                .put("coverage", run.coverage()).put("projection", run.projection())
                .put("fixture", run.fixture().getFileName().toString()));
        Files.writeString(outDirectory.resolve("collection.json"),
                MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(summary) + "\n",
                StandardCharsets.UTF_8);
        return new Collection(bundleId, status, reasons, runs, outDirectory);
    }

    private static Path findTerminalFile(Path evidence) {
        for (String name : List.of("task-results.json", "task-results.jsonl")) {
            Path candidate = evidence.resolve(name);
            if (Files.isRegularFile(candidate)) return candidate;
        }
        return null;
    }

    private static List<SnapshotEntry> readSnapshots(Path file) throws IOException {
        var entries = new ArrayList<SnapshotEntry>();
        if (file.getFileName().toString().endsWith(".jsonl")) {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (line.isBlank()) continue;
                JsonNode node = MAPPER.readTree(line);
                entries.add(new SnapshotEntry(unwrap(node), file));
            }
            return entries;
        }
        JsonNode root = MAPPER.readTree(Files.readString(file, StandardCharsets.UTF_8));
        if (root.isArray()) {
            for (JsonNode node : root) entries.add(new SnapshotEntry(unwrap(node), file));
        } else if (!root.isMissingNode() && !root.isNull()) {
            entries.add(new SnapshotEntry(unwrap(root), file));
        }
        return entries;
    }

    private static JsonNode unwrap(JsonNode node) {
        return node.has("snapshot") && node.get("snapshot").isObject() ? node.get("snapshot") : node;
    }

    private static Map<UUID, OutcomeEntry> readOutcomes(Path evidence) throws IOException {
        var outcomes = new LinkedHashMap<UUID, OutcomeEntry>();
        try (Stream<Path> stream = Files.list(evidence)) {
            List<Path> files = stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith("-outcome.json"))
                    .sorted().toList();
            for (Path file : files) {
                JsonNode root = MAPPER.readTree(Files.readString(file, StandardCharsets.UTF_8));
                JsonNode snapshot = root.path("snapshot");
                if (!snapshot.isObject() || snapshot.path("runId").asText().isBlank()) continue;
                UUID runId = UUID.fromString(snapshot.path("runId").asText());
                outcomes.put(runId, new OutcomeEntry(file, snapshot, root.path("events")));
            }
        }
        return outcomes;
    }

    private static List<EvalTraceFixture.ActionSpec> actionsOf(JsonNode events) {
        var actions = new ArrayList<EvalTraceFixture.ActionSpec>();
        int step = 1;
        for (JsonNode event : events) {
            String rawType = event.path("action").asText();
            String type = ACTION_TYPES.contains(rawType) ? rawType : "INVALID";
            JsonNode observationNode = event.path("observation");
            EvalTraceFixture.ObservationSpec observation = null;
            if (observationNode.isObject()) {
                String reason = observationNode.path("reasonCode").isNull()
                        ? null : observationNode.path("reasonCode").asText();
                if (reason == null && !event.path("reason").isNull()) {
                    reason = event.path("reason").asText();
                }
                observation = new EvalTraceFixture.ObservationSpec(
                        observationNode.path("status").asText(),
                        observationNode.path("dataLevel").isNull() ? null
                                : observationNode.path("dataLevel").asText(),
                        reason);
            }
            actions.add(new EvalTraceFixture.ActionSpec(type, step, event.path("attempt").asInt(),
                    observation,
                    event.path("reason").isNull() ? null : event.path("reason").asText(),
                    event.path("publicationReason").isNull() ? null
                            : event.path("publicationReason").asText()));
            step++;
        }
        return actions;
    }

    private static EvalTraceFixture.TerminalSpec terminalOf(JsonNode snapshot) {
        JsonNode result = snapshot.path("result");
        var issues = new ArrayList<EvalTraceFixture.IssueSpec>();
        var items = new ArrayList<EvalTraceFixture.ItemSpec>();
        for (JsonNode item : result.path("items")) {
            var claims = new ArrayList<EvalTraceFixture.ClaimSpec>();
            for (JsonNode claim : item.path("claims")) {
                claims.add(new EvalTraceFixture.ClaimSpec(claim.path("id").asText(),
                        EvalTraceFixture.opaqueFactId(claim.path("factId").asText())));
            }
            items.add(new EvalTraceFixture.ItemSpec(claims));
            for (JsonNode issue : item.path("issues")) {
                issues.add(new EvalTraceFixture.IssueSpec(issue.path("kind").asText(),
                        issue.path("reasonCode").asText()));
            }
        }
        var evidence = new ArrayList<EvalTraceFixture.EvidenceSpec>();
        for (JsonNode node : result.path("evidence")) {
            evidence.add(new EvalTraceFixture.EvidenceSpec(node.path("id").asText(),
                    node.path("sourceId").asText(), node.path("chunkId").asText(),
                    node.path("revision").asText()));
        }
        var relations = new ArrayList<EvalTraceFixture.RelationSpec>();
        for (JsonNode node : result.path("evidenceRelations")) {
            relations.add(new EvalTraceFixture.RelationSpec(node.path("targetId").asText(),
                    node.path("evidenceId").asText(), node.path("role").asText()));
        }
        return new EvalTraceFixture.TerminalSpec(snapshot.path("taskStatus").asText(),
                snapshot.path("runState").asText(), result.path("coverage").asText(),
                issues, items, evidence, relations);
    }

    /** 来源路径尽量以仓库根为基准，保证夹具在不同机器上仍可定位与校验。 */
    private static String repoRelative(Path repoRoot, Path file) {
        Path absolute = file.toAbsolutePath().normalize();
        if (repoRoot != null) {
            try {
                return normalizeSeparators(repoRoot.toAbsolutePath().normalize().relativize(absolute));
            } catch (IllegalArgumentException unrelatedRoots) {
                // 证据在仓库外时退回绝对路径；调用方仍可重放，但不再有跨机器来源校验。
                return normalizeSeparators(absolute);
            }
        }
        return normalizeSeparators(absolute);
    }

    private static String normalizeSeparators(Path path) {
        return path.toString().replace(java.io.File.separatorChar, (char) 47);
    }

    private record SnapshotEntry(JsonNode snapshot, Path file) {}

    private record OutcomeEntry(Path file, JsonNode snapshot, JsonNode events) {}
}
