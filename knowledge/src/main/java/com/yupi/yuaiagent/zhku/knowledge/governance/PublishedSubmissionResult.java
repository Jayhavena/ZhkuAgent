package com.yupi.yuaiagent.zhku.knowledge.governance;

import java.util.Objects;
import java.util.UUID;

/**
 * 本次发布成功后可用于问答验证的投稿和来源编号。
 */
public record PublishedSubmissionResult(UUID submissionId, UUID sourceId) {

    public PublishedSubmissionResult {
        submissionId = Objects.requireNonNull(submissionId, "投稿编号不能为空");
        sourceId = Objects.requireNonNull(sourceId, "来源编号不能为空");
    }
}
