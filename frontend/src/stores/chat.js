import { defineStore } from 'pinia'
import { ref } from 'vue'

/**
 * 问答会话状态。
 * 会话与消息都保存在内存中，刷新页面即清空（历史落库后改为从后端拉取）。
 */
export const useChatStore = defineStore('chat', () => {
  /** 会话标识，首次提问时由后端返回 */
  const sessionId = ref('')

  /**
   * 消息列表。
   * {
   *   role: 'user' | 'assistant',
   *   content: string,
   *   sources: Array,
   *   trace?: Object,        // 检索过程快照（透明化面板数据源）
   *   recordId?: number,     // 问答记录主键（供反馈评价）
   *   streaming?: boolean,   // 是否仍在流式生成（决定「正在思考」动画）
   *   error?: string,        // 生成失败时的友好提示
   *   retryText?: string     // 失败后用于重试的原始问题
   * }
   */
  const messages = ref([])

  /** 是否正在生成回答 */
  const loading = ref(false)

  function setSessionId(id) {
    sessionId.value = id
  }

  function appendMessage(message) {
    messages.value.push(message)
  }

  function removeMessage(message) {
    const index = messages.value.indexOf(message)
    if (index >= 0) messages.value.splice(index, 1)
  }

  /** 追加流式片段到最后一条 assistant 消息 */
  function appendChunk(chunk) {
    const last = messages.value[messages.value.length - 1]
    if (last && last.role === 'assistant') {
      last.content += chunk
    }
  }

  /** 给最后一条 assistant 消息挂上引用来源（来源事件先于正文到达） */
  function setSources(sources) {
    const last = messages.value[messages.value.length - 1]
    if (last && last.role === 'assistant') {
      last.sources = sources || []
    }
  }

  /** 给最后一条 assistant 消息挂上检索过程快照（trace 事件先于正文到达） */
  function setTrace(trace) {
    const last = messages.value[messages.value.length - 1]
    if (last && last.role === 'assistant') {
      last.trace = trace || null
    }
  }

  /** 给最后一条 assistant 消息挂上问答记录主键（done 事件到达） */
  function setRecordId(recordId) {
    const last = messages.value[messages.value.length - 1]
    if (last && last.role === 'assistant' && recordId) {
      last.recordId = recordId
    }
  }

  /** 标记最后一条 assistant 消息生成结束 */
  function finishStreaming() {
    const last = messages.value[messages.value.length - 1]
    if (last && last.role === 'assistant') {
      last.streaming = false
    }
  }

  function clear() {
    messages.value = []
    sessionId.value = ''
  }

  return {
    sessionId,
    messages,
    loading,
    setSessionId,
    appendMessage,
    removeMessage,
    appendChunk,
    setSources,
    setTrace,
    setRecordId,
    finishStreaming,
    clear
  }
})
