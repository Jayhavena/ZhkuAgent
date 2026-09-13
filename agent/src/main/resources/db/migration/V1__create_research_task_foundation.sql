CREATE TABLE api_consumer (
    id UUID PRIMARY KEY,
    display_name VARCHAR(100) NOT NULL,
    api_key_hash VARCHAR(255) NOT NULL UNIQUE,
    api_key_prefix VARCHAR(16) NOT NULL,
    max_concurrent_tasks INTEGER NOT NULL CHECK (max_concurrent_tasks > 0),
    max_model_calls_per_task INTEGER NOT NULL CHECK (max_model_calls_per_task > 0),
    max_github_calls_per_task INTEGER NOT NULL CHECK (max_github_calls_per_task > 0),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE agent_task (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES api_consumer(id),
    repository_owner VARCHAR(100) NOT NULL,
    repository_name VARCHAR(100) NOT NULL,
    research_question TEXT NOT NULL,
    status VARCHAR(32) NOT NULL CHECK (status IN (
        'CREATED', 'PLANNING', 'READY', 'RUNNING', 'WAITING_RETRY',
        'CANCEL_REQUESTED', 'SUCCEEDED', 'FAILED', 'CANCELLED', 'TIMED_OUT'
    )),
    active_plan_version INTEGER,
    deadline_at TIMESTAMPTZ NOT NULL,
    deadline_exceeded_at TIMESTAMPTZ,
    termination_pending BOOLEAN NOT NULL DEFAULT FALSE,
    next_retry_at TIMESTAMPTZ,
    last_activity_at TIMESTAMPTZ NOT NULL,
    last_subscriber_at TIMESTAMPTZ,
    model_call_count INTEGER NOT NULL DEFAULT 0 CHECK (model_call_count >= 0),
    embedding_call_count INTEGER NOT NULL DEFAULT 0 CHECK (embedding_call_count >= 0),
    github_call_count INTEGER NOT NULL DEFAULT 0 CHECK (github_call_count >= 0),
    next_event_seq BIGINT NOT NULL DEFAULT 1 CHECK (next_event_seq > 0),
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at TIMESTAMPTZ NOT NULL,
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE research_plan (
    id UUID PRIMARY KEY,
    task_id UUID NOT NULL REFERENCES agent_task(id) ON DELETE CASCADE,
    plan_version INTEGER NOT NULL CHECK (plan_version > 0),
    status VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE', 'SUPERSEDED')),
    change_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    activated_at TIMESTAMPTZ NOT NULL,
    superseded_at TIMESTAMPTZ,
    UNIQUE (task_id, plan_version)
);

-- 同一任务只能有一个活动计划，避免恢复时读取到两份互相冲突的步骤定义。
CREATE UNIQUE INDEX uq_research_plan_one_active
    ON research_plan(task_id)
    WHERE status = 'ACTIVE';

CREATE TABLE task_step (
    id UUID PRIMARY KEY,
    task_id UUID NOT NULL REFERENCES agent_task(id) ON DELETE CASCADE,
    plan_version INTEGER NOT NULL CHECK (plan_version > 0),
    step_no INTEGER NOT NULL CHECK (step_no > 0),
    step_type VARCHAR(64) NOT NULL,
    title VARCHAR(200) NOT NULL,
    input_json JSONB NOT NULL,
    status VARCHAR(32) NOT NULL CHECK (status IN (
        'PENDING', 'RUNNING', 'SUCCEEDED', 'WAITING_RETRY',
        'FAILED', 'INTERRUPTED', 'SKIPPED'
    )),
    attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    result_summary TEXT,
    last_error_type VARCHAR(64),
    last_error_message TEXT,
    next_retry_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE (task_id, plan_version, step_no),
    FOREIGN KEY (task_id, plan_version)
        REFERENCES research_plan(task_id, plan_version) ON DELETE CASCADE
);

CREATE TABLE task_execution_attempt (
    id UUID PRIMARY KEY,
    task_id UUID NOT NULL REFERENCES agent_task(id) ON DELETE CASCADE,
    run_id UUID NOT NULL UNIQUE,
    executor_instance_id VARCHAR(100) NOT NULL,
    status VARCHAR(32) NOT NULL CHECK (status IN (
        'STARTING', 'RUNNING', 'SUCCEEDED', 'FAILED',
        'INTERRUPTED', 'REJECTED'
    )),
    stop_reason VARCHAR(64),
    heartbeat_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    error_type VARCHAR(64),
    error_message TEXT
);

-- 队列或恢复扫描并发触发时，数据库仍保证一个任务只有一个活动执行尝试。
CREATE UNIQUE INDEX uq_task_execution_one_active
    ON task_execution_attempt(task_id)
    WHERE status IN ('STARTING', 'RUNNING');

CREATE TABLE task_step_attempt (
    id UUID PRIMARY KEY,
    task_step_id UUID NOT NULL REFERENCES task_step(id) ON DELETE CASCADE,
    attempt_no INTEGER NOT NULL CHECK (attempt_no > 0),
    idempotency_key VARCHAR(200) NOT NULL UNIQUE,
    status VARCHAR(32) NOT NULL CHECK (status IN (
        'STARTING', 'RUNNING', 'SUCCEEDED', 'FAILED', 'INTERRUPTED'
    )),
    request_summary TEXT,
    result_summary TEXT,
    error_type VARCHAR(64),
    error_message TEXT,
    started_at TIMESTAMPTZ NOT NULL,
    finished_at TIMESTAMPTZ,
    UNIQUE (task_step_id, attempt_no)
);

CREATE TABLE task_event (
    id UUID PRIMARY KEY,
    task_id UUID NOT NULL REFERENCES agent_task(id) ON DELETE CASCADE,
    event_seq BIGINT NOT NULL CHECK (event_seq > 0),
    event_type VARCHAR(64) NOT NULL,
    schema_version INTEGER NOT NULL CHECK (schema_version > 0),
    payload_json JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (task_id, event_seq)
);

CREATE INDEX idx_agent_task_owner_status
    ON agent_task(owner_id, status, created_at);

CREATE INDEX idx_agent_task_dispatch
    ON agent_task(status, next_retry_at, created_at);

CREATE INDEX idx_task_step_task_plan
    ON task_step(task_id, plan_version, step_no);

CREATE INDEX idx_task_event_task_seq
    ON task_event(task_id, event_seq);

CREATE INDEX idx_execution_attempt_heartbeat
    ON task_execution_attempt(status, heartbeat_at);
