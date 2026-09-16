-- 动态通知需要学期、时态和替代关系；只复用静态来源表无法阻止旧通知参与当前问答。
ALTER TABLE knowledge_submission
    DROP CONSTRAINT ck_knowledge_submission_family;

ALTER TABLE knowledge_submission
    ADD CONSTRAINT ck_knowledge_submission_family CHECK (
        document_family IN ('STUDENT_HANDBOOK', 'OFFICIAL_NOTICE')
    );

CREATE TABLE official_notice (
    id UUID PRIMARY KEY,
    source_id UUID NOT NULL REFERENCES knowledge_source(id) ON DELETE RESTRICT,
    academic_term VARCHAR(11) NOT NULL,
    notice_type VARCHAR(32) NOT NULL,
    lifecycle_status VARCHAR(24) NOT NULL,
    valid_from TIMESTAMPTZ NOT NULL,
    valid_to TIMESTAMPTZ NOT NULL,
    official_url TEXT NOT NULL,
    snapshot_storage_key VARCHAR(160) NOT NULL,
    snapshot_checksum CHAR(64) NOT NULL,
    snapshot_captured_at TIMESTAMPTZ NOT NULL,
    snapshot_media_type VARCHAR(32) NOT NULL,
    student_visible BOOLEAN NOT NULL DEFAULT FALSE,
    sharing_rights_confirmed BOOLEAN NOT NULL DEFAULT FALSE,
    superseded_by_notice_id UUID REFERENCES official_notice(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_official_notice_term CHECK (
        academic_term ~ '^\d{4}-\d{4}-[123]$'
    ),
    CONSTRAINT ck_official_notice_type CHECK (
        notice_type = 'COURSE_SELECTION'
    ),
    CONSTRAINT ck_official_notice_status CHECK (
        lifecycle_status IN (
            'DRAFT', 'IN_REVIEW', 'PUBLISHED', 'SUPERSEDED', 'EXPIRED',
            'NEEDS_OCR_REVIEW'
        )
    ),
    CONSTRAINT ck_official_notice_window CHECK (valid_from < valid_to),
    CONSTRAINT ck_official_notice_snapshot_key CHECK (
        snapshot_storage_key <> ''
        AND snapshot_storage_key !~ '[\\/]'
    ),
    CONSTRAINT ck_official_notice_snapshot_checksum CHECK (
        snapshot_checksum ~ '^[A-F0-9]{64}$'
    ),
    CONSTRAINT ck_official_notice_media_type CHECK (
        snapshot_media_type IN ('text/html', 'application/pdf')
    ),
    -- 面向学生公开时必须同时具备发布、权利确认和可复现快照，不能只存一个会变化的链接。
    CONSTRAINT ck_official_notice_student_visibility CHECK (
        NOT student_visible
        OR (sharing_rights_confirmed AND lifecycle_status = 'PUBLISHED')
    )
);

-- 同一学期同类通知只能有一个当前版本；发布服务会在一个事务中先替代旧版再写新版。
CREATE UNIQUE INDEX uq_official_notice_current_scope
    ON official_notice (academic_term, notice_type)
    WHERE lifecycle_status = 'PUBLISHED';

CREATE INDEX idx_official_notice_current_lookup
    ON official_notice (
        academic_term, notice_type, valid_from, valid_to
    ) WHERE lifecycle_status = 'PUBLISHED'
      AND student_visible = TRUE
      AND sharing_rights_confirmed = TRUE;

CREATE TABLE official_notice_fact (
    id UUID PRIMARY KEY,
    notice_id UUID NOT NULL REFERENCES official_notice(id) ON DELETE RESTRICT,
    fact_type VARCHAR(32) NOT NULL,
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    official_entry_url TEXT NOT NULL,
    starts_at_confirmed BOOLEAN NOT NULL,
    ends_at_confirmed BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_official_notice_fact_type CHECK (
        fact_type IN (
            'COURSE_SELECTION', 'COURSE_WITHDRAWAL',
            'COURSE_SUPPLEMENT', 'COURSE_CHANGE'
        )
    ),
    -- 半确认的日期不能作为学生可见办理时间。
    CONSTRAINT ck_official_notice_fact_confirmed_window CHECK (
        starts_at_confirmed AND ends_at_confirmed AND starts_at < ends_at
    )
);

CREATE INDEX idx_official_notice_fact_notice ON official_notice_fact (notice_id);
