-- 旧六字段及约束保留，旧行永远NULL；只有新owned INSERT可绑定可信身份。
ALTER TABLE conversation_session
    ADD COLUMN owner_user_id UUID REFERENCES preview_identity(user_id) ON DELETE RESTRICT;

CREATE INDEX idx_conversation_session_owned_expiry
    ON conversation_session(owner_user_id, expires_at, session_id);

CREATE FUNCTION conversation_session_ownership_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.owner_user_id IS DISTINCT FROM OLD.owner_user_id
        OR NEW.session_id IS DISTINCT FROM OLD.session_id THEN
        RAISE EXCEPTION '会话编号及归属不可改写' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER conversation_session_ownership_guard
    BEFORE UPDATE ON conversation_session
    FOR EACH ROW EXECUTE FUNCTION conversation_session_ownership_guard();
