package com.zhku.knowledge.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import static com.zhku.knowledge.eval.RuleFactOfflineScorer.*;

/** 离线模拟：只消费人工标注，不能据此声称自动识别事实或引用。 */
public final class RuleFactReviewFlow {
    private static final ObjectMapper JSON = new ObjectMapper();
    private RuleFactReviewFlow() {}
    public record Result(String exampleId, String caseId, Verdict facts, String factReason,
                         Verdict citations, String citationReason, String citationFailureTag,
                         List<String> annotations, String boundary) {}

    public static List<Result> review(Dataset dataset, JsonNode input) {
        if (input.path("schemaVersion").asInt(-1) != 1 || !"SYNTHETIC_MANUALLY_ANNOTATED".equals(input.path("origin").asText())
                || !input.path("examples").isArray()) throw new IllegalArgumentException("invalid synthetic input");
        List<Result> results = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (JsonNode item : input.path("examples")) {
            String id = text(item, "exampleId"), caseId = text(item, "caseId"), answer = text(item, "answer");
            if (!ids.add(id)) throw new IllegalArgumentException("duplicate example");
            Case c = dataset.caseById(caseId);
            if (!item.path("factsComplete").isBoolean() || !item.path("citationsComplete").asBoolean(false))
                throw new IllegalArgumentException("missing complete citation review or fact review status");
            if (!item.path("matchedFacts").isArray() || !item.path("citations").isArray()) throw new IllegalArgumentException("missing annotations");
            Set<String> facts = new HashSet<>();
            List<String> annotations = new ArrayList<>();
            for (JsonNode f : item.path("matchedFacts")) {
                String factId = text(f, "factId"), excerpt = text(f, "answerExcerpt"), reason = text(f, "reason");
                if (!answer.contains(excerpt) || !facts.add(factId)
                        || !(c.requiredFactIds().contains(factId) || c.optionalFactIds().contains(factId)))
                    throw new IllegalArgumentException("invalid fact annotation");
                annotations.add("事实 " + factId + "：" + reason + "；回答片段：" + excerpt);
            }
            List<Citation> citations = new ArrayList<>();
            for (JsonNode cite : item.path("citations")) {
                String locator = text(cite, "locator"), excerpt = text(cite, "answerExcerpt"), reason = text(cite, "reason");
                if (!answer.contains(excerpt) || !excerpt.contains(locator)) throw new IllegalArgumentException("invalid citation anchor");
                Verification verification = Verification.valueOf(text(cite, "verification"));
                citations.add(new Citation(locator, verification));
                annotations.add("引用 " + locator + " [" + verification + "]：" + reason + "；回答片段：" + excerpt);
            }
            Verdict fv = RuleFactOfflineScorer.facts(c, facts, item.path("factsComplete").asBoolean());
            Verdict cv = RuleFactOfflineScorer.citations(c, citations);
            String fr = switch (fv) {
                case PASS -> "标注已覆盖全部必答事实；可选事实不计为必答。";
                case FAIL -> "标注完成但未覆盖全部必答事实。";
                case UNJUDGEABLE -> "必答事实尚未完整核验。";
                case NOT_APPLICABLE -> "本题没有必答事实，事实覆盖维度不适用。";
            };
            String cr = switch (cv) {
                case PASS -> "提供的引用及必需定位符均由标注者标为支持。";
                case FAIL -> "必需引用缺失，或有引用被标注为错误。";
                case UNJUDGEABLE -> "至少一个引用的支持关系无法核验。";
                case NOT_APPLICABLE -> "本题无需引用且标注未发现引用。";
            };
            String tag = citations.stream().anyMatch(x -> x.verification() == Verification.MISMATCH) ? "CITATION_MISMATCH" : null;
            results.add(new Result(id, caseId, fv, fr, cv, cr, tag, List.copyOf(annotations),
                    "合成回答与人工标注；只评估两个维度，不代表其他维度或整题通过"));
        }
        return List.copyOf(results);
    }
    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isTextual() || value.asText().isBlank()) throw new IllegalArgumentException("missing " + field);
        return value.asText();
    }
    /** Args: dataset JSON, manifest JSON, annotated examples JSON. Output is a JSON array. */
    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("dataset manifest examples required");
        Dataset dataset = RuleFactOfflineScorer.load(Path.of(args[0]), Path.of(args[1]));
        JsonNode input = JSON.readTree(Files.readAllBytes(Path.of(args[2])));
        byte[] output = (JSON.writerWithDefaultPrettyPrinter().writeValueAsString(review(dataset, input)) + "\n")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        System.out.write(output);
    }
}
