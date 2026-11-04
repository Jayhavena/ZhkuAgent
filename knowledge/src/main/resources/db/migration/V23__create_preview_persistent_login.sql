-- 只保存高熵随机凭据的SHA-256摘要；数据库泄漏不能直接构造Cookie。
CREATE TABLE preview_persistent_login (
    token_hash char(64) PRIMARY KEY CHECK (token_hash ~ '^[0-9a-f]{64}$'),
    user_id uuid NOT NULL REFERENCES preview_identity(user_id) ON DELETE CASCADE,
    auth_revision bigint NOT NULL CHECK (auth_revision >= 0),
    expires_at timestamptz NOT NULL
);
CREATE INDEX preview_persistent_login_user_idx ON preview_persistent_login(user_id);
CREATE INDEX preview_persistent_login_expiry_idx ON preview_persistent_login(expires_at);
