package com.zhku.knowledge.rulecheck;

import java.util.List;

/** 业务判断与执行状态分开；HTTP只投影决策，不允许请求声明资料资格。 */
public record RuleCheckDecision(Status status, String referenceRevision, List<SupportedFact> confirmedFacts,
                                List<Clarification> clarificationQuestions, List<String> unknownItems,
                                List<ConflictEvidence> conflicts) {
    public RuleCheckDecision {
        if (status == null || referenceRevision == null || referenceRevision.isBlank())
            throw new IllegalArgumentException("判断必须明确状态和引用版本");
        confirmedFacts = List.copyOf(confirmedFacts); clarificationQuestions = List.copyOf(clarificationQuestions);
        unknownItems = List.copyOf(unknownItems); conflicts = List.copyOf(conflicts);
        // 不允许调用者把缺证/冲突标成完整答案，或在缺证状态夹带未经确认的肯定正文。
        boolean valid = switch (status) {
            case ANSWERED -> !confirmedFacts.isEmpty() && unknownItems.isEmpty() && clarificationQuestions.isEmpty() && conflicts.isEmpty();
            case PARTIALLY_ANSWERED -> !confirmedFacts.isEmpty() && !unknownItems.isEmpty() && clarificationQuestions.isEmpty() && conflicts.isEmpty();
            case NEED_CLARIFICATION -> !clarificationQuestions.isEmpty() && conflicts.isEmpty();
            case INSUFFICIENT_EVIDENCE -> confirmedFacts.isEmpty() && clarificationQuestions.isEmpty() && conflicts.isEmpty() && !unknownItems.isEmpty();
            case CONFLICTING_EVIDENCE -> confirmedFacts.isEmpty() && clarificationQuestions.isEmpty() && !conflicts.isEmpty();
        };
        if (!valid) throw new IllegalArgumentException("业务状态与判断内容不一致");
    }
    public enum Status { ANSWERED, PARTIALLY_ANSWERED, NEED_CLARIFICATION, INSUFFICIENT_EVIDENCE, CONFLICTING_EVIDENCE }
    public record SupportedFact(String id, String text, List<RuleCheckVerifiedExcerpt> evidence) {
        public SupportedFact {
            if (id == null || id.isBlank() || text == null || text.isBlank()) throw new IllegalArgumentException("事实不能为空");
            evidence = List.copyOf(evidence);
            if (evidence.isEmpty()) throw new IllegalArgumentException("输出事实必须有核验依据");
        }
    }
    public record Clarification(String key, String question, List<String> allowedValues) {
        public Clarification { allowedValues = List.copyOf(allowedValues); }
    }
    public record ConflictEvidence(String issue, List<SupportedFact> sides) {
        public ConflictEvidence { sides = List.copyOf(sides); }
    }
}
