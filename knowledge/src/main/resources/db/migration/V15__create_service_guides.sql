-- 阶段 9（线 A）：办事清单（service guide）——步骤、材料、官方渠道、风险提示。
-- 号段裁决：V15 = 线 A（本文件），V16 = 线 B（匿名反馈）。两者互不建外键、各自自包含；
-- 清单与规则/通知/反馈的关联一律用软引用（标识 + 版本号字符串），由服务层校验。
-- 白名单约束：不收集任何学生身份或自由文本列（无 student_id / name / contact / message 等）。

-- 清单主表：同一 (guide_identifier, guide_version) 唯一，发布语义复用"已发布 + 有效期"。
CREATE TABLE service_guide (
    id                       UUID PRIMARY KEY,
    guide_identifier         VARCHAR(128)  NOT NULL,
    guide_version            VARCHAR(64)   NOT NULL,
    title                    VARCHAR(256)  NOT NULL,
    applicable_scope         VARCHAR(256)  NOT NULL,
    -- 软引用：规则标识 + 版本号（如 PUBLIC_ELECTIVE_RULE#2025版），不建外键。
    source_rule_reference    VARCHAR(256),
    -- 软引用：当期通知标识 + 版本号。
    source_notice_reference  VARCHAR(256),
    review_notice            TEXT          NOT NULL,
    student_visible          BOOLEAN       NOT NULL DEFAULT FALSE,
    published_at             TIMESTAMPTZ,
    valid_until              TIMESTAMPTZ,
    created_at               TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_service_guide_identifier_version UNIQUE (guide_identifier, guide_version),
    -- 学生可见的清单必须确认分享权利（与既有资料发布口径一致）。
    CONSTRAINT ck_service_guide_student_visible
        CHECK (student_visible = FALSE OR published_at IS NOT NULL),
    -- 有效期上界为排他：validUntil 时刻即失效。
    CONSTRAINT ck_service_guide_valid_window
        CHECK (valid_until IS NULL OR published_at IS NULL OR valid_until > published_at)
);

CREATE TABLE service_guide_step (
    id                      UUID PRIMARY KEY,
    guide_id                UUID         NOT NULL REFERENCES service_guide (id),
    step_order              INTEGER      NOT NULL CHECK (step_order >= 1),
    instruction             TEXT         NOT NULL,
    requires_identity_check BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_service_guide_step_order UNIQUE (guide_id, step_order)
);

CREATE TABLE service_guide_material (
    id             UUID PRIMARY KEY,
    guide_id       UUID         NOT NULL REFERENCES service_guide (id),
    material_order INTEGER      NOT NULL CHECK (material_order >= 1),
    material_name  VARCHAR(256) NOT NULL,
    note           TEXT,
    required       BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_service_guide_material_order UNIQUE (guide_id, material_order)
);

-- 官方渠道：channel_value 与 source_locator 都强制非空——
-- "渠道只能来自审核资料"在表层就成立，没有来源定位的联系方式无法入库。
CREATE TABLE service_guide_channel (
    id              UUID PRIMARY KEY,
    guide_id        UUID         NOT NULL REFERENCES service_guide (id),
    channel_order   INTEGER      NOT NULL CHECK (channel_order >= 1),
    channel_type    VARCHAR(32)  NOT NULL
        CHECK (channel_type IN ('SCHOOL_OFFICE', 'PHONE', 'WEBSITE', 'EMAIL')),
    channel_name    VARCHAR(256) NOT NULL,
    channel_value   VARCHAR(512) NOT NULL,
    source_locator  VARCHAR(512) NOT NULL,
    verified_at     TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_service_guide_channel_order UNIQUE (guide_id, channel_order)
);

CREATE TABLE service_guide_risk_notice (
    id            UUID PRIMARY KEY,
    guide_id      UUID         NOT NULL REFERENCES service_guide (id),
    notice_order  INTEGER      NOT NULL CHECK (notice_order >= 1),
    notice_text   TEXT         NOT NULL,
    CONSTRAINT uq_service_guide_risk_order UNIQUE (guide_id, notice_order)
);

CREATE INDEX idx_service_guide_step_guide ON service_guide_step (guide_id, step_order);
CREATE INDEX idx_service_guide_material_guide ON service_guide_material (guide_id, material_order);
CREATE INDEX idx_service_guide_channel_guide ON service_guide_channel (guide_id, channel_order);
CREATE INDEX idx_service_guide_risk_guide ON service_guide_risk_notice (guide_id, notice_order);
