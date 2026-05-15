import axios from 'axios'
import { useAuthStore } from '../stores/auth'
import { createIdempotencyKeyCache } from './idempotencyKeyCache'
import { resolveApiBaseUrl } from '../config/endpointResolution'
import { showToast } from '../ui/toastService'

const http = axios.create({
  baseURL: resolveApiBaseUrl(),
  withCredentials: true,
  timeout: 15000
})

let refreshingPromise = null

const IDEMPOTENCY_HEADER = 'Idempotency-Key'
const IDEMPOTENCY_WINDOW_MS = 10000
const IDEMPOTENCY_MAX_CACHE_SIZE = 5000

function shouldAttachIdempotencyKey(config) {
  const method = String(config?.method || '').toLowerCase()
  const url = String(config?.url || '')
  if (method !== 'post') return false

  if (url === '/api/posts') return true
  if (/^\/api\/posts\/[^/]+\/comments$/.test(url)) return true
  if (url === '/api/wallet/recharges') return true
  if (url === '/api/wallet/withdrawals') return true
  if (url === '/api/wallet/transfers') return true
  if (url === '/api/market/orders') return true

  return false
}

function resolveIdempotencyKey(config) {
  const url = String(config?.url || '')
  const body = safeStringify(config?.data)
  const fingerprint = `post:${url}:${hashString(body)}`
  return idempotencyKeyCache.getOrReuse(fingerprint)
}

function generateIdempotencyKey() {
  try {
    const cryptoObj = globalThis?.crypto
    if (cryptoObj?.randomUUID) return cryptoObj.randomUUID()
  } catch { }

  const rand = Math.random().toString(36).slice(2)
  const now = Date.now().toString(36)
  return `idem_${now}_${rand}`
}

const idempotencyKeyCache = createIdempotencyKeyCache({
  windowMs: IDEMPOTENCY_WINDOW_MS,
  maxSize: IDEMPOTENCY_MAX_CACHE_SIZE,
  generateKey: generateIdempotencyKey
})

function safeStringify(data) {
  if (data == null) return ''
  if (typeof data === 'string') return data
  try {
    return JSON.stringify(data)
  } catch {
    return ''
  }
}

function hashString(str) {
  const s = String(str || '')
  let h = 5381
  for (let i = 0; i < s.length; i += 1) {
    h = ((h << 5) + h) ^ s.charCodeAt(i)
  }
  return (h >>> 0).toString(36)
}

function isAuthEndpointUrl(url) {
  let path = String(url || '')
  try {
    path = new URL(path, 'http://community.local').pathname
  } catch { }
  return path === '/api/auth' || path.startsWith('/api/auth/')
}

http.interceptors.request.use((config) => {
  const auth = useAuthStore()
  if (auth.accessToken) {
    config.headers = config.headers || {}
    config.headers.Authorization = `Bearer ${auth.accessToken}`
  }

  if (shouldAttachIdempotencyKey(config)) {
    config.headers = config.headers || {}
    if (!config.headers[IDEMPOTENCY_HEADER]) {
      config.headers[IDEMPOTENCY_HEADER] = resolveIdempotencyKey(config)
    }
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
      showToast({
        type: 'error',
        title: '系统错误',
        text: `${text}${traceSuffix}`
      })
    }

    if (status === 401 && !original._retry && !isAuthEndpoint) {
      original._retry = true
      try {
        if (!refreshingPromise) {
          refreshingPromise = http
            .post('/api/auth/refresh', null, { skipGlobalErrorToast: true })
            .finally(() => {
              refreshingPromise = null
            })
        }
        const refreshResp = await refreshingPromise
        const newToken = refreshResp?.data?.data?.accessToken
        if (newToken) {
          useAuthStore().setAccessToken(newToken)
          return http(original)
        }
      } catch (e) {
        useAuthStore().clear()
        // 避免与 router 的循环依赖；这里使用硬跳转回登录页即可。
        try {
          if (typeof globalThis !== 'undefined' && globalThis.location) {
            globalThis.location.href = '/#/auth/login'
          }
        } catch { }
        return Promise.reject(e)
      }
    }

    // 对 4xx 也尽量展示后端 message/traceId，便于定位（但避免影响 refresh 重试逻辑）
    if (!skipGlobalErrorToast && status >= 400 && status < 500) {
      const title = status === 401 ? '未登录或登录失效' : '请求失败'
      const text = resultMessage || error.message || '请求失败'
      const traceSuffix = traceId ? ` (traceId=${traceId})` : ''
      showToast({
        type: 'error',
        title,
        text: `${text}${traceSuffix}`
      })
    }

    return Promise.reject(error)
  }
)

export default http
