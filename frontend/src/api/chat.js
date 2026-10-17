import request from './request'

/**
 * 提问（同步返回）
 * @returns {Promise<{sessionId, answer, sources, latencyMs}>}
 */
export function askQuestion(payload) {
  return request.post('/qa/ask', payload)
}

/** 查询会话历史 */
export function getHistory(sessionId) {
  return request.get('/qa/history', { params: { sessionId } })
}

/**
 * 解析单个 SSE 事件块。
 *
 * 按规范处理：`event:` 指定事件名；一个事件可以有多行 `data:`，
 * 拼接时用 `\n` 还原（后端正是把含换行的正文拆成多条 data 行发出来的）。
 *
 * @param {string} block 以空行分隔后的事件块
 * @returns {{event: string, data: string} | null}
 */
function parseEventBlock(block) {
  let event = 'message'
  const dataLines = []

  for (const rawLine of block.split('\n')) {
    const line = rawLine.endsWith('\r') ? rawLine.slice(0, -1) : rawLine
    if (!line || line.startsWith(':')) continue

    if (line.startsWith('event:')) {
      event = line.slice(6).trim()
    } else if (line.startsWith('data:')) {
      // 冒号后的第一个空格是分隔符，不算正文
      const value = line.slice(5)
      dataLines.push(value.startsWith(' ') ? value.slice(1) : value)
    }
  }

  if (!dataLines.length) return null
  return { event, data: dataLines.join('\n') }
}

/**
 * 提问（SSE 流式）。
 * 使用 fetch + ReadableStream 而非 EventSource，因为需要 POST 传参。
 *
 * 事件协议：
 * - `trace`  ：检索过程快照（JSON，透明化面板数据源），可能省略
 * - `sources`：引用来源（JSON 数组），在正文之前到达
 * - `message`：回答增量正文，可能出现任意多次
 * - `error`  ：生成失败原因（会以异常形式抛出）
 * - `done`   ：JSON `{recordId: number}`（问答记录主键，可null），表示结束；
 *              兼容旧版固定 `[DONE]`
 *
 * @param {{question: string, sessionId?: string}} payload
 * @param {{onTrace?: (trace: Object) => void, onSources?: (sources: Array) => void,
 *          onMessage?: (text: string) => void, onDone?: (recordId: number|null) => void}} handlers
 * @returns {Promise<void>}
 */
export async function askQuestionStream(payload, handlers = {}) {
  const { onTrace, onSources, onMessage, onDone } = handlers
  const baseURL = import.meta.env.VITE_API_BASE_URL || '/api'
  const response = await fetch(`${baseURL}/qa/ask/stream`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream'
    },
    body: JSON.stringify({ ...payload, stream: true })
  })

  if (!response.ok) {
    throw new Error(`请求失败：HTTP ${response.status}`)
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  let finished = false

  try {
    while (!finished) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })

      // SSE 以空行分隔事件；最后一段可能不完整，留在 buffer 等下一次
      const blocks = buffer.split('\n\n')
      buffer = blocks.pop() || ''

      for (const block of blocks) {
        const parsed = parseEventBlock(block)
        if (!parsed) continue

        if (parsed.event === 'done' || parsed.data === '[DONE]') {
          let recordId = null
          try {
            const obj = JSON.parse(parsed.data)
            if (obj && obj.recordId) recordId = obj.recordId
          } catch {
            // 旧版固定 [DONE]，无 recordId
          }
          onDone?.(recordId)
          finished = true
          break
        }
        if (parsed.event === 'sources') {
          try {
            onSources?.(JSON.parse(parsed.data))
          } catch {
            // 来源解析失败不影响正文渲染
          }
        } else if (parsed.event === 'trace') {
          try {
            onTrace?.(JSON.parse(parsed.data))
          } catch {
            // 快照解析失败不影响正文渲染
          }
        } else if (parsed.event === 'error') {
          throw new Error(parsed.data || '生成回答失败')
        } else if (parsed.event === 'message') {
          onMessage?.(parsed.data)
        }
      }
    }
  } finally {
    reader.cancel().catch(() => {})
  }
}
