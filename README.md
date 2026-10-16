# 湖南科技职业学院 · 校园问答系统

基于学校规章制度 PDF 的智能问答平台。学生用自然语言提问，系统检索 PDF 内容并由大模型生成回答，**每条回答标注来源文档与页码**。

> 当前仓库为**项目骨架**，仅包含目录结构、基础配置与空实现类，业务逻辑待后续填充。

---

## 一、技术栈

### 前端
| 组件 | 版本 | 说明 |
| --- | --- | --- |
| Vue | 3.5.x | 组合式 API + `<script setup>` |
| Vite | 6.x | 构建工具 |
| Vue Router | 4.x | 路由 |
| Pinia | 2.x | 状态管理 |
| Element Plus | 2.8.x | UI 组件库 |
| Axios | 1.x | HTTP 请求 |
| Sass | 1.x | 样式预处理 |

### 后端
| 组件 | 版本 | 说明 |
| --- | --- | --- |
| Java | 17 | LTS |
| Spring Boot | 3.4.1 | 基础框架 |
| Spring AI | 1.0.0 | LLM 调用 / Embedding / RAG |
| Spring Data JPA | - | 数据访问 |
| Flyway | - | 数据库版本管理 |
| PostgreSQL | 16 + pgvector | 业务数据 + 向量存储 |
| Apache PDFBox | 3.x | PDF 解析兜底 |
| springdoc-openapi | 2.7.x | 接口文档 |

---

## 二、目录结构

```
hnjgAichat/
├── README.md                       # 本文件
├── .gitignore
├── docker-compose.yml              # PostgreSQL(pgvector) 本地环境
├── docs/
│   └── architecture.md             # 架构与数据流说明
├── backend/                        # Java 17 + Spring Boot 3
│   ├── pom.xml
│   └── src/
│       ├── main/
│       │   ├── java/com/hnkj/qa/
│       │   │   ├── HnkjQaApplication.java      # 启动类
│       │   │   ├── common/                     # 通用能力
│       │   │   │   ├── api/                    # 统一响应体、分页
│       │   │   │   ├── enums/                  # 错误码枚举
│       │   │   │   └── exception/              # 业务异常 + 全局异常处理
│       │   │   ├── config/                     # 配置类
│       │   │   │   ├── WebMvcConfig.java       # 跨域 / 静态资源
│       │   │   │   ├── AiConfig.java           # ChatClient Bean
│       │   │   │   └── OpenApiConfig.java      # 接口文档
│       │   │   ├── controller/                 # 接口层
│       │   │   ├── service/                    # 业务接口
│       │   │   │   └── impl/                   # 业务实现
│       │   │   ├── repository/                 # 数据访问层
│       │   │   ├── entity/                     # 数据库实体
│       │   │   ├── dto/                        # 请求/响应对象
│       │   │   │   ├── request/
│       │   │   │   └── response/
│       │   │   └── ai/                         # RAG 相关（解析/切分/检索/生成）
│       │   └── resources/
│       │       ├── application.yml             # 主配置
│       │       ├── application-dev.yml         # 开发环境
│       │       ├── application-prod.yml        # 生产环境
│       │       ├── db/migration/               # Flyway 脚本
│       │       └── prompts/                    # 提示词模板
│       └── test/java/com/hnkj/qa/
└── frontend/                       # Vue 3 + Vite + Element Plus
    ├── package.json
    ├── vite.config.js
    ├── index.html
    ├── jsconfig.json
    ├── .env.development / .env.production
    └── src/
        ├── main.js
        ├── App.vue
        ├── api/                    # 接口封装
        ├── assets/                 # 静态资源
        ├── components/             # 通用组件
        ├── layouts/                # 布局
        ├── router/                 # 路由
        ├── stores/                 # Pinia
        ├── styles/                 # 全局样式
        ├── utils/                  # 工具函数
        └── views/                  # 页面
```

---

## 三、快速开始

### 1. 启动数据库

```bash
docker compose up -d
```

默认连接信息：`localhost:5432`，库 `campus_qa`，账号 `postgres` / `postgres`。

> **本机没有 Docker？** 可原生安装 PostgreSQL 16 + pgvector，无需容器：
> 参见 **[docs/local-db-setup.md](docs/local-db-setup.md)**。

### 2. 启动后端

```bash
cd backend
# 配置大模型 API Key（不配置则使用默认占位值，仅可启动无法真实调用）
export OPENAI_API_KEY=sk-xxxxxx
export OPENAI_BASE_URL=https://api.openai.com
mvn spring-boot:run
```

- 服务地址：http://localhost:8080/api
- 接口文档：http://localhost:8080/api/swagger-ui.html

### 3. 启动前端

```bash
cd frontend
npm install
npm run dev
```

- 访问地址：http://localhost:5173

### 4. 一键全栈（Docker）

若想把数据库、后端、前端一起用容器跑起来：

```bash
# 项目根目录
docker compose up -d --build
```

| 入口 | 地址 |
| --- | --- |
| 前端 | http://localhost |
| 后端接口文档 | http://localhost/api/swagger-ui.html |
| 数据库 | `localhost:5432`（外部工具可直连） |

大模型 Key 可通过项目根目录 `.env` 文件或环境变量 `OPENAI_API_KEY` 传入。

---

## 四、环境变量

| 变量 | 说明 | 默认值 |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | 运行模式：`local`（普通表+余弦检索）/ `railway`（pgvector） | `local` |
| `OPENAI_API_KEY` | 大模型 API Key | `demo-key` |
| `OPENAI_BASE_URL` | 接口地址（兼容 OpenAI 协议均可） | `https://api.openai.com` |
| `DB_HOST` / `DB_PORT` | 数据库地址 | `localhost` / `5432` |
| `DB_NAME` | 数据库名 | `campus_qa` |
| `DB_USERNAME` / `DB_PASSWORD` | 数据库账号 | `postgres` / `postgres` |
| `PGHOST` / `PGPORT` / `PGDATABASE` / `PGUSER` / `PGPASSWORD` | Railway Postgres 插件自动注入的变量（railway profile 优先使用） | - |

---

## 五、向量存储：双模式（本机免 pgvector）

本机 Windows 装不上 pgvector 扩展也没关系，系统内置两套向量存储，按 profile 一键切换，**业务代码完全一致**：

| | `local`（默认） | `railway` |
| --- | --- | --- |
| 向量存储 | 普通表 `t_vector_chunk`（embedding 以 JSONB 数组保存） | pgvector `vector_store` 表（HNSW 索引） |
| 相似度计算 | Java 侧余弦相似度，取 Top-K | pgvector 数据库内计算 |
| 是否需要 pgvector 扩展 | **不需要**（纯普通 PostgreSQL） | 需要（`CREATE EXTENSION vector;`） |
| 适用场景 | 本地开发、Docker、任何 PG | Railway / 生产部署 |

- 切换方式：`SPRING_PROFILES_ACTIVE=railway`（默认 `local`）
- 上传的 PDF 自动切分（`TokenTextSplitter`）→ 向量化（`text-embedding-3-small`）→ 入库，每个文本块带 `documentId` / `page_number` / `title` 元数据
- 性能边界：local 模式全量加载 + 内存余弦计算，适合数百~数千文本块；数据量大了切 railway 模式
- Railway 部署注意：Railway 默认 Postgres 模板**不含** pgvector，需用带 pgvector 的镜像自建数据库服务

## 六、当前进度与核心业务流程

### 已实现

- **PDF 上传接口**：`POST /api/upload`（字段名 `file`，multipart/form-data）
  - 保存到 `backend/uploads/` 目录（带时间戳前缀防重名）
  - 用 Apache PDFBox 提取全文并打印到控制台
  - 文档元信息落库 `t_document`；随后**自动切分 + 向量化入库**（local: `t_vector_chunk` / railway: pgvector）
  - 向量化失败不阻断上传：状态降级为 `FAILED`，可稍后调 `POST /api/documents/{id}/reindex` 重试
  - 返回：`{ code, message, data: { fileName, storedName, textLength, pageCount, documentId, status, chunkCount } }`
  - 测试：`curl -F "file=@某规章制度.pdf" http://localhost:8080/api/upload`
- **向量存储双模式**：`JdbcCosineVectorStore`（普通表 + Java 余弦 Top-3）与 pgvector 按 profile 切换
- 提取与向量库均有单元测试覆盖：`PdfIngestServiceTest`、`JdbcCosineVectorStoreTest`（6 用例全绿）

### 待实现（业务路线）

1. **检索问答**：`RagQaService.answer` 补全——Top-K 检索 → 组装上下文 → 生成回答 + 引用页码（存储层已就绪，直接调 `vectorStore.similaritySearch`）。
2. **文档管理**：`page / detail / delete / reindex` 接口补全，前端列表展示真实数据。
3. 问答记录落库 `t_chat_record`、流式输出。

详见 `docs/architecture.md`。
