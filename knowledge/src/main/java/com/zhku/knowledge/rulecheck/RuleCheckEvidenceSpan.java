package com.zhku.knowledge.rulecheck;

/** 不可信的摘录提议。位置按UTF-16计数，起点包含、终点不包含；非法值由核验器拒绝。 */
public record RuleCheckEvidenceSpan(String citationId, int startInclusive, int endExclusive, String quote) {
}
