package com.zhku.knowledge.rulecheck;

import java.util.UUID;

/** 只用于核验器产物，证明摘录来源一致；不证明语义完整、相关或个人适用。 */
public record RuleCheckVerifiedExcerpt(String citationId, UUID sourceId, UUID chunkId,
                                       String revision, String checksum, int startInclusive,
                                       int endExclusive, String text) {
}
