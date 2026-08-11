import axios from 'axios'
import { recoverUnauthorized } from '../auth/refreshCoordinator'
import { useAuthStore } from '../stores/auth'
import { resolveApiBaseUrl } from '../config/endpointResolution'
import { showErrorToast } from '../ui/toastService'

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

function setAuthorization(config, accessToken) {
  config.headers = config.headers || {}
  if (typeof config.headers.set === 'function') {
    config.headers.set('Authorization', `Bearer ${accessToken}`)
  } else {
    config.headers.Authorization = `Bearer ${accessToken}`
  }
}

http.interceptors.request.use((config) => {
  const auth = useAuthStore()
  config._authTokenGeneration = auth.tokenGeneration
  if (auth.accessToken) {
    setAuthorization(config, auth.accessToken)
  }

  return config
})

http.interceptors.response.use(
  (response) => response,
  async (error) => {
    const status = error?.response?.status
    const original = error?.config || {}
    const url = original?.url || ''
    const skipGlobalErrorToast = !!original?.skipGlobalErrorToast
    const result = error?.response?.data
    const resultMessage = typeof result?.message === 'string' ? result.message : ''
    const traceId = typeof result?.traceId === 'string' ? result.traceId : ''

    const isAuthEndpoint = isAuthEndpointUrl(url)

    // Global Error Toast for non-2xx / network errors (prefer backend Result.message + traceId)
    if (!skipGlobalErrorToast && (status >= 500 || error.code === 'ERR_NETWORK')) {
      const text = resultMessage || error.message || '服务异常，请稍后重试。'
      const traceSuffix = traceId ? ` (traceId=${traceId})` : ''
      showErrorToast(error, {
        type: 'error',
        title: '系统错误',
        text: `${text}${traceSuffix}`
      })
    }

    if (status === 401 && !original._retry && !isAuthEndpoint) {
      original._retry = true
      const auth = useAuthStore()
      try {
        const accessToken = await recoverUnauthorized({
          auth,
          requestGeneration: original._authTokenGeneration
        })
        setAuthorization(original, accessToken)
        return http(original)
      } catch (e) {
        if (e?.sessionRefreshState === 'terminal' && !auth.accessToken) {
          try {
            if (typeof globalThis !== 'undefined' && globalThis.location) {
              globalThis.location.href = '/#/auth/login'
            }
          } catch { }
        }
        return Promise.reject(e)
      }
    }

    // 对 4xx 也尽量展示后端 message/traceId，便于定位（但避免影响 refresh 重试逻辑）
    if (!skipGlobalErrorToast && status >= 400 && status < 500) {
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
