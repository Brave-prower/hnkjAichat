-- ============================================================
-- V2 本地向量存储表（不依赖 pgvector）
-- 用途：local profile 下由 JdbcCosineVectorStore 使用，
--       embedding 以 JSONB 数组保存，检索时在 Java 侧计算余弦相似度。
-- railway（pgvector）profile 下该表不会被使用，保留不影响。
-- ============================================================

CREATE TABLE IF NOT EXISTS t_vector_chunk
(
    id         VARCHAR(64) PRIMARY KEY,
    content    TEXT   NOT NULL,
    metadata   JSONB,
    embedding  JSONB  NOT NULL
);

COMMENT ON TABLE t_vector_chunk IS '文档文本块 + 向量（本地余弦检索用）';
COMMENT ON COLUMN t_vector_chunk.content IS '文本块内容';
COMMENT ON COLUMN t_vector_chunk.metadata IS '元数据 {documentId, page_number, title}';
COMMENT ON COLUMN t_vector_chunk.embedding IS 'Embedding 向量（JSON 数组）';

-- 按文档清理向量时使用
CREATE INDEX IF NOT EXISTS idx_vector_chunk_document
    ON t_vector_chunk ((metadata ->> 'documentId'));
