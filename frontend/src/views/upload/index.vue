<script setup>
/**
 * 上传学校规章制度 —— PDF 上传页面
 *
 * 功能：
 *   1. 仅允许选择 PDF 文件（前端先校验，后端仍会二次校验）
 *   2. 上传过程展示真实进度条（基于 axios 的 onUploadProgress）
 *   3. 上传成功展示绿色提示："上传成功，共提取 X 字"
 *   4. 上传失败展示红色提示
 *
 * 接口：POST {VITE_API_URL}/upload
 *       请求体 multipart/form-data，文件字段名固定为 file
 *
 * 关于接口地址：优先读取环境变量 VITE_API_URL；
 * 未配置时回落到项目既有的 VITE_API_BASE_URL（默认 /api，走 Vite 代理），
 * 因此本页面开箱即用，无需额外配置。
 */
import { computed, ref } from 'vue'
import axios from 'axios'
import { ElMessage } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'

// ---------- 接口地址 ----------
// 去掉末尾斜杠，避免拼出 //upload 这种路径
const API_BASE = (
  import.meta.env.VITE_API_URL ||
  import.meta.env.VITE_API_BASE_URL ||
  '/api'
).replace(/\/+$/, '')

const UPLOAD_URL = `${API_BASE}/upload`

// ---------- 组件状态 ----------
const uploadRef = ref(null)   // el-upload 实例，用于清空文件列表
const uploading = ref(false)  // 是否正在上传
const percent = ref(0)        // 上传进度（0-100）
const result = ref(null)      // 上传成功后后端返回的数据
const errorMsg = ref('')      // 上传失败提示

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

  return true
}

// ---------- 自定义上传 ----------
// el-upload 默认使用内置 XHR，拿不到细粒度的上传进度；
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
    // 如果你的后端直接返回业务数据、没有这层包装，删掉下面这段判断即可。
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
  // 超时
  if (err?.code === 'ECONNABORTED') {
    return '上传超时：文件较大或后端处理较慢，请稍后重试'
  }
  const status = err?.response?.status
  if (status === 413) {
    return '文件超过后端允许的大小上限'
  }
  // 优先展示后端返回的业务提示
  const msg = err?.response?.data?.message || err?.message
  return msg ? `上传失败：${msg}` : '上传失败，请检查网络或后端服务是否正常'
}

// 提取字数：后端字段为 textLength，这里兼容几种常见命名
const extractedChars = computed(
  () => result.value?.textLength ?? result.value?.charCount ?? 0
)

// 复位，便于连续上传下一份文件
function reset() {
  result.value = null
  errorMsg.value = ''
  percent.value = 0
}
</script>

<template>
  <div class="upload-page">
    <!-- 页头 -->
    <header class="upload-page__header">
      <h1 class="upload-page__title">上传学校规章制度</h1>
      <p class="upload-page__subtitle">
        支持 PDF 格式。上传后系统将自动解析全文、切分文本块并建立检索索引
      </p>
    </header>

    <section class="upload-card">
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

      <!-- 成功提示（绿色） -->
      <el-alert
        v-if="result"
        class="upload-alert"
        type="success"
        :closable="false"
        show-icon
      >
        <template #title>
          <span class="upload-alert__title">
            上传成功，共提取 {{ extractedChars }} 字
          </span>
        </template>
        <p class="upload-alert__meta">
          共 {{ result.pageCount ?? 0 }} 页 · 切分 {{ result.chunkCount ?? 0 }} 个文本块
        </p>
      </el-alert>

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
        :title="errorMsg"
      />

      <!-- 复位按钮 -->
      <div v-if="result || errorMsg" class="upload-actions">
        <el-button @click="reset">继续上传</el-button>
      </div>
    </section>
  </div>
</template>

<style scoped>
/* 整体：居中窄栏 + 充足留白，营造简洁的学术感 */
.upload-page {
  max-width: 720px;
  margin: 0 auto;
  padding: 8px 0 32px;
}

.upload-page__header {
  margin-bottom: 28px;
  text-align: center;
}

.upload-page__title {
  margin: 0;
  font-family: Georgia, 'Times New Roman', 'Songti SC', 'SimSun', serif;
  font-size: 26px;
  font-weight: 600;
  letter-spacing: 2px;
  color: #1a2b47;
}

.upload-page__subtitle {
  margin: 10px 0 0;
  font-size: 13px;
  line-height: 1.7;
  color: #6b7280;
}

.upload-card {
  padding: 28px;
  background: #ffffff;
  border: 1px solid #e6e8ec;
  border-radius: 10px;
}

/* 拖拽区外观（覆盖 el-upload 默认样式，改用 :deep 穿透 scoped） */
.upload-drop :deep(.el-upload-dragger) {
  padding: 36px 20px;
  background: #fbfcfe;
  border: 1px dashed #c8d0dc;
  border-radius: 8px;
  transition: border-color 0.2s, background-color 0.2s;
}

.upload-drop :deep(.el-upload-dragger:hover) {
  background: #f6f9ff;
  border-color: #1f6feb;
}

.upload-drop__icon {
  font-size: 40px;
  color: #1f6feb;
}

.upload-drop__main {
  margin: 12px 0 0;
  font-size: 14px;
  color: #1a2b47;
}

.upload-drop__main em {
  font-style: normal;
  color: #1f6feb;
}

.upload-drop__hint {
  margin: 6px 0 0;
  font-size: 12px;
  color: #9aa4b2;
}

/* 进度条 */
.upload-progress {
  margin-top: 24px;
}

.upload-progress__label {
  margin: 10px 0 0;
  font-size: 13px;
  color: #6b7280;
  text-align: center;
}

/* 提示条 */
.upload-alert {
  margin-top: 20px;
}

.upload-alert__title {
  font-size: 14px;
  font-weight: 600;
}

.upload-alert__meta {
  margin: 0;
  font-size: 12px;
  opacity: 0.85;
}

.upload-actions {
  margin-top: 20px;
  text-align: center;
}
</style>
