-- ============================================================
-- V1 初始化脚本
-- 业务表结构；向量表 vector_store 由 Spring AI PgVectorStore 自动创建
-- ============================================================

-- ---------- 规章制度文档表 ----------
CREATE TABLE IF NOT EXISTS t_document
(
    id          BIGSERIAL PRIMARY KEY,
    title       VARCHAR(255) NOT NULL,
    file_name   VARCHAR(255) NOT NULL,
    file_path   VARCHAR(512) NOT NULL,
    file_size   BIGINT,
    page_count  INTEGER,
    status      VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
    remark      VARCHAR(512),
    deleted     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE t_document IS '学校规章制度文档';
COMMENT ON COLUMN t_document.status IS 'PENDING / PARSING / READY / FAILED';

CREATE INDEX IF NOT EXISTS idx_document_status ON t_document (status);
CREATE INDEX IF NOT EXISTS idx_document_created_at ON t_document (created_at DESC);

-- ---------- 问答记录表 ----------
CREATE TABLE IF NOT EXISTS t_chat_record
(
    id          BIGSERIAL PRIMARY KEY,
    session_id  VARCHAR(64) NOT NULL,
    question    TEXT        NOT NULL,
    answer      TEXT,
    sources     JSONB,
    latency_ms  INTEGER,
    created_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE t_chat_record IS '问答记录';
COMMENT ON COLUMN t_chat_record.sources IS '引用来源 [{documentId,title,page,snippet}]';

CREATE INDEX IF NOT EXISTS idx_chat_session ON t_chat_record (session_id);
CREATE INDEX IF NOT EXISTS idx_chat_created_at ON t_chat_record (created_at DESC);
