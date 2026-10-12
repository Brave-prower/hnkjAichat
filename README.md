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

---

## 四、环境变量

| 变量 | 说明 | 默认值 |
| --- | --- | --- |
| `OPENAI_API_KEY` | 大模型 API Key | `demo-key` |
| `OPENAI_BASE_URL` | 接口地址（兼容 OpenAI 协议均可） | `https://api.openai.com` |
| `DB_HOST` / `DB_PORT` | 数据库地址 | `localhost` / `5432` |
| `DB_NAME` | 数据库名 | `campus_qa` |
| `DB_USERNAME` / `DB_PASSWORD` | 数据库账号 | `postgres` / `postgres` |

---

## 五、核心业务流程（待实现）

1. **上传**：学生端上传学校规章制度 PDF → 存入对象存储/本地目录，落库文档元信息。
2. **解析**：PDFBox / Spring AI `PagePdfDocumentReader` 提取文本，**按页保留页码元数据**。
3. **切分**：`TokenTextSplitter` 切块，每块携带 `documentId` + `pageNumber`。
4. **向量化**：Embedding 模型生成向量，写入 pgvector。
5. **检索**：问题向量化 → 相似度检索 Top-K → 组装上下文。
6. **生成**：Prompt 约束模型「仅依据上下文作答」，并要求输出引用页码。
7. **返回**：答案 + 来源列表（文档名、页码、原文片段）。

详见 `docs/architecture.md`。
