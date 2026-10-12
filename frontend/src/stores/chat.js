import { defineStore } from 'pinia'
import { ref } from 'vue'

/**
 * 问答会话状态。
 * 骨架阶段仅提供最小能力，真正的发送逻辑待接入接口后补充。
 */
export const useChatStore = defineStore('chat', () => {
  /** 会话标识，首次提问时由后端返回 */
  const sessionId = ref('')

  /** 消息列表：{ role: 'user' | 'assistant', content, sources: [] } */
  const messages = ref([])

  /** 是否正在生成回答 */
  const loading = ref(false)

  function setSessionId(id) {
    sessionId.value = id
  }

  function appendMessage(message) {
    messages.value.push(message)
  }

  /** 追加流式片段到最后一条 assistant 消息 */
  function appendChunk(chunk) {
    const last = messages.value[messages.value.length - 1]
    if (last && last.role === 'assistant') {
      last.content += chunk
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
    appendChunk,
    clear
  }
})
