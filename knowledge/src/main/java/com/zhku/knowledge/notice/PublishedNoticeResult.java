package com.zhku.knowledge.notice;

import java.util.Objects;
import java.util.UUID;

/** 官方通知发布完成后返回的可审计标识，不暴露本机文件路径。 */
public record PublishedNoticeResult(
        UUID submissionId,
        UUID sourceId,
        PublishedNotice notice
) {
    public PublishedNoticeResult {
        submissionId = Objects.requireNonNull(submissionId, "投稿编号不能为空");
        sourceId = Objects.requireNonNull(sourceId, "来源编号不能为空");
        notice = Objects.requireNonNull(notice, "通知不能为空");
    }
}
