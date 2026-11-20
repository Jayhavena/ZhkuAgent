package com.zhku.knowledge.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 离线维度评分：输入是已独立核验的观察，不判断自然语言语义或整题结论。 */
public final class RuleFactOfflineScorer {
    private static final ObjectMapper JSON = new ObjectMapper();
    private RuleFactOfflineScorer() {}

    public enum Verdict { PASS, FAIL, UNJUDGEABLE, NOT_APPLICABLE }
    public enum Verification { SUPPORTED, MISMATCH, UNKNOWN }
    public record Citation(String locator, Verification verification) {
        public Citation {
            if (locator == null || locator.isBlank()) throw new IllegalArgumentException("citation locator required");
            Objects.requireNonNull(verification);
        }
    }
    public record Case(String id, Set<String> requiredFactIds, Set<String> optionalFactIds, Set<String> requiredLocators) {
        public Case {
            requiredFactIds = Set.copyOf(requiredFactIds);
            optionalFactIds = Set.copyOf(optionalFactIds);
            requiredLocators = Set.copyOf(requiredLocators);
            if (!java.util.Collections.disjoint(requiredFactIds, optionalFactIds)) throw new IllegalArgumentException("overlapping facts");
        }
    }
    public record Dataset(Map<String, Case> cases) {
        public Dataset { cases = Map.copyOf(cases); }
        public Case caseById(String id) {
            Case c = cases.get(id);
            if (c == null) throw new IllegalArgumentException("unknown case");
            return c;
        }
    }
    public record CitationCounts(long applicable, long passed, long failed, long unjudgeable, long notApplicable) {}

    /** Accept the historical draft or reviewed frozen development v1.0 with a byte-pinned manifest. */
    public static Dataset load(Path datasetFile, Path manifestFile) throws IOException {
        byte[] bytes = Files.readAllBytes(datasetFile);
        JsonNode manifest = JSON.readTree(Files.readAllBytes(manifestFile));
        JsonNode data = JSON.readTree(bytes);
        String version = data.path("datasetVersion").asText();
        if (!("v3-review-draft".equals(version) || "v1.0".equals(version))
                || !data.path("datasetVersion").asText().equals(manifest.path("datasetVersion").asText())
                || !data.path("datasetId").asText().equals(manifest.path("datasetId").asText())
                || !datasetFile.getFileName().toString().equals(manifest.path("datasetFile").asText())
                || !sha256(bytes).equals(manifest.path("datasetSha256").asText())
                || !data.path("corpusFingerprint").asText().equals(manifest.path("corpusFingerprint").asText())
                || !data.path("cases").isArray()
                || data.path("caseCount").asInt(-1) != data.path("cases").size()
                || manifest.path("caseCount").asInt(-1) != data.path("cases").size()) {
            throw new IllegalArgumentException("dataset or manifest mismatch");
        }
        if ("v1.0".equals(version)) validateFrozen(data, manifest);
        JsonNode policy = data.path("scoringContract").path("citationApplicability");
        if (!"NOT_APPLICABLE".equals(policy.path("noRequiredEvidenceAndNoCitation").asText())
                || !"VERIFY_EACH_CITATION".equals(policy.path("citationPresent").asText())
                || !"UNJUDGEABLE".equals(policy.path("citationVerificationUnavailable").asText())) {
            throw new IllegalArgumentException("unsupported citation policy");
        }
        Map<String, Case> cases = new HashMap<>();
        for (JsonNode item : data.path("cases")) {
            String id = text(item, "caseId");
            Case value = new Case(id, extract(item.path("expectedFacts"), "semanticFactId", true),
                    extract(item.path("optionalContextFacts"), "semanticFactId", false),
                    extract(item.path("requiredEvidence"), "locatorId", true));
            if (cases.putIfAbsent(id, value) != null) throw new IllegalArgumentException("duplicate case");
        }
        return new Dataset(cases);
    }

    private static void validateFrozen(JsonNode data, JsonNode manifest) {
        if (!"FROZEN".equals(data.path("datasetStatus").asText())
                || !"zhku-rule-fact-check-effect".equals(data.path("datasetId").asText())
                || !"HISTORICAL_2025_09_02_DEVELOPMENT_EXPECTATIONS_ONLY".equals(data.path("freezeScope").asText())
                || data.path("caseCount").asInt() != 20) {
            throw new IllegalArgumentException("invalid frozen development identity");
        }
        for (String field : List.of("datasetStatus", "frozenAt", "reviewer", "freezeScope")) {
            if (text(data, field).isBlank() || !data.path(field).equals(manifest.path(field)))
                throw new IllegalArgumentException("frozen metadata mismatch: " + field);
        }
        try { java.time.OffsetDateTime.parse(text(data, "frozenAt")); }
        catch (java.time.format.DateTimeParseException ex) {
            throw new IllegalArgumentException("invalid frozen timestamp", ex);
        }
        Set<String> ids = new HashSet<>();
        for (JsonNode item : data.path("cases")) {
            String id = text(item, "caseId");
            if (!ids.add(id) || !id.matches("RFE-(00[1-9]|01[0-9]|020)")
                    || !"APPROVED".equals(item.path("humanReviewStatus").asText())
                    || !"APPROVED".equals(item.path("humanReview").path("humanReviewStatus").asText())
                    || !id.equals(item.path("humanReview").path("caseId").asText())) {
                throw new IllegalArgumentException("unapproved or invalid frozen case: " + id);
            }
            text(item.path("humanReview"), "reviewer");
            text(item.path("humanReview"), "reviewedAt");
        }
    }

    /** matchedFactIds are externally verified; complete means all required facts were assessed. */
    public static Verdict facts(Case c, Set<String> matchedFactIds, boolean complete) {
        Objects.requireNonNull(c); Objects.requireNonNull(matchedFactIds);
        if (c.requiredFactIds().isEmpty()) return Verdict.NOT_APPLICABLE;
        if (matchedFactIds.containsAll(c.requiredFactIds())) return Verdict.PASS;
        return complete ? Verdict.FAIL : Verdict.UNJUDGEABLE;
    }

    /** citations must be the complete observed list; each verification includes support for its associated claim. */
    public static Verdict citations(Case c, List<Citation> citations) {
        Objects.requireNonNull(c); citations = List.copyOf(citations);
        if (citations.stream().anyMatch(x -> x.verification() == Verification.MISMATCH)) return Verdict.FAIL;
        if (citations.isEmpty() && c.requiredLocators().isEmpty()) return Verdict.NOT_APPLICABLE;
        Set<String> observed = new HashSet<>();
        citations.forEach(x -> observed.add(x.locator()));
        if (!observed.containsAll(c.requiredLocators())) return Verdict.FAIL;
        if (citations.stream().anyMatch(x -> x.verification() == Verification.UNKNOWN)) return Verdict.UNJUDGEABLE;
        return Verdict.PASS;
    }

    public static CitationCounts countCitations(List<Verdict> verdicts) {
        long pass = verdicts.stream().filter(v -> v == Verdict.PASS).count();
        long fail = verdicts.stream().filter(v -> v == Verdict.FAIL).count();
        long unknown = verdicts.stream().filter(v -> v == Verdict.UNJUDGEABLE).count();
        long na = verdicts.stream().filter(v -> v == Verdict.NOT_APPLICABLE).count();
        if (pass + fail + unknown + na != verdicts.size()) throw new IllegalArgumentException("null verdict");
        return new CitationCounts(pass + fail + unknown, pass, fail, unknown, na);
    }

    private static Set<String> extract(JsonNode array, String field, boolean required) {
        if (array.isMissingNode() && !required) return Set.of();
        if (!array.isArray()) throw new IllegalArgumentException("array required");
        Set<String> values = new HashSet<>();
        for (JsonNode node : array) if (!values.add(text(node, field))) throw new IllegalArgumentException("duplicate value");
        return values;
    }
    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isTextual() || value.asText().isBlank()) throw new IllegalArgumentException("missing " + field);
        return value.asText();
    }
    private static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
