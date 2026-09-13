-- 归档资料是历史记录，不应阻止管理员将相同 PDF 作为新一轮审核投稿。
ALTER TABLE knowledge_submission
    DROP CONSTRAINT uq_knowledge_submission_active_checksum;

CREATE UNIQUE INDEX uq_knowledge_submission_active_checksum
    ON knowledge_submission (
        document_family,
        knowledge_scope,
        content_checksum
    )
    WHERE submission_status <> 'ARCHIVED';
