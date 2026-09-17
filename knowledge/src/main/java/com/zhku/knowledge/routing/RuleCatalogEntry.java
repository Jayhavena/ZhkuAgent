package com.zhku.knowledge.routing;

import java.util.List;
import java.util.Objects;

/**
 * 规则目录项：一条可被路由命中的确定性规则的元数据声明。
 *
 * <p>目录项只声明"回答这条规则需要什么"，不包含回答内容本身：
 * 所需事实、证据范围、风险等级和转人工条件缺一不可——缺少任一声明的规则
 * 不允许进入目录，防止高风险结论在证据边界不清的情况下被直接给出。</p>
 */
public record RuleCatalogEntry(
        String ruleId,
        String topic,
        List<String> requiredFacts,
        String evidenceScope,
        RuleRisk risk,
        String handoffCondition
) {

    public RuleCatalogEntry {
        ruleId = requireText(ruleId, "规则标识不能为空");
        topic = requireText(topic, "规则主题不能为空");
        Objects.requireNonNull(requiredFacts, "所需事实列表不能为空");
        if (requiredFacts.isEmpty()) {
            // 没有声明所需事实的规则无法判断"证据是否足够"，不允许入目录。
            throw new IllegalArgumentException("所需事实至少声明一项");
        }
        requiredFacts = List.copyOf(requiredFacts);
        evidenceScope = requireText(evidenceScope, "证据范围不能为空");
        Objects.requireNonNull(risk, "风险等级不能为空");
        handoffCondition = requireText(handoffCondition, "转人工条件不能为空");
    }

    private static String requireText(String value, String message) {
        Objects.requireNonNull(value, message);
        String trimmed = value.strip();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return trimmed;
    }
}
