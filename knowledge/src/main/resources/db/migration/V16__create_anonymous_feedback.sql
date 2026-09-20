CREATE TABLE anonymous_feedback_event (
    guide_identifier VARCHAR(128) NOT NULL,
    guide_version VARCHAR(128) NOT NULL,
    feedback_reason VARCHAR(32) NOT NULL CHECK (
        feedback_reason IN ('NOT_HELPFUL', 'INCORRECT', 'OUTDATED', 'MISSING_INFORMATION')
    ),
    anonymous_session_hash CHAR(64) NOT NULL,
    dedupe_key CHAR(64) PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    CHECK (expires_at > created_at)
);

CREATE INDEX idx_anonymous_feedback_event_expires_at
    ON anonymous_feedback_event (expires_at);

CREATE TABLE anonymous_feedback_aggregate (
    guide_identifier VARCHAR(128) NOT NULL,
    guide_version VARCHAR(128) NOT NULL,
    feedback_reason VARCHAR(32) NOT NULL CHECK (
        feedback_reason IN ('NOT_HELPFUL', 'INCORRECT', 'OUTDATED', 'MISSING_INFORMATION')
    ),
    feedback_date DATE NOT NULL,
    feedback_count BIGINT NOT NULL CHECK (feedback_count > 0),
    PRIMARY KEY (guide_identifier, guide_version, feedback_reason, feedback_date)
);
