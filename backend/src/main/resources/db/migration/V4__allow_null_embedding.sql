-- ============================================================
-- V4 允许 embedding 为 NULL（关键词检索兜底方案）
--
-- 背景：Embedding 服务未配置或临时不可用时，若 embedding 强制 NOT NULL，
--       文本块将整批入库失败，导致上传的文档内容完全无法被问答检索。
--
-- 调整：改为允许为空。内容照常保存，检索阶段自动退化为关键词匹配，
--       待 Embedding 可用后调用「重建索引」即可补全向量。
-- ============================================================

ALTER TABLE IF EXISTS document_chunks
    ALTER COLUMN embedding DROP NOT NULL;

COMMENT ON COLUMN document_chunks.embedding IS
    'Embedding 向量（JSON 数组）；为 NULL 表示尚未向量化，检索时走关键词匹配';
