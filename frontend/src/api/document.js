import request from './request'

/** 上传规章制度 PDF */
export function uploadDocument(file, title) {
  const formData = new FormData()
  formData.append('file', file)
  if (title) formData.append('title', title)
  return request.post('/documents/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
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
