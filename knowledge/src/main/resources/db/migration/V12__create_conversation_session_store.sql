-- 只保存会话控制状态；回答与资料正文不入表，避免把短期澄清状态扩张为对话内容留存。
CREATE TABLE conversation_session (
    session_id UUID PRIMARY KEY,
    status VARCHAR(16) NOT NULL,
    pending_intent VARCHAR(64),
    pending_field VARCHAR(64),
    expires_at TIMESTAMPTZ NOT NULL,
    revision BIGINT NOT NULL,
    CONSTRAINT ck_conversation_session_status CHECK (
        status IN ('ACTIVE', 'CANCELLED')
    ),
    CONSTRAINT ck_conversation_session_revision CHECK (revision >= 0),
    CONSTRAINT ck_conversation_session_pending_pair CHECK (
        (pending_intent IS NULL) = (pending_field IS NULL)
    ),
    -- 取消是终态；允许保留字段会给晚到写入留下重新激活会话的歧义。
    CONSTRAINT ck_conversation_session_cancelled_pending CHECK (
        status <> 'CANCELLED' OR pending_intent IS NULL
    )
);

CREATE INDEX idx_conversation_session_expires_at
    ON conversation_session (expires_at);
