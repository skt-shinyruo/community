import axios from 'axios'
import { useAuthStore } from '../stores/auth'
import http from './http'

function resolveImCoreBaseUrl() {
  const configured = import.meta.env?.VITE_IM_CORE_BASE_URL
  if (typeof configured === 'string' && configured.trim()) {
    return configured.trim()
  }

  try {
    const loc = globalThis?.location
    if (!loc) return ''
    const isLocalHost = loc.hostname === 'localhost' || loc.hostname === '127.0.0.1'
    if (isLocalHost) {
      return `${loc.protocol}//${loc.hostname}:18082`
    }
  } catch {}

  // Default: same origin (if an edge proxy/ingress routes /api/im/** to im-core)
  return ''
}

const imCoreHttp = axios.create({
  baseURL: resolveImCoreBaseUrl(),
  withCredentials: false,
  timeout: 15000
})

imCoreHttp.interceptors.request.use((config) => {
  const auth = useAuthStore()
  if (auth.accessToken) {
    config.headers = config.headers || {}
    config.headers.Authorization = `Bearer ${auth.accessToken}`
  }
  return config
})

imCoreHttp.interceptors.response.use(
  (response) => response,
  async (error) => {
    const status = error?.response?.status
    const original = error?.config || {}
    const result = error?.response?.data
    const msg = typeof result?.message === 'string' ? result.message : (error?.message || '请求失败')

    // Best-effort refresh on 401 (reuse community-app refresh cookie via `http`).
    if (status === 401 && !original._retry) {
      original._retry = true
      try {
        const refreshResp = await http.post('/api/auth/refresh')
        const newToken = refreshResp?.data?.data?.accessToken
        if (newToken) {
          useAuthStore().setAccessToken(newToken)
          original.headers = original.headers || {}
          original.headers.Authorization = `Bearer ${newToken}`
          return imCoreHttp(original)
        }
      } catch (e) {
        useAuthStore().clear()
        return Promise.reject(e)
      }
    }

    if (status >= 400 && typeof window !== 'undefined' && window.$toast) {
      window.$toast({
        type: 'error',
        title: status === 401 ? '未登录或登录失效' : '请求失败',
        text: msg
      })
    }
    return Promise.reject(error)
  }
)

export default imCoreHttp
