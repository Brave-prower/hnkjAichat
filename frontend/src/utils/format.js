/**
 * 格式化工具函数。
 */

/** 字节数转可读体积 */
export function formatFileSize(bytes) {
  if (bytes === null || bytes === undefined) return '-'
  if (bytes < 1024) return `${bytes} B`
  const units = ['KB', 'MB', 'GB']
  let value = bytes / 1024
  let i = 0
  while (value >= 1024 && i < units.length - 1) {
    value /= 1024
    i++
  }
  return `${value.toFixed(1)} ${units[i]}`
}

/** 时间字符串美化：2026-10-12T09:30:00 -> 2026-10-12 09:30 */
export function formatDateTime(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 16)
}

/** 生成前端临时会话 ID */
export function genSessionId() {
  return `s-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
}
