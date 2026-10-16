# 项目地图 · 每个文件是干什么的

> 这份文档面向第一次接触本项目的同学。读完它，你会知道**每个文件放在哪、干什么用、什么时候需要动它**。
>
> 项目：**湖南科技职业学院 · 校园问答系统**（把学校规章制度 PDF 变成能对话的问答机器人）

---

## 一、先搞懂这个项目在做什么

用大白话讲，它做了这么一件事：

```
你把一份《学生管理规定》的 PDF 传上去
        ↓
系统把 PDF 里的文字全部抽出来，切成一小块一小块（叫"文本块"）
        ↓
每一块都换算成一串数字（叫"向量"），存进数据库
        ↓
你提问"请销假怎么办手续？"
        ↓
系统也把你的问题换算成数字，去数据库里找出"方向和它最像"的几块原文
        ↓
把这几块原文塞给 AI（DeepSeek），让它"看着材料"写答案
        ↓
答案 + 引用的原文页码，一起返回给你
```

这套做法有个专门的名字叫 **RAG**（检索增强生成）——先检索资料，再让 AI 基于资料回答。**好处是 AI 不能瞎编**，因为它的"材料"是你上传的制度原文，而且会标出页码，你可以去核对。

### 一句话架构

```
浏览器(Vue 页面)  ←→  后端(Java Spring Boot)  ←→  数据库(PostgreSQL)
                            ↓            ↑
                       DeepSeek(写答案)  向量化服务(算数字)
```

---

## 二、怎么把项目跑起来

**需要两样东西**：数据库 + 后端 + 前端（三个都要开着）。

| 第几步 | 做什么 | 命令 / 位置 |
|---|---|---|
| 1 | 启动数据库 | 双击或运行 PostgreSQL，本机在 `D:\postgresql-18.6-5-windows-x64-binaries\pgsql` |
| 2 | 启动后端 | 在 `backend` 目录执行 `mvn spring-boot:run`，或双击项目根目录的 `run-backend.ps1` |
| 3 | 启动前端 | 在 `frontend` 目录执行 `npm run dev` |
| 4 | 打开浏览器 | 访问 `http://localhost:5173` |

> **端口记住三个数**：前端 `5173`、后端 `8080`、数据库 `5432`。

---

## 三、顶层目录总览

打开项目文件夹，你会看到这些东西：

```
D:\hnjgAichat\
├── backend/            ← 后端代码（Java，负责所有"干活"的逻辑）
├── frontend/           ← 前端代码（Vue，负责所有"给人看"的界面）
├── docs/               ← 项目文档（架构说明、数据库安装教程）
├── docker-compose.yml  ← 一键用 Docker 起全套服务（本机 Docker 装不上，暂未使用）
├── run-backend.ps1     ← 一键启动后端的脚本（Windows PowerShell）
├── README.md           ← 项目说明书（GitHub 首页显示的那份）
├── backend-run.log     ← 后端运行日志（出问题先看这里）
├── frontend-run.log    ← 前端运行日志
└── compile.log         ← 编译日志
```

**只有两个文件夹是你要关心的：`backend`（后端）和 `frontend`（前端）。** 其他都是辅助文件。

---

## 四、后端：`backend/` 目录

后端用的是 **Java + Spring Boot**。它的职责是：收文件、解析 PDF、算向量、查数据库、调 AI、把结果吐给前端。浏览器看不到它，但所有"重活"都是它干的。

### 4.1 整体分层思路（重要）

后端代码严格按**职责分层**，从外到内是这样的：

```
Controller（接客的）  →  Service（拿主意的）  →  Repository（管仓库的）
                              ↓
                        Entity（数据长什么样）
                        DTO（对外传输的包裹）
```

- **Controller**：只负责"接收请求、返回结果"，不写业务逻辑。
- **Service**：真正的业务逻辑在这里（比如"上传后要先存文件再切分再向量化"）。
- **Repository**：只管和数据库打交道（增删改查）。
- **Entity**：对应数据库里的一张表，一个对象 = 一行数据。
- **DTO**：专门用来和前端的"包裹"，避免把数据库结构暴露出去。

### 4.2 启动类

| 文件 | 它是干啥的 |
|---|---|
| `src/main/java/com/hnkj/qa/HnkjQaApplication.java` | **整个后端的入口**。你点"启动后端"就是运行它。相当于房子的总电闸 |

### 4.3 `controller/` —— 对外提供的接口（前端会调这些）

| 文件 | 它是干啥的 | 对应前端哪里 |
|---|---|---|
| `UploadController.java` | 接收 PDF 上传（`POST /api/upload`） | 上传页面 |
| `DocumentController.java` | 文档的查询、详情、删除、重建索引 | 规章制度库页面 |
| `QaController.java` | 提问接口：普通问答 + 流式问答（边说边显示） | 智能问答页面 |
| `HealthController.java` | 健康检查（`/api/health`），用来确认"后端还活着吗" | 一般不直接用，运维用 |

> **小白理解**：Controller 就是"前台接待"。你告诉它要干什么，它转身去找对应的 Service。

### 4.4 `service/` —— 业务逻辑（真正干活的）

| 文件 | 它是干啥的 |
|---|---|
| `service/DocumentService.java` | **接口**：规定文档服务能做哪些事（上传/分页/详情/删除/重建索引） |
| `service/impl/DocumentServiceImpl.java` | **实现**：上面那些事的具体做法。**你改"上传后怎么处理"就来这里** |
| `service/QaService.java` | **接口**：规定问答服务能做哪些事 |
| `service/impl/QaServiceImpl.java` | **实现**：普通问答和流式问答的具体做法（流式问答就是"打字机效果"的实现处） |

> **为什么要拆成"接口 + 实现"？** 这是 Java 项目的习惯写法，方便以后换实现、也方便写测试。看到 `XxxService` 和 `XxxServiceImpl` 成对出现，是正常现象。

### 4.5 `ai/` —— 人工智能相关（本项目最核心、也最特别的文件夹）

这是整个项目最有技术含量的地方，四个文件各管一件事：

| 文件 | 它是干啥的 |
|---|---|
| `ai/PdfIngestService.java` | **PDF 解析 + 切分**。把 PDF 里的文字抽出来，切成一块一块，并**记住每块来自第几页**（这样回答才能标页码） |
| `ai/embedding/EmbeddingService.java` | **把文字变成数字**。调用向量化服务（本项目用硅基流动的 `BAAI/bge-m3` 模型），输入一段文字，输出一串 1024 个数字 |
| `ai/vector/JdbcCosineVectorStore.java` | **向量仓库**。负责把向量存进数据库，以及提问时"找出最像的几块"。**如果向量化服务坏了，它会自动降级成"关键词搜索"**，保证系统还能用 |
| `ai/deepseek/DeepSeekClient.java` | **和 AI 对话的客户端**。负责把问题发到 DeepSeek，把 AI 写的答案收回来（支持逐字返回） |
| `ai/RagQaService.java` | **问答总指挥**。串起全流程：检索资料 → 拼提示词 → 让 AI 写 → 整理引用来源。**想让回答风格变化，先看这里** |

> **一句话记忆**：PDF 进来找 `PdfIngestService`，文字变数字找 `EmbeddingService`，数字存哪儿找 `JdbcCosineVectorStore`，问 AI 找 `DeepSeekClient`，整个流水线归 `RagQaService` 管。

### 4.6 `entity/` —— 数据库表对应的 Java 类

| 文件 | 对应数据库表 | 存的是什么 |
|---|---|---|
| `entity/Document.java` | `t_document` | 你上传的每一份 PDF 的档案（标题、文件名、大小、页数、状态） |
| `entity/VectorChunk.java` | `document_chunks` | 切分出来的每一个文本块 + 它的向量数字 |
| `entity/ChatRecord.java` | `t_chat_record` | 每一条问答记录（你问了什么、AI 答了什么） |

> **小白理解**：数据库里的表看不见摸不着，这些 Java 类就是表的"遥控器"。改表结构 → 改这里。

### 4.7 `repository/` —— 数据库操作（增删改查）

| 文件 | 管哪张表 |
|---|---|
| `repository/DocumentRepository.java` | 文档表：查列表、按标题搜、删除 |
| `repository/VectorChunkRepository.java` | 文本块表：存向量、取向量、按文档清理 |
| `repository/ChatRecordRepository.java` | 问答记录表：存历史、按会话查 |

> 这些文件通常很短，因为 Spring Data JPA 会自动生成 SQL。你只要按命名规则写个方法名（比如 `findByStatus`），它就懂。

### 4.8 `dto/` —— 和前端之间传输的"包裹"

| 文件 | 装的是啥 | 方向 |
|---|---|---|
| `dto/request/AskRequest.java` | 前端传来的提问（问题内容 + 会话 ID） | 前端 → 后端 |
| `dto/response/AskResponse.java` | 返回给前端的答案（回答正文 + 来源 + 耗时） | 后端 → 前端 |
| `dto/response/DocumentVO.java` | 文档列表里的一行 | 后端 → 前端 |
| `dto/response/UploadResult.java` | 上传结果（文档 ID、状态、字数、页数、文本块数） | 后端 → 前端 |
| `dto/response/SourceRef.java` | **一条引用来源**（文档标题 + 第几页 + 原文片段 + 相似度）。前端那个"来源卡片"就靠它 | 后端 → 前端 |

> `VO` = View Object（给界面看的对象），`DTO` = Data Transfer Object（数据传输对象）。名字唬人，本质就是"装数据的盒子"。

### 4.9 `common/` —— 公共工具（全项目通用）

| 文件 | 它是干啥的 |
|---|---|
| `common/api/ApiResponse.java` | **统一返回格式**。所有接口都返回 `{code, message, data}` 这个固定形状，前端好处理 |
| `common/api/PageResult.java` | 统一的分页格式（当前第几页、总共几页、共几条） |
| `common/enums/ErrorCode.java` | 错误码字典（0=成功，1xxxx=通用错误，2xxxx=文档相关，3xxxx=问答相关） |
| `common/exception/BizException.java` | 自定义业务异常。业务出错时抛它，比如"文件格式不对" |
| `common/exception/GlobalExceptionHandler.java` | **全局兜底**。任何地方抛异常，都由它整理成统一格式返回，不会把一堆报错堆栈甩给用户 |
| `common/util/CosineSimilarity.java` | 数学工具：算两个向量的"余弦相似度"（判断两段文字像不像） |

### 4.10 `config/` —— 各种配置（接线的地方）

| 文件 | 它是干啥的 |
|---|---|
| `config/AiConfig.java` | Spring AI 相关配置 |
| `config/DeepSeekConfig.java` | 创建调用 DeepSeek 用的 HTTP 客户端 |
| `config/VectorStoreConfig.java` | **决定用哪种向量存储**：本地开发用普通表，线上用 pgvector（见下方"环境切换"） |
| `config/OpenApiConfig.java` | 接口文档配置。启动后可访问 `http://localhost:8080/api/swagger-ui.html` 看到所有接口 |
| `config/WebMvcConfig.java` | 跨域设置、静态资源映射 |
| `config/properties/DeepSeekProperties.java` | 读取 `application.yml` 里 `campus.deepseek.*` 的配置（Key、模型名等） |
| `config/properties/QaProperties.java` | 问答参数：检索取几条（Top-K）、相似度门槛等 |
| `config/properties/StorageProperties.java` | 上传文件的存储目录、大小限制 |

### 4.11 `src/main/resources/` —— 配置与数据库脚本

| 文件 | 它是干啥的 |
|---|---|
| `application.yml` | **主配置文件**。端口、数据库连接、AI 参数都在这 |
| `application-local.yml` | **本地开发**专用配置（本机开发用这个） |
| `application-prod.yml` | 生产环境配置 |
| `application-railway.yml` | 部署到 Railway 平台时的配置（线上用 pgvector 向量库） |
| `prompts/qa-system-prompt.st` | **给 AI 的"岗位说明书"**。里面写着"必须依据原文回答、不许编造、要标页码"等规则。**想让 AI 回答更严谨/更活泼，改这个文件** |
| `db/migration/V1__init_schema.sql` | 建表脚本：文档表、问答记录表 |
| `db/migration/V2__create_t_vector_chunk.sql` | 建表脚本：本地向量表 |
| `db/migration/V3__rename_to_document_chunks.sql` | 把上面那张表改名为 `document_chunks` |
| `db/migration/V4__allow_null_embedding.sql` | 允许向量字段为空（配合"没有 Key 也能用"的降级方案） |

> **V1~V4 是"数据库版本升级包"**（Flyway 工具）。后端每次启动都会自动检查并执行没跑过的脚本，**所以表结构不用你手动建，也别手改这些文件**——要改就新增一个 V5。

### 4.12 `src/test/` —— 单元测试

| 文件 | 测试什么 |
|---|---|
| `test/.../HnkjQaApplicationTests.java` | 能否成功启动整个应用（默认关闭，因为需要数据库和 Key） |
| `test/.../ai/PdfIngestServiceTest.java` | PDF 文字提取是否正常 |
| `test/.../ai/vector/JdbcCosineVectorStoreTest.java` | 向量检索逻辑是否正确 |

### 4.13 其他后端文件

| 文件 | 它是干啥的 |
|---|---|
| `backend/pom.xml` | **后端的"购物清单"**。声明用到了哪些 Java 库、Java 版本等。加新依赖改这里 |
| `backend/Dockerfile` | 把后包装成 Docker 镜像的说明书（本机 Docker 用不了，暂未使用） |
| `backend/uploads/` | 你上传的 PDF 实际保存在这里（已被 Git 忽略，不会提交） |
| `backend/target/` | 编译产物（`.class` 文件），**自动生成，不要手动改** |

---

## 五、前端：`frontend/` 目录

前端用的是 **Vue 3**。它的职责是：把界面画出来，响应用户点击，把数据发给后端。**你看到的每一个页面都是前端做的。**

### 5.1 前端结构思路

```
index.html（网页外壳）
    ↓
main.js（启动，装载各种工具）
    ↓
App.vue（根容器，只放一个"路由出口"）
    ↓
BasicLayout.vue（整体框架：左边菜单 + 顶栏 + 右边内容区）
    ↓
views/xxx/index.vue（各个具体页面）
    ↓
components/xxx.vue（页面里复用的小积木）
```

### 5.2 入口三件套

| 文件 | 它是干啥的 |
|---|---|
| `index.html` | 网页的外壳（一个空 `<div id="app">`），Vue 会把整个应用塞进去 |
| `src/main.js` | **前端入口**。创建 Vue 应用、注册路由/状态管理/Element Plus 组件库 |
| `src/App.vue` | 根组件，只有一个 `<router-view />`（"这里显示当前页面"） |

### 5.3 路由与布局

| 文件 | 它是干啥的 |
|---|---|
| `src/router/index.js` | **路由表**：哪个网址显示哪个页面。四页：`/home`、`/qa`、`/document`、`/upload`。**加新页面先改这里** |
| `src/layouts/BasicLayout.vue` | **页面骨架**：左侧导航菜单 + 顶部标题栏 + 右侧内容区（内容区最大 1200px 居中） |

### 5.4 `views/` —— 四个页面（最常改的地方）

| 文件 | 页面 | 干啥的 |
|---|---|---|
| `views/home/index.vue` | 首页 | 欢迎页，两个功能入口卡片 + 示例问题 |
| `views/qa/index.vue` | 智能问答 | **主战场**。消息气泡、正在思考动画、Markdown 渲染、回车发送、来源卡片 |
| `views/document/index.vue` | 规章制度库 | 表格列出所有文档，可搜索、删除、重建索引 |
| `views/upload/index.vue` | 上传规章制度 | 拖拽上传 PDF，显示进度，成功后显示文件名和字数 |
| `views/error/404.vue` | 404 | 网址输错时显示的页面 |

### 5.5 `components/` —— 可复用的小积木

| 文件 | 它是干啥的 |
|---|---|
| `components/PageHeader.vue` | 页面顶部标题条（标题 + 小字说明 + 右侧按钮），好几个页面都在用 |
| `components/MarkdownText.vue` | **把 AI 回答渲染成漂亮格式**（列表、加粗、表格）。还负责把 `[第2页]` 变成蓝色小标签 |
| `components/SourceCitation.vue` | **来源引用卡片**。显示"这段答案来自哪份文件第几页"，点一下能展开看原文 |

### 5.6 `api/` —— 和后端说话的三种方式

| 文件 | 它是干啥的 |
|---|---|
| `api/request.js` | **公共请求工具**（axios）。统一设置后端地址、超时、错误提示 |
| `api/document.js` | 文档相关的请求：上传、列表、详情、删除、重建索引 |
| `api/chat.js` | 问答相关的请求。**流式问答的核心逻辑在这**（一段一段接收 AI 的回答） |

### 5.7 `stores/` —— 共享状态（Pinia）

| 文件 | 它是干啥的 |
|---|---|
| `stores/chat.js` | **存放聊天记录的地方**。你发的消息、AI 的回答、来源，都存在这里，页面切换不会丢 |

> **小白理解**：`store` 就像一个"公共抽屉"，多个页面都能从里面拿、往里面放数据。

### 5.8 `styles/` —— 全局样式（想换配色改这里）

| 文件 | 它是干啥的 |
|---|---|
| `styles/variables.scss` | **调色板 + 尺寸表**。主色（学术蓝 `#2563eb`）、灰阶、圆角、阴影、字体、页面宽度都在这定义。**改配色只改这个文件就够了** |
| `styles/index.scss` | 全局样式：字体、重置、通用卡片样式、Markdown 排版、滚动条、动画 |

### 5.9 `utils/`

| 文件 | 它是干啥的 |
|---|---|
| `utils/format.js` | 小工具函数：字节转 KB/MB、时间格式化、生成会话 ID |

### 5.10 前端配置文件

| 文件 | 它是干啥的 |
|---|---|
| `package.json` | **前端的"购物清单"**。声明依赖库和命令（`npm run dev` 启动、`npm run build` 打包） |
| `vite.config.js` | Vite 构建工具配置。**含关键的代理设置**：把 `/api` 请求转发到 `localhost:8080`（解决跨域） |
| `jsconfig.json` | 让编辑器认识 `@/` 代表 `src/` 目录（写代码时路径短一点） |
| `.env.development` | 开发环境变量（后端地址等） |
| `.env.production` | 生产环境变量 |
| `nginx.conf` | 线上用 Nginx 部署时：托管静态页面 + 把 `/api` 转发给后端 |
| `Dockerfile` / `.dockerignore` | 打包成 Docker 镜像的说明书 |
| `src/assets/` | 放图片、字体等静态素材（目前是空的占位目录） |
| `node_modules/` | 下载下来的第三方库（几百 MB，**不要动、不要提交**） |
| `dist/` | 打包产物（`npm run build` 生成，**不要动、不要提交**） |

---

## 六、`docs/` 与根目录其他文件

| 文件 | 它是干啥的 |
|---|---|
| `docs/architecture.md` | 项目架构设计说明（技术选型、模块划分） |
| `docs/local-db-setup.md` | **本地数据库安装教程**（PostgreSQL 怎么装、库怎么建） |
| `docs/initdb/01-init-vector.sql` | 数据库容器首次创建时自动执行的脚本（启用向量扩展） |
| `README.md` | 项目门面：介绍、技术栈、启动步骤。**GitHub 上首先看到它** |
| `docker-compose.yml` | 一条命令同时启动"数据库 + 后端 + 前端"三个容器（本机 Docker 装不了，留着备用） |
| `run-backend.ps1` | 一键启动后端的 PowerShell 脚本（会自动处理端口和 API Key） |
| `.gitignore` | **哪些文件不上传到 GitHub**（日志、临时文件、密钥、node_modules 等） |
| `.workbuddy/` | AI 助手的工作记录（项目记忆），与程序运行无关 |
| `.idea/` | IntelliJ IDEA 编辑器的个人设置，不提交 |
| `backend-run.log` / `frontend-run.log` / `compile.log` | 运行日志。**程序报错时，先来这里搜 `ERROR`** |

---

## 七、小白最关心的几个问题

**Q1：我要改界面文案/颜色，去哪改？**
颜色 → `frontend/src/styles/variables.scss`；页面文字 → 对应的 `frontend/src/views/xxx/index.vue`。

**Q2：我要加一个新页面，怎么做？**
三步：① 在 `frontend/src/views/` 下新建一个文件夹和 `index.vue`；② 在 `frontend/src/router/index.js` 里加一条路由；③ 在 `frontend/src/layouts/BasicLayout.vue` 的菜单数组里加一项。

**Q3：AI 回答得不好/爱瞎编，改哪里？**
先改 `backend/src/main/resources/prompts/qa-system-prompt.st`（给 AI 立的规矩）；如果效果还不理想，再看 `backend/src/main/java/com/hnkj/qa/ai/RagQaService.java`（检索参数）。

**Q4：上传 PDF 后问答搜不到内容，怎么查？**
① 看 `backend-run.log` 里搜 `ERROR`；② 到「规章制度库」页面点"重建索引"；③ 检查环境变量里 Embedding 的 Key 是否配置正确。

**Q5：哪些文件千万不要手改？**
`target/`、`node_modules/`、`dist/`、日志文件——这些都是自动生成的。另外已经执行过的数据库脚本 `V1~V4` 也不要改，要改就新增 `V5`。

**Q6：`local` 和 `railway` 是什么意思？**
这是两套"运行环境"。**local** = 你本机开发，用普通表 + Java 算相似度（因为本机装不了 pgvector 插件）；**railway** = 部署到线上，用专业向量数据库。切换靠一个环境变量 `SPRING_PROFILES_ACTIVE`，代码不用动。

---

## 八、名词速查表

| 名词 | 大白话解释 |
|---|---|
| **PDF Box** | 一个 Java 库，专门用来读 PDF 里的文字 |
| **切分 / 文本块** | 把一整篇文档切成若干小段，方便精确查找（就像把一本书拆成卡片） |
| **向量 / Embedding** | 把一段文字换算成一串数字。意思相近的文字，数字也相近 |
| **余弦相似度** | 衡量两串数字"像不像"的数学方法，越接近 1 越像 |
| **RAG** | 先查资料、再让 AI 照着资料回答的套路。本项目核心方法 |
| **Top-K** | "取最像的前 K 条"，本项目默认取 3 条 |
| **相似度阈值** | 像的程度低于这个数就不要了，避免拿不相关的材料误导 AI |
| **SSE / 流式** | 服务器像打字机一样一个字一个字往外送，不用等全部写完 |
| **会话 ID / sessionId** | 给每次聊天打一个编号，用来串起上下文 |
| **Flyway** | 数据库版本管理工具，用 V1/V2/V3 这样的脚本按顺序升级表结构 |
| **profile** | Spring Boot 的"环境档案"，一套配置对应一种运行环境 |
| **Lombok** | Java 插件，用注解自动生成 getter/setter，少写很多重复代码 |
| **Element Plus** | Vue 的界面组件库（按钮、表格、弹窗都是现成的） |
| **Pinia** | Vue 的状态管理库，用来在多页面间共享数据 |
| **Vite** | 前端的开发服务器和打包工具，改代码后浏览器秒刷新 |
| **Nginx** | 线上的网页服务器，负责发网页 + 转发接口请求 |

---

## 九、建议的上手路线

如果你要开始动手改这个项目，按这个顺序最不容易迷路：

1. **先跑起来** → 把三个服务启动，浏览器里点一遍四个页面，建立直观印象。
2. **从页面入手** → 打开 `frontend/src/views/` 下的文件，改改文字、颜色，看浏览器里的变化（Vite 会热更新，不用重启）。
3. **理解数据怎么走** → 看 `frontend/src/api/chat.js`（前端怎么发请求）→ 再看 `backend/.../controller/QaController.java`（后端怎么接）→ 顺着看 `QaServiceImpl.java`（怎么处理）。
4. **最后碰 AI 部分** → 等前面都清楚了，再研究 `backend/src/main/java/com/hnkj/qa/ai/` 这个文件夹。
5. **遇到报错** → 先看 `backend-run.log` / 浏览器按 `F12` 看 Console，90% 的问题一眼就能定位。

---

*本文档由项目结构自动梳理生成。如果你新增了文件，记得回来补上一行说明。*
