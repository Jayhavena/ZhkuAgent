package com.zhku.knowledge.rulecheck;

import java.util.Objects;
import java.util.UUID;

/** 服务端取得的片段快照；不能将学生或模型提供的字段当作资料资格。 */
public record RuleCheckEvidenceSnapshot(String citationId, UUID sourceId, UUID chunkId,
                                        String revision, String checksum, String text, boolean eligible) {
    public RuleCheckEvidenceSnapshot {
        Objects.requireNonNull(citationId);
        Objects.requireNonNull(sourceId);
        Objects.requireNonNull(chunkId);
        Objects.requireNonNull(revision);
        Objects.requireNonNull(checksum);
        Objects.requireNonNull(text);
    }
}
