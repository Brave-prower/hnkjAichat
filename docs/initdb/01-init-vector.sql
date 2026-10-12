-- 容器首次初始化时自动执行
-- 启用 pgvector 扩展，供 Spring AI 的 PgVectorStore 使用
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
