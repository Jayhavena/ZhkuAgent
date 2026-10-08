-- 与ADMITTED在同一事务保存；仅保留关联和摘要，不保存任务输入或恢复原文。
CREATE TABLE campus_agent_run_submission (
    owner_user_id uuid NOT NULL REFERENCES preview_identity(user_id) ON DELETE RESTRICT,
    task_kind varchar(32) NOT NULL CHECK (task_kind='CAMPUS_CREDIT_SUMMARY'),
    submission_key uuid NOT NULL,
    run_id uuid NOT NULL UNIQUE REFERENCES campus_agent_run_archive(run_id) ON DELETE RESTRICT,
    fingerprint_version integer NOT NULL CHECK (fingerprint_version=1),
    request_sha256 varchar(64) NOT NULL CHECK (request_sha256 ~ '^[0-9a-f]{64}$'),
    accepted_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    PRIMARY KEY (owner_user_id,task_kind,submission_key)
);

CREATE FUNCTION campus_submission_reference_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'UPDATE' THEN
        RAISE EXCEPTION '提交事实不可改写' USING ERRCODE = '23514';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM campus_agent_run_archive a WHERE a.run_id=NEW.run_id
                   AND a.owner_user_id=NEW.owner_user_id AND a.task_kind=NEW.task_kind) THEN
        RAISE EXCEPTION '提交关联归属不一致' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER campus_submission_reference_guard BEFORE INSERT OR UPDATE ON campus_agent_run_submission
    FOR EACH ROW EXECUTE FUNCTION campus_submission_reference_guard();
