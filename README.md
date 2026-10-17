# 湖南科技职业学院 · 校园规章制度智能问答系统

> **一句话介绍**：把学校的规章制度 PDF 变成「会回答问题」的知识库——学生用自然语言提问，系统做**语义检索**命中最相关的制度原文，由 DeepSeek 生成回答，**每条回答都标注来源文档与页码**，从根源上抑制大模型幻觉。
>
> **应用场景**：学生问「考试作弊会受什么处分」「转专业要满足什么条件」，再也不用翻几十页 PDF 找条文；管理员只需上传文档，系统自动完成解析、切分、向量化全流程。

**痛点 → 方案：**

| 传统方式 | 本系统 |
| --- | --- |
| 规章制度散落在几十个 PDF 里，人工翻找 | 上传即入库，自然语言直接问 |
| 大模型直接回答校规问题 → 容易编造（幻觉） | RAG 架构：**先检索原文，再依据原文回答**，无依据时明确说「制度中未涉及」 |
| 关键词搜索搜不到「换了说法」的问题 | 1024 维向量语义检索，同义改写也能命中 |
| 回答不可信、无从核对 | 每条回答附**来源文档 + 页码 + 原文片段**，可点击展开核对 |

---

## ⚡ 10 分钟极速复现指南

### 🐳 方式一：Docker 一键启动（推荐，3 分钟）

只需 Docker Desktop，**不用装 JDK / Maven / Node / PostgreSQL**。

**第 1 步：配置 Key（1 分钟）**

```bash
# 复制环境变量模板
copy .env.example .env      # Windows
cp .env.example .env        # macOS / Linux
```

打开 `.env`，填入至少一个 `DEEPSEEK_API_KEY`（申请：https://platform.deepseek.com）。
`OPENAI_API_KEY`（硅基流动，向量化用）可选，不填会自动降级为关键词检索。

**第 2 步：启动（1 分钟）**

```bash
docker compose up -d --build
```

**第 3 步：访问**

| 地址 | 内容 |
| --- | --- |
| http://localhost | 前端（上传规章制度 / 智能问答） |
| http://localhost:8080/api/swagger-ui.html | 后端接口文档 |

打开 http://localhost → **上传规章制度** 页传一份 PDF → 切到**智能问答**提问。

**常用运维命令**

```bash
docker compose ps          # 查看三个服务状态（都应 healthy）
docker compose logs -f backend   # 跟踪后端日志
docker compose down        # 停止（数据保留在 volume 中）
docker compose down -v     # 停止并清空数据（含已上传文档）
```

> **卡住了？** 见文末「[Docker 常见问题](#docker-常见问题)」。

---

### 🛠 方式二：本地开发模式（改代码用）

适合需要调试源码的场景，需自行安装 JDK 17 / Maven / Node 18 / PostgreSQL。

### 前置条件（2 分钟）

| 依赖 | 版本 | 用途 |
| --- | --- | --- |
| JDK | 17+ | 后端运行 |
| Maven | 3.8+ | 后端构建 |
| Node.js | 18+ | 前端构建 |
| PostgreSQL | 14+ | 业务数据 + 向量存储（**无需 pgvector 扩展**） |

### 第 1 步：启动数据库（1 分钟）

```bash
# 方式 A：本机已装 PostgreSQL，直接建库（本项目的向量存储不需要 pgvector 扩展）
psql -U postgres -c "CREATE DATABASE campus_qa;"

# 方式 B：只用 Docker 起一个数据库，前后端仍在本机跑
docker compose up -d postgres
```

### 第 2 步：申请两个 Key（3 分钟）

| Key | 用途 | 申请地址 |
| --- | --- | --- |
| `DEEPSEEK_API_KEY` | 生成回答 | https://platform.deepseek.com |
| `OPENAI_API_KEY` | 文本向量化（Embedding） | https://cloud.siliconflow.cn （实名认证后创建，`BAAI/bge-m3` 模型免费） |

> 为什么需要两个 Key？DeepSeek 官方**不提供 Embedding 接口**，所以生成与向量化分离——这也是本项目「检索/生成解耦」架构的体现。

### 第 3 步：配置环境变量（1 分钟）

```bash
# 参考模板：backend/.env.example（复制后填入你的 Key）
# Windows PowerShell 示例：
$env:DEEPSEEK_API_KEY  = "sk-你的DeepSeek密钥"
$env:OPENAI_API_KEY    = "sk-你的硅基流动密钥"
$env:OPENAI_BASE_URL   = "https://api.siliconflow.cn"   # 注意：不要带 /v1
$env:AI_EMBEDDING_MODEL = "BAAI/bge-m3"
```

### 第 4 步：启动前后端（2 分钟）

```bash
# 后端（首次会自动下载依赖，Flyway 自动建表）
cd backend && mvn spring-boot:run

# 新开一个终端，前端
cd frontend && npm install && npm run dev
```

打开 http://localhost:5173 → **上传规章制度** 页传一份 PDF → 切到**智能问答**提问。

### ✅ 复现验收标准

1. 上传 PDF 后返回「提取字数 / 页数 / 文本块数」统计
2. 提问后 **2~3 秒**内开始流式输出（SSE），回答为 Markdown 格式
3. 回答下方出现 3 条**可展开的来源卡片**，含页码标签与相似度
4. 问一个制度里没有的问题（如「今天天气如何」），系统回答「制度中未涉及」而非编造
5. 点开回答下方的**「为什么这样答」**面板：可看到本轮检索的模式徽标（语义/关键词）、每个候选文本块的相似度分数条（带阈值刻度线）、命中/未命中标记，以及闸门结论（PASS 放行 / REJECT 拒答的人话解释）

**一键复现演示数据**（含压线对比）：

```bash
python docs/scripts/gen-demo-traces.py
```

脚本会调用真实接口跑 7 个问题，控制台直接输出分数与闸门结论，完整 trace 存到 `docs/scripts/demo-traces-result.json`。其中第 ⑥⑦ 条是**压线对比组**，最能说明闸门机制：

| # | 问题 | Top1 分数 | 与阈值 0.6 的差 | 结论 |
|---|---|---|---|---|
| ⑥ | 校园内吸烟会被处分吗 | 0.6144 | **+0.0144** | ✅ 放行 |
| ⑦ | 学生的权利有哪些 | 0.5833 | **−0.0167** | ❌ 拒答 |

两者**仅差 0.031 分，结论完全相反**——这就是「宁可不答也不编造」的直接证据，答辩时对着面板截图讲最有说服力。

---

## 一、技术栈

### 前端
| 组件 | 版本 | 说明 |
| --- | --- | --- |
| Vue | 3.5.x | 组合式 API + `<script setup>` |
| Vite | 6.x | 构建工具 |
| Vue Router / Pinia | 4.x / 2.x | 路由 / 状态管理 |
| Element Plus | 2.8.x | UI 组件库 |
| marked + DOMPurify | - | Markdown 渲染 + 防 XSS |
| Sass | 1.x | 样式预处理（设计令牌系统） |

### 后端
| 组件 | 版本 | 说明 |
| --- | --- | --- |
| Java | 17 | LTS |
| Spring Boot | 3.4.1 | 基础框架 |
| Spring AI | 1.0.0 | Embedding / RAG 抽象层 |
| Spring Data JPA + Flyway | - | 数据访问 / 数据库版本管理 |
| PostgreSQL | 14+ | 业务数据 + 向量存储 |
| Apache PDFBox | 3.x | PDF 解析 |
| springdoc-openapi | 2.7.x | 接口文档（/api/swagger-ui.html） |

### 大模型
| 用途 | 供应商 | 模型 | 说明 |
| --- | --- | --- | --- |
| 生成回答 | DeepSeek | `deepseek-flash` | 思维链可配置开关，流式 SSE 输出 |
| 文本向量化 | 硅基流动 | `BAAI/bge-m3`（1024 维） | 免费模型，任何 OpenAI 协议兼容服务均可替换 |

---

## 二、系统架构

```
┌─────────── 前端 Vue3 ───────────┐
│  上传页 │ 问答页(SSE流式) │ 文档管理 │
└───────┬────────────┬───────────┘
        │ POST /api/upload        │ POST /api/qa/ask/stream (SSE)
┌───────▼─────────────────────────▼───────┐
│                 后端 Spring Boot          │
│  PdfIngestService      RagQaService       │
│  ┌─────────┐  ┌──────────┐  ┌─────────┐  │
│  │PDF 解析 │→│ 切分+向量化│→│ 语义检索 │  │
│  └─────────┘  └────┬─────┘  └────┬────┘  │
│                    │       ┌─────▼─────┐ │
│                    │       │DeepSeek   │ │
│                    │       │生成回答   │ │
│  JdbcCosineVectorStore │  └───────────┘ │
│  (普通表+Java余弦) / pgvector 双模式      │
└────────────┬────────────────────────────┘
             │
      ┌──────▼──────┐
      │ PostgreSQL  │ ← Flyway 自动建表(V1~V4)
      └─────────────┘
```

**核心流程（一次问答的生命周期）：**

1. **入库**：PDF → PDFBox 提取文本 → 按 token 切分 → 每块调 bge-m3 生成 1024 维向量 → 存入 `document_chunks`（向量 + 页码 + 标题元数据）
2. **检索**：用户提问 → 问题向量化 → 与库内全量向量算余弦相似度 → 取 Top-3（相似度 ≥ 0.6）
3. **生成**：命中文本块 + 页码注入系统提示词 → DeepSeek 依据**原文**回答并标注 `[第X页]`
4. **兜底**：Embedding 服务不可用时自动降级为中文 2-gram 关键词匹配（服务不中断）；生成失败时推送制度原文摘录

## 三、目录结构

```
hnjgAichat/
├── README.md                       # 本文件（10分钟复现指南）
├── PROJECT-GUIDE.md                # 新手向逐文件说明书
├── docker-compose.yml              # PostgreSQL 本地环境
├── docs/
│   ├── architecture.md             # 架构与数据流
│   ├── TECH-DOC.md                 # 技术文档（意图控制/架构/可复现性）
│   ├── prompt-history/             # AI 协同演进记录（Prompt 链快照）
│   └── local-db-setup.md           # 本机免 Docker 建库指南
├── backend/                        # Java 17 + Spring Boot 3
│   ├── .env.example                # 环境变量模板 ← 复制这个
│   └── src/main/java/com/hnkj/qa/
│       ├── ai/                     # RAG 核心
│       │   ├── vector/             #   JdbcCosineVectorStore（含 Embedding 熔断器）
│       │   ├── deepseek/           #   DeepSeek 客户端（同步/流式）
│       │   ├── embedding/          #   EmbeddingService 封装
│       │   └── RagQaService.java   #   检索→上下文→生成→来源引用
│       ├── controller/             # 上传/文档/问答 接口层
│       ├── service/                # 业务实现
│       ├── repository/ entity/ dto/
│       └── config/                 # 向量存储装配、跨域等
└── frontend/                       # Vue 3 + Vite
    └── src/
        ├── components/             # MarkdownText(渲染) / SourceCitation(可展开引用)
        ├── views/                  # upload / qa / document / home
        ├── stores/chat.js          # 流式消息状态机
        └── styles/                 # 设计令牌 variables.scss
```

## 四、环境变量

完整清单见 **[backend/.env.example](backend/.env.example)**，核心项：

| 变量 | 说明 | 默认值 |
| --- | --- | --- |
| `DB_HOST` / `DB_PORT` / `DB_NAME` | 数据库连接 | `localhost:5432/campus_qa` |
| `DB_USERNAME` / `DB_PASSWORD` | 数据库账号 | `postgres` / `postgres` |
| `DEEPSEEK_API_KEY` | **必填**，生成回答 | — |
| `DEEPSEEK_MODEL` | 生成模型 | `deepseek-flash` |
| `OPENAI_API_KEY` | **必填**，Embedding | — |
| `OPENAI_BASE_URL` | Embedding 服务地址（**不带 /v1**） | `https://api.siliconflow.cn` |
| `AI_EMBEDDING_MODEL` | 嵌入模型 | `BAAI/bge-m3` |
| `SPRING_PROFILES_ACTIVE` | `local`（免 pgvector）/ `railway`（pgvector） | `local` |

## 五、核心设计

### 1. 向量存储双模式（免 pgvector 也能跑）
| | `local`（默认） | `railway` |
| --- | --- | --- |
| 向量存储 | 普通表 `document_chunks`，向量存 JSONB | pgvector `vector_store`（HNSW） |
| 相似度计算 | Java 侧余弦相似度 Top-K | 数据库内计算 |
| 依赖 | **纯 PostgreSQL，无需任何扩展** | 需 pgvector 扩展 |
| 适用 | 本地开发 / 一般规模 | 云部署 / 大规模 |

两者实现同一 `VectorStore` 接口，业务代码零改动切换。

### 2. 三层防幻觉
- **相关性闸门**：检索命中相似度全部低于阈值时，不调用大模型，直接告知「制度中未涉及」
- **来源强制标注**：提示词要求回答必须标注 `[第X页]` 且禁止估算页码，前端渲染为可核对来源卡
- **降级链路**：Embedding 失败 → 关键词匹配兜底；生成失败 → 推送制度原文摘录。全程服务可用

### 3. 健壮性设计
- **Embedding 熔断器**：Embedding 服务连续失败立即熔断 10 分钟，避免每次提问都等待网络超时（曾把响应从 44s 优化回 2s）
- **SSE 流式问答**：`sources → message* → done` 事件协议，前端 `fetch + ReadableStream` 消费，首字延迟 < 2s
- **数据库版本管理**：Flyway V1~V4 迁移脚本，clone 后启动即自动建表

## 六、API 一览

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/upload` | 上传 PDF（multipart `file`），返回统计与 documentId |
| GET | `/api/documents/page` | 文档分页列表 |
| GET | `/api/documents/{id}` | 文档详情 |
| DELETE | `/api/documents/{id}` | 删除文档及其向量 |
| POST | `/api/documents/{id}/reindex` | 重建索引（重新切分+向量化） |
| POST | `/api/qa/ask` | 同步问答 |
| POST | `/api/qa/ask/stream` | **SSE 流式问答** |
| GET | `/api/qa/history?sessionId=` | 会话历史 |

交互式文档：启动后端后访问 http://localhost:8080/api/swagger-ui.html

## 七、常见问题（FAQ）

<details>
<summary><b>Embedding 调用报 404？</b></summary>

`OPENAI_BASE_URL` 不要带 `/v1`。Spring AI 会自动拼接 `/v1/embeddings`，写成 `https://api.siliconflow.cn/v1` 会请求到 `/v1/v1/embeddings` 导致 404。
</details>

<details>
<summary><b>硅基流动 Key 报 401 Token is invalid？</b></summary>

未实名认证的账号签发的 Key 服务端不认。完成实名认证后**重新创建** Key 即可。
</details>

<details>
<summary><b>本机装不了 pgvector？</b></summary>

无需安装。默认 `local` profile 用普通表 + Java 余弦计算，任何 PostgreSQL 都能跑。
</details>

<details>
<summary><b>回答等待很久才开始输出？</b></summary>

检查 Embedding Key 是否有效。Key 无效时系统自动降级为关键词检索（可用但稍慢）；配置有效 Key 并点「重建索引」即恢复语义检索。
</details>

### Docker 常见问题

<details>
<summary><b>端口 80 / 8080 / 5432 被占用？</b></summary>

改 `docker-compose.yml` 里对应服务的 `ports`，冒号左边是宿主机端口。例如前端被占用：

```yaml
ports:
  - "8000:80"    # 改为访问 http://localhost:8000
```

Windows 上 5432 常被本机已装的 PostgreSQL 占用，若不需要外部直连数据库，直接删掉 postgres 的 `ports` 两行即可。
</details>

<details>
<summary><b>启动后提问只返回制度原文，没有 AI 回答？</b></summary>

`DEEPSEEK_API_KEY` 没配或无效。确认根目录 `.env` 已填 Key，然后：

```bash
docker compose up -d --force-recreate backend
```
</details>

<details>
<summary><b>面板显示「关键词检索」而不是「语义检索」？</b></summary>

`OPENAI_API_KEY`（硅基流动）未配置或未实名认证。系统已自动降级，问答仍可用。
配好 Key 后重启后端，并在文档管理页点「重建索引」把已有文本块补上向量。
</details>

<details>
<summary><b>backend 容器一直 unhealthy？</b></summary>

先看日志定位真实原因：

```bash
docker compose logs backend | tail -50
```

常见原因：① 首次构建需下载 Maven 依赖，耗时 3~5 分钟，`start-period` 内显示 unhealthy 属正常；② 数据库还没就绪，compose 已配 `depends_on: service_healthy`，通常会自动重试。
</details>

<details>
<summary><b>改了代码后容器没变化？</b></summary>

Docker 镜像不会自动跟随源码更新，需要重新构建：

```bash
docker compose up -d --build
```
</details>

---

## 附：项目文档

- [PROJECT-GUIDE.md](PROJECT-GUIDE.md) —— 新手向逐文件说明书（每个文件是干啥的）
- [docs/TECH-DOC.md](docs/TECH-DOC.md) —— 技术文档：意图控制策略 / 架构设计 / 可复现性论证
- [docs/prompt-history/](docs/prompt-history/) —— AI 协同演进记录（核心迭代的 Prompt 链快照）
- [docs/architecture.md](docs/architecture.md) —— 架构与数据流
