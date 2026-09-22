-- 阶段 10（线 A）：匿名遥测事件表。
-- 号段：V17（PM 预批给线 A）；V18 备用不动。只新增，不改任何已应用迁移。
--
-- 设计口径（为什么是这些列、为什么没有自由文本列）：
--   ① 列集合与 `PrivacyBoundary.anonymousTelemetry` 的白名单维度**逐一对应**
--      （route / retrieval_profile / generator_profile / handoff_required /
--        failure_category / latency_bucket / estimated_cost_bucket / event_date）；
--      新增维度必须先改白名单（跨线登记），不允许"先建列再补边界"。
--   ② 表里**没有**任何可承载自由文本、原问答、身份信息（姓名/学号/联系方式）、密钥
--      或逐条散列的列——隐私边界靠"列本身不存在"兜底，而不是靠应用层自觉。
--   ③ 表层 CHECK 复刻白名单的取值形态（应用层已校验一次，这里是第二道闸）：
--      代码维度 `^[A-Z][A-Z0-9_]{0,63}$`、profile `^[A-Za-z0-9._-]{1,128}$`、日期为 DATE。
--   ④ 本表回答四个问题：哪条链路（route + 两个 profile）、哪一级路由（route）、
--      命中/降级/转人工（failure_category + handoff_required）、耗时（latency_bucket）。

CREATE TABLE anonymous_telemetry_event (
    id                    UUID PRIMARY KEY,
    -- 五级链路结论：AWAITING_CLARIFICATION / CURRENT_NOTICE / HIGH_RISK_RULE /
    -- RETRIEVAL_AUGMENTED / INSUFFICIENT_EVIDENCE（既有 AnswerRoute 枚举名）。
    route                 VARCHAR(64)  NOT NULL
        CHECK (route ~ '^[A-Z][A-Z0-9_]{0,63}$'),
    retrieval_profile     VARCHAR(128)
        CHECK (retrieval_profile IS NULL OR retrieval_profile ~ '^[A-Za-z0-9._-]{1,128}$'),
    generator_profile     VARCHAR(128)
        CHECK (generator_profile IS NULL OR generator_profile ~ '^[A-Za-z0-9._-]{1,128}$'),
    -- 转人工标记：与 route=INSUFFICIENT_EVIDENCE 一起表达"该转人工"。
    handoff_required      BOOLEAN      NOT NULL,
    -- 命中/降级：NONE 表示正常作答；其余为降级或拒答原因（如 MODEL_DEGRADED / RETRIEVAL_MISS）。
    failure_category      VARCHAR(64)
        CHECK (failure_category IS NULL OR failure_category ~ '^[A-Z][A-Z0-9_]{0,63}$'),
    -- 耗时只落"桶"（如 LT_1S / S_1_3 / S_3_10 / GE_10S），不落精确毫秒——精确值可参与指纹化。
    latency_bucket        VARCHAR(64)  NOT NULL
        CHECK (latency_bucket ~ '^[A-Z][A-Z0-9_]{0,63}$'),
    estimated_cost_bucket VARCHAR(64)
        CHECK (estimated_cost_bucket IS NULL OR estimated_cost_bucket ~ '^[A-Z][A-Z0-9_]{0,63}$'),
    event_date            DATE         NOT NULL,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 按日聚合与按路由排查是主要读法；不建"按人/按会话"的索引——本表没有可归人的维度。
CREATE INDEX idx_anonymous_telemetry_event_date ON anonymous_telemetry_event (event_date);
CREATE INDEX idx_anonymous_telemetry_route_date ON anonymous_telemetry_event (route, event_date);
