<script setup>
/**
 * 智能问答页面。
 *
 * 交互要点：
 *   - 输入框回车发送（Shift + Enter 换行），中文输入法组词中的回车不会误发送
 *   - 发送后立即清空输入框
 *   - 新消息自动滚到底部；用户上翻查看历史时不打扰，改为显示「回到底部」
 *   - AI 生成中显示「正在思考」动画，正文以 Markdown 渲染
 *   - 出错时展示友好提示，并提供「重试」
 */
import { nextTick, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useChatStore } from '@/stores/chat'
import { askQuestionStream } from '@/api/chat'
import SourceCitation from '@/components/SourceCitation.vue'
import MarkdownText from '@/components/MarkdownText.vue'
import RetrievalTracePanel from '@/components/RetrievalTracePanel.vue'
import { genSessionId } from '@/utils/format'

const chatStore = useChatStore()

const question = ref('')
const scrollRef = ref(null)

/** 是否处于输入法组词状态（组词中回车不发送） */
const composing = ref(false)
/** 用户是否停留在底部附近 */
const atBottom = ref(true)

const quickQuestions = [
  '学生请销假需要办理哪些手续？',
  '国家奖学金的评审条件是什么？',
  '宿舍用电有哪些禁止性规定？',
  '学生考试违纪如何处理？'
]

// ==================== 滚动控制 ====================

function onScroll() {
  const el = scrollRef.value
  if (!el) return
  atBottom.value = el.scrollHeight - el.scrollTop - el.clientHeight < 60
}

/** 滚到底部；force=true 时忽略用户当前位置（发新消息用） */
async function scrollToBottom(force = false) {
  await nextTick()
  const el = scrollRef.value
  if (!el) return
  if (!force && !atBottom.value) return
  el.scrollTop = el.scrollHeight
  atBottom.value = true
}

// ==================== 发送与重试 ====================

async function handleSend() {
  const text = question.value.trim()
  if (!text) {
    ElMessage.warning('请输入问题')
    return
  }
  if (chatStore.loading) return

  // 先清空输入框，视觉上响应更快
  question.value = ''

  if (!chatStore.sessionId) {
    chatStore.setSessionId(genSessionId())
  }

  chatStore.appendMessage({ role: 'user', content: text, sources: [] })
  await runAsk(text)
}

/** 发起一次流式问答（不追加用户消息，供重试复用） */
async function runAsk(text) {
  chatStore.loading = true
  chatStore.appendMessage({
    role: 'assistant',
    content: '',
    sources: [],
    trace: null,
    showTrace: false,
    recordId: null,
    streaming: true
  })
  await scrollToBottom(true)

  try {
    await askQuestionStream(
      { question: text, sessionId: chatStore.sessionId },
      {
        onTrace: (trace) => {
          chatStore.setTrace(trace)
        },
        onSources: (sources) => {
          chatStore.setSources(sources)
          scrollToBottom()
        },
        onMessage: (chunk) => {
          chatStore.appendChunk(chunk)
          scrollToBottom()
        },
        onDone: (recordId) => {
          chatStore.setRecordId(recordId)
        }
      }
    )
  } catch (e) {
    const last = chatStore.messages[chatStore.messages.length - 1]
    if (last && last.role === 'assistant') {
      last.error = friendlyError(e)
      last.retryText = text
      // 若已产出部分正文，保留正文并追加中断说明
      if (!last.content) last.content = ''
    }
  } finally {
    chatStore.loading = false
    chatStore.finishStreaming()
    await scrollToBottom()
  }
}

function handleRetry(msg) {
  if (chatStore.loading) return
  const retryText = msg.retryText
  if (!retryText) return
  chatStore.removeMessage(msg)
  runAsk(retryText)
}

/** 把各种异常整理成一句能看懂的提示 */
function friendlyError(e) {
  const raw = String(e?.message || '')
  if (/HTTP\s*5\d\d/.test(raw)) return '服务器暂时无法处理该问题，请稍后重试。'
  if (/HTTP\s*4\d\d/.test(raw)) return '请求未能被接受，请刷新页面后重试。'
  if (/Failed to fetch|NetworkError|load failed/i.test(raw)) {
    return '无法连接到问答服务，请确认后端服务已启动、网络正常。'
  }
  if (/timeout|aborted|超时/i.test(raw)) return '回答生成时间过长已中断，请稍后重试。'
  return raw || '回答生成失败，请稍后重试。'
}

// ==================== 输入框 ====================

function onCompositionStart() {
  composing.value = true
}

function onCompositionEnd() {
  composing.value = false
}

/** 回车发送（排除输入法组词） */
function onEnterKey(e) {
  if (composing.value) return
  e.preventDefault()
  handleSend()
}

function handleQuickAsk(text) {
  if (chatStore.loading) return
  question.value = text
  handleSend()
}

// ==================== 其他 ====================

function handleClear() {
  chatStore.clear()
  question.value = ''
}

/** 判断某条 assistant 消息是否处于「正在思考」阶段 */
function isThinking(msg, index) {
  return (
    msg.role === 'assistant' &&
    chatStore.loading &&
    index === chatStore.messages.length - 1 &&
    !msg.content
  )
}

function isLastStreaming(msg, index) {
  return (
    msg.role === 'assistant' &&
    index === chatStore.messages.length - 1 &&
    !!msg.streaming
  )
}

/** 切换某条消息的透明化面板展开状态 */
function toggleTrace(msg) {
  msg.showTrace = !msg.showTrace
}

onMounted(() => scrollToBottom(true))
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
      <div ref="scrollRef" class="qa-messages" @scroll.passive="onScroll">
        <!-- 空状态 + 快捷提问 -->
        <div v-if="!chatStore.messages.length" class="qa-empty">
          <div class="qa-empty__icon">
            <el-icon :size="30"><ChatDotRound /></el-icon>
          </div>
          <h3 class="qa-empty__title">还没有对话</h3>
          <p class="qa-empty__desc">输入你的问题，或从下面的常见问题开始</p>
          <div class="qa-empty__list">
            <button
              v-for="q in quickQuestions"
              :key="q"
              type="button"
              class="quick-card"
              @click="handleQuickAsk(q)"
            >
              <span class="quick-card__text">{{ q }}</span>
              <el-icon class="quick-card__arrow"><ArrowRight /></el-icon>
            </button>
          </div>
        </div>

        <!-- 消息列表 -->
        <div
          v-for="(msg, index) in chatStore.messages"
          :key="index"
          class="msg"
          :class="`msg--${msg.role}`"
        >
          <div class="msg__avatar">
            <el-icon :size="17">
              <UserFilled v-if="msg.role === 'user'" />
              <MagicStick v-else />
            </el-icon>
          </div>

          <div class="msg__content">
            <!-- 用户消息：纯文本 -->
            <div v-if="msg.role === 'user'" class="msg__bubble msg__bubble--user">
              {{ msg.content }}
            </div>

            <!-- AI 消息：Markdown 渲染 -->
            <div v-else class="msg__bubble msg__bubble--assistant">
              <!-- 正在思考动画 -->
              <div v-if="isThinking(msg, index)" class="thinking">
                <span class="thinking__dots">
                  <i></i><i></i><i></i>
                </span>
                <span class="thinking__text">正在思考</span>
                <span v-if="msg.sources?.length" class="thinking__hint">
                  · 已检索到 {{ msg.sources.length }} 条相关制度
                </span>
              </div>

              <template v-else>
                <MarkdownText :content="msg.content" />
                <span v-if="isLastStreaming(msg, index)" class="cursor">▍</span>
              </template>
            </div>

            <!-- 出错提示（友好文案 + 重试） -->
            <div v-if="msg.error" class="msg__error">
              <el-icon class="msg__error-icon"><WarningFilled /></el-icon>
              <div class="msg__error-body">
                <p class="msg__error-text">{{ msg.error }}</p>
                <el-button
                  v-if="msg.retryText"
                  link
                  type="primary"
                  size="small"
                  :disabled="chatStore.loading"
                  @click="handleRetry(msg)"
                >
                  重新生成
                </el-button>
              </div>
            </div>

            <!-- 参考来源（有正文时展示；可点击展开完整原文） -->
            <SourceCitation
              v-if="msg.role === 'assistant' && msg.content && msg.sources?.length"
              :sources="msg.sources"
            />

            <!-- 透明化面板入口：有 trace 即可看（拒答场景也能解释为什么拒答） -->
            <div
              v-if="msg.role === 'assistant' && msg.trace"
              class="msg__trace-entry"
            >
              <button
                type="button"
                class="trace-toggle"
                :aria-expanded="!!msg.showTrace"
                @click="toggleTrace(msg)"
              >
                <el-icon><DataAnalysis /></el-icon>
                为什么这样答
                <el-icon class="trace-toggle__arrow" :class="{ 'is-open': msg.showTrace }">
                  <ArrowDown />
                </el-icon>
              </button>
            </div>
            <div v-if="msg.showTrace && msg.trace" class="msg__trace">
              <RetrievalTracePanel :trace="msg.trace" />
            </div>
          </div>
        </div>
      </div>

      <!-- 回到底部 -->
      <transition name="fade">
        <button v-if="!atBottom" type="button" class="scroll-bottom" @click="scrollToBottom(true)">
          <el-icon><ArrowDown /></el-icon>
          回到底部
        </button>
      </transition>

      <!-- 输入区 -->
      <div class="qa-input">
        <div class="qa-input__box">
          <el-input
            v-model="question"
            type="textarea"
            :rows="3"
            resize="none"
            maxlength="500"
            show-word-limit
            :disabled="chatStore.loading"
            placeholder="请输入你的问题，例如：请销假需要哪些手续？（Enter 发送，Shift + Enter 换行）"
            @compositionstart="onCompositionStart"
            @compositionend="onCompositionEnd"
            @keydown.enter.exact="onEnterKey"
          />
          <div class="qa-input__actions">
            <span class="qa-input__tip">Enter 发送 · Shift + Enter 换行</span>
            <el-button type="primary" :loading="chatStore.loading" @click="handleSend">
              <el-icon v-if="!chatStore.loading"><Promotion /></el-icon>
              {{ chatStore.loading ? '生成中…' : '发送' }}
            </el-button>
          </div>
        </div>
        <p class="qa-input__disclaimer">
          回答由 AI 依据制度原文生成，仅供参考；涉及具体事务请以学校正式文件为准。
        </p>
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
  position: relative;
  flex: 1;
  display: flex;
  flex-direction: column;
  background: var(--app-card-bg);
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-lg);
  box-shadow: var(--app-shadow-sm);
  overflow: hidden;
  min-height: 0;
}

/* ==================== 消息区 ==================== */
.qa-messages {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
  scroll-behavior: smooth;
}

/* ---------- 空状态 ---------- */
.qa-empty {
  max-width: 560px;
  margin: 48px auto 0;
  text-align: center;

  &__icon {
    width: 60px;
    height: 60px;
    margin: 0 auto 16px;
    display: flex;
    align-items: center;
    justify-content: center;
    color: var(--app-primary);
    background: var(--app-primary-soft);
    border-radius: 50%;
  }

  &__title {
    margin: 0;
    font-size: 16px;
  }

  &__desc {
    margin: 6px 0 20px;
    font-size: 13px;
    color: var(--app-text-secondary);
  }

  &__list {
    display: grid;
    gap: 10px;
  }
}

.quick-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  width: 100%;
  padding: 12px 16px;
  font-family: inherit;
  font-size: 13.5px;
  color: var(--app-text-regular);
  text-align: left;
  background: var(--app-card-bg);
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius);
  cursor: pointer;
  transition: all var(--app-transition);

  &:hover {
    color: var(--app-primary);
    border-color: var(--app-primary-border);
    background: var(--app-primary-soft);
    box-shadow: var(--app-shadow-xs);
    transform: translateY(-1px);
  }

  &__text {
    flex: 1;
  }

  &__arrow {
    flex-shrink: 0;
    color: var(--app-text-placeholder);
    transition: transform var(--app-transition);
  }

  &:hover &__arrow {
    color: var(--app-primary);
    transform: translateX(2px);
  }
}

/* ---------- 消息行 ---------- */
.msg {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
  animation: app-fade-in-up 0.24s ease both;

  &--user {
    flex-direction: row-reverse;
  }

  &__avatar {
    width: 34px;
    height: 34px;
    flex-shrink: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: 50%;
    color: #ffffff;
    background: var(--app-text-secondary);
    box-shadow: var(--app-shadow-xs);
  }

  &--assistant &__avatar {
    background: linear-gradient(135deg, #3b82f6 0%, var(--app-primary) 100%);
  }

  &__content {
    flex: 1;
    min-width: 0;
    max-width: 88%;
  }

  &--user &__content {
    display: flex;
    flex-direction: column;
    align-items: flex-end;
  }

  /* ---------- 气泡：圆角 + 柔和阴影 ---------- */
  &__bubble {
    display: inline-block;
    max-width: 100%;
    padding: 12px 16px;
    font-size: 14px;
    line-height: 1.75;
    word-break: break-word;
    overflow-wrap: anywhere;
    border-radius: var(--app-radius-lg);
    box-shadow: var(--app-shadow-sm);
  }

  &__bubble--user {
    white-space: pre-wrap;
    color: #ffffff;
    background: linear-gradient(135deg, #3b82f6 0%, var(--app-primary) 100%);
    border-bottom-right-radius: 4px;
  }

  &__bubble--assistant {
    width: 100%;
    color: var(--app-text-primary);
    background: var(--app-card-bg);
    border: 1px solid var(--app-border);
    border-bottom-left-radius: 4px;
  }

  /* ---------- 错误提示 ---------- */
  &__error {
    display: flex;
    gap: 8px;
    margin-top: 8px;
    padding: 10px 14px;
    font-size: 13px;
    color: #8f2f2f;
    background: var(--app-danger-soft);
    border: 1px solid #f3cccc;
    border-radius: var(--app-radius);
  }

  &__error-icon {
    flex-shrink: 0;
    margin-top: 2px;
    color: var(--app-danger);
  }

  &__error-body {
    flex: 1;
    min-width: 0;
  }

  &__error-text {
    margin: 0;
    line-height: 1.6;
  }

  /* ---------- 透明化面板入口 ---------- */
  &__trace-entry {
    margin-top: 8px;
  }

  &__trace {
    margin-top: 6px;
  }
}

.trace-toggle {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 4px 10px;
  font-family: inherit;
  font-size: 12.5px;
  color: var(--app-text-secondary);
  background: none;
  border: 1px dashed var(--app-border);
  border-radius: var(--app-radius-pill);
  cursor: pointer;
  transition: all var(--app-transition);

  &:hover {
    color: var(--app-primary);
    border-color: var(--app-primary-border);
    background: var(--app-primary-soft);
  }

  &__arrow {
    transition: transform var(--app-transition);

    &.is-open {
      transform: rotate(180deg);
    }
  }
}

/* ---------- 正在思考动画 ---------- */
.thinking {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--app-text-secondary);
  font-size: 13.5px;

  &__dots {
    display: inline-flex;
    gap: 4px;

    i {
      width: 6px;
      height: 6px;
      border-radius: 50%;
      background: var(--app-primary);
      opacity: 0.35;
      animation: thinking-bounce 1.2s infinite ease-in-out;

      &:nth-child(2) {
        animation-delay: 0.15s;
      }

      &:nth-child(3) {
        animation-delay: 0.3s;
      }
    }
  }

  &__text {
    color: var(--app-text-regular);
  }

  &__hint {
    color: var(--app-text-placeholder);
    font-size: 12.5px;
  }
}

@keyframes thinking-bounce {
  0%,
  60%,
  100% {
    transform: translateY(0);
    opacity: 0.35;
  }
  30% {
    transform: translateY(-4px);
    opacity: 1;
  }
}

.cursor {
  animation: app-blink 1s steps(2) infinite;
  color: var(--app-primary);
}

/* ---------- 回到底部 ---------- */
.scroll-bottom {
  position: absolute;
  right: 24px;
  bottom: 176px;
  z-index: 5;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  font-family: inherit;
  font-size: 12.5px;
  color: var(--app-primary);
  background: var(--app-card-bg);
  border: 1px solid var(--app-primary-border);
  border-radius: var(--app-radius-pill);
  box-shadow: var(--app-shadow-sm);
  cursor: pointer;
  transition: all var(--app-transition);

  &:hover {
    background: var(--app-primary-soft);
    box-shadow: var(--app-shadow-md);
  }
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity var(--app-transition);
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

/* ==================== 输入区 ==================== */
.qa-input {
  padding: 14px 24px 16px;
  border-top: 1px solid var(--app-border);
  background: var(--app-bg-subtle);

  &__box {
    background: var(--app-card-bg);
    border: 1px solid var(--app-border);
    border-radius: var(--app-radius);
    padding: 8px;
    transition: border-color var(--app-transition), box-shadow var(--app-transition);

    &:focus-within {
      border-color: var(--app-primary);
      box-shadow: 0 0 0 3px var(--app-primary-ring);
    }
  }

  &__box :deep(.el-textarea__inner) {
    box-shadow: none;
    background: transparent;
    padding: 6px 8px;
    font-family: var(--app-font);
    font-size: 14px;
    line-height: 1.7;
  }

  &__actions {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    padding: 4px 8px 2px;
  }

  &__tip {
    font-size: 12px;
    color: var(--app-text-placeholder);
  }

  &__disclaimer {
    margin: 8px 0 0;
    font-size: 12px;
    text-align: center;
    color: var(--app-text-placeholder);
  }
}

/* ==================== 响应式 ==================== */
@media (max-width: 768px) {
  .qa-messages {
    padding: 16px;
  }

  .msg__content {
    max-width: 100%;
  }

  .qa-input {
    padding: 12px 16px 14px;
  }

  .scroll-bottom {
    right: 16px;
  }
}
</style>
