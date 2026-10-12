# 架构说明

## 1. 系统分层

```
┌───────────────────────────────────────────────┐
│  前端 Vue 3 + Element Plus                     │
│  ├─ 文档管理：上传 PDF / 列表 / 删除            │
│  └─ 智能问答：提问 / 流式回答 / 来源页码标注     │
└───────────────────┬───────────────────────────┘
                    │ HTTP (JSON, SSE)
┌───────────────────▼───────────────────────────┐
│  后端 Spring Boot 3                            │
│  ├─ Controller  接口层（参数校验 / 鉴权）        │
│  ├─ Service     业务编排                       │
│  ├─ AI 层       RAG 管线（解析→切分→检索→生成）  │
│  └─ Repository  数据访问                       │
└───────────────────┬───────────────────────────┘
                    │
        ┌───────────┴───────────┐
        ▼                       ▼
┌───────────────┐      ┌────────────────────┐
│ PostgreSQL    │      │  大模型服务         │
│ ├─ 业务表      │      │  ├─ Chat Model     │
│ └─ pgvector   │      │  └─ Embedding Model│
└───────────────┘      └────────────────────┘
```

## 2. 数据模型（骨架）

### `t_document` 规章制度文档
| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigserial | 主键 |
| title | varchar(255) | 文档标题 |
| file_name | varchar(255) | 原始文件名 |
| file_path | varchar(512) | 存储路径 |
| file_size | bigint | 字节数 |
| page_count | int | 页数 |
| status | varchar(32) | PENDING/PARSING/READY/FAILED |
| remark | varchar(512) | 备注 |
| created_at / updated_at | timestamp | 时间戳 |
| deleted | boolean | 逻辑删除 |

### `t_chat_record` 问答记录
| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigserial | 主键 |
| session_id | varchar(64) | 会话标识 |
| question | text | 用户问题 |
| answer | text | 模型回答 |
| sources | jsonb | 引用来源 [{documentId,title,page,snippet}] |
| latency_ms | int | 耗时 |
| created_at | timestamp | 时间戳 |

### `vector_store` 向量表
由 Spring AI `PgVectorStore` 自动建表，`metadata` 中保留 `documentId`、`pageNumber`、`title` 等字段用于来源溯源。

## 3. 关键设计点

- **页码溯源**：解析阶段使用按页读取（`PagePdfDocumentReader` / PDFBox），把 `pageNumber` 写入每个文本块的 metadata；生成阶段要求模型输出 `[页码]` 标记，前端据此渲染可点击的来源卡片。
- **回答约束**：System Prompt 明确「仅依据检索到的制度原文作答，无依据时回答『制度中未找到相关规定』」，抑制幻觉。
- **流式输出**：问答接口使用 SSE，前端 `fetch` + `ReadableStream` 逐字渲染。
- **检索策略**：默认余弦相似度 Top-K（K=5）；后续可加关键词召回做混合检索 + 重排序。

## 4. 待办清单（业务逻辑）

- [ ] `PdfIngestService`：PDF 上传 → 解析 → 切分 → 向量化 → 入库
- [ ] `RagQaService`：检索 → 组装上下文 → 调用 ChatClient → 结构化输出
- [ ] 会话历史持久化与多轮改写
- [ ] 文档重新索引 / 删除时同步清理向量
- [ ] 前端流式渲染 + 来源卡片跳转定位
