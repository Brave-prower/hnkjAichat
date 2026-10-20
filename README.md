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

## 📑 目录

**[⚡ 2.2 可复现性声明与重现指南](#-10-分钟极速复现指南)** —— 评审重点，评委请从这里开始

| 小节 | 内容 |
| --- | --- |
| [2.2.1](#221-可复现性声明) | 可复现性声明（含不可复现因素的消除、复现边界） |
| [2.2.2](#222-环境配置说明) | 环境配置说明（硬件、系统、端口、两条路径取舍） |
| [2.2.3](#223-依赖清单) | 依赖清单（运行环境 / 后端 / 前端 / 镜像 / 外部服务，均为实际版本） |
| [2.2.4](#224-环境变量模板) | 环境变量模板（两份模板的分工 + 变量速查表 + 高频错误） |
| [路径 A](#-路径-adocker-一键启动推荐3-分钟) | Docker 一键启动（推荐，评委复现用这个） |
| [路径 B](#-路径-b本地开发模式改代码用) | 本地开发模式（改代码用） |
| [验收标准](#-复现验收标准) | 复现验收标准（6 项可观察判定） |

其余章节：[技术栈](#一技术栈) · [系统架构](#二系统架构) · [目录结构](#三目录结构) · [核心设计](#五核心设计) · [API 一览](#六api-一览) · [FAQ](#七常见问题faq)

---

## 📌 一图看懂进度

| 模块 | 状态 | 说明 |
| --- | --- | --- |
| PDF 上传与解析 | ✅ 完成 | PDFBox 提取文本，切分入库，失败自动降级 |
| 语义检索（RAG） | ✅ 完成 | bge-m3 1024 维 + 余弦 Top-3，阈值 0.6 |
| 流式问答（SSE） | ✅ 完成 | `sources → message* → done`，首字 < 2s |
| 检索透明化面板 | ✅ 完成 | 展示候选分数、阈值线、闸门结论，可现场自证 |
| 三层防幻觉 | ✅ 完成 | 相关性闸门 + 来源页码标注 + 全链路降级 |
| 文档管理（增删改查） | ✅ 完成 | 分页 / 详情 / 删除 / 重建索引 |
| 向量存储双模式 | ✅ 完成 | local 免 pgvector / railway 用 pgvector，同一接口切换 |
| 容器化交付 | ✅ 完成 | 三容器 Compose 一键起，全部经实测 |
| 用户登录与权限 | ⏳ 未实现 | 当前为单租户内部系统，无鉴权 |
| 反馈闭环（点赞/纠错） | ⏳ 待开发 | 已有实施方案，见 `docs/prompt-history/` |

> 上面「未实现」的两项是**有意的范围取舍**，不是遗漏：本项目定位是校内单租户工具，第一版优先把「回答可信、来源可查、部署可复现」做到位。

---

## ⚡ 10 分钟极速复现指南

> 对应评审要求 **2.2 可复现性声明与重现指南**。本章分为四块：**可复现性声明** → **环境配置说明** → **依赖清单** → **环境变量模板**，之后是两种启动路径与验收标准。按顺序走完即可从零跑通。

---

### 2.2.1 可复现性声明

#### 结论先行

本项目**在全新机器上，仅凭本仓库 + 两个 API Key，即可在 10 分钟内完整复现**，无需修改任何源码，无需手工建表，无需安装 pgvector 扩展。

#### 已消除的不可复现因素

复现失败通常来自五个隐藏变量。本项目逐个做了处理：

| 不可复现因素 | 本项目的处理 | 验证状态 |
| --- | --- | --- |
| **数据库结构漂移**——手工执行 SQL、建表语句散落 | Flyway 迁移脚本 `V1~V4` 随启动自动执行，版本号可查 | ✅ 脚手架启动即建表 |
| **依赖版本漂移**——`npm install` 拉到不兼容的新版本 | 后端版本固定在 `pom.xml`；前端已提交 `package-lock.json`；Docker 构建基于固定基础镜像 | ✅ 版本清单见 2.2.3 |
| **环境差异**——"我这儿能跑" | 全链路容器化：同一镜像在任意装了 Docker 的机器上行为一致 | ✅ 三容器实测通过 |
| **配置散落**——Key 硬编码、路径写死 | 全部走环境变量，启动时集中校验；仓库只提交 `.env.example` 占位模板 | ✅ 见 2.2.4 |
| **locale / 编码坑**——中文路径、中文文件名偶发失败 | 容器 locale 固定 `C.UTF-8`；落盘文件名用纯 ASCII 的 `时间戳_UUID` | ✅ 中文名 PDF 实测 200 |

#### 复现所需的最小外部依赖

- **一个 DeepSeek API Key**（生成回答）——免费注册即可获得
- **一个硅基流动 API Key**（向量化，`BAAI/bge-m3` 免费模型）——需实名认证

> 💡 **两个 Key 都缺失也能跑通基本流程**：上传、解析、切分、关键词检索、页面展示全部可用，只是问答退化为「返回制度原文摘录」。因此即使评委当天拿不到 Key，也能验证系统骨架与降级链路。

#### 数据可复现性

| 维度 | 说明 |
| --- | --- |
| 示例语料 | 自带上传入口，评委可上传任意学校制度 PDF 自行验证 |
| 演示脚本 | `docs/scripts/gen-demo-traces.py` 调用**真实接口**跑 7 个预设问题，输出真实的分数与闸门结论，**零造假** |
| 关键结论可复算 | 压线对比组 `0.6146 → PASS` / `0.5833 → REJECT` 每次运行均可复现（已在容器化环境实测一致） |
| 随机性 | 检索为确定性余弦相似度计算，同一语料同一问题分数稳定；仅大模型生成的措辞存在轻微非确定性 |

#### 复现结果的历史记录

| 环境 | 结论 | 备注 |
| --- | --- | --- |
| Docker Compose（三容器） | ✅ 全链路通过 | 上传 / 检索 / SSE 流式 / 透明化面板 / 降级链路 |
| 本地开发模式（JDK + Node + 本机 PG） | ✅ 全链路通过 | 免 pgvector 扩展 |

#### 已知的复现边界（**主动声明，非隐瞒**）

以下情形**不在**本文档保证范围内，遇到时请按相应提示处理：

1. **80 端口被占用**——Windows 上 `System` 进程（`http.sys`）常年占用 80。改 `docker-compose.yml` 端口映射即可，详见 [FAQ](#faq-ports)。
2. **国内网络拉取基础镜像缓慢**——已内置阿里云 Maven 源；Docker 需自行配置 `registry-mirrors`。
3. **首次构建耗时 10~15 分钟**——需下载约 1.5GB 基础镜像与依赖；**重新构建仅需 1~2 分钟**。答辩演示建议提前构建好镜像，现场直接 `up`。
4. **`railway` profile 未纳入保证**——该 profile 使用 pgvector，需要带扩展的 PG 实例。默认 `local` profile 已覆盖全部功能。
5. **无用户鉴权**——当前版本为校内单租户工具，所有接口不校验身份，**不可直接暴露到公网**。

---

### 2.2.2 环境配置说明

#### 硬件与系统要求

| 项 | 最低 | 推荐 |
| --- | --- | --- |
| 内存 | 4 GB | 8 GB（后端容器约占 600 MB） |
| 磁盘 | 5 GB 可用 | 10 GB（镜像 + Maven 依赖约 1.5 GB） |
| 操作系统 | Windows 10 / macOS 12 / 主流 Linux | 同左 |
| 网络 | 可访问 Docker Hub 与 API 服务 | 配置国内镜像源加速 |

#### 两条启动路径的取舍

| | 🐳 路径 A：Docker（推荐） | 🛠 路径 B：本地开发 |
| --- | --- | --- |
| 需要预装 | **只要 Docker Desktop** | JDK 17 + Maven + Node 18+ + PostgreSQL 14+ |
| 环境一致性 | 高（同一镜像） | 取决于本机环境 |
| 启动耗时 | 首次构建 10~15 min，之后 < 1 min | 首次下载依赖 5~8 min |
| 适合场景 | **评委复现、答辩演示**、交付验收 | 改代码、调试 |

> **给评委的建议**：走路径 A。不需要装 JDK / Maven / Node / PostgreSQL 任何一个。

#### 端口占用说明

| 端口 | 用途 | 冲突可能 |
| --- | --- | --- |
| 80 | 前端入口（Nginx） | **Windows 高概率被 `http.sys` 占用** |
| 8080 | 后端 API / Swagger | 常被其他 Java 项目占用 |
| 5432 | PostgreSQL | 常被本机已装 PG 占用 |

冲突处理统一方式是改 `docker-compose.yml` 的 `ports` 冒号左侧，详见 [FAQ](#faq-ports)。

---

### 2.2.3 依赖清单

#### 运行环境

| 依赖 | 版本 | 说明 |
| --- | --- | --- |
| Docker Desktop | 4.x（含 Compose v2） | 路径 A 唯一前置条件 |
| Docker Engine | 24+ | Linux 环境单独装 |
| JDK | **17**（LTS） | 路径 B 需要 |
| Maven | 3.8+ | 路径 B 需要 |
| Node.js | 18+（实测 22.12） | 路径 B 需要 |
| PostgreSQL | 14+（实测 18.6） | 路径 B 需要，**无需 pgvector 扩展** |
| Python | 3.8+ | 仅演示脚本用，只用标准库 |

#### 后端依赖（`backend/pom.xml` 实际版本）

| 依赖 | 版本 | 用途 |
| --- | --- | --- |
| Spring Boot | **3.4.1** | 基础框架 |
| Spring AI | **1.0.0** | Embedding / 向量存储抽象 |
| `spring-ai-starter-model-openai` | 1.0.0 | OpenAI 协议 Embedding 客户端 |
| `spring-ai-starter-vector-store-pgvector` | 1.0.0 | 仅 `railway` profile 使用 |
| `spring-ai-pdf-document-reader` | 1.0.0 | 按页读取 PDF，保留页码 |
| Apache PDFBox | **3.0.3** | PDF 文本提取 |
| springdoc-openapi | **2.7.0** | Swagger 接口文档 |
| Flyway | 随 Boot 版本 | 数据库迁移 V1~V4 |
| Lombok | 随 Boot 版本 | 简化样板代码 |

#### 前端依赖（`frontend/package.json` 实际版本）

| 依赖 | 版本 | 用途 |
| --- | --- | --- |
| Vue | **3.5.13** | 组合式 API + `<script setup>` |
| Vite | **6.0.7** | 构建工具 |
| Vue Router | 4.5.0 | 路由 |
| Pinia | 2.3.0 | 状态管理 |
| Element Plus | **2.9.1** | UI 组件库 |
| `@element-plus/icons-vue` | 2.3.1 | 图标 |
| Axios | 1.7.9 | HTTP 客户端 |
| marked | 18.1.1 | Markdown 渲染 |
| DOMPurify | 3.4.17 | 防 XSS |
| Sass | 1.83.0 | 样式预处理 |
| `@vitejs/plugin-vue` | 5.2.1 | Vue 插件 |

#### 基础镜像

| 镜像 | 标签 | 用途 |
| --- | --- | --- |
| `maven` | `3.9-eclipse-temurin-17` | 后端构建阶段 |
| `eclipse-temurin` | `17-jre-jammy` | 后端运行阶段 |
| `node` | `20-alpine` | 前端构建阶段 |
| `nginx` | `1.27-alpine` | 前端运行（静态资源 + 反代） |
| `pgvector/pgvector` | `pg16` | 数据库（兼容普通 PG 用法） |

#### 外部服务

| 服务 | 模型 | 是否必需 | 免费额度 |
| --- | --- | --- | --- |
| DeepSeek | `deepseek-flash` | **强烈建议** | 注册赠送少量额度 |
| 硅基流动 | `BAAI/bge-m3`（1024 维） | 可选（缺失自动降级） | **该模型免费** |

---

### 2.2.4 环境变量模板

**仓库只提交占位值，真实 Key 绝不入库。** 两份模板用途不同，别拿错：

| 模板文件 | 给谁用 | 拿到后怎么做 |
| --- | --- | --- |
| [`.env.example`](.env.example)（根目录） | **路径 A：Docker Compose** | 复制为根目录 `.env`，compose 自动读取 |
| [`backend/.env.example`](backend/.env.example) | **路径 B：本地开发** | 复制为 `backend/.env`，或在 IDE / 系统环境变量中设置 |

**根目录 `.env.example` 原文（Docker 路径）**：

```bash
# ---------- 生成回答：DeepSeek（必需）----------
DEEPSEEK_API_KEY=
DEEPSEEK_BASE_URL=https://api.deepseek.com/v1
DEEPSEEK_MODEL=deepseek-flash

# ---------- 文本向量化：硅基流动（可选）----------
OPENAI_API_KEY=
OPENAI_BASE_URL=https://api.siliconflow.cn
AI_EMBEDDING_MODEL=BAAI/bge-m3

# ---------- 运行环境 ----------
SPRING_PROFILES_ACTIVE=local
```

**变量速查表**：

| 变量 | 必填 | 说明 | 默认值 |
| --- | --- | --- | --- |
| `DEEPSEEK_API_KEY` | 是 | 生成回答的密钥 | — |
| `DEEPSEEK_BASE_URL` | 否 | DeepSeek 接口地址 | `https://api.deepseek.com` |
| `DEEPSEEK_MODEL` | 否 | 生成模型 | `deepseek-flash` |
| `OPENAI_API_KEY` | 否 | Embedding 密钥（缺失降级关键词检索） | — |
| `OPENAI_BASE_URL` | 否 | Embedding 地址，**结尾不带 `/v1`** | `https://api.siliconflow.cn` |
| `AI_EMBEDDING_MODEL` | 否 | 嵌入模型（1024 维） | `BAAI/bge-m3` |
| `SPRING_PROFILES_ACTIVE` | 否 | `local`（免 pgvector）/ `railway` | `local` |
| `DB_HOST` / `DB_PORT` / `DB_NAME` | 否 | 数据库连接 | `localhost:5432/campus_qa` |
| `DB_USERNAME` / `DB_PASSWORD` | 否 | 数据库账号 | `postgres` / `postgres` |
| `QA_TOP_K` | 否 | 检索返回条数 | `3` |
| `QA_SIMILARITY_THRESHOLD` | 否 | 相似度阈值 | `0.6` |

**两个高频配置错误**（都已在 FAQ 收录）：

1. `OPENAI_BASE_URL` 写成 `https://api.siliconflow.cn/v1` → Spring AI 再拼 `/v1/embeddings`，变成 `/v1/v1/embeddings` → **404**，表现为「向量入库 N 个文本块（无向量 · 关键词检索模式）」。
2. 硅基流动账号**未实名认证**就创建 Key → 服务端返回 **401 `code=30014 Token is invalid`**，症状与 Key 抄错完全一样，极易误判。

---

### 🐳 路径 A：Docker 一键启动（推荐，3 分钟）

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

**第 3 步：确认三个服务都 healthy**

```bash
docker compose ps
```

预期输出（三个服务均为 `Up (healthy)`）：

```
NAME                 STATUS
campus-qa-postgres   Up (healthy)
campus-qa-backend    Up (healthy)
campus-qa-frontend   Up (healthy)
```

后端首次启动需建表 + 初始化向量表，`start-period` 设为 90 秒，这段时间显示 `starting` 属正常。

**第 4 步：访问**

| 地址 | 内容 |
| --- | --- |
| http://localhost | 前端（上传规章制度 / 智能问答） |
| http://localhost:8080/api/swagger-ui.html | 后端接口文档 |

打开 http://localhost → **上传规章制度** 页传一份 PDF → 切到**智能问答**提问。

> ⚠️ **如果 http://localhost 打开的不是本系统**（比如是 Docker 的欢迎页、IIS 默认页、别的项目），说明 80 端口被占了。这是 Windows 上最常见的情况——**System 进程（`http.sys`）常年占着 80**。
>
> 处理方式：改 `docker-compose.yml` 里 frontend 的端口映射，例如改成 `"8888:80"`，然后访问 http://localhost:8888 即可。完整改法见下方 [端口被占用怎么办](#faq-ports)。
>
> 一键确认当前是否被占：
>
> ```bash
> docker compose ps          # 看 frontend 的 PORTS 列，确认实际映射到哪个宿主机端口
> ```

**常用运维命令**

```bash
docker compose ps                # 查看三个服务状态（都应 healthy）
docker compose logs -f backend   # 跟踪后端日志
docker compose down              # 停止（数据保留在 volume 中）
docker compose down -v           # 停止并清空数据（含已上传文档）
```

> **卡住了？** 见文末「[Docker 常见问题](#docker-常见问题)」。

---

### 🛠 路径 B：本地开发模式（改代码用）

适合需要调试源码的场景，需自行安装 JDK 17 / Maven / Node 18+ / PostgreSQL 14+（版本要求见 2.2.3）。

#### 第 1 步：启动数据库（1 分钟）

```bash
# 方式 A：本机已装 PostgreSQL，直接建库（本项目的向量存储不需要 pgvector 扩展）
psql -U postgres -c "CREATE DATABASE campus_qa;"

# 方式 B：只用 Docker 起一个数据库，前后端仍在本机跑
docker compose up -d postgres
```

#### 第 2 步：申请两个 Key（3 分钟）

| Key | 用途 | 申请地址 |
| --- | --- | --- |
| `DEEPSEEK_API_KEY` | 生成回答 | https://platform.deepseek.com |
| `OPENAI_API_KEY` | 文本向量化（Embedding） | https://cloud.siliconflow.cn （实名认证后创建，`BAAI/bge-m3` 模型免费） |

> 为什么需要两个 Key？DeepSeek 官方**不提供 Embedding 接口**，所以生成与向量化分离——这也是本项目「检索/生成解耦」架构的体现。

#### 第 3 步：配置环境变量（1 分钟）

```bash
# 参考模板：backend/.env.example（复制后填入你的 Key）
# Windows PowerShell 示例：
$env:DEEPSEEK_API_KEY  = "sk-你的DeepSeek密钥"
$env:OPENAI_API_KEY    = "sk-你的硅基流动密钥"
$env:OPENAI_BASE_URL   = "https://api.siliconflow.cn"   # 注意：不要带 /v1
$env:AI_EMBEDDING_MODEL = "BAAI/bge-m3"
```

#### 第 4 步：启动前后端（2 分钟）

```bash
# 后端（首次会自动下载依赖，Flyway 自动建表）
cd backend && mvn spring-boot:run

# 新开一个终端，前端
cd frontend && npm install && npm run dev
```

打开 http://localhost:5173 → **上传规章制度** 页传一份 PDF → 切到**智能问答**提问。

#### ⚠️ 本地开发的一个已知问题

端口已在 `application.yml` 中固定为 `8080`，无需额外指定。但若启动时报：

```
java.lang.ClassFormatError: Invalid method Code length 0
```

这是 Lombok 增量编译产生的损坏 class 文件（重复编译后偶发），执行一次完整清理即可：

```bash
cd backend && mvn clean compile
```

---

### ✅ 复现验收标准

按顺序执行下列 6 项，全部通过即视为复现成功。**每项都给出了可观察的判定依据**，不需要读代码：

| # | 检查项 | 通过标准 | 判定方式 |
| --- | --- | --- | --- |
| 1 | 服务就绪 | 三个容器均 `healthy`；`GET /api/system/health` 返回 `code: 0` | `docker compose ps` + 浏览器访问 |
| 2 | 上传入库 | 返回「提取字数 / 页数 / 文本块数」，`status: READY` | 上传页结果卡片 |
| 3 | 流式问答 | 提问后 **2~3 秒**内开始输出，回答为 Markdown | 问答页观察 |
| 4 | 来源可核对 | 回答下方 3 条可展开来源卡，含页码标签与相似度 | 点击展开 |
| 5 | 防幻觉 | 问制度外问题（如「今天天气如何」），回答「制度中未涉及」而非编造 | 问答页 |
| 6 | 检索透明化 | 点开「为什么这样答」面板：模式徽标、候选分数条（含阈值刻度线）、命中标记、闸门结论齐全 | 面板截图 |

**一键复现演示数据**（含压线对比，对应第 6 项）：

```bash
# 本地开发模式（后端 8080）
python docs/scripts/gen-demo-traces.py

# Docker 模式（经 Nginx 反代，端口填你实际的宿主机端口）
python docs/scripts/gen-demo-traces.py http://localhost:8888
```

脚本会调用真实接口跑 7 个问题，控制台直接输出分数与闸门结论，完整 trace 存到 `docs/scripts/demo-traces-result.json`（已 gitignore）。只依赖 Python 标准库，无需 `pip install`。其中第 ⑥⑦ 条是**压线对比组**，最能说明闸门机制：

| # | 问题 | Top1 分数 | 与阈值 0.6 的差 | 结论 |
|---|---|---|---|---|
| ⑥ | 校园内吸烟会被处分吗 | 0.6146 | **+0.0146** | ✅ 放行 |
| ⑦ | 学生的权利有哪些 | 0.5833 | **−0.0167** | ❌ 拒答 |

两者**仅差 0.031 分，结论完全相反**——这就是「宁可不答也不编造」的直接证据，答辩时对着面板截图讲最有说服力。

> 该组分数已在两个独立环境（本地开发模式、Docker 容器化）分别复算，结论一致。

---

## 一、技术栈

> 精确版本见 [2.2.3 依赖清单](#223-依赖清单)。本节只做概述。

### 前端
| 组件 | 版本 | 说明 |
| --- | --- | --- |
| Vue | 3.5.13 | 组合式 API + `<script setup>` |
| Vite | 6.0.7 | 构建工具 |
| Vue Router / Pinia | 4.5.0 / 2.3.0 | 路由 / 状态管理 |
| Element Plus | 2.9.1 | UI 组件库 |
| marked + DOMPurify | 18.1.1 / 3.4.17 | Markdown 渲染 + 防 XSS |
| Sass | 1.83.0 | 样式预处理（设计令牌系统） |

### 后端
| 组件 | 版本 | 说明 |
| --- | --- | --- |
| Java | 17 | LTS |
| Spring Boot | 3.4.1 | 基础框架 |
| Spring AI | 1.0.0 | Embedding / 向量存储抽象层 |
| Spring Data JPA + Flyway | 随 Boot 版本 | 数据访问 / 数据库版本管理（V1~V4） |
| PostgreSQL | 14+ | 业务数据 + 向量存储（默认免 pgvector） |
| Apache PDFBox | 3.0.3 | PDF 解析 |
| springdoc-openapi | 2.7.0 | 接口文档（/api/swagger-ui.html） |

### 大模型
| 用途 | 供应商 | 模型 | 说明 |
| --- | --- | --- | --- |
| 生成回答 | DeepSeek | `deepseek-flash` | 流式 SSE 输出；显式关闭思维链（`thinking: disabled`），避免「等思考完才吐首字」 |
| 文本向量化 | 硅基流动 | `BAAI/bge-m3`（1024 维） | 免费模型，任何 OpenAI 协议兼容服务均可替换 |

> **为什么是两个供应商？** DeepSeek 官方**不提供 Embedding 接口**（只有 Chat Completions），所以「生成」与「向量化」必须拆开。这也正好对应本项目「检索/生成解耦」的架构——任一侧故障都不影响另一侧，可各自降级。

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
├── README.md                       # 本文件（复现指南 + 设计说明 + FAQ）
├── PROJECT-GUIDE.md                # 新手向逐文件说明书
├── .env.example                    # ← Docker Compose 用的环境变量模板
├── docker-compose.yml              # 三服务编排（postgres / backend / frontend）
├── docs/
│   ├── architecture.md             # 架构与数据流
│   ├── TECH-DOC.md                 # 技术文档（意图控制/架构/可复现性）
│   ├── local-db-setup.md           # 本机免 Docker 建库指南
│   ├── prompt-history/             # AI 协同演进记录（Prompt 链快照）
│   └── scripts/
│       └── gen-demo-traces.py      # 透明化面板演示数据一键生成
├── backend/                        # Java 17 + Spring Boot 3
│   ├── Dockerfile                  # 多阶段构建（Maven 打包 → JRE 运行）
│   ├── docker-mvn-settings.xml     # 阿里云 Maven 镜像（仅构建期）
│   ├── .env.example                # ← 本地开发用的环境变量模板
│   └── src/main/
│       ├── java/com/hnkj/qa/
│       │   ├── ai/                 # RAG 核心
│       │   │   ├── vector/         #   JdbcCosineVectorStore（含 Embedding 熔断器）
│       │   │   ├── deepseek/       #   DeepSeek 客户端（同步/流式 + 网络重试）
│       │   │   ├── embedding/      #   EmbeddingService 封装
│       │   │   ├── RetrievalTrace  #   检索透明化的数据结构
│       │   │   └── RagQaService    #   检索→上下文→生成→来源引用
│       │   ├── controller/         # 上传/文档/问答/健康检查 接口层
│       │   ├── service/            # 业务实现（控制器不写业务逻辑）
│       │   ├── repository/ entity/ dto/
│       │   ├── common/             # 统一响应体 / 错误码 / 全局异常处理
│       │   └── config/             # 向量存储装配、DeepSeek、跨域等
│       └── resources/
│           ├── db/migration/       # Flyway V1~V4
│           └── prompts/            # qa-system-prompt.st（防幻觉提示词）
└── frontend/                       # Vue 3 + Vite
    ├── Dockerfile / nginx.conf     # Nginx 托管静态资源 + 反代 /api
    └── src/
        ├── components/             # MarkdownText / SourceCitation / RetrievalTracePanel
        ├── views/                  # upload / qa / document / home
        ├── api/                    # axios 封装（统一错误映射）
        ├── stores/chat.js          # 流式消息状态机
        └── styles/                 # 设计令牌 variables.scss
```

## 四、环境变量

项目有**两份**模板，用途不同，别拿错：

| 模板文件 | 给谁用 | 怎么用 |
| --- | --- | --- |
| [`.env.example`](.env.example)（根目录） | **Docker Compose** | 复制为根目录 `.env`，`docker compose up` 自动读取 |
| [`backend/.env.example`](backend/.env.example) | **本地开发**（`mvn spring-boot:run`） | 复制为 `backend/.env`，或在 IDE / 系统环境变量里设置 |

核心变量：

| 变量 | 说明 | 默认值 |
| --- | --- | --- |
| `DB_HOST` / `DB_PORT` / `DB_NAME` | 数据库连接 | `localhost:5432/campus_qa`（容器内为 `postgres:5432`） |
| `DB_USERNAME` / `DB_PASSWORD` | 数据库账号 | `postgres` / `postgres` |
| `DEEPSEEK_API_KEY` | **必填**，生成回答 | — |
| `DEEPSEEK_BASE_URL` | DeepSeek 接口地址 | `https://api.deepseek.com` |
| `DEEPSEEK_MODEL` | 生成模型 | `deepseek-flash` |
| `OPENAI_API_KEY` | Embedding（**不填自动降级为关键词检索**） | — |
| `OPENAI_BASE_URL` | Embedding 服务地址（**结尾不带 `/v1`**） | `https://api.siliconflow.cn` |
| `AI_EMBEDDING_MODEL` | 嵌入模型 | `BAAI/bge-m3` |
| `SPRING_PROFILES_ACTIVE` | `local`（免 pgvector）/ `railway`（pgvector） | `local` |
| `QA_TOP_K` / `QA_SIMILARITY_THRESHOLD` | 检索条数 / 相似度阈值 | `3` / `0.6` |

> 🔑 **只有 `DEEPSEEK_API_KEY` 是真正的必填项**。没有它，问答会退化成「返回制度原文摘录」；没有 `OPENAI_API_KEY`，检索会退化成中文关键词匹配。两个都缺也不算崩，只是体验打折——这是有意的设计。
>
> 🔒 真实 Key 只放 `.env` 或系统环境变量，`.env` 已在 `.gitignore` 中。仓库里永远只提交占位值。

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

| 机制 | 参数 | 解决什么问题 |
| --- | --- | --- |
| **Embedding 熔断器** | 连续失败 3 次熔断，冷却 3 分钟 | 网络抖动时不至于一次失败就长时间降级；熔断期间直接走关键词路，不空等超时 |
| **DeepSeek 网络重试** | 网络类异常重试 1 次（4xx/5xx 不重试） | 单次连接抖动不至于让用户看到「服务不可用」 |
| **流式重试保护** | 仅在「尚未推送任何 token」时重试 | 已吐出的正文不会因重试而重复输出 |
| **SSE 流式问答** | 独立线程池，超时 180s | 长回答不被中断，也不占用请求线程 |
| **全链路降级** | 向量化失败→关键词；生成失败→原文摘录 | 上游任何一家挂掉，系统仍能给出有用回答 |
| **数据库版本管理** | Flyway V1~V4 | clone 后启动即自动建表，无需手工执行 SQL |

> 📊 熔断器参数与降级逻辑的详细说明见 [docs/TECH-DOC.md](docs/TECH-DOC.md)。实测效果：关键词兜底模式下响应约 42s，恢复语义检索后降到 1.5~3s。

## 六、API 一览

后端统一前缀 `/api`（`server.servlet.context-path=/api`），响应体统一为 `{ code, message, data, timestamp }`，`code = 0` 表示成功。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/upload` | 上传 PDF（multipart，字段名 `file`），返回统计与 documentId |
| GET | `/api/documents?page=1&size=10&keyword=` | 文档分页列表（`keyword` 可选，按标题模糊搜索） |
| GET | `/api/documents/{id}` | 文档详情 |
| GET | `/api/documents/{id}/chunks` | 文档切分后的文本块列表（页码 / 字数 / 是否已向量化） |
| GET | `/api/documents/{id}/file` | PDF 原文（`inline` 内嵌预览；`?download=true` 下载） |
| DELETE | `/api/documents/{id}` | 逻辑删除文档及其向量 |
| POST | `/api/documents/{id}/reindex` | 重建索引（重新切分 + 向量化） |
| POST | `/api/qa/ask` | 同步问答（一次返回完整回答 + trace） |
| POST | `/api/qa/ask/stream` | **SSE 流式问答**（`text/event-stream`） |
| GET | `/api/qa/history?sessionId=` | 指定会话的历史问答记录 |
| GET | `/api/system/health` | 业务健康检查（数据库 / 向量库 / Key 配置状态） |
| GET | `/api/actuator/health` | Actuator 标准健康端点（容器 HEALTHCHECK 用） |

> ⚠️ **注意分页接口没有 `/page` 后缀**。写成 `/api/documents/page` 会被路由到 `/api/documents/{id}`，`"page"` 转 `Long` 失败直接 500——这是个很容易踩的坑。

**SSE 事件协议**（提问时的推送顺序）：

```
event: sources     ← 引用来源数组（正文之前先到，前端可先渲染来源卡）
event: message     ← 增量正文，可能多次
event: done        ← [DONE] 结束标记
event: error       ← 出错时推送（连接不中断，前端展示友好文案）
```

交互式文档：启动后端后访问 http://localhost:8080/api/swagger-ui.html（本地开发模式）

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

<a id="faq-ports"></a>

<details>
<summary><b>端口 80 / 8080 / 5432 被占用？</b></summary>

改 `docker-compose.yml` 里对应服务的 `ports`，冒号左边是宿主机端口。例如前端被占用：

```yaml
ports:
  - "8888:80"    # 改为访问 http://localhost:8888
```

**怎么判断真的被占了？** 启动后 `docker compose ps` 看 frontend 的 PORTS 列，
如果显示 `0.0.0.0:80->80/tcp` 但浏览器打开是别的东西，就是被占了。

**Windows 上 80 端口几乎总是被占**——`System` 进程（`http.sys` 驱动）长期持有它，
IIS、SQL Server Reporting Services、部分 VPN 客户端都会用到。查一下是谁：

```powershell
netstat -ano | findstr ":80 "
```

已知本项目实测环境就是这种情况，最终改用 `9088:80` 正常访问。

后端 8080、数据库 5432 同理；5432 常被本机已装的 PostgreSQL 占用，
若不需要从宿主机直连数据库，直接删掉 postgres 的 `ports` 两行最省事。
</details>

<details>
<summary><b>启动后提问只返回制度原文，没有 AI 回答？</b></summary>

`DEEPSEEK_API_KEY` 没配或无效。确认根目录 `.env` 已填 Key，然后：

```bash
docker compose up -d --force-recreate backend
```
</details>

<details>
<summary><b>上传 PDF 失败 / 提示「服务器处理失败」/ 413？</b></summary>

先看后端日志拿到真实原因：

```bash
docker compose logs backend | tail -50
```

本项目已修复两个历史上会导致上传失败的缺陷（v1.0.0 之后）：

1. **中文文件名编码**：早期 `Dockerfile` 用了镜像内不存在的 `zh_CN.UTF-8` locale，
   JVM 回退到 ASCII，中文文件名构造路径时抛 `InvalidPathException`。
   现改用 `C.UTF-8`，并把落盘文件名改为纯 ASCII 的「时间戳 + UUID」
   （原始文件名仍完整保存在数据库并正常展示）。
2. **Nginx 默认 1MB 体积上限**：早期 `nginx.conf` 未设 `client_max_body_size`，
   超过 1MB 的 PDF 在代理层直接被 413 拒绝。现放开到 60MB，与后端上限对齐。

若你的镜像是在修复前构建的，重新构建即可：

```bash
docker compose up -d --build
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
<summary><b>首次构建镜像很慢或卡在依赖下载？</b></summary>

项目已内置两层加速：Maven 依赖走阿里云镜像源（`backend/docker-mvn-settings.xml`，仅构建期生效），Docker Hub 基础镜像可在 Docker Desktop 的 Docker Engine 设置中添加 `registry-mirrors`（国内网络实测必需）。

首次构建需下载基础镜像 + 全部依赖（约 1.5GB），国内网络通常 10~15 分钟；之后源码改动触发的重建只编译增量，1~2 分钟即可。
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
- [docs/scripts/gen-demo-traces.py](docs/scripts/gen-demo-traces.py) —— 演示数据一键生成脚本（真实接口，零造假）
- [docs/prompt-history/](docs/prompt-history/) —— AI 协同演进记录（核心迭代的 Prompt 链快照）
- [docs/architecture.md](docs/architecture.md) —— 架构与数据流

> **复现遇到问题？** 先看 [FAQ](#七常见问题faq)，其中的 Docker 常见问题覆盖了端口冲突、构建缓慢、上传失败、容器 unhealthy 等高发场景。若仍未解决，运行 `docker compose logs backend | tail -50` 并把输出附上，绝大多数问题都能直接从日志定位。
