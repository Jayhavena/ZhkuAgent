package com.zhku.knowledge.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhku.knowledge.ruleagent.RuleAgentRunTrace;
import com.zhku.knowledge.ruleagent.RuleAgentTraceEvaluator;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import static com.zhku.knowledge.eval.RuleFactOfflineScorer.*;

/** Binds execution observations and independent semantic reviews; never infers semantic truth from text. */
public final class RuleFactExecutionEvaluation {
    private RuleFactExecutionEvaluation() {}
    public record Identity(String caseId, UUID taskId, UUID runId, String datasetVersion,
                           String datasetSha256, String model, String configurationSha256) {}
    public record Review(Verification verification, String reviewer, String reason, String answerExcerpt) {}
    public record Execution(Identity identity, String answer, RuleAgentRunTrace trace,
                            Set<String> retrievedLocators, boolean retrievalComplete,
                            Set<String> matchedFacts, boolean factsComplete,
                            List<Citation> citations, boolean citationsComplete,
                            Map<String, Review> reviews) {}
    public record Dimension(Verdict verdict, List<String> reasons) {}
    public record Result(Identity identity, String outcome, Map<String, Dimension> dimensions) {}
    public static final List<String> DIMENSIONS = List.of("retrievalRecall", "factSupport", "citationIntegrity",
            "scopeAndApplicability", "clarificationAndGap", "executionSafety");

    public static Result evaluate(Dataset dataset, String datasetSha256, Execution e,
                                  RuleAgentTraceEvaluator.Expectation expected) {
        Objects.requireNonNull(e); Identity i = Objects.requireNonNull(e.identity());
        Case c = dataset.caseById(i.caseId());
        if (i.taskId() == null || i.runId() == null || !"v1.0".equals(i.datasetVersion())
                || !Objects.equals(datasetSha256, i.datasetSha256()) || blank(i.model())
                || i.configurationSha256() == null || !i.configurationSha256().matches("[a-f0-9]{64}"))
            throw new IllegalArgumentException("execution identity mismatch");
        if (e.trace() != null && (!i.taskId().equals(e.trace().taskId()) || !i.runId().equals(e.trace().runId())))
            throw new IllegalArgumentException("trace identity mismatch");
        if (expected != null && !i.caseId().equals(expected.caseId()))
            throw new IllegalArgumentException("expectation case mismatch");
        Map<String, Dimension> d = new LinkedHashMap<>();
        if (blank(e.answer())) {
            DIMENSIONS.forEach(k -> d.put(k, dim(Verdict.UNJUDGEABLE, "ANSWER_MISSING")));
        } else {
            d.put("retrievalRecall", !e.retrievalComplete() || e.retrievedLocators() == null
                    ? dim(Verdict.UNJUDGEABLE, "RETRIEVAL_OBSERVATION_MISSING")
                    : dim(c.requiredLocators().isEmpty() ? Verdict.NOT_APPLICABLE
                    : e.retrievedLocators().containsAll(c.requiredLocators()) ? Verdict.PASS : Verdict.FAIL,
                    "required=" + c.requiredLocators() + "; observed=" + e.retrievedLocators()));
            d.put("factSupport", combine(e.matchedFacts() == null || !e.factsComplete()
                    ? dim(Verdict.UNJUDGEABLE, "FACT_REVIEW_INCOMPLETE")
                    : dim(facts(c, e.matchedFacts(), true), "required=" + c.requiredFactIds() + "; matched=" + e.matchedFacts()),
                    review(e, "unsupportedClaims")));
            d.put("citationIntegrity", !e.citationsComplete() || e.citations() == null
                    ? dim(Verdict.UNJUDGEABLE, "CITATION_INVENTORY_MISSING")
                    : dim(citations(c, e.citations()), "verified citations=" + e.citations()));
            d.put("scopeAndApplicability", review(e, "scopeAndApplicability"));
            d.put("clarificationAndGap", review(e, "clarificationAndGap"));
        }
        d.put("executionSafety", safety(e, expected));
        // Missing terminal or observations cannot be hidden by successful semantic annotations.
        if (e.trace() == null || !Set.of("RESOLVED", "WAITING_USER", "STOPPED", "FAILED").contains(e.trace().taskStatus()))
            d.put("executionSafety", dim(Verdict.UNJUDGEABLE, "TERMINAL_MISSING"));
        String outcome = d.values().stream().anyMatch(x -> x.verdict() == Verdict.FAIL) ? "FAIL"
                : d.values().stream().anyMatch(x -> x.verdict() == Verdict.UNJUDGEABLE) ? "UNJUDGEABLE" : "PASS";
        return new Result(i, outcome, Collections.unmodifiableMap(d));
    }
    private static Dimension safety(Execution e, RuleAgentTraceEvaluator.Expectation expected) {
        var t = e.trace();
        if (t == null) return dim(Verdict.UNJUDGEABLE, "TRACE_MISSING");
        if (Set.of("FAILED", "STOPPED").contains(t.taskStatus())) {
            var reasons=new ArrayList<String>();reasons.add("EXECUTION_FAILED_OR_STOPPED");
            t.result().issues().forEach(issue->reasons.add("TERMINAL_ISSUE:"+issue.reasonCode()));
            return new Dimension(Verdict.FAIL,List.copyOf(reasons));
        }
        if (t.actions().isEmpty()) return dim(Verdict.UNJUDGEABLE, "EXECUTION_ACTIONS_MISSING");
        if (!("RESOLVED".equals(t.taskStatus()) && Set.of("SUCCEEDED", "INCOMPLETE").contains(t.runState())
                || "WAITING_USER".equals(t.taskStatus()) && "INCOMPLETE".equals(t.runState())))
            return dim(Verdict.UNJUDGEABLE, "TERMINAL_STATE_INVALID");
        // Expectations must be supplied from an independently reviewed contract, never derived from actuals.
        if (expected == null) return dim(Verdict.UNJUDGEABLE, "EXECUTION_CONTRACT_MISSING");
        var graded = RuleAgentTraceEvaluator.evaluate(expected, t);
        Verdict v = switch (graded.verdict()) { case PASS -> Verdict.PASS; case FAIL -> Verdict.FAIL;
            case NOT_EVALUABLE -> Verdict.UNJUDGEABLE; };
        return combine(new Dimension(v, graded.details()), review(e, "executionSafety"));
    }
    private static Dimension review(Execution e, String key) {
        Review r = e.reviews() == null ? null : e.reviews().get(key);
        if (r == null || r.verification() == null || blank(r.reviewer()) || blank(r.reason())
                || blank(r.answerExcerpt()) || blank(e.answer()) || !e.answer().contains(r.answerExcerpt()))
            return dim(Verdict.UNJUDGEABLE, "REVIEW_MISSING_OR_UNANCHORED:" + key);
        return dim(switch (r.verification()) { case SUPPORTED -> Verdict.PASS; case MISMATCH -> Verdict.FAIL;
            case UNKNOWN -> Verdict.UNJUDGEABLE; }, key + ": " + r.reviewer() + ": " + r.reason() + "; excerpt=" + r.answerExcerpt());
    }
    private static Dimension combine(Dimension a, Dimension b) {
        Verdict v = a.verdict() == Verdict.FAIL || b.verdict() == Verdict.FAIL ? Verdict.FAIL
                : a.verdict() == Verdict.UNJUDGEABLE || b.verdict() == Verdict.UNJUDGEABLE ? Verdict.UNJUDGEABLE
                : a.verdict() == Verdict.NOT_APPLICABLE && b.verdict() == Verdict.NOT_APPLICABLE ? Verdict.NOT_APPLICABLE : Verdict.PASS;
        var reasons = new ArrayList<>(a.reasons()); reasons.addAll(b.reasons()); return new Dimension(v, List.copyOf(reasons));
    }
    private static Dimension dim(Verdict v, String reason) { return new Dimension(v, List.of(reason)); }
    private static boolean blank(String s) { return s == null || s.isBlank(); }

    /** Offline entry: dataset, manifest, execution-record array. Missing cases stay in the denominator. */
    public static void main(String[] args) throws Exception {
        if (args.length != 4) throw new IllegalArgumentException("dataset manifest executions reviewed-expectations required");
        var json = new ObjectMapper();
        var dataset = load(Path.of(args[0]), Path.of(args[1]));
        if (!"v1.0".equals(json.readTree(Files.readAllBytes(Path.of(args[0]))).path("datasetVersion").asText()))
            throw new IllegalArgumentException("frozen v1.0 required");
        String sha = json.readTree(Files.readAllBytes(Path.of(args[1]))).path("datasetSha256").asText();
        var records = json.readValue(Files.readAllBytes(Path.of(args[2])), Execution[].class);
        var contracts = json.readValue(Files.readAllBytes(Path.of(args[3])), RuleAgentTraceEvaluator.Expectation[].class);
        Map<String, RuleAgentTraceEvaluator.Expectation> expectations = new HashMap<>();
        for (var contract : contracts) {
            dataset.caseById(contract.caseId());
            if (expectations.putIfAbsent(contract.caseId(), contract) != null)
                throw new IllegalArgumentException("duplicate execution contract");
        }
        Map<String, Result> results = new TreeMap<>();
        for (var record : records) {
            if (record == null || record.identity() == null) throw new IllegalArgumentException("unbound execution");
            String id = record.identity().caseId();
            dataset.caseById(id);
            if (results.containsKey(id)) {
                results.put(id, unavailable(record.identity(), "ERROR", "DUPLICATE_CASE_EXECUTION"));
                continue;
            }
            try {
                results.put(id, evaluate(dataset, sha, record, expectations.get(id)));
            } catch (IllegalArgumentException ex) {
                results.put(id, unavailable(record.identity(), "ERROR", "EXECUTION_INPUT_INVALID"));
            }
        }
        Map<String, Result> report = new TreeMap<>();
        for (String id : dataset.cases().keySet()) report.put(id, results.containsKey(id) ? results.get(id)
                : unavailable(null, "UNJUDGEABLE", "EXECUTION_MISSING"));
        System.out.println(json.writerWithDefaultPrettyPrinter().writeValueAsString(report));
    }
    private static Result unavailable(Identity identity, String outcome, String reason) {
        Map<String, Dimension> dimensions = new LinkedHashMap<>();
        DIMENSIONS.forEach(k -> dimensions.put(k, dim(Verdict.UNJUDGEABLE, reason)));
        return new Result(identity, outcome, Collections.unmodifiableMap(dimensions));
    }
}
