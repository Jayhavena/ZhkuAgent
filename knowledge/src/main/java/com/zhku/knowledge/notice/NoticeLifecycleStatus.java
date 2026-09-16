package com.zhku.knowledge.notice;

/** 通知从审核到失效的可见生命周期。 */
public enum NoticeLifecycleStatus {
    DRAFT,
    IN_REVIEW,
    PUBLISHED,
    SUPERSEDED,
    EXPIRED,
    NEEDS_OCR_REVIEW
}
