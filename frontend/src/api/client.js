import axios from 'axios'
import { ElMessage } from 'element-plus'

export const TOKEN_KEY = 'miniwho.token'
export const USER_KEY = 'miniwho.user'

const client = axios.create({
  baseURL: import.meta.env.VITE_API_BASE || '/api',
  timeout: 20000
})

client.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) config.headers.satoken = token
  return config
})

let redirecting = false

client.interceptors.response.use(
  (response) => {
    if (response.config.responseType === 'blob') return response.data
    const body = response.data
    if (body && body.code === 'OK') return body.data
    const error = new Error(body?.message || '请求失败')
    error.code = body?.code || 'UNKNOWN'
    // 服务端的错误载荷复用 ApiResponse 的 data 字段带 details（如模板包的 problems）。
    error.details = body?.data
    error.status = response.status
    throw error
  },
  async (responseError) => {
    const response = responseError.response
    let body = response?.data
    // responseType: 'blob' 的请求（PDF / 模板包下载）出错时，错误载荷也是 Blob，
    // 不解析出来就只剩「请求失败」，看不到「导出任务繁忙」这类具体提示。
    if (body instanceof Blob && (body.type || '').includes('json')) {
      try {
        body = JSON.parse(await body.text())
      } catch {
        body = undefined
      }
    }
    const error = new Error(body?.message || (response ? '请求失败，请稍后重试' : '无法连接服务器，请确认后端已启动'))
    error.code = body?.code || 'NETWORK_ERROR'
    error.details = body?.data
    error.status = response?.status
    if (error.code === 'UNAUTHORIZED' && !redirecting && !location.pathname.startsWith('/login')) {
      redirecting = true
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(USER_KEY)
      const redirect = encodeURIComponent(location.pathname + location.search)
      setTimeout(() => {
        location.href = `/login?redirect=${redirect}`
        redirecting = false
      }, 400)
    }
    return Promise.reject(error)
  }
)

export function notifyError(error, fallback = '操作失败') {
  if (error?.status === 401 && error?.code === 'UNAUTHORIZED') return
  ElMessage.error(error?.message || fallback)
}

const API_BASE = import.meta.env.VITE_API_BASE || '/api'
export { API_BASE }
export const API_ORIGIN = API_BASE.replace(/\/api\/?$/, '')

/** 拼一个后端接口的完整路径，供浏览器直接导航用（OAuth 跳转就是其一）。 */
export function apiUrl(path) {
  return `${API_BASE}${path}`
}

/** 后端返回的 /api/... 资源路径补上部署前缀，供 <img src> 直接使用。 */
export function mediaUrl(path) {
  if (!path) return ''
  if (/^(https?:)?\/\//.test(path) || path.startsWith('data:')) return path
  return API_ORIGIN + path
}

/**
 * 条目图片（院校 / 公司 logo）：content 里存的是 uploads 下的存储 key，链接由 key 直接拼出。
 * 后端不给它签名，否则编辑器保存时会把临时链接一起写进数据。
 */
export function itemMediaUrl(key) {
  if (!key || typeof key !== 'string' || !key.startsWith('media/')) return ''
  return mediaUrl(`/api/public/${key}`)
}

export default client
