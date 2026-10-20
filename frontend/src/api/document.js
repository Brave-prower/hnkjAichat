import request from './request'

/** 上传规章制度 PDF（后端保存文件 → 切分 → 向量化入库，耗时较长故单独放宽超时） */
export function uploadDocument(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 300000
  })
}

/** 分页查询文档 */
export function pageDocuments(params) {
  return request.get('/documents', { params })
}

/** 文档详情 */
export function getDocument(id) {
  return request.get(`/documents/${id}`)
}

/** 文档切分后的文本块列表（页码 / 内容 / 是否已向量化） */
export function getDocumentChunks(id) {
  return request.get(`/documents/${id}/chunks`)
}

/**
 * 构造 PDF 原文访问地址。
 *
 * 后端返回的 previewUrl 是应用内相对路径（`/documents/{id}/file`，因为 context-path
 * 是 /api），这里统一补上 baseURL，才能直接塞进 <iframe src> / <a href>。
 *
 * @param {number|string} id 文档 ID
 * @param {boolean} download true 则触发下载（Content-Disposition: attachment）
 */
export function getDocumentFileUrl(id, download = false) {
  const base = request.defaults.baseURL || import.meta.env.VITE_API_BASE_URL || '/api'
  const prefix = String(base).replace(/\/+$/, '') // 去掉末尾斜杠，避免拼出 //documents
  return `${prefix}/documents/${id}/file${download ? '?download=true' : ''}`
}

/** 删除文档 */
export function deleteDocument(id) {
  return request.delete(`/documents/${id}`)
}

/** 重建向量索引 */
export function reindexDocument(id) {
  return request.post(`/documents/${id}/reindex`)
}
