-- 已发布来源增加替换范围，防止新领域资料误归档公共选修基线。
ALTER TABLE knowledge_source
    ADD COLUMN document_family VARCHAR(64) NOT NULL DEFAULT 'LEGACY',
    ADD COLUMN knowledge_scope VARCHAR(100) NOT NULL DEFAULT 'LEGACY';

CREATE INDEX idx_knowledge_source_version_scope
    ON knowledge_source (
        document_family,
        knowledge_scope,
        publication_status
    );

-- 旧的公共选修测试资料存在时才映射；空数据库中更新零行是预期行为。
UPDATE knowledge_source
SET document_family = 'STUDENT_HANDBOOK',
    knowledge_scope = 'PUBLIC_ELECTIVE',
    updated_at = CURRENT_TIMESTAMP
WHERE id = '2a04fd61-529e-4e6b-8ad6-9666f21a5037';
