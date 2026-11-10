-- 合成账号身份跨进程稳定；登录Cookie不存这里，也不保存业务Session或密码原文。
CREATE TABLE preview_identity (
    user_id uuid PRIMARY KEY,
    login_name varchar(32) NOT NULL UNIQUE CHECK (login_name ~ '^[a-z][a-z0-9-]{2,31}$'),
    -- 合成账号只采用有界BCrypt成本，避免异常数据库值把身份线程占用到无穷等待。
    password_hash varchar(60) NOT NULL CHECK (password_hash ~ '^\$2[aby]\$(10|11|12)\$[./A-Za-z0-9]{53}$'),
    role varchar(16) NOT NULL CHECK (role IN ('USER','MAINTAINER')),
    enabled boolean NOT NULL DEFAULT true,
    auth_revision bigint NOT NULL DEFAULT 0 CHECK (auth_revision >= 0),
    created_at timestamptz NOT NULL DEFAULT clock_timestamp()
);

CREATE FUNCTION preview_identity_update_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.user_id IS DISTINCT FROM OLD.user_id THEN
        RAISE EXCEPTION '身份编号不可改写' USING ERRCODE = '23514';
    END IF;
    IF NEW.login_name IS DISTINCT FROM OLD.login_name OR NEW.password_hash IS DISTINCT FROM OLD.password_hash
       OR NEW.role IS DISTINCT FROM OLD.role OR NEW.enabled IS DISTINCT FROM OLD.enabled THEN
        IF OLD.auth_revision = 9223372036854775807 OR NEW.auth_revision <> OLD.auth_revision + 1 THEN
            RAISE EXCEPTION '身份变更必须递增版本且不可溢出' USING ERRCODE = '23514';
        END IF;
    ELSIF NEW.auth_revision IS DISTINCT FROM OLD.auth_revision THEN
        IF OLD.auth_revision = 9223372036854775807 OR NEW.auth_revision <> OLD.auth_revision + 1 THEN
            RAISE EXCEPTION '身份版本只能逐次递增' USING ERRCODE = '23514';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER preview_identity_update_guard BEFORE UPDATE ON preview_identity
    FOR EACH ROW EXECUTE FUNCTION preview_identity_update_guard();
