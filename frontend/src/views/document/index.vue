<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  deleteDocument,
  pageDocuments,
  reindexDocument,
  uploadDocument
} from '@/api/document'
import { formatDateTime, formatFileSize } from '@/utils/format'
import DocumentDetailDrawer from '@/components/DocumentDetailDrawer.vue'

const loading = ref(false)
const uploading = ref(false)
const tableData = ref([])

/* ---------- 详情抽屉 ---------- */
const drawerVisible = ref(false)
const activeDocumentId = ref(null)

function openDetail(row) {
  activeDocumentId.value = row.id
  drawerVisible.value = true
}

const query = reactive({
  page: 1,
  size: 10,
  keyword: ''
})

const total = ref(0)

const STATUS_MAP = {
  PENDING: { text: '待解析', type: 'info' },
  PARSING: { text: '解析中', type: 'warning' },
  READY: { text: '已就绪', type: 'success' },
  FAILED: { text: '解析失败', type: 'danger' }
}

async function loadList() {
  loading.value = true
  try {
    const data = await pageDocuments(query)
    tableData.value = data?.records || []
    total.value = data?.total || 0
  } catch (e) {
    // 接口未实现时保持空列表
    tableData.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  query.page = 1
  loadList()
}

function handleReset() {
  query.keyword = ''
  query.page = 1
  loadList()
}

/** 自定义上传：交给后端 POST /api/upload */
async function customUpload(options) {
  const { file, onSuccess, onError } = options
  if (!file.name.toLowerCase().endsWith('.pdf')) {
    ElMessage.error('只支持上传 PDF 格式的规章制度文件')
    onError?.(new Error('格式不支持'))
    return
  }

  uploading.value = true
  try {
    const result = await uploadDocument(file)
    if (result?.status === 'FAILED') {
      ElMessage.warning(
        '文件已保存，但向量化失败（请检查 Embedding API Key 配置），可稍后点“重建索引”重试'
      )
    } else {
      ElMessage.success(
        `上传成功：已切分 ${result?.chunkCount ?? 0} 个文本块并建立索引`
      )
    }
    onSuccess?.({})
    loadList()
  } catch (e) {
    onError?.(e)
    ElMessage.error('上传失败，请稍后重试')
  } finally {
    uploading.value = false
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(
      `确认删除《${row.title}》？删除后相关索引也会一并清理。`,
      '删除确认',
      { type: 'warning' }
    )
  } catch {
    return
  }
  try {
    await deleteDocument(row.id)
    ElMessage.success('删除成功')
    loadList()
  } catch (e) {
    ElMessage.error('删除失败，请稍后重试')
  }
}

async function handleReindex(row) {
  try {
    await reindexDocument(row.id)
    ElMessage.success('已触发重建索引')
    loadList()
  } catch (e) {
    ElMessage.error('重建索引失败，请检查后端日志')
  }
}

onMounted(loadList)
</script>

<template>
  <div class="document-page">
    <PageHeader title="规章制度库" description="上传学校规章制度 PDF，系统自动解析并建立检索索引">
      <template #extra>
        <el-upload
          :show-file-list="false"
          :http-request="customUpload"
          accept=".pdf"
          :disabled="uploading"
        >
          <el-button type="primary" size="large" :loading="uploading">
            <el-icon><UploadFilled /></el-icon>
            上传 PDF
          </el-button>
        </el-upload>
      </template>
    </PageHeader>

    <div class="card-panel">
      <!-- 搜索栏 -->
      <div class="toolbar">
        <el-input
          v-model="query.keyword"
          placeholder="搜索文档标题"
          clearable
          style="width: 260px"
          @keyup.enter="handleSearch"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-button @click="handleSearch">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
      </div>

      <!-- 列表 -->
      <el-table v-loading="loading" :data="tableData" stripe style="width: 100%">
        <el-table-column label="文档标题" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <!-- 标题本身即入口：想看的多半就是这一行 -->
            <el-link type="primary" :underline="false" class="title-link" @click="openDetail(row)">
              {{ row.title }}
            </el-link>
          </template>
        </el-table-column>
        <el-table-column prop="fileName" label="文件名" min-width="180" show-overflow-tooltip />
        <el-table-column label="大小" width="100">
          <template #default="{ row }">{{ formatFileSize(row.fileSize) }}</template>
        </el-table-column>
        <el-table-column label="页数" width="80">
          <template #default="{ row }">{{ row.pageCount ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="STATUS_MAP[row.status]?.type || 'info'" size="small">
              {{ STATUS_MAP[row.status]?.text || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="上传时间" width="160">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">
              <el-icon><View /></el-icon>查看
            </el-button>
            <el-button link type="primary" @click="handleReindex(row)">重建索引</el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>

        <template #empty>
          <el-empty description="暂无规章制度文档，点击右上角上传" />
        </template>
      </el-table>

      <!-- 分页 -->
      <div class="pagination">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.size"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="loadList"
          @size-change="handleSearch"
        />
      </div>
    </div>

    <!-- 文档详情抽屉：原文预览 + 文本块 -->
    <DocumentDetailDrawer v-model="drawerVisible" :document-id="activeDocumentId" />
  </div>
</template>

<style scoped lang="scss">
.toolbar {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
}

.pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.title-link {
  font-weight: 500;

  &:hover {
    text-decoration: underline;
  }
}
</style>
