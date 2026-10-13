package com.zhku.knowledge.rulecheck;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Objects;

/** 服务端审核规则；相关性、条件完整性与独立性由审核负责，不能由学生或模型自证。 */
public record RuleCheckRuleTemplate(String ruleId, String referenceRevision, ReviewState reviewState,
                                    String reviewer, LocalDate reviewedAt, List<Fact> facts,
                                    List<Condition> conditions, List<String> evidenceGaps,
                                    List<Conflict> conflicts, List<ConditionalEvidenceGap> conditionalEvidenceGaps) {
    /** 旧模板的缺口仍为全局缺口；不能默默把旧规则改成仅在部分分支阻断。 */
    public RuleCheckRuleTemplate(String ruleId, String referenceRevision, ReviewState reviewState,
                                String reviewer, LocalDate reviewedAt, List<Fact> facts,
                                List<Condition> conditions, List<String> evidenceGaps, List<Conflict> conflicts) {
        this(ruleId,referenceRevision,reviewState,reviewer,reviewedAt,facts,conditions,evidenceGaps,conflicts,List.of());
    }
    public RuleCheckRuleTemplate {
        requireText(ruleId); requireText(referenceRevision); Objects.requireNonNull(reviewState);
        if (reviewState == ReviewState.APPROVED && (reviewer == null || reviewer.isBlank() || reviewedAt == null))
            throw new IllegalArgumentException("已审规则必须保留实际审核责任");
        facts = List.copyOf(facts); conditions = List.copyOf(conditions);
        evidenceGaps = List.copyOf(evidenceGaps); conflicts = List.copyOf(conflicts);
        conditionalEvidenceGaps = List.copyOf(conditionalEvidenceGaps);
        evidenceGaps.forEach(RuleCheckRuleTemplate::requireText);
        var definitions = new LinkedHashMap<String,Condition>();
        for (var condition : conditions)
            if (definitions.putIfAbsent(condition.key(), condition) != null)
                throw new IllegalArgumentException("决定性条件不能重复定义");
        var ids = new HashSet<String>();
        for (var fact : facts) {
            if (!ids.add(fact.id())) throw new IllegalArgumentException("事实标识不能重复");
            for (var requirement : fact.when().entrySet()) {
                var condition = definitions.get(requirement.getKey());
                if (condition == null || !condition.allowedValues().contains(requirement.getValue()))
                    throw new IllegalArgumentException("事实前提必须属于已声明条件及允许值");
            }
        }
        for (var gap : conditionalEvidenceGaps)
            for (var requirement : gap.when().entrySet()) {
                var condition = definitions.get(requirement.getKey());
                if (condition == null || !condition.allowedValues().contains(requirement.getValue()))
                    throw new IllegalArgumentException("条件缺口必须属于已声明条件及允许值");
            }
    }
    public enum ReviewState { DRAFT, APPROVED, RETIRED }
    /** 只表达经审核的分支资料缺口；问题范围仍由服务端目录项限定，不能由模型提交缺口。 */
    public record ConditionalEvidenceGap(String reason, Map<String,String> when) {
        public ConditionalEvidenceGap {
            requireText(reason); when=Map.copyOf(when);
            if(when.isEmpty())throw new IllegalArgumentException("无条件资料缺口必须使用全局缺口");
            when.forEach((key,value)->{requireText(key);requireText(value);});
        }
    }
    public record EvidenceBinding(UUID sourceId, UUID chunkId, String revision, String checksum,
                                  int startInclusive, int endExclusive, String quote) {
        public EvidenceBinding {
            Objects.requireNonNull(sourceId); Objects.requireNonNull(chunkId);
            requireText(revision); requireText(checksum); requireText(quote);
        }
    }
    public record Fact(String id, String text, boolean independentlyAnswerable, Map<String,String> when,
                       List<EvidenceBinding> evidenceBindings) {
        public Fact {
            requireText(id); requireText(text);
            when = Map.copyOf(when); evidenceBindings = List.copyOf(evidenceBindings);
            if (evidenceBindings.isEmpty()) throw new IllegalArgumentException("事实必须绑定必要证据");
            when.forEach((key,value)->{requireText(key); requireText(value);});
        }
    }
    public record Condition(String key, String question, List<String> allowedValues) {
        public Condition {
            requireText(key); requireText(question); allowedValues = List.copyOf(allowedValues);
            if (allowedValues.isEmpty() || new HashSet<>(allowedValues).size() != allowedValues.size())
                throw new IllegalArgumentException("条件必须有不重复的允许值");
            allowedValues.forEach(RuleCheckRuleTemplate::requireText);
        }
    }
    public record Conflict(String issue, List<Fact> sides) {
        public Conflict {
            requireText(issue); sides = List.copyOf(sides);
            if (sides.size() < 2 || sides.stream().map(Fact::id).distinct().count() != sides.size())
                throw new IllegalArgumentException("冲突必须有至少两个明确且不同的证据侧");
            // 冲突须在选定规则的适用范围内；条件冲突应先由范围解析消解，不能无条件下发。
            if (sides.stream().anyMatch(side -> !side.when().isEmpty()))
                throw new IllegalArgumentException("冲突侧不能携带尚未消解的条件分支");
        }
    }
    private static void requireText(String text) {
        if (text == null || text.isBlank()) throw new IllegalArgumentException("规则字段不能缺失或为空白");
    }
}
