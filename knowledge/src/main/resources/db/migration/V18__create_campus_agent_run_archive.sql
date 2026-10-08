-- owner 行序列化参与者的准入、收尾和接管；session advisory lock 仍由独立连接持有。
CREATE TABLE campus_agent_runtime_owner (
    owner_key varchar(64) PRIMARY KEY CHECK (owner_key = 'campus-credit-preview'),
    epoch bigint NOT NULL CHECK (epoch >= 0),
    instance_id uuid NOT NULL,
    claimed_at timestamptz NOT NULL
);

CREATE TABLE campus_agent_run_archive (
    run_id uuid PRIMARY KEY,
    owner_instance_id uuid NOT NULL,
    owner_epoch bigint NOT NULL CHECK (owner_epoch >= 0),
    revision bigint NOT NULL DEFAULT 0 CHECK (revision >= 0),
    task_kind varchar(32) NOT NULL DEFAULT 'CAMPUS_CREDIT_SUMMARY'
        CHECK (task_kind = 'CAMPUS_CREDIT_SUMMARY'),
    archive_status varchar(16) NOT NULL CHECK (archive_status IN ('ADMITTED','FINALIZED','INTERRUPTED')),
    schema_version integer NOT NULL CHECK (schema_version > 0),
    writer_version varchar(64) NOT NULL CHECK (writer_version ~ '^[A-Za-z0-9_.-]{1,64}$'),
    admitted_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    execution_finished_at timestamptz,
    closed_at timestamptz,
    retained_until timestamptz,
    execution_state varchar(16),
    stop_reason varchar(32) CHECK (stop_reason IN ('NONE','USER_REQUEST','SSE_TIMEOUT','RUN_TIMEOUT','CLIENT_DISCONNECTED','SEND_FAILURE')),
    end_reason varchar(32) CHECK (end_reason IN ('COMPLETED','STEP_LIMIT','BLOCKED','DOMAIN_DECISION','STOPPED','ERROR')),
    current_step integer CHECK (current_step >= 0),
    error_category varchar(32) CHECK (error_category IN ('MODEL_FAILURE','TOOL_FAILURE','EXECUTOR_REJECTED','SEND_FAILURE',
        'CLEANUP_FAILURE','EXECUTION_FAILURE','INTERNAL_FAILURE','DOMAIN_FAILURE')),
    error_code varchar(64) CHECK (error_code ~ '^[A-Z][A-Z0-9_]{0,63}$'),
    business_status varchar(32) CHECK (business_status IN ('ANSWERED','NEED_CLARIFICATION','HANDOFF','CANCELLED')),
    failure_category varchar(64) CHECK (failure_category ~ '^[A-Z][A-Z0-9_]{0,63}$'),
    cleanup_outcome varchar(16) CHECK (cleanup_outcome IN ('SUCCESS','FAILED')),
    -- json 保留原序列化文本的字节计数；不建立 source 的级联外键，以免撤回同时删除运行事实。
    result_payload json CHECK (octet_length(result_payload::text) <= 16384),
    payload_disposition varchar(24) CHECK (payload_disposition IN ('STORED','NOT_APPLICABLE','SUPPRESSED')),
    payload_reason varchar(40) CHECK (payload_reason IN ('NONE','NO_RESULT_FOR_OUTCOME','SOURCE_REFERENCE_MISSING',
        'SOURCE_UNAVAILABLE','SOURCE_VERSION_CHANGED','SOURCE_NOT_VISIBLE','PAYLOAD_TOO_LARGE','UNSUPPORTED_SCHEMA')),
    interruption_reason varchar(48),
    CONSTRAINT campus_archive_error_pair CHECK ((error_category IS NULL) = (error_code IS NULL)),
    -- 显式 IS NULL/IS NOT NULL，避免 SQL 三值逻辑让残缺 FINALIZED 通过 CHECK。
    CONSTRAINT campus_archive_state_facts CHECK (
        (archive_status = 'ADMITTED'
         AND execution_state IS NULL AND stop_reason IS NULL AND end_reason IS NULL AND current_step IS NULL
         AND error_category IS NULL AND error_code IS NULL AND business_status IS NULL AND failure_category IS NULL
         AND cleanup_outcome IS NULL AND execution_finished_at IS NULL AND closed_at IS NULL AND retained_until IS NULL
         AND result_payload IS NULL AND payload_disposition IS NULL AND payload_reason IS NULL AND interruption_reason IS NULL)
        OR
        (archive_status = 'FINALIZED'
         AND execution_state IS NOT NULL AND execution_state IN ('SUCCEEDED','INCOMPLETE','FAILED','CANCELLED')
         AND stop_reason IS NOT NULL AND end_reason IS NOT NULL AND current_step IS NOT NULL
         AND failure_category IS NOT NULL AND cleanup_outcome IS NOT NULL AND execution_finished_at IS NOT NULL
         AND closed_at IS NOT NULL AND retained_until IS NOT NULL AND retained_until > closed_at
         AND payload_disposition IS NOT NULL AND payload_reason IS NOT NULL AND interruption_reason IS NULL
         AND (
             (execution_state = 'SUCCEEDED' AND stop_reason = 'NONE' AND end_reason = 'COMPLETED' AND error_category IS NULL)
             OR (execution_state = 'INCOMPLETE' AND stop_reason = 'NONE' AND end_reason IN ('STEP_LIMIT','BLOCKED','DOMAIN_DECISION') AND error_category IS NULL)
             OR (execution_state = 'CANCELLED' AND stop_reason <> 'NONE' AND end_reason = 'STOPPED' AND error_category IS NULL)
             OR (execution_state = 'FAILED' AND end_reason = 'ERROR' AND error_category IS NOT NULL AND error_code IS NOT NULL)
         )
         AND (cleanup_outcome = 'SUCCESS' OR (cleanup_outcome = 'FAILED' AND error_category IS NOT NULL AND error_category = 'CLEANUP_FAILURE'))
         AND (
             (payload_disposition = 'STORED' AND result_payload IS NOT NULL AND payload_reason = 'NONE'
              AND execution_state = 'SUCCEEDED' AND business_status IS NOT NULL AND business_status = 'ANSWERED')
             OR (payload_disposition = 'NOT_APPLICABLE' AND result_payload IS NULL AND payload_reason = 'NO_RESULT_FOR_OUTCOME')
             OR (payload_disposition = 'SUPPRESSED' AND result_payload IS NULL
                 AND payload_reason NOT IN ('NONE','NO_RESULT_FOR_OUTCOME'))
         ))
        OR
        (archive_status = 'INTERRUPTED'
         AND execution_state IS NULL AND stop_reason IS NULL AND end_reason IS NULL AND current_step IS NULL
         AND error_category IS NULL AND error_code IS NULL AND business_status IS NULL AND failure_category IS NULL
         AND cleanup_outcome IS NULL AND execution_finished_at IS NULL
         AND closed_at IS NOT NULL AND retained_until IS NOT NULL AND retained_until > closed_at
         AND result_payload IS NULL AND payload_disposition IS NULL AND payload_reason IS NULL
         AND interruption_reason IS NOT NULL AND interruption_reason = 'PREVIOUS_OWNER_RESULT_UNCONFIRMED')
    )
);

CREATE INDEX campus_archive_page_idx ON campus_agent_run_archive(admitted_at DESC,run_id DESC);
CREATE INDEX campus_archive_expiry_idx ON campus_agent_run_archive(archive_status,retained_until);
CREATE INDEX campus_archive_reconcile_idx ON campus_agent_run_archive(owner_epoch)
    WHERE archive_status = 'ADMITTED';
