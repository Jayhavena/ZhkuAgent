-- 未审核资料必须与学生问答使用的已发布知识表隔离。
CREATE TABLE knowledge_submission (
    id UUID PRIMARY KEY,
    document_family VARCHAR(64) NOT NULL,
    knowledge_scope VARCHAR(100) NOT NULL,
    title VARCHAR(300) NOT NULL,
    source_version VARCHAR(100) NOT NULL,
    source_description TEXT NOT NULL,
    source_url TEXT,
    applicable_scope TEXT NOT NULL,
    publication_date DATE,
    publication_date_confirmed BOOLEAN NOT NULL DEFAULT FALSE,
    effective_date DATE,
    effective_date_confirmed BOOLEAN NOT NULL DEFAULT FALSE,
    storage_key VARCHAR(160) NOT NULL,
    original_file_name VARCHAR(500) NOT NULL,
    content_checksum CHAR(64) NOT NULL,
    submission_status VARCHAR(16) NOT NULL,
    review_notice TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_knowledge_submission_family CHECK (
        document_family = 'STUDENT_HANDBOOK'
    ),
    CONSTRAINT ck_knowledge_submission_scope CHECK (
        knowledge_scope ~ '^[A-Z0-9_]+$'
    ),
    CONSTRAINT ck_knowledge_submission_status CHECK (
        submission_status IN (
            'DRAFT', 'EXTRACTED', 'IN_REVIEW',
            'REJECTED', 'PUBLISHED', 'ARCHIVED'
        )
    ),
    CONSTRAINT ck_knowledge_submission_publication_date CHECK (
        publication_date IS NOT NULL OR NOT publication_date_confirmed
    ),
    CONSTRAINT ck_knowledge_submission_effective_date CHECK (
        effective_date IS NOT NULL OR NOT effective_date_confirmed
    ),
    CONSTRAINT uq_knowledge_submission_active_checksum UNIQUE (
        document_family, knowledge_scope, content_checksum
    )
);

-- 候选片段属于审核暂存区，审核通过前不会写入 knowledge_chunk。
CREATE TABLE knowledge_candidate (
    id UUID PRIMARY KEY,
    submission_id UUID NOT NULL,
    start_page_number INTEGER NOT NULL,
    end_page_number INTEGER NOT NULL,
    raw_text TEXT NOT NULL,
    edited_text TEXT,
    section_title TEXT,
    source_locator VARCHAR(500),
    review_status VARCHAR(24) NOT NULL DEFAULT 'PENDING_REVIEW',
    review_note TEXT,
    discard_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_knowledge_candidate_submission FOREIGN KEY (submission_id)
        REFERENCES knowledge_submission (id) ON DELETE RESTRICT,
    CONSTRAINT ck_knowledge_candidate_page_range CHECK (
        start_page_number >= 1
        AND end_page_number >= start_page_number
    ),
    CONSTRAINT ck_knowledge_candidate_status CHECK (
        review_status IN ('PENDING_REVIEW', 'APPROVED', 'DISCARDED')
    )
);

-- 本机操作记录用于流程回溯，不被视为可认证的人员审计。
CREATE TABLE knowledge_review_event (
    id UUID PRIMARY KEY,
    submission_id UUID NOT NULL,
    candidate_id UUID,
    action_type VARCHAR(48) NOT NULL,
    action_note TEXT NOT NULL,
    actor_label VARCHAR(100) NOT NULL DEFAULT '本机管理员',
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_knowledge_review_event_submission FOREIGN KEY (submission_id)
        REFERENCES knowledge_submission (id) ON DELETE RESTRICT,
    CONSTRAINT fk_knowledge_review_event_candidate FOREIGN KEY (candidate_id)
        REFERENCES knowledge_candidate (id) ON DELETE RESTRICT
);

CREATE INDEX idx_knowledge_submission_status
    ON knowledge_submission (submission_status, updated_at DESC);
CREATE INDEX idx_knowledge_candidate_submission
    ON knowledge_candidate (submission_id, start_page_number, end_page_number);
CREATE INDEX idx_knowledge_review_event_submission
    ON knowledge_review_event (submission_id, occurred_at ASC);
