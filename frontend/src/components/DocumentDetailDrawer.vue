<script setup>
/**
 * 文档详情抽屉。
 *
 * 从右侧滑出，用两个标签页回答「这个文档到底是什么」：
 *   1) 原文预览 —— iframe 内嵌 PDF，可核对系统读到的确实是这份文件
 *   2) 文本块   —— 切分结果（页码 / 字数 / 是否已向量化），可核对切分质量
 *
 * 为什么用抽屉而不是整页跳转：核对文档通常是「一个个快速过」，抽屉不离开列表，
 * 关掉即回到原位置，比来回前进后退顺手得多。
 */
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getDocument, getDocumentChunks, getDocumentFileUrl } from '@/api/document'
import { formatDateTime, formatFileSize } from '@/utils/format'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  /** 要展示的文档 ID，为 null 时不加载 */
  documentId: { type: [Number, String], default: null }
})

const emit = defineEmits(['update:modelValue'])

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

const STATUS_MAP = {
  PENDING: { text: '待解析', type: 'info' },
  PARSING: { text: '解析中', type: 'warning' },
  READY: { text: '已就绪', type: 'success' },
  FAILED: { text: '解析失败', type: 'danger' }
}

const activeTab = ref('preview')
const detail = ref(null)
const chunks = ref([])
const loadingDetail = ref(false)
const loadingChunks = ref(false)
const detailError = ref('')

/** PDF 预览地址（走同源 /api，浏览器原生 PDF 阅读器直接渲染） */
const previewUrl = computed(() =>
  props.documentId ? getDocumentFileUrl(props.documentId) : ''
)

const downloadUrl = computed(() =>
  props.documentId ? getDocumentFileUrl(props.documentId, true) : ''
)

const statusMeta = computed(() => {
  const s = detail.value?.status
  return STATUS_MAP[s] || { text: s || '-', type: 'info' }
})

/** 已生成向量的块数，用于在标签上直接给出结论 */
const embeddedCount = computed(() => chunks.value.filter((c) => c.hasEmbedding).length)

async function loadDetail() {
  if (!props.documentId) return
  loadingDetail.value = true
  detailError.value = ''
  try {
    detail.value = await getDocument(props.documentId)
  } catch (e) {
    detail.value = null
    detailError.value = e?.response?.data?.message || '文档详情加载失败'
  } finally {
    loadingDetail.value = false
  }
}

async function loadChunks() {
  if (!props.documentId) return
  loadingChunks.value = true
  try {
    chunks.value = (await getDocumentChunks(props.documentId)) || []
  } catch (e) {
    chunks.value = []
  } finally {
    loadingChunks.value = false
  }
}

/** 切换标签页时才加载对应数据，避免打开抽屉就发两个请求 */
function handleTabChange(name) {
  if (name === 'chunks' && chunks.value.length === 0 && !loadingChunks.value) {
    loadChunks()
  }
}

function copyText(text) {
  navigator.clipboard
    ?.writeText(text)
    .then(() => ElMessage.success('已复制该文本块'))
    .catch(() => ElMessage.warning('复制失败，请手动选中'))
}

watch(
  () => [props.modelValue, props.documentId],
  ([open, id]) => {
    if (!open || !id) return
    // 每次打开都重新拉取，保证状态（如重建索引后）是最新的
    detail.value = null
    chunks.value = []
    activeTab.value = 'preview'
    loadDetail()
  },
  { immediate: true }
)
</script>

<template>
  <el-drawer
    v-model="visible"
    :size="900"
    direction="rtl"
    destroy-on-close
    class="doc-detail-drawer"
  >
    <template #header>
      <div class="drawer-header">
        <div class="drawer-title">
          <el-icon><Document /></el-icon>
          <span>{{ detail?.title || '文档详情' }}</span>
        </div>
        <el-tag :type="statusMeta.type" size="small" effect="light">
          {{ statusMeta.text }}
        </el-tag>
      </div>
    </template>

    <!-- 元信息 -->
    <div v-if="detail" class="meta-bar">
      <div class="meta-item">
        <span class="meta-label">文件名</span>
        <span class="meta-value" :title="detail.fileName">{{ detail.fileName }}</span>
      </div>
      <div class="meta-item">
        <span class="meta-label">大小</span>
        <span class="meta-value">{{ formatFileSize(detail.fileSize) }}</span>
      </div>
      <div class="meta-item">
        <span class="meta-label">页数</span>
        <span class="meta-value">{{ detail.pageCount ?? '-' }}</span>
      </div>
      <div class="meta-item">
        <span class="meta-label">上传时间</span>
        <span class="meta-value">{{ formatDateTime(detail.createdAt) }}</span>
      </div>
      <div class="meta-item meta-item--action">
        <el-link type="primary" :href="downloadUrl" target="_blank" :underline="false">
          <el-icon><Download /></el-icon>
          下载原文
        </el-link>
      </div>
    </div>

    <el-alert
      v-if="detailError"
      :title="detailError"
      type="error"
      show-icon
      :closable="false"
      style="margin-bottom: 12px"
    />

    <el-tabs v-model="activeTab" class="detail-tabs" @tab-change="handleTabChange">
      <!-- ---------- 原文预览 ---------- -->
      <el-tab-pane name="preview">
        <template #label>
          <span class="tab-label"><el-icon><View /></el-icon>原文预览</span>
        </template>

        <div v-if="previewUrl" class="pdf-wrap">
          <iframe :src="previewUrl" class="pdf-frame" title="PDF 原文预览" />
        </div>
        <el-empty v-else description="没有可预览的文件" />
      </el-tab-pane>

      <!-- ---------- 文本块 ---------- -->
      <el-tab-pane name="chunks">
        <template #label>
          <span class="tab-label">
            <el-icon><Grid /></el-icon>文本块
            <el-badge
              v-if="chunks.length"
              :value="chunks.length"
              type="primary"
              class="tab-badge"
            />
          </span>
        </template>

        <div v-loading="loadingChunks" class="chunk-list">
          <div v-if="chunks.length" class="chunk-summary">
            共 <b>{{ chunks.length }}</b> 个文本块，其中
            <b class="ok">{{ embeddedCount }}</b> 个已生成向量
            <span v-if="embeddedCount < chunks.length" class="warn">
              （{{ chunks.length - embeddedCount }} 个无向量，仅关键词可命中）
            </span>
          </div>

          <div v-for="(chunk, i) in chunks" :key="chunk.id" class="chunk-card">
            <div class="chunk-head">
              <div class="chunk-tags">
                <el-tag size="small" effect="plain" type="info">#{{ chunk.index ?? i + 1 }}</el-tag>
                <el-tag v-if="chunk.pageNumber != null" size="small" effect="plain" type="primary">
                  第 {{ chunk.pageNumber }} 页
                </el-tag>
                <el-tag
                  size="small"
                  effect="light"
                  :type="chunk.hasEmbedding ? 'success' : 'warning'"
                >
                  {{ chunk.hasEmbedding ? '已向量化' : '无向量' }}
                </el-tag>
                <span class="chunk-chars">{{ chunk.charCount }} 字</span>
              </div>
              <el-button link size="small" type="primary" @click="copyText(chunk.content)">
                复制
              </el-button>
            </div>
            <p class="chunk-body">{{ chunk.content }}</p>
          </div>

          <el-empty
            v-if="!loadingChunks && !chunks.length"
            description="暂无文本块。若文档状态异常，可尝试在列表中点击「重建索引」"
          />
        </div>
      </el-tab-pane>
    </el-tabs>
  </el-drawer>
</template>

<style scoped lang="scss">
.drawer-header {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.drawer-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
  color: var(--app-text-primary);
  min-width: 0;

  span {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.meta-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 24px;
  padding: 12px 16px;
  margin-bottom: 8px;
  background: var(--app-bg-subtle);
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius);
}

.meta-item {
  display: flex;
  align-items: baseline;
  gap: 6px;
  min-width: 0;
  font-size: 13px;

  &--action {
    margin-left: auto;
  }
}

.meta-label {
  color: var(--app-text-secondary);
  flex-shrink: 0;
}

.meta-value {
  color: var(--app-text-regular);
  max-width: 260px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tab-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.tab-badge {
  margin-left: 2px;

  :deep(.el-badge__content) {
    transform: translateY(-2px);
  }
}

/* ---------- PDF 预览 ---------- */
.pdf-wrap {
  height: calc(100vh - 260px);
  min-height: 420px;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius);
  overflow: hidden;
  background: var(--app-bg-subtle);
}

.pdf-frame {
  width: 100%;
  height: 100%;
  border: 0;
  display: block;
}

/* ---------- 文本块 ---------- */
.chunk-list {
  height: calc(100vh - 260px);
  min-height: 420px;
  overflow-y: auto;
  padding-right: 4px;
}

.chunk-summary {
  font-size: 13px;
  color: var(--app-text-regular);
  padding: 0 2px 10px;

  .ok {
    color: var(--app-success);
  }

  .warn {
    color: var(--app-warning);
  }
}

.chunk-card {
  padding: 12px 14px;
  margin-bottom: 10px;
  background: var(--app-card-bg);
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius);
  box-shadow: var(--app-shadow-xs);
  transition: border-color var(--app-transition);

  &:hover {
    border-color: var(--app-primary-border);
  }
}

.chunk-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 8px;
}

.chunk-tags {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.chunk-chars {
  font-size: 12px;
  color: var(--app-text-secondary);
}

.chunk-body {
  margin: 0;
  font-size: 13px;
  line-height: 1.75;
  color: var(--app-text-regular);
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
