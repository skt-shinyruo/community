import axios from 'axios'
import { recoverUnauthorized } from '../auth/refreshCoordinator'
import { useAuthStore } from '../stores/auth'
import { resolveApiBaseUrl } from '../config/endpointResolution'
import { showErrorToast } from '../ui/toastService'
import { installAuthenticatedHttpInterceptors } from './authenticatedHttp'

const http = axios.create({
  baseURL: resolveApiBaseUrl(),
  withCredentials: true,
  timeout: 15000
})

function isAuthEndpointUrl(url) {
  let path = String(url || '')
  try {
    path = new URL(path, 'http://community.local').pathname
  } catch { }
  return path === '/api/auth' || path.startsWith('/api/auth/')
}

installAuthenticatedHttpInterceptors(http, {
  authProvider: useAuthStore,
  unauthorizedRecovery: recoverUnauthorized,
  shouldRecoverUnauthorized: ({ config }) => !isAuthEndpointUrl(config?.url),
  onRecoveryFailure: ({ error, auth }) => {
    if (error?.sessionRefreshState !== 'terminal' || auth.accessToken) return
    try {
      if (typeof globalThis !== 'undefined' && globalThis.location) {
        globalThis.location.href = '/#/auth/login'
      }
    } catch { }
  }
})

http.interceptors.response.use(
  (response) => response,
  async (error) => {
    const status = error?.response?.status
    const result = error?.response?.data
    const resultMessage = typeof result?.message === 'string' ? result.message : ''
    const traceId = typeof result?.traceId === 'string' ? result.traceId : ''

    // Global Error Toast for non-2xx / network errors (prefer backend Result.message + traceId)
    if (status >= 500 || error.code === 'ERR_NETWORK') {
      const text = resultMessage || error.message || '服务异常，请稍后重试。'
      const traceSuffix = traceId ? ` (traceId=${traceId})` : ''
      showErrorToast(error, {
        type: 'error',
        title: '系统错误',
        text: `${text}${traceSuffix}`
      })
    }

    // 对 4xx 也尽量展示后端 message/traceId，便于定位。
    if (status >= 400 && status < 500) {
      const title = status === 401 ? '未登录或登录失效' : '请求失败'
      const text = resultMessage || error.message || '请求失败'
      const traceSuffix = traceId ? ` (traceId=${traceId})` : ''
      showErrorToast(error, {
        type: 'error',
        title,
        text: `${text}${traceSuffix}`
      })
    }

    return Promise.reject(error)
  }
)

export default http
