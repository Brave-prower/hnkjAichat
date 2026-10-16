# AI 协同演进记录 · 快照 1

## SSE 流式问答「双重前缀」Bug 的定位与修复

> 本快照对应核心功能迭代：**SSE 流式问答链路**（`POST /api/qa/ask/stream`）。
> 记录方式：关键 Prompt 原文 + AI 修改建议 + 引导 AI 修复 Bug 的完整过程。

---

### 迭代背景

前端用 `fetch + ReadableStream` 消费 SSE 流。联调时发现前端解析不到事件类型：所有事件被当成**纯文本正文**渲染，`sources`（来源卡片）永远不出现。

### Prompt 1（提出问题）

> 前端 SSE 事件流解析失败。后端代码是 `SseEmitter.send("event: sources\ndata: [...]\n\n")` 这样手工拼的帧，前端拿到的却是 `data:event:sources`、`data:data:...` 这样的内容，事件类型全丢了。帮我定位原因。

### AI 分析与修改建议

AI 定位根因：**Spring 的 `SseEmitter.send(String)` 本身就会自动按 SSE 规范分帧**（把字符串作为 `data:` 字段发出）。手工再拼 `event:`/`data:` 前缀，等于把「帧」当成了「数据」再次嵌套，产生 `data:event:sources` 的双重前缀。

修改建议：放弃手工拼帧，改用结构化 API：

```java
// 修复前（手工拼帧 + send(String) 自动分帧 → 双重前缀）
emitter.send("event: sources\ndata: " + json + "\n\n");

// 修复后（让 Spring 负责分帧）
emitter.send(SseEmitter.event().name("sources").data(json));
emitter.send(SseEmitter.event().name("message").data(chunk));
emitter.send(SseEmitter.event().name("done").data("[DONE]"));
```

### Prompt 2（验证中的新发现，引导继续修复）

> 修改后事件类型对了，但多行正文（AI 回答里的换行）一到前端就变成多个独立的 `data:` 行。另外校验失败时后端返回 JSON 错误体，前端报 406。

### AI 修改建议（两条）

1. 换行问题不是 Bug：SSE 规范规定多行 `data:` 由客户端自行用 `\n` 连接，前端 `ReadableStream` 解析器需要按「缓存 + 双换行分帧 + `data:` 行拼接」实现，补上这段解析逻辑即可。
2. `406` 的根因：`@Valid` 校验失败时 Spring 写 JSON 错误体，与 `Accept: text/event-stream` 冲突。修改方案：**`QaController#askStream` 不加 `@Valid`**，把参数校验下沉到 Service，校验失败不抛异常而是通过 `error` 事件推给前端。

### 最终结果

事件协议稳定为 `sources → message*（含多行）→ done`，失败走 `error` 事件且连接不断开。经 Vite 代理与直连 8080 双路验证，各得预期事件序列（1 sources + N message + done）。

### 经验沉淀

- 用框架封装的 SSE API 时，**绝不要再手工拼 `event:`/`data:` 前缀**——分帧职责已在框架手里
- SSE 接口的参数校验要走事件通道，不能走 HTTP 状态码通道
