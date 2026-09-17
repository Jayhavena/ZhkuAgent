-- 阶段 8：统一规则路由与受控检索增强的元数据层。
-- 原则：模型输出不是原始知识；检索只能建立在"已发布知识片段 + 版本化索引"之上，
-- 每一次索引构建与每一次引用下发都要可审计。

-- 规则目录：声明一条确定性规则作答所需的事实、证据范围、风险与转人工条件。
-- 缺任一声明的规则不允许入库，与 RuleCatalogEntry 的应用层约束保持一致。
CREATE TABLE rule_catalog_entry (
    rule_id VARCHAR(100) PRIMARY KEY,
    topic VARCHAR(300) NOT NULL,
    -- 所需事实用 JSONB 数组保存，避免为声明性元数据再建一对多表。
    required_facts JSONB NOT NULL,
    evidence_scope VARCHAR(500) NOT NULL,
    risk VARCHAR(16) NOT NULL,
    handoff_condition VARCHAR(500) NOT NULL,
    -- 目录本身也版本化：同一 rule_id 的声明变化必须可追溯到具体目录版本。
    catalog_version VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_rule_catalog_risk CHECK (risk IN ('HIGH', 'MEDIUM', 'LOW')),
    CONSTRAINT ck_rule_catalog_required_facts CHECK (jsonb_typeof(required_facts) = 'array')
);

-- embedding profile：一次只允许一个 ACTIVE，索引切换走"新 profile → 重建 → 评测 → 原子切换"。
CREATE TABLE embedding_profile (
    profile_id VARCHAR(64) PRIMARY KEY,
    provider VARCHAR(64) NOT NULL,
    model VARCHAR(128) NOT NULL,
    dimensions INTEGER NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'BUILDING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    activated_at TIMESTAMPTZ,
    CONSTRAINT ck_embedding_profile_dimensions CHECK (dimensions > 0),
    CONSTRAINT ck_embedding_profile_status CHECK (status IN ('BUILDING', 'ACTIVE', 'RETIRED')),
    CONSTRAINT ck_embedding_profile_activation CHECK (
        status <> 'ACTIVE' OR activated_at IS NOT NULL
    )
);

-- 部分唯一索引：ACTIVE 全局唯一，原子切换由应用层在同一事务内"退旧启新"完成。
CREATE UNIQUE INDEX uq_embedding_profile_single_active
    ON embedding_profile (status) WHERE status = 'ACTIVE';

-- 索引运行：每次重建留痕，失败与回滚同样记录，禁止"看不见的重建"。
CREATE TABLE retrieval_index_run (
    id UUID PRIMARY KEY,
    profile_id VARCHAR(64) NOT NULL,
    chunking_version VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'RUNNING',
    started_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at TIMESTAMPTZ,
    indexed_entries INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT fk_index_run_profile FOREIGN KEY (profile_id)
        REFERENCES embedding_profile (profile_id) ON DELETE RESTRICT,
    CONSTRAINT ck_index_run_status CHECK (status IN ('RUNNING', 'COMPLETED', 'FAILED', 'ROLLED_BACK')),
    CONSTRAINT ck_index_run_finish CHECK (
        status = 'RUNNING' OR finished_at IS NOT NULL
    )
);

-- 索引条目：检索 chunk 的版本化映射。
-- 每条关联原 knowledge_chunk、来源版本、切分版本、内容哈希与 profile，
-- 使任一向量都能反查到"它出自哪份已发布资料的哪个版本"。
CREATE TABLE retrieval_index_entry (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL,
    profile_id VARCHAR(64) NOT NULL,
    chunk_id UUID NOT NULL,
    source_version VARCHAR(100) NOT NULL,
    chunking_version VARCHAR(64) NOT NULL,
    -- 冗余记录切分时刻的内容哈希：源片段日后变更时，可审计索引内容是否已过期。
    content_checksum CHAR(64) NOT NULL,
    -- 维度不写在表级约束：不同 profile 维度不同，维度一致性由 profile 切换流程
    -- （禁止混用维度）与应用层校验保证，避免旧索引回滚窗口被表结构卡死。
    embedding vector,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_index_entry_run FOREIGN KEY (run_id)
        REFERENCES retrieval_index_run (id) ON DELETE RESTRICT,
    CONSTRAINT fk_index_entry_profile FOREIGN KEY (profile_id)
        REFERENCES embedding_profile (profile_id) ON DELETE RESTRICT,
    -- 禁止删除仍被索引引用的知识片段，防止向量悬空指向不存在的原文。
    CONSTRAINT fk_index_entry_chunk FOREIGN KEY (chunk_id)
        REFERENCES knowledge_chunk (id) ON DELETE RESTRICT,
    -- 同一 profile 下同一切分版本的同一片段只索引一次，防止重复向量扭曲召回。
    CONSTRAINT uq_index_entry_profile_chunk UNIQUE (profile_id, chunk_id, chunking_version)
);

-- 引用审计：服务端下发的每个不透明引用标识都要能反查到它对应的索引条目与路由级别。
CREATE TABLE citation_audit (
    id UUID PRIMARY KEY,
    citation_id VARCHAR(128) NOT NULL,
    index_entry_id UUID NOT NULL,
    route VARCHAR(32) NOT NULL,
    -- 会话标识只作普通列记录、不设外键：会话持久化表由线 B 负责（V12），
    -- 此处跨线引用会在合并期造成迁移顺序耦合。
    session_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_citation_audit_citation UNIQUE (citation_id),
    CONSTRAINT fk_citation_audit_entry FOREIGN KEY (index_entry_id)
        REFERENCES retrieval_index_entry (id) ON DELETE RESTRICT,
    CONSTRAINT ck_citation_audit_route CHECK (route IN (
        'AWAITING_CLARIFICATION', 'CURRENT_NOTICE', 'HIGH_RISK_RULE',
        'RETRIEVAL_AUGMENTED', 'INSUFFICIENT_EVIDENCE'
    ))
);

-- 检索入口首先按 profile 过滤，召回只面向当前 ACTIVE profile 的条目。
CREATE INDEX idx_index_entry_profile ON retrieval_index_entry (profile_id);
CREATE INDEX idx_index_run_profile ON retrieval_index_run (profile_id);
