package com.yupi.yuaiagent.zhku.knowledge.governance;

/**
 * 本机资料投稿从提取到归档的可迁移状态。
 *
 * <p>状态边界放在领域对象中，避免页面或数据库调用方绕过审核流程直接发布资料。</p>
 */
public enum KnowledgeSubmissionStatus {
    DRAFT,
    EXTRACTED,
    IN_REVIEW,
    REJECTED,
    PUBLISHED,
    ARCHIVED;

    public boolean canTransitionTo(KnowledgeSubmissionStatus nextStatus) {
        return switch (this) {
            case DRAFT -> nextStatus == EXTRACTED;
            case EXTRACTED -> nextStatus == IN_REVIEW
                    || nextStatus == REJECTED;
            case IN_REVIEW -> nextStatus == PUBLISHED
                    || nextStatus == REJECTED;
            case PUBLISHED -> nextStatus == ARCHIVED;
            case REJECTED, ARCHIVED -> false;
        };
    }
}
