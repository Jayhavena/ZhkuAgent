package com.zhku.knowledge.eval.candidate;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhku.agent.agent.runtime.AgentRunState;
import com.zhku.knowledge.ruleagent.RuleAgentRunTrace;
import com.zhku.knowledge.ruleagent.RuleAgentTraceEvaluator;
import com.zhku.knowledge.ruleagent.RuleAgentTraceEvaluator.CaseResult;
import com.zhku.knowledge.ruleagent.RuleAgentTraceEvaluator.Expectation;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import static com.zhku.knowledge.ruleagent.RuleAgentContract.*;

/** 离线 sidecar：只消费保存的 T08 v1 白名单投影；SHA 绑定文件，不证明真实执行或语义正确。 */
public final class CandidateTraceLoader {
    private static final int MAX_BYTES = 1024 * 1024;
    private static final Set<String> ACTIONS = Set.of("SEARCH_REVIEWED_RULES", "READ_VERIFIED_RULE",
            "ASK_NECESSARY_CONDITION", "FINISH_CHECK", "PUBLICATION_REVIEW", "INVALID");
    private static final Set<String> STATUSES = Set.of("SUCCEEDED", "NO_MATCH", "REJECTED", "UNAVAILABLE", "NOT_OBSERVED");
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private CandidateTraceLoader() {}

    public record Provenance(String sourceSha, String datasetFile, String datasetSha256,
            String traceFile, String traceSha256, String expectationSource) {}
    public record EvaluationCandidate(String candidateId, String caseId, String status,
            String reviewStatus, Provenance provenance, Expectation expectation) {}
    public record LoadedCandidate(EvaluationCandidate candidate, RuleAgentRunTrace trace) {
        public CaseResult evaluate() {
            // 零动作只能证明终态；不能把它作为完整执行链交给结构性 Grader 判 PASS。
            if (trace != null && trace.actions().isEmpty()) {
                return new CaseResult(candidate.caseId(), RuleAgentTraceEvaluator.Verdict.NOT_EVALUABLE,
                        List.of("FULL_CHAIN_NOT_ARCHIVED"), trace.taskStatus(), trace.result().coverage(), List.of());
            }
            return RuleAgentTraceEvaluator.evaluate(candidate.expectation(), trace);
        }
    }

    public static LoadedCandidate load(Path repositoryRoot, Path candidateFile) throws IOException {
        Path root = repositoryRoot.toRealPath();
        var doc = parse(bytes(candidateFile));
        fields(doc, "recordType schemaVersion candidateId caseId status reviewStatus provenance expectation", "");
        equal(doc.get("recordType"), "RULE_AGENT_EVALUATION_CANDIDATE");
        number(doc.get("schemaVersion"), 1, 1);
        equal(doc.get("status"), "CANDIDATE");
        equal(doc.get("reviewStatus"), "PENDING");
        String candidateId = pattern(doc.get("candidateId"), "[A-Za-z0-9][A-Za-z0-9_.-]{0,127}");
        String caseId = pattern(doc.get("caseId"), "[A-Za-z0-9_-]{1,64}");
        var p = doc.get("provenance");
        fields(p, "sourceSha datasetFile datasetSha256 traceFile traceSha256 expectationSource", "");
        var provenance = new Provenance(pattern(p.get("sourceSha"), "[a-f0-9]{40}([a-f0-9]{24})?"),
                text(p.get("datasetFile")), pattern(p.get("datasetSha256"), "[a-f0-9]{64}"),
                text(p.get("traceFile")), pattern(p.get("traceSha256"), "[a-f0-9]{64}"), text(p.get("expectationSource")));
        var candidate = new EvaluationCandidate(candidateId, caseId, "CANDIDATE", "PENDING",
                provenance, expectation(doc.get("expectation")));
        if (!caseId.equals(candidate.expectation().caseId())) throw invalid("IDENTITY_MISMATCH");
        var dataset = parse(checkedBytes(reference(root, provenance.datasetFile()), provenance.datasetSha256()));
        var caseIds = new HashSet<String>();
        for (var c : array(dataset.get("cases"), 10000)) {
            if (!caseIds.add(text(c.get("caseId")))) throw invalid("DUPLICATE_CASE_ID");
        }
        if (!caseIds.contains(caseId)) throw invalid("DATASET_CASE_MISSING");
        var exported = parse(checkedBytes(reference(root, provenance.traceFile()), provenance.traceSha256()));
        fields(exported, "recordType schemaVersion sourceLayer executionMode providerCalls candidateId sourceSha caseId datasetSha256 traces", "");
        equal(exported.get("recordType"), "T08_JAVA_TRACE_EXPORT");
        number(exported.get("schemaVersion"), 1, 1);
        equal(exported.get("sourceLayer"), "ACTUAL_JAVA_RUNNER_OFFLINE_SIMULATED");
        equal(exported.get("executionMode"), "OFFLINE_SIMULATED");
        number(exported.get("providerCalls"), 0, 0);
        equal(exported.get("candidateId"), candidateId);
        equal(exported.get("caseId"), caseId);
        equal(exported.get("sourceSha"), provenance.sourceSha());
        equal(exported.get("datasetSha256"), provenance.datasetSha256());
        var traces = array(exported.get("traces"), 1);
        return new LoadedCandidate(candidate, traces.isEmpty() ? null : trace(traces.get(0), caseId));
    }

    private static Expectation expectation(JsonNode e) {
        fields(e, "caseId taskStatus runState coverage requiredActions issueReason expectClaims observedAction observationStatus requireEvidenceLinks expectRejectedUnknownTool", "");
        var required = new ArrayList<String>();
        for (var action : array(e.get("requiredActions"), RuleAgentRunTrace.MAX_ACTIONS)) required.add(oneOf(action, ACTIONS));
        return new Expectation(pattern(e.get("caseId"), "[A-Za-z0-9_-]{1,64}"),
                enumeration(e.get("taskStatus"), TaskStatus.class), enumeration(e.get("runState"), AgentRunState.class),
                enumeration(e.get("coverage"), Coverage.class), required, optionalEnum(e.get("issueReason"), ReasonCode.class),
                bool(e.get("expectClaims")), e.get("observedAction").isNull() ? null : oneOf(e.get("observedAction"), ACTIONS),
                e.get("observationStatus").isNull() ? null : oneOf(e.get("observationStatus"), STATUSES),
                bool(e.get("requireEvidenceLinks")), bool(e.get("expectRejectedUnknownTool")));
    }

    private static RuleAgentRunTrace trace(JsonNode node, String caseId) {
        fields(node, "taskId turnId runId version taskStatus runState exceptionalFailure truncated actions observations result", "");
        equal(node.get("taskId"), "task-1"); equal(node.get("turnId"), "turn-1"); equal(node.get("runId"), "run-1");
        number(node.get("version"), 2, 2); // T08 v1 只导出一个单轮已发布快照，不支持跨轮重建。
        var actionNodes = array(node.get("actions"), RuleAgentRunTrace.MAX_ACTIONS);
        var ids = new HashSet<String>();
        for (var a : actionNodes) {
            fields(a, "actionId type step modelAttempt", "");
            if (!ids.add(pattern(a.get("actionId"), "action-[1-9][0-9]{0,3}"))) throw invalid("DUPLICATE_ACTION_ID");
            oneOf(a.get("type"), ACTIONS); number(a.get("step"), 1, 10000); number(a.get("modelAttempt"), 1, 10000);
        }
        var observations = new LinkedHashMap<String, JsonNode>();
        for (var o : array(node.get("observations"), RuleAgentRunTrace.MAX_ACTIONS)) {
            fields(o, "actionId status dataLevel", "reasonCode");
            String id = pattern(o.get("actionId"), "action-[1-9][0-9]{0,3}");
            if (!ids.contains(id)) throw invalid("ORPHAN_OBSERVATION");
            if (observations.putIfAbsent(id, o) != null) throw invalid("DUPLICATE_OBSERVATION_ID");
            String status = oneOf(o.get("status"), STATUSES);
            optionalEnum(o.get("reasonCode"), ReasonCode.class);
            if ("NOT_OBSERVED".equals(status)) {
                if (!o.get("dataLevel").isNull()) throw invalid("OBSERVATION_STATE");
            } else enumeration(o.get("dataLevel"), DataLevel.class);
        }
        var actions = new ArrayList<RuleAgentRunTrace.Action>();
        for (var a : actionNodes) {
            var o = observations.get(a.get("actionId").textValue());
            boolean observed = o != null && !"NOT_OBSERVED".equals(o.get("status").textValue());
            String reason = o == null ? null : optionalEnum(o.get("reasonCode"), ReasonCode.class);
            boolean tool = Set.of("SEARCH_REVIEWED_RULES", "READ_VERIFIED_RULE").contains(a.get("type").textValue());
            if (observed && !tool) throw invalid("OBSERVATION_STATE");
            // 缺失或 NOT_OBSERVED 保留为 null；不把“没有记录”解释为工具成功。
            actions.add(new RuleAgentRunTrace.Action(a.get("actionId").textValue(), a.get("type").textValue(),
                    a.get("step").intValue(), a.get("modelAttempt").intValue(), observed
                    ? new RuleAgentRunTrace.Observation(o.get("status").textValue(), o.get("dataLevel").textValue(), reason)
                    : null, observed ? null : reason, null));
        }
        return new RuleAgentRunTrace(localId(caseId, "task-1"), localId(caseId, "turn-1"), localId(caseId, "run-1"),
                node.get("version").longValue(), enumeration(node.get("taskStatus"), TaskStatus.class),
                enumeration(node.get("runState"), AgentRunState.class), bool(node.get("exceptionalFailure")),
                bool(node.get("truncated")), actions, result(node.get("result")));
    }

    private static RuleAgentRunTrace.ResultView result(JsonNode result) {
        fields(result, "coverage evidence evidenceRelations items issues", "");
        var evidence = new ArrayList<RuleAgentRunTrace.EvidenceView>();
        var evidenceIds = new HashSet<String>();
        for (var e : array(result.get("evidence"), 8)) {
            fields(e, "id revision", "");
            String id = pattern(e.get("id"), "evidence-[1-9][0-9]{0,3}");
            if (!evidenceIds.add(id)) throw invalid("DUPLICATE_EVIDENCE_ID");
            equal(e.get("revision"), "2025.9.2");
            // T08 已删除 sourceId/chunkId，必须保持未知，不能从局部标签反推原始标识。
            evidence.add(new RuleAgentRunTrace.EvidenceView(id, null, null, e.get("revision").textValue()));
        }
        var relations = new ArrayList<RuleAgentRunTrace.RelationView>();
        for (var r : array(result.get("evidenceRelations"), 64)) {
            fields(r, "targetId evidenceId role", "");
            relations.add(new RuleAgentRunTrace.RelationView(pattern(r.get("targetId"), "claim-[1-9][0-9]{0,3}"),
                    pattern(r.get("evidenceId"), "evidence-[1-9][0-9]{0,3}"), enumeration(r.get("role"), EvidenceRole.class)));
        }
        var items = new ArrayList<RuleAgentRunTrace.ItemView>();
        var claimIds = new HashSet<String>();
        for (var i : array(result.get("items"), 4)) {
            fields(i, "claims", "");
            var claims = new ArrayList<RuleAgentRunTrace.ClaimView>();
            for (var c : array(i.get("claims"), 64)) {
                fields(c, "id factId", "");
                String id = pattern(c.get("id"), "claim-[1-9][0-9]{0,3}");
                if (!claimIds.add(id)) throw invalid("DUPLICATE_CLAIM_ID");
                claims.add(new RuleAgentRunTrace.ClaimView(id, pattern(c.get("factId"), "sha256:[a-f0-9]{64}")));
            }
            items.add(new RuleAgentRunTrace.ItemView(claims));
        }
        var issues = new ArrayList<RuleAgentRunTrace.IssueView>();
        for (var i : array(result.get("issues"), 32)) {
            fields(i, "kind reasonCode", "");
            issues.add(new RuleAgentRunTrace.IssueView(enumeration(i.get("kind"), IssueKind.class),
                    enumeration(i.get("reasonCode"), ReasonCode.class)));
        }
        // 关系是否支撑 claim 属于 Grader 的可定位 FAIL，不在加载时悄悄修补。
        return new RuleAgentRunTrace.ResultView(enumeration(result.get("coverage"), Coverage.class), evidence, relations, items, issues);
    }

    private static UUID localId(String caseId, String label) {
        // 仅为 Java 类型提供稳定的本地占位身份，不宣称还原真实 task/run 或跨轮关联。
        return UUID.nameUUIDFromBytes(("offline-export:" + caseId + ":" + label).getBytes(StandardCharsets.UTF_8));
    }
    private static Path reference(Path root, String value) throws IOException {
        Path relative = Path.of(value);
        if (relative.isAbsolute()) throw invalid("REFERENCE_PATH");
        for (Path part : relative) if ("..".equals(part.toString())) throw invalid("REFERENCE_PATH");
        Path path = root.resolve(relative).normalize();
        if (!path.startsWith(root) || !path.toRealPath().startsWith(root)) throw invalid("REFERENCE_PATH");
        return path;
    }
    private static byte[] bytes(Path path) throws IOException {
        try (var input = Files.newInputStream(path)) {
            byte[] bytes = input.readNBytes(MAX_BYTES + 1);
            if (bytes.length > MAX_BYTES) throw invalid("SIZE_LIMIT");
            return bytes;
        }
    }
    private static byte[] checkedBytes(Path path, String expected) throws IOException {
        byte[] bytes = bytes(path);
        try {
            if (!HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)).equals(expected)) {
                throw invalid("SHA256_MISMATCH");
            }
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
        return bytes;
    }
    private static JsonNode parse(byte[] bytes) {
        try {
            JsonNode node = JSON.readTree(bytes);
            if (node == null || !node.isObject()) throw invalid("JSON_OBJECT_REQUIRED");
            return node;
        } catch (IOException malformed) {
            // 不把 Jackson 的原始输入片段带到错误消息或评测报告。
            throw invalid("JSON_INVALID");
        }
    }
    private static void fields(JsonNode node, String required, String optional) {
        if (node == null || !node.isObject()) throw invalid("OBJECT_REQUIRED");
        var allowed = new HashSet<>(List.of(required.split(" ")));
        if (!optional.isBlank()) allowed.addAll(List.of(optional.split(" ")));
        for (String key : required.split(" ")) if (!node.has(key)) throw invalid("FIELD_MISSING");
        node.fieldNames().forEachRemaining(key -> { if (!allowed.contains(key)) throw invalid("FIELD_UNKNOWN"); });
    }
    private static JsonNode array(JsonNode node, int max) {
        if (node == null || !node.isArray() || node.size() > max) throw invalid("ARRAY_REQUIRED_OR_LIMIT");
        return node;
    }
    private static String text(JsonNode node) {
        if (node == null || !node.isTextual() || node.textValue().isBlank() || node.textValue().length() > 512) {
            throw invalid("TEXT_REQUIRED");
        }
        return node.textValue();
    }
    private static String pattern(JsonNode node, String regex) {
        String value = text(node);
        if (!value.matches(regex)) throw invalid("VALUE_INVALID");
        return value;
    }
    private static String oneOf(JsonNode node, Set<String> values) {
        String value = text(node);
        if (!values.contains(value)) throw invalid("ENUM_INVALID");
        return value;
    }
    private static <E extends Enum<E>> String enumeration(JsonNode node, Class<E> type) {
        String value = text(node);
        try { return Enum.valueOf(type, value).name(); }
        catch (IllegalArgumentException unknown) { throw invalid("ENUM_INVALID"); }
    }
    private static <E extends Enum<E>> String optionalEnum(JsonNode node, Class<E> type) {
        return node == null || node.isNull() ? null : enumeration(node, type);
    }
    private static void equal(JsonNode node, String expected) {
        if (!expected.equals(text(node))) throw invalid("IDENTITY_OR_MODE_MISMATCH");
    }
    private static long number(JsonNode node, long min, long max) {
        if (node == null || !node.isIntegralNumber() || !node.canConvertToLong()
                || node.longValue() < min || node.longValue() > max) throw invalid("NUMBER_INVALID");
        return node.longValue();
    }
    private static boolean bool(JsonNode node) {
        if (node == null || !node.isBoolean()) throw invalid("BOOLEAN_REQUIRED");
        return node.booleanValue();
    }
    private static IllegalArgumentException invalid(String code) {
        return new IllegalArgumentException("CANDIDATE_" + code);
    }
}
