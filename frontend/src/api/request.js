import axios from 'axios'
import { ElMessage } from 'element-plus'

/**
 * Axios 实例：统一 baseURL、超时、响应解包与错误提示。
 * 后端统一响应体：{ code, message, data, timestamp }，code === 0 表示成功。
 */
const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 60000
})

request.interceptors.request.use(
  (config) => {
    // TODO 接入登录后在此附加 token
    // const token = localStorage.getItem('token')
    // if (token) config.headers.Authorization = `Bearer ${token}`
    return config
  },
  (error) => Promise.reject(error)
)

request.interceptors.response.use(
  (response) => {
    const body = response.data
    // 非标准结构（如文件流）直接返回
    if (!body || typeof body.code === 'undefined') {
      return body
    }
    if (body.code === 0) {
      return body.data
    }
    ElMessage.error(body.message || '请求失败')
    return Promise.reject(new Error(body.message || '请求失败'))
  },
  (error) => {
    const status = error.response?.status
    let message = '网络异常，请稍后重试'
    if (status === 401) message = '登录已过期，请重新登录'
    else if (status === 403) message = '没有权限执行该操作'
    else if (status === 404) message = '请求的资源不存在'
    else if (status >= 500) message = '服务器繁忙，请稍后重试'
    else if (error.response?.data?.message) message = error.response.data.message
    else if (error.code === 'ECONNABORTED') message = '请求超时，请稍后重试'

    ElMessage.error(message)
    return Promise.reject(error)
  }
)

export default request
