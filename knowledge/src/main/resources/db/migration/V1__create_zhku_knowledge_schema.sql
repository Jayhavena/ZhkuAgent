-- 部署到未经过容器初始化的环境时，迁移也应能自包含地提供向量扩展。
-- IF NOT EXISTS 允许它与本地容器初始化脚本安全地重复执行。
CREATE EXTENSION IF NOT EXISTS vector;

-- 一条资料先作为“来源”入库并审核，不能直接把未经确认的文本交给问答系统。
CREATE TABLE knowledge_source (
    id UUID PRIMARY KEY,
    title VARCHAR(300) NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    source_url TEXT,
    source_version VARCHAR(100) NOT NULL,
    -- 只有 PUBLISHED 状态的资料才应在后续检索链路中对学生可见。
    publication_status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    published_at TIMESTAMPTZ,
    content_checksum CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_knowledge_source_status CHECK (
        publication_status IN ('DRAFT', 'REVIEWING', 'PUBLISHED', 'ARCHIVED')
    ),
    -- 已发布资料必须留下发布时间，便于在规则更新时判断其时效性。
    CONSTRAINT ck_knowledge_source_published_time CHECK (
        publication_status <> 'PUBLISHED' OR published_at IS NOT NULL
    )
);

-- 片段是问答引用的最小单位：回答应指向具体页码或网页定位，而非笼统指向整份资料。
CREATE TABLE knowledge_chunk (
    id UUID PRIMARY KEY,
    source_id UUID NOT NULL,
    chunk_index INTEGER NOT NULL,
    section_title TEXT,
    source_locator VARCHAR(500) NOT NULL,
    page_number INTEGER,
    content TEXT NOT NULL,
    content_checksum CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- 禁止删除仍被片段引用的来源，避免答案引用变成悬空链接。
    CONSTRAINT fk_knowledge_chunk_source FOREIGN KEY (source_id)
        REFERENCES knowledge_source (id) ON DELETE RESTRICT,
    -- 同一资料的同一片段序号只能出现一次，防止重复入库造成检索权重偏差。
    CONSTRAINT uq_knowledge_chunk_source_index UNIQUE (source_id, chunk_index),
    CONSTRAINT ck_knowledge_chunk_index CHECK (chunk_index >= 0),
    CONSTRAINT ck_knowledge_chunk_page CHECK (
        page_number IS NULL OR page_number >= 1
    )
);

-- 后续检索入口首先按发布状态过滤，避免草稿或归档资料进入学生端回答。
CREATE INDEX idx_knowledge_source_publication_status
    ON knowledge_source (publication_status);

-- 审核、更新或撤回资料时需要快速定位其所有片段。
CREATE INDEX idx_knowledge_chunk_source_id
    ON knowledge_chunk (source_id);
