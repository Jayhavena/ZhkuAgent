-- PUBLISHED 仅表示资料已经进入内部知识库，不等同于可以向学生公开。
-- 默认保持 INTERNAL_ONLY，避免历史资料在新增学生接口时被意外暴露。
ALTER TABLE knowledge_source
    ADD COLUMN visibility_scope VARCHAR(24) NOT NULL DEFAULT 'INTERNAL_ONLY',
    ADD COLUMN publication_scope VARCHAR(100) NOT NULL DEFAULT '本地验证',
    ADD COLUMN review_notice TEXT NOT NULL DEFAULT '资料仅限内部验证，公开前需完成复核。',
    ADD COLUMN sharing_rights_confirmed BOOLEAN NOT NULL DEFAULT FALSE,
    ADD CONSTRAINT ck_knowledge_source_visibility_scope CHECK (
        visibility_scope IN ('INTERNAL_ONLY', 'STUDENT_VISIBLE')
    ),
    -- 用户上传资料可以没有官方链接，但没有确认分享权限时不能进入学生可见范围。
    ADD CONSTRAINT ck_knowledge_source_student_visibility CHECK (
        visibility_scope <> 'STUDENT_VISIBLE'
        OR (publication_status = 'PUBLISHED' AND sharing_rights_confirmed)
    );

-- 学生端检索会同时按发布状态、可见范围和分享权限过滤。
CREATE INDEX idx_knowledge_source_student_visibility
    ON knowledge_source (publication_status, visibility_scope, sharing_rights_confirmed);
