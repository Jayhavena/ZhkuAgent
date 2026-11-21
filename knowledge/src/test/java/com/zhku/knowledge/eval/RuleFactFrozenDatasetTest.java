package com.zhku.knowledge.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuleFactFrozenDatasetTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static Path directory() {
        Path p = Path.of("").toAbsolutePath();
        while (p != null && !Files.isDirectory(p.resolve("docs/evidence/agent-eval"))) p = p.getParent();
        if (p == null) throw new IllegalStateException("repository missing");
        return p.resolve("docs/evidence/agent-eval/2026-11-17-frozen-gold-v1");
    }

    @Test void frozenVersionLoadsAndRetainsAcceptedScoringSemantics() throws Exception {
        Path d = directory();
        var dataset = RuleFactOfflineScorer.load(d.resolve("rule-fact-check-effect-v1.0.json"), d.resolve("manifest.json"));
        assertThat(dataset.cases()).hasSize(20);
        assertThat(dataset.caseById("RFE-017").requiredFactIds()).containsExactly("schedule-order");
        assertThat(dataset.caseById("RFE-017").optionalFactIds()).containsExactly("schedule-previous-semester");
        assertThat(RuleFactOfflineScorer.citations(dataset.caseById("RFE-020"), java.util.List.of()))
                .isEqualTo(RuleFactOfflineScorer.Verdict.NOT_APPLICABLE);
        Path root = d.getParent().getParent().getParent().getParent();
        var input = JSON.readTree(root.resolve("knowledge/src/test/resources/agent-effect-eval/offline-review-examples.json").toFile());
        var results = RuleFactReviewFlow.review(dataset, input);
        assertThat(results).hasSize(6);
        assertThat(results.get(0).facts()).isEqualTo(RuleFactOfflineScorer.Verdict.PASS);
        assertThat(results.get(2).facts()).isEqualTo(RuleFactOfflineScorer.Verdict.FAIL);
        assertThat(results.get(3).citations()).isEqualTo(RuleFactOfflineScorer.Verdict.NOT_APPLICABLE);
        assertThat(results.get(4).citations()).isEqualTo(RuleFactOfflineScorer.Verdict.FAIL);
        assertThat(results.get(5).citations()).isEqualTo(RuleFactOfflineScorer.Verdict.UNJUDGEABLE);
    }

    @Test void frozenLoaderRejectsUnapprovedOrUnfrozenDataEvenWithUpdatedHash(@TempDir Path tmp) throws Exception {
        Path d = directory();
        for (String mutation : java.util.List.of("review", "status", "reviewer", "timestamp", "manifest", "hash", "version")) {
            ObjectNode data = (ObjectNode) JSON.readTree(d.resolve("rule-fact-check-effect-v1.0.json").toFile());
            ObjectNode manifest = (ObjectNode) JSON.readTree(d.resolve("manifest.json").toFile());
            switch (mutation) {
                case "review" -> ((ObjectNode) data.path("cases").get(0)).put("humanReviewStatus", "PENDING");
                case "status" -> data.put("datasetStatus", "DRAFT_NEEDS_HUMAN_CASE_REVIEW");
                case "reviewer" -> data.put("reviewer", "");
                case "timestamp" -> { data.put("frozenAt", "not-a-time"); manifest.put("frozenAt", "not-a-time"); }
                case "manifest" -> manifest.put("datasetStatus", "DRAFT");
                case "version" -> { data.put("datasetVersion", "v99"); manifest.put("datasetVersion", "v99"); }
                case "hash" -> { }
                default -> throw new AssertionError();
            }
            byte[] bytes = JSON.writeValueAsBytes(data);
            manifest.put("datasetSha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
            if (mutation.equals("hash")) manifest.put("datasetSha256", "0".repeat(64));
            Path file = tmp.resolve("rule-fact-check-effect-v1.0.json"), mf = tmp.resolve("manifest.json");
            Files.write(file, bytes); JSON.writeValue(mf.toFile(), manifest);
            assertThatThrownBy(() -> RuleFactOfflineScorer.load(file, mf))
                    .as(mutation).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
