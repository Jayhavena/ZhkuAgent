package com.zhku.knowledge.rulecheck;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import static com.zhku.knowledge.rulecheck.RuleCheckDecision.*;
import static com.zhku.knowledge.rulecheck.RuleCheckDecision.Status.*;
import static com.zhku.knowledge.rulecheck.RuleCheckRuleTemplate.*;

/** 只消费服务端审核规则和已取证快照；不负责自然语言路由，也不接受模型事实正文。 */
public final class RuleCheckDecisionService {
    private final RuleCheckEvidenceVerifier verifier;
    public RuleCheckDecisionService(RuleCheckEvidenceVerifier verifier) { this.verifier = Objects.requireNonNull(verifier); }
    /** 返回前重建本轮快照的内部只读入口，不产生新的检索、模型调用或捕获审计。 */
    public Optional<RuleCheckEvidenceSnapshot> currentSnapshot(java.util.UUID sourceId,java.util.UUID chunkId) {
        return verifier.currentSnapshot(sourceId,chunkId);
    }

    public RuleCheckDecision decide(RuleCheckRuleTemplate template, Map<String,String> suppliedConditions,
                                    List<RuleCheckEvidenceSnapshot> retrieved) {
        Objects.requireNonNull(template, "服务端必须选择规则");
        if (template.reviewState() != ReviewState.APPROVED)
            return insufficient(template, "规则尚未审核或已退役");
        if (suppliedConditions == null || retrieved == null)
            return insufficient(template, "条件或证据输入缺失");
        Map<String,String> values;
        List<RuleCheckEvidenceSnapshot> snapshots;
        try { values = Map.copyOf(suppliedConditions); snapshots = List.copyOf(retrieved); }
        catch (NullPointerException malformed) { return insufficient(template, "条件或证据输入不完整"); }
        var definitions = new HashMap<String,Condition>();
        template.conditions().forEach(condition -> definitions.put(condition.key(), condition));
        var candidates = template.facts().stream().filter(fact -> compatible(fact, values, definitions)).toList();
        var relevantKeys = new HashSet<String>();
        candidates.forEach(fact -> relevantKeys.addAll(fact.when().keySet()));
        for (var condition : template.conditions()) {
            if (!relevantKeys.contains(condition.key())) continue;
            var value = values.get(condition.key());
            if (value != null && !value.isBlank() && !condition.allowedValues().contains(value))
                return insufficient(template, "已提供条件不在本规则覆盖范围：" + condition.key());
        }

        var conflicts = new ArrayList<ConflictEvidence>();
        for (var conflict : template.conflicts()) {
            var sides = new ArrayList<SupportedFact>();
            for (var side : conflict.sides()) {
                var verified = verifyFact(template, side, snapshots);
                // 任一方缺证都不能挑剩下的一方肯定作答，也不能下发未核验的冲突正文。
                if (verified.isEmpty()) return insufficient(template, "冲突证据不完整或当前不可用");
                sides.add(verified.get());
            }
            conflicts.add(new ConflictEvidence(conflict.issue(), sides));
        }
        if (!conflicts.isEmpty()) return new RuleCheckDecision(CONFLICTING_EVIDENCE, template.referenceRevision(),
                List.of(), List.of(), List.of(), conflicts);

        var known = new ArrayList<SupportedFact>();
        var independent = new ArrayList<SupportedFact>();
        var gaps = new ArrayList<>(template.evidenceGaps());
        int compatibleCount = 0;
        for (var fact : candidates) {
            compatibleCount++;
            var verified = verifyFact(template, fact, snapshots);
            if (verified.isEmpty()) {
                gaps.add("必要证据未通过核验：" + fact.id());
                continue;
            }
            if (requirementsKnown(fact, values)) {
                known.add(verified.get());
                if (fact.independentlyAnswerable()) independent.add(verified.get());
            }
        }
        if (compatibleCount == 0) return insufficient(template, "本规则没有覆盖该问题条件的事实");
        if (!gaps.isEmpty()) {
            // 缺资料不能通过重复询问用户掩盖；部分事实必须由审核显式声明可独立成立。
            return new RuleCheckDecision(independent.isEmpty() ? INSUFFICIENT_EVIDENCE : PARTIALLY_ANSWERED,
                    template.referenceRevision(), independent, List.of(), gaps, List.of());
        }
        var questions = new ArrayList<Clarification>();
        for (var condition : template.conditions()) {
            if (!relevantKeys.contains(condition.key())) continue;
            var value = values.get(condition.key());
            if (value == null || value.isBlank()) {
                questions.add(new Clarification(condition.key(), condition.question(), condition.allowedValues()));
                // 先解一个决定性条件，避免同时询问后续可能已被排除分支的专用信息。
                break;
            }
        }
        if (!questions.isEmpty()) return new RuleCheckDecision(NEED_CLARIFICATION, template.referenceRevision(),
                independent, questions, List.of(), List.of());
        if (known.isEmpty()) return insufficient(template, "尚无可以确认的结论");
        return new RuleCheckDecision(ANSWERED, template.referenceRevision(), known, List.of(), List.of(), List.of());
    }

    private Optional<SupportedFact> verifyFact(RuleCheckRuleTemplate rule, Fact fact, List<RuleCheckEvidenceSnapshot> snapshots) {
        var excerpts = new ArrayList<RuleCheckVerifiedExcerpt>();
        for (var binding : fact.evidenceBindings()) {
            if (!rule.referenceRevision().equals(binding.revision())) return Optional.empty();
            var matches = snapshots.stream().filter(e -> binding.sourceId().equals(e.sourceId())
                    && binding.chunkId().equals(e.chunkId())).toList();
            // 相同持久身份的多个本轮别名也有歧义；不能取第一项或接受客户端提供的资格。
            if (matches.size() != 1) return Optional.empty();
            var captured = matches.getFirst();
            if (!binding.revision().equals(captured.revision()) || !binding.checksum().equalsIgnoreCase(captured.checksum()))
                return Optional.empty();
            var span = new RuleCheckEvidenceSpan(captured.citationId(), binding.startInclusive(), binding.endExclusive(), binding.quote());
            var result = verifier.verify(span, snapshots);
            if (!result.accepted()) return Optional.empty();
            excerpts.add(result.excerpt());
        }
        // 输出文字来自审核模板；核验绑定只证明来源一致，不重新推断模板的语义正确性。
        return Optional.of(new SupportedFact(fact.id(), fact.text(), excerpts));
    }
    private static boolean compatible(Fact fact, Map<String,String> values, Map<String,Condition> definitions) {
        // 非法值不能用来消除仍可能适用的规则，再由相关键校验拒绝；否则可绕过必要前提。
        return fact.when().entrySet().stream().allMatch(e -> values.get(e.getKey()) == null
                || values.get(e.getKey()).isBlank()
                || !definitions.get(e.getKey()).allowedValues().contains(values.get(e.getKey()))
                || e.getValue().equals(values.get(e.getKey())));
    }
    private static boolean requirementsKnown(Fact fact, Map<String,String> values) {
        return fact.when().entrySet().stream().allMatch(e -> e.getValue().equals(values.get(e.getKey())));
    }
    private static RuleCheckDecision insufficient(RuleCheckRuleTemplate rule, String reason) {
        return new RuleCheckDecision(INSUFFICIENT_EVIDENCE, rule.referenceRevision(), List.of(), List.of(), List.of(reason), List.of());
    }
}
