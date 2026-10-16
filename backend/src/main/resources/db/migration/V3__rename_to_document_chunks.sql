-- ============================================================
-- V3 重命名向量表为 document_chunks（对齐需求规范的表名）
-- 说明：t_vector_chunk（V2）已含 content/metadata/embedding 三列，
--       结构与本需求完全一致，仅更名以符合文档约定。
-- ============================================================

ALTER TABLE IF EXISTS t_vector_chunk RENAME TO document_chunks;

-- 索引名也同步（仅更名，不重建）
ALTER INDEX IF EXISTS idx_vector_chunk_document RENAME TO idx_document_chunks_document;

COMMENT ON TABLE  document_chunks               IS '文档文本块 + 向量（本地余弦检索用）';
COMMENT ON COLUMN document_chunks.content       IS '文本块内容';
COMMENT ON COLUMN document_chunks.metadata      IS '元数据 {documentId, page_number, title}';
COMMENT ON COLUMN document_chunks.embedding     IS 'Embedding 向量（JSON 数组）';
