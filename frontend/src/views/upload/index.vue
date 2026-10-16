<script setup>
/**
 * 上传学校规章制度 —— PDF 上传页面
 *
 * 功能：
 *   1. 支持拖拽上传（el-upload drag），也支持点击选择
 *   2. 仅允许 PDF（前端先校验，后端仍会二次校验），单文件 ≤ 50MB
 *   3. 展示真实上传进度（axios onUploadProgress）
 *   4. 上传成功后显示「文件名 + 提取字数 + 页数 + 文本块数」
 *   5. 失败时给出可读的中文提示
 *
 * 接口：POST {VITE_API_URL}/upload
 *       请求体 multipart/form-data，文件字段名固定为 file
 */
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'
import { ElMessage } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'

const router = useRouter()

// ---------- 接口地址 ----------
// 去掉末尾斜杠，避免拼出 //upload 这种路径
const API_BASE = (
  import.meta.env.VITE_API_URL ||
  import.meta.env.VITE_API_BASE_URL ||
  '/api'
).replace(/\/+$/, '')

const UPLOAD_URL = `${API_BASE}/upload`

// ---------- 组件状态 ----------
const uploadRef = ref(null) // el-upload 实例，用于清空文件列表
const uploading = ref(false) // 是否正在上传
const percent = ref(0) // 上传进度（0-100）
const result = ref(null) // 上传成功后后端返回的数据
const errorMsg = ref('') // 上传失败提示
const fileName = ref('') // 本次上传的文件名（el-upload 清空列表后仍要展示）
const fileSize = ref(0) // 本次上传的文件字节数

// ---------- 处理流程说明（页面底部） ----------
const steps = [
  { icon: 'Document', title: '解析全文', desc: '提取 PDF 全部文字内容' },
  { icon: 'Scissor', title: '切分文本', desc: '按段落切成可检索小块' },
  { icon: 'Search', title: '建立索引', desc: '生成向量，支持语义检索' }
]

// ---------- 选中文件后、发起请求前的校验 ----------
function beforeUpload(file) {
  // 校验 1：必须是 PDF（浏览器给的 mime 偶尔为空，所以再看一眼扩展名）
  const isPdf =
    file.type === 'application/pdf' || file.name.toLowerCase().endsWith('.pdf')
  if (!isPdf) {
    ElMessage.error('只能上传 PDF 格式的规章制度文件')
    return false
  }

  // 校验 2：大小限制（与后端 multipart 配置保持一致：单文件 50MB）
  const withinLimit = file.size / 1024 / 1024 <= 50
  if (!withinLimit) {
    ElMessage.error('单个文件不能超过 50MB')
    return false
  }

  // 记录文件名/体积：el-upload 在请求结束会清空内部列表，这里先留存
  fileName.value = file.name
  fileSize.value = file.size
  return true
}

// ---------- 自定义上传 ----------
// el-upload 默认使用内置 XHR，拿不到细粒度进度；
// 这里改成用 axios 发请求，通过 onUploadProgress 回传真实进度。
async function customUpload(options) {
  const { file, onSuccess, onError } = options

  const formData = new FormData()
  formData.append('file', file) // 字段名必须是 file，与后端 @RequestParam 对应

  uploading.value = true
  percent.value = 0
  result.value = null
  errorMsg.value = ''

  try {
    const { data: res } = await axios.post(UPLOAD_URL, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 300000, // 大文件解析 + 建立索引耗时较久，放宽到 5 分钟
      onUploadProgress(e) {
        if (e.total) {
          percent.value = Math.round((e.loaded / e.total) * 100)
        }
      }
    })

    // 后端统一响应包装：{ code, message, data, timestamp }，code === 0 表示成功。
    if (res && typeof res.code !== 'undefined' && res.code !== 0) {
      throw new Error(res.message || '上传失败')
    }

    result.value = res?.data ?? res
    percent.value = 100
    onSuccess?.(result.value)
    ElMessage.success('上传成功')
  } catch (err) {
    errorMsg.value = resolveError(err)
    onError?.(err)
  } finally {
    uploading.value = false
    // 清空 el-upload 内部文件列表，否则 limit=1 会拦住下一次选择同一文件
    uploadRef.value?.clearFiles()
  }
}

// 把各种异常整理成一句能看懂的中文提示
function resolveError(err) {
  if (err?.code === 'ECONNABORTED') {
    return '上传超时：文件较大或后端处理较慢，请稍后重试'
  }
  const status = err?.response?.status
  if (status === 413) {
    return '文件超过后端允许的大小上限，请压缩后再试'
  }
  if (status === 415) {
    return '文件格式不被支持，请确认是标准 PDF 文件'
  }
  if (status >= 500) {
    return '服务器处理失败，请稍后重试或联系管理员'
  }
  // 优先展示后端返回的业务提示
  const msg = err?.response?.data?.message || err?.message
  return msg ? `上传失败：${msg}` : '上传失败，请检查网络或后端服务是否正常'
}

// 提取字数：后端字段为 textLength，这里兼容几种常见命名
const extractedChars = computed(
  () => result.value?.textLength ?? result.value?.charCount ?? 0
)

// 展示用文件名：优先取后端回传，其次取本次选择的文件名
const displayName = computed(
  () => result.value?.fileName || fileName.value || '未命名文件'
)

const displaySize = computed(() => {
  const bytes = result.value?.fileSize ?? fileSize.value
  if (!bytes) return ''
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(2)} MB`
})

// 字数格式化：12345 -> 12,345
const charsText = computed(() => Number(extractedChars.value || 0).toLocaleString())

// 复位，便于连续上传下一份文件
function reset() {
  result.value = null
  errorMsg.value = ''
  percent.value = 0
  fileName.value = ''
  fileSize.value = 0
}

function goDocumentList() {
  router.push('/document')
}
</script>

<template>
  <div class="upload-page">
    <!-- 页头 -->
    <header class="upload-page__header">
      <h1 class="upload-page__title">上传学校规章制度</h1>
      <p class="upload-page__subtitle">
        支持 PDF 格式。上传后系统将自动解析全文、切分文本块并建立检索索引，
        随后即可在智能问答中被检索引用。
      </p>
    </header>

    <section class="upload-card">
      <!-- 上传区说明：图标 + 文字 -->
      <div class="upload-intro">
        <div class="upload-intro__icon">
          <el-icon :size="24"><UploadFilled /></el-icon>
        </div>
        <div class="upload-intro__text">
          <h2 class="upload-intro__title">选择或拖拽文件</h2>
          <p class="upload-intro__desc">
            把 PDF 文件拖到下方虚线区域内即可开始上传，也可以点击区域选择本地文件
          </p>
        </div>
      </div>

      <!-- 拖拽上传区 -->
      <el-upload
        ref="uploadRef"
        class="upload-drop"
        drag
        accept=".pdf,application/pdf"
        :limit="1"
        :show-file-list="false"
        :before-upload="beforeUpload"
        :http-request="customUpload"
        :disabled="uploading"
      >
        <el-icon class="upload-drop__icon"><UploadFilled /></el-icon>
        <div class="upload-drop__text">
          <p class="upload-drop__main">
            将 PDF 文件拖到此处，或<em>点击选择文件</em>
          </p>
          <p class="upload-drop__hint">仅支持 PDF，单个文件不超过 50MB</p>
        </div>
      </el-upload>

      <!-- 上传进度 -->
      <div v-if="uploading" class="upload-progress">
        <el-progress
          :percentage="percent"
          :stroke-width="10"
          :status="percent >= 100 ? 'success' : undefined"
        />
        <p class="upload-progress__label">
          {{ percent < 100 ? `正在上传 ${percent}%` : '上传完成，正在解析并建立索引…' }}
        </p>
      </div>

      <!-- 上传成功：显示文件名与字数 -->
      <transition name="fade">
        <div v-if="result" class="upload-result">
          <div class="upload-result__flag">
            <el-icon><CircleCheckFilled /></el-icon>
          </div>

          <div class="upload-result__body">
            <div class="upload-result__file">
              <el-icon class="upload-result__file-icon"><Document /></el-icon>
              <span class="upload-result__file-name" :title="displayName">
                {{ displayName }}
              </span>
              <span v-if="displaySize" class="upload-result__file-size">
                {{ displaySize }}
              </span>
            </div>

            <div class="upload-result__stats">
              <div class="stat">
                <span class="stat__value">{{ charsText }}</span>
                <span class="stat__label">提取字数</span>
              </div>
              <div class="stat">
                <span class="stat__value">{{ result.pageCount ?? 0 }}</span>
                <span class="stat__label">总页数</span>
              </div>
              <div class="stat">
                <span class="stat__value">{{ result.chunkCount ?? 0 }}</span>
                <span class="stat__label">文本块</span>
              </div>
            </div>

            <p class="upload-result__hint">
              上传成功，已建立索引，可前往「智能问答」提问，或到「规章制度库」查看。
            </p>
          </div>
        </div>
      </transition>

      <!-- 索引未完成时的补充提醒（橙色，非上传失败） -->
      <el-alert
        v-if="result?.status === 'FAILED'"
        class="upload-alert"
        type="warning"
        :closable="false"
        show-icon
        title="内容尚未建立索引"
        description="文件已保存，但向量化未完成，问答暂时检索不到该文档。请检查 Embedding 配置后在文档列表点击「重建索引」。"
      />

      <!-- 失败提示（红色） -->
      <el-alert
        v-if="errorMsg"
        class="upload-alert"
        type="error"
        :closable="false"
        show-icon
        title="上传失败"
        :description="errorMsg"
      />

      <!-- 操作按钮 -->
      <div v-if="result || errorMsg" class="upload-actions">
        <el-button @click="reset">继续上传</el-button>
        <el-button v-if="result" type="primary" @click="goDocumentList">
          查看规章制度库
        </el-button>
      </div>
    </section>

    <!-- 处理流程说明 -->
    <div class="upload-steps">
      <div v-for="(s, i) in steps" :key="s.title" class="step">
        <div class="step__icon">
          <el-icon :size="18"><component :is="s.icon" /></el-icon>
        </div>
        <div class="step__text">
          <span class="step__index">{{ String(i + 1).padStart(2, '0') }}</span>
          <span class="step__title">{{ s.title }}</span>
          <span class="step__desc">{{ s.desc }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
/* 页面居中，最大宽度由 .app-container 统一控制（1200px） */
.upload-page {
  padding: 8px 0 32px;
}

.upload-page__header {
  margin-bottom: 24px;
  text-align: center;
}

.upload-page__title {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
  letter-spacing: 0.5px;
}

.upload-page__subtitle {
  max-width: 620px;
  margin: 10px auto 0;
  font-size: 13px;
  line-height: 1.8;
  color: var(--app-text-secondary);
}

/* ---------- 主卡片 ---------- */
.upload-card {
  padding: 28px;
  background: var(--app-card-bg);
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-lg);
  box-shadow: var(--app-shadow-sm);
}

.upload-intro {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 20px;

  &__icon {
    flex-shrink: 0;
    width: 44px;
    height: 44px;
    display: flex;
    align-items: center;
    justify-content: center;
    color: var(--app-primary);
    background: var(--app-primary-soft);
    border-radius: var(--app-radius);
  }

  &__title {
    margin: 0 0 2px;
    font-size: 15px;
  }

  &__desc {
    margin: 0;
    font-size: 13px;
    color: var(--app-text-secondary);
  }
}

/* ---------- 拖拽区 ---------- */
.upload-drop :deep(.el-upload) {
  display: block;
}

.upload-drop :deep(.el-upload-dragger) {
  padding: 44px 20px;
  background: var(--app-bg-subtle);
  border: 1.5px dashed var(--app-border-strong);
  border-radius: var(--app-radius);
  transition: border-color var(--app-transition), background-color var(--app-transition),
    box-shadow var(--app-transition);
}

.upload-drop :deep(.el-upload-dragger:hover) {
  background: var(--app-primary-soft);
  border-color: var(--app-primary);
  box-shadow: inset 0 0 0 1px var(--app-primary-ring);
}

.upload-drop :deep(.el-upload-dragger.is-dragover) {
  background: var(--app-primary-soft);
  border-color: var(--app-primary);
  border-style: solid;
}

.upload-drop__icon {
  font-size: 44px;
  color: var(--app-primary);
}

.upload-drop__main {
  margin: 14px 0 0;
  font-size: 14.5px;
  color: var(--app-text-primary);

  em {
    font-style: normal;
    font-weight: 500;
    color: var(--app-primary);
  }
}

.upload-drop__hint {
  margin: 6px 0 0;
  font-size: 12px;
  color: var(--app-text-placeholder);
}

/* ---------- 进度 ---------- */
.upload-progress {
  margin-top: 24px;

  &__label {
    margin: 10px 0 0;
    font-size: 13px;
    color: var(--app-text-secondary);
    text-align: center;
  }
}

/* ---------- 成功结果 ---------- */
.upload-result {
  display: flex;
  gap: 16px;
  margin-top: 24px;
  padding: 20px;
  background: var(--app-success-soft);
  border: 1px solid #bfe6d0;
  border-radius: var(--app-radius);

  &__flag {
    flex-shrink: 0;
    font-size: 26px;
    color: var(--app-success);
    line-height: 1;
  }

  &__body {
    flex: 1;
    min-width: 0;
  }

  &__file {
    display: flex;
    align-items: center;
    gap: 8px;
    min-width: 0;
  }

  &__file-icon {
    color: var(--app-success);
    flex-shrink: 0;
  }

  &__file-name {
    font-size: 14px;
    font-weight: 600;
    color: var(--app-text-primary);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  &__file-size {
    flex-shrink: 0;
    font-size: 12px;
    color: var(--app-text-secondary);
  }

  &__stats {
    display: flex;
    gap: 12px;
    margin-top: 14px;
    flex-wrap: wrap;
  }

  &__hint {
    margin: 14px 0 0;
    font-size: 12.5px;
    color: var(--app-text-regular);
  }
}

.stat {
  min-width: 96px;
  padding: 10px 14px;
  background: rgba(255, 255, 255, 0.75);
  border-radius: var(--app-radius-sm);
  text-align: center;

  &__value {
    display: block;
    font-size: 20px;
    font-weight: 600;
    line-height: 1.2;
    color: var(--app-success);
    font-variant-numeric: tabular-nums;
  }

  &__label {
    display: block;
    margin-top: 2px;
    font-size: 12px;
    color: var(--app-text-secondary);
  }
}

/* ---------- 提示条 ---------- */
.upload-alert {
  margin-top: 20px;
}

/* ---------- 操作 ---------- */
.upload-actions {
  margin-top: 20px;
  display: flex;
  justify-content: center;
  gap: 10px;
}

/* ---------- 流程说明 ---------- */
.upload-steps {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  margin-top: 18px;
}

.step {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  background: var(--app-card-bg);
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius);

  &__icon {
    flex-shrink: 0;
    width: 36px;
    height: 36px;
    display: flex;
    align-items: center;
    justify-content: center;
    color: var(--app-primary);
    background: var(--app-primary-soft);
    border-radius: var(--app-radius-sm);
  }

  &__text {
    display: flex;
    flex-direction: column;
    gap: 2px;
    min-width: 0;
  }

  &__index {
    font-size: 11px;
    font-weight: 600;
    letter-spacing: 1px;
    color: var(--app-text-placeholder);
  }

  &__title {
    font-size: 13.5px;
    font-weight: 600;
  }

  &__desc {
    font-size: 12px;
    color: var(--app-text-secondary);
  }
}

/* ---------- 过渡 ---------- */
.fade-enter-active,
.fade-leave-active {
  transition: opacity var(--app-transition), transform var(--app-transition);
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
  transform: translateY(6px);
}

/* ---------- 响应式 ---------- */
@media (max-width: 900px) {
  .upload-steps {
    grid-template-columns: 1fr;
  }
}
</style>
