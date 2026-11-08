package com.zhku.knowledge.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RuleFactReviewFlowTest {
    private static Path root() {
        Path p = Path.of("").toAbsolutePath();
        while (p != null && !Files.isDirectory(p.resolve("docs/evidence/agent-eval"))) p = p.getParent();
        if (p == null) throw new IllegalStateException("repository not found");
        return p;
    }
    @Test void offlineExamplesProduceSeparateDimensionsAndReasons() throws Exception {
        Path root = root();
        Path d = root.resolve("docs/evidence/agent-eval/2026-11-08-v3-review-revision");
        var dataset = RuleFactOfflineScorer.load(d.resolve("rule-fact-check-effect-v3-review-draft.json"), d.resolve("manifest-v3-review-draft.json"));
        var input = new ObjectMapper().readTree(root.resolve("knowledge/src/test/resources/agent-effect-eval/offline-review-examples.json").toFile());
        var out = RuleFactReviewFlow.review(dataset, input);
        assertThat(out).hasSize(6);
        assertThat(out.get(0).facts()).isEqualTo(RuleFactOfflineScorer.Verdict.PASS);
        assertThat(out.get(0).citations()).isEqualTo(RuleFactOfflineScorer.Verdict.PASS);
        assertThat(out.get(1).facts()).isEqualTo(RuleFactOfflineScorer.Verdict.PASS);
        assertThat(out.get(2).facts()).isEqualTo(RuleFactOfflineScorer.Verdict.FAIL);
        assertThat(out.get(3).citations()).isEqualTo(RuleFactOfflineScorer.Verdict.NOT_APPLICABLE);
        assertThat(out.get(4).citations()).isEqualTo(RuleFactOfflineScorer.Verdict.FAIL);
        assertThat(out.get(4).citationFailureTag()).isEqualTo("CITATION_MISMATCH");
        assertThat(out.get(5).citations()).isEqualTo(RuleFactOfflineScorer.Verdict.UNJUDGEABLE);
        assertThat(out).allSatisfy(x -> { assertThat(x.factReason()).isNotBlank(); assertThat(x.citationReason()).isNotBlank(); });
    }
    @Test void rejectsInventedFactAnchorsAndUnreviewedCitations() throws Exception {
        Path root = root(), d = root.resolve("docs/evidence/agent-eval/2026-11-08-v3-review-revision");
        var dataset = RuleFactOfflineScorer.load(d.resolve("rule-fact-check-effect-v3-review-draft.json"), d.resolve("manifest-v3-review-draft.json"));
        var input = (com.fasterxml.jackson.databind.node.ObjectNode) new ObjectMapper().readTree(root.resolve("knowledge/src/test/resources/agent-effect-eval/offline-review-examples.json").toFile());
        var fact = (com.fasterxml.jackson.databind.node.ObjectNode) input.withArray("examples").get(0).withArray("matchedFacts").get(0);
        String excerpt = fact.path("answerExcerpt").asText();
        fact.put("answerExcerpt", "not in answer");
        assertThatThrownBy(() -> RuleFactReviewFlow.review(dataset, input)).isInstanceOf(IllegalArgumentException.class);
        fact.put("answerExcerpt", excerpt);
        ((com.fasterxml.jackson.databind.node.ObjectNode) input.withArray("examples").get(0)).put("citationsComplete", false);
        assertThatThrownBy(() -> RuleFactReviewFlow.review(dataset, input)).isInstanceOf(IllegalArgumentException.class);
    }
}
