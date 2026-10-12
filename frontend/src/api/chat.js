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
 * 提问（SSE 流式）。
 * 使用 fetch + ReadableStream 而非 EventSource，因为需要 POST 传参。
 *
 * @param {{question: string, sessionId?: string}} payload
 * @param {(text: string) => void} onMessage 每收到一段文本的回调
 * @returns {Promise<void>}
 */
export async function askQuestionStream(payload, onMessage) {
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
    throw new Error(`请求失败：${response.status}`)
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''

  // eslint-disable-next-line no-constant-condition
  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })

    // SSE 以两个换行分隔一个事件
    const events = buffer.split('\n\n')
    buffer = events.pop() || ''
    for (const event of events) {
      const line = event.split('\n').find((l) => l.startsWith('data:'))
      if (!line) continue
      const data = line.slice(5).trim()
      if (data === '[DONE]') return
      onMessage?.(data)
    }
  }
}
