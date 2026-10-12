<script setup>
import { ref, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { useChatStore } from '@/stores/chat'
import { askQuestionStream } from '@/api/chat'
import SourceCitation from '@/components/SourceCitation.vue'
import { genSessionId } from '@/utils/format'

const chatStore = useChatStore()

const question = ref('')
const scrollRef = ref(null)

const quickQuestions = [
  '学生请销假需要办理哪些手续？',
  '国家奖学金的评审条件是什么？',
  '宿舍用电有哪些禁止性规定？',
  '学生考试违纪如何处理？'
]

async function scrollToBottom() {
  await nextTick()
  if (scrollRef.value) {
    scrollRef.value.scrollTop = scrollRef.value.scrollHeight
  }
}

/**
 * 发送提问。骨架阶段：调用流式接口，接口未实现时会走 catch 分支。
 */
async function handleSend() {
  const text = question.value.trim()
  if (!text) {
    ElMessage.warning('请输入问题')
    return
  }
  if (chatStore.loading) return

  if (!chatStore.sessionId) {
    chatStore.setSessionId(genSessionId())
  }

  chatStore.appendMessage({ role: 'user', content: text, sources: [] })
  question.value = ''
  chatStore.loading = true
  chatStore.appendMessage({ role: 'assistant', content: '', sources: [] })
  await scrollToBottom()

  try {
    // TODO 后端 /qa/ask/stream 实现后即可正常渲染
    await askQuestionStream(
      { question: text, sessionId: chatStore.sessionId },
      (chunk) => {
        chatStore.appendChunk(chunk)
        scrollToBottom()
      }
    )
  } catch (e) {
    const last = chatStore.messages[chatStore.messages.length - 1]
    if (last && last.role === 'assistant' && !last.content) {
      last.content = '智能问答接口尚未接通（后端业务逻辑待实现）。'
    }
  } finally {
    chatStore.loading = false
    await scrollToBottom()
  }
}

function handleQuickAsk(text) {
  question.value = text
  handleSend()
}

function handleClear() {
  chatStore.clear()
}
</script>

<template>
  <div class="qa-page">
    <PageHeader title="智能问答" description="回答基于学校规章制度文档，并标注来源页码">
      <template #extra>
        <el-button :disabled="!chatStore.messages.length" @click="handleClear">
          <el-icon><Delete /></el-icon>
          清空对话
        </el-button>
      </template>
    </PageHeader>

    <div class="qa-body">
      <!-- 消息区 -->
      <div ref="scrollRef" class="qa-messages">
        <el-empty
          v-if="!chatStore.messages.length"
          description="还没有对话，输入你的问题开始吧"
        >
          <div class="quick-list">
            <el-tag
              v-for="q in quickQuestions"
              :key="q"
              class="quick-tag"
              effect="plain"
              @click="handleQuickAsk(q)"
            >
              {{ q }}
            </el-tag>
          </div>
        </el-empty>

        <div
          v-for="(msg, index) in chatStore.messages"
          :key="index"
          class="msg"
          :class="`msg--${msg.role}`"
        >
          <div class="msg__avatar">
            <el-icon :size="18">
              <UserFilled v-if="msg.role === 'user'" />
              <MagicStick v-else />
            </el-icon>
          </div>

          <div class="msg__content">
            <div class="msg__bubble">
              <template v-if="msg.content">
                {{ msg.content }}
                <span v-if="chatStore.loading && index === chatStore.messages.length - 1" class="cursor">▍</span>
              </template>
              <span v-else class="text-secondary">思考中…</span>
            </div>
            <SourceCitation :sources="msg.sources || []" />
          </div>
        </div>
      </div>

      <!-- 输入区 -->
      <div class="qa-input">
        <el-input
          v-model="question"
          type="textarea"
          :rows="3"
          resize="none"
          maxlength="500"
          show-word-limit
          placeholder="请输入你的问题，例如：请销假需要哪些手续？（Enter 发送，Shift + Enter 换行）"
          @keydown.enter.exact.prevent="handleSend"
        />
        <el-button
          type="primary"
          :loading="chatStore.loading"
          @click="handleSend"
        >
          <el-icon><Promotion /></el-icon>
          发送
        </el-button>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.qa-page {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.qa-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: var(--app-card-bg);
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius);
  overflow: hidden;
  min-height: 0;
}

.qa-messages {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
}

.quick-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-items: center;
}

.quick-tag {
  cursor: pointer;
}

.msg {
  display: flex;
  gap: 12px;
  margin-bottom: 18px;

  &__avatar {
    width: 32px;
    height: 32px;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
    color: #ffffff;
    background: var(--app-text-secondary);
  }

  &__content {
    flex: 1;
    min-width: 0;
  }

  &__bubble {
    display: inline-block;
    max-width: 100%;
    padding: 10px 14px;
    border-radius: 8px;
    line-height: 1.7;
    white-space: pre-wrap;
    word-break: break-word;
    background: var(--app-bg);
  }

  &--assistant &__avatar {
    background: var(--app-primary);
  }

  &--assistant &__bubble {
    background: var(--app-primary-light);
  }
}

.cursor {
  animation: blink 1s steps(2) infinite;
}

@keyframes blink {
  50% {
    opacity: 0;
  }
}

.qa-input {
  display: flex;
  gap: 12px;
  align-items: flex-end;
  padding: 16px 20px;
  border-top: 1px solid var(--app-border);
  background: #fafbfc;
}
</style>
