# 本地运行数据库（不使用 Docker）

> **好消息（2026-10-13 更新）**：系统已内置 `local` profile 双向量存储方案——
> 向量存普通表 `t_vector_chunk`（JSONB 数组），检索在 Java 侧算余弦相似度，
> **不再依赖 pgvector 扩展**。本机只需要装一个普通的 PostgreSQL 16 即可。
> （pgvector 安装步骤保留在第二、三节，仅供想本地验证 `railway` 模式时参考。）

本项目需要 **PostgreSQL 16**。没有 Docker 时，可以在 Windows 上原生安装。

> 为什么必须装数据库：Spring Boot 启动时会执行 Flyway 建表脚本
> （`t_document` / `t_chat_record` / `t_vector_chunk`），数据库连不上，后端起不来。

---

## 一、安装 PostgreSQL 16

1. 打开官方下载页：<https://www.enterprisedb.com/downloads/postgres-postgresql-downloads>
2. 选择 **PostgreSQL 16.x → Windows x86-64**，下载并双击安装
3. 安装过程注意：

   | 步骤 | 设置 |
   | --- | --- |
   | 安装目录 | 默认 `C:\Program Files\PostgreSQL\16`（下文用 `PGROOT` 指代） |
   | 组件 | 全选（含 pgAdmin、Command Line Tools） |
   | 数据目录 | 默认 |
   | 超级用户密码 | **`postgres`**（与项目默认配置一致） |
   | 端口 | **`5432`** |
   | Locale | 默认 |

4. 安装完成后，PostgreSQL 会作为 Windows 服务 `postgresql-x64-16` 自动启动

### 验证

打开「SQL Shell (psql)」或命令行：

```cmd
"C:\Program Files\PostgreSQL\16\bin\psql" -U postgres -c "SELECT version();"
```

输入密码 `postgres`，能打印版本号即安装成功。

---

## 二、安装 pgvector 扩展（可选）

> **local 模式不需要这一节**，普通 PostgreSQL 就能跑。装 pgvector 只是为了本地验证
> `railway` profile（pgvector 方案）。

pgvector 官方只发源码，Windows 上需要用**社区预编译包**（无需编译，3 步搞定）。

### 1. 下载

PostgreSQL **16** 对应的包：

<https://github.com/andreiramani/pgvector_pgsql_windows/releases/download/0.8.6_16/vector.v0.8.6-pg16.zip>

> 如果你的 PostgreSQL 是 17，请到 <https://github.com/andreiramani/pgvector_pgsql_windows/releases> 找 `0.8.6_17` 版本，**DLL 版本必须与 PostgreSQL 主版本一致**。

### 2. 解压并复制文件

解压后得到 `vector.dll`、`vector.control`、若干 `vector--*.sql`。**以管理员身份**复制：

```cmd
:: DLL 放 lib 目录
copy vector.dll "C:\Program Files\PostgreSQL\16\lib\"

:: control 与 sql 脚本放 share\extension 目录
copy vector.control "C:\Program Files\PostgreSQL\16\share\extension\"
copy vector--*.sql  "C:\Program Files\PostgreSQL\16\share\extension\"
```

三个文件的分工：
- `vector.dll` —— 扩展的实际运行代码
- `vector.control` —— 扩展元数据，PostgreSQL 靠它识别扩展
- `vector--*.sql` —— 版本化建表/升级脚本

### 3. 重启服务

```cmd
:: 管理员命令行
net stop  postgresql-x64-16
net start postgresql-x64-16
```

也可以在 `services.msc` 里找到 `postgresql-x64-16` 右键重启。

---

## 三、创建数据库（pgvector 扩展为可选）

```cmd
:: 1) 建库（local 模式到此为止就够了）
"C:\Program Files\PostgreSQL\16\bin\psql" -U postgres -c "CREATE DATABASE campus_qa;"

:: 2) 启用 pgvector 扩展（仅 railway 模式需要；脚本已存在于仓库中）
"C:\Program Files\PostgreSQL\16\bin\psql" -U postgres -d campus_qa -f D:\hnjgAichat\docs\initdb\01-init-vector.sql
```

`01-init-vector.sql` 内容：

```sql
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
```

### 验证 pgvector 生效

```cmd
"C:\Program Files\PostgreSQL\16\bin\psql" -U postgres -d campus_qa -c "SELECT '[1,2,3]'::vector;"
```

输出 `[1,2,3]` 即成功。

---

## 四、启动后端

项目默认配置就是 `localhost:5432 / campus_qa / postgres / postgres`，**无需修改任何配置**，直接跑：

```cmd
cd /d D:\hnjgAichat\backend
mvn spring-boot:run
```

看到日志里 Flyway 输出类似：

```
Successfully applied 2 migrations to schema "public", now at version v2
```

说明建表成功（v1 业务表 + v2 向量表）。访问 <http://localhost:8080/api/swagger-ui.html> 即可。

---

## 五、常见问题

| 现象 | 原因 / 解决 |
| --- | --- |
| `FATAL: password authentication failed` | 安装时密码不是 `postgres`。改 `application.yml` 里的 `DB_PASSWORD`，或设环境变量 |
| `extension "vector" is not available` | pgvector 文件没放对目录，或没重启服务；确认 `lib\vector.dll` 与 `share\extension\vector.control` 都存在 |
| `could not load library ... vector.dll` | DLL 版本与 PostgreSQL 主版本不匹配（如把 pg17 的 DLL 装进了 pg16） |
| 503 / 端口占用 | 5432 被别的 PostgreSQL 实例占了，`netstat -ano \| findstr 5432` 查进程 |
| 想用 pgAdmin 查看表 | 安装时已自带，开始菜单搜索 pgAdmin 4，连接 `localhost` 即可 |

---

## 六、备选方案

### A. 免安装绿色版（不需要管理员权限）

EDB 提供 PostgreSQL 二进制 zip 包：<https://www.enterprisedb.com/download-postgresql-binaries>

解压后手动初始化并启动：

```cmd
cd /d D:\pgsql\bin
initdb -D D:\pgsql\data -U postgres --pwprompt --encoding=UTF8
pg_ctl -D D:\pgsql\data -l D:\pgsql\log.txt start
```

再按第二、三步装 pgvector 并建库。适合没有管理员权限的电脑。

### B. 连 PostgreSQL 都不想装？

~~改造为 H2 内存数据库~~ —— 已有更简单的方案：当前 `local` profile 的向量存储
就是普通表 + Java 余弦检索，**不需要 pgvector**，但业务表（Flyway）仍需要 PostgreSQL。
若确实需要零安装方案再考虑 H2（需改 pom、迁移脚本中的 JSONB/COMMENT ON 等 PG 专有语法），不推荐。
