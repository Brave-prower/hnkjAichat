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

/** 删除文档 */
export function deleteDocument(id) {
  return request.delete(`/documents/${id}`)
}

/** 重建向量索引 */
export function reindexDocument(id) {
  return request.post(`/documents/${id}/reindex`)
}
