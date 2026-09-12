package com.yupi.yuaiagent.zhku.knowledge.governance;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 记录本机资料治理操作，服务于流程回溯而非人员身份审计。
 */
public record KnowledgeReviewEvent(
        UUID id,
        UUID submissionId,
        UUID candidateId,
        String actionType,
        String actionNote,
        String actorLabel,
        Instant occurredAt
) {

    public KnowledgeReviewEvent {
        id = Objects.requireNonNull(id, "操作记录编号不能为空");
        submissionId = Objects.requireNonNull(submissionId, "投稿编号不能为空");
        actionType = requireText(actionType, "操作类型不能为空");
        actionNote = requireText(actionNote, "操作说明不能为空");
        actorLabel = requireText(actorLabel, "操作者标签不能为空");
        occurredAt = Objects.requireNonNull(occurredAt, "操作时间不能为空");
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.strip();
    }
}
