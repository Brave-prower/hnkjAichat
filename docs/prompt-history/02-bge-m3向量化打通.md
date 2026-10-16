# AI 协同演进记录 · 快照 2

## 语义检索打通：从「关键词兜底」到 bge-m3 向量化的完整排障链

> 本快照对应核心功能迭代：**Embedding 向量化接入与语义检索升级**。
> 这是一次典型的「现象 → 假设 → 验证 → 反证 → 真因」排障过程，完整保留了 Prompt 链。

---

### 迭代背景

项目初期没有 Embedding Key，检索走的是中文 2-gram 关键词兜底（可用但召回弱）。拿到硅基流动 Key 后，目标是把 40 个文本块全部向量化，检索模式升级为语义。

### Prompt 1（目标描述）

> 我要把项目搞成向量化检索，不许改别的（提示词、RAG 策略都不要动）。现在库里 40 个文本块 embedding 全是 NULL，环境变量里只有 DeepSeek 的 Key。帮我配好 Embedding 并重建索引。

### AI 行动

确认只缺 Key → 配置环境变量（`OPENAI_API_KEY` / `OPENAI_BASE_URL` / `AI_EMBEDDING_MODEL=BAAI/bge-m3`）→ 重启后端 → 调 `/api/documents/{id}/reindex`。

### 现象 1：reindex 成功但向量数为 0

日志出现：`向量入库：26 个文本块（无向量 · 关键词检索模式）`，且 `WARN Retry error: HTTP 404 - Not Found`。

### Prompt 2（给出日志，要求定位）

> 重建索引返回成功，但日志全是「无向量·关键词检索模式」，还有 OpenAiApi 的 404 重试告警。为什么？

### AI 定位

Spring AI 的 `OpenAiApi` **会在 base-url 后自动拼接 `/v1/embeddings`**。配置里写了 `https://api.siliconflow.cn/v1`，实际请求变成 `https://api.siliconflow.cn/v1/v1/embeddings` → 404。

修复：`OPENAI_BASE_URL=https://api.siliconflow.cn`（去掉 `/v1`）。

### 现象 2：修复 404 后依然「无向量」，且块数从 40 涨到 77

### Prompt 3（继续排障）

> base-url 修正后重新 reindex，向量还是没入库，而且 document_chunks 从 40 块变成 77 块，全是重复数据。日志里还有「旧向量清理失败（继续重建）」。

### AI 定位（两个独立 Bug）

1. **块数翻倍**：`VectorChunkRepository.deleteByDocumentId` 的原生 SQL 写的是**旧表名 `t_vector_chunk`**（V3 迁移已把表改名为 `document_chunks`），删除永远影响 0 行，且方法缺 `@Transactional`。reindex 变成了「只增不删」。
2. **向量仍为空**：编译期 `JdbcCosineVectorStore` 里两个字段重复声明导致 javac 第一轮就崩，Lombok 注解处理器没跑，产生大量「找不到符号 log/getTitle」的级联假象错误，服务根本没起来；修复重复声明后，又发现 `VectorStoreConfig` 用旧的两参构造器 new 对象，`@Value` 在手动 new 的对象上不生效，Key 没传进去。

### 引导修复的完整改动

- 删除 `JdbcCosineVectorStore` 的重复字段声明（让 Lombok 恢复工作）
- `VectorStoreConfig` 改为在 `@Bean` 方法参数上注入 ApiKey，传入三参构造器
- `deleteByDocumentId` 表名改为 `document_chunks` 并补 `@Transactional`
- 修正测试类构造器签名（`spring-boot:run` 会连带编译测试代码）

### 最终验证

- `reindex` 后日志：`向量入库：26 个文本块（余弦语义检索）`、`已清理文档 [3] 的全部向量数据`
- 数据库：37/40 块带 1024 维向量
- 端到端问答：「学生考试作弊会受到什么处分？」**1.6s** 命中《违纪处分办法》第 16/4 页；「转专业需要满足什么条件？」**2.7s** 命中《学籍管理规定》第 2/3 页（此前关键词模式约 42s）

### 经验沉淀

- Spring AI 的 `OpenAiApi` 自拼 `/v1` 路径，base-url **不能**再带 `/v1`
- javac 第一轮崩溃会连坐 Lombok，报出一堆不相干的「找不到符号」——先修第一个真实错误，级联错误会消失
- 表名重构（V3 迁移）必须同步检查所有**原生 SQL**（JPA 方法名派生查询不受影响，`@Query(nativeQuery=true)` 会静默失效）
