import axios from 'axios'
import { useAuthStore } from '../stores/auth'

function resolveApiBaseUrl() {
  const configured = import.meta.env?.VITE_API_BASE_URL
  if (typeof configured === 'string' && configured.trim()) {
    return configured.trim()
  }

  try {
    const loc = globalThis?.location
    if (!loc) return ''

    // Edge/同源模式：前端与 /api 在同一 origin（例如 http://localhost:8080）
    // 这种情况下走相对路径即可，由反代（edge）或同域网关处理。
    if (loc.port === '8080') return ''

    // 本地“前端直连 gateway”模式：前端端口与网关端口分离（例如 12881 -> 12882）。
    // 仅在 localhost/127.0.0.1 下做默认推导，避免影响非本地部署场景。
    if ((loc.hostname === 'localhost' || loc.hostname === '127.0.0.1') && loc.port === '12881') {
      return `${loc.protocol}//${loc.hostname}:12882`
    }
  } catch {}

  return ''
}

const http = axios.create({
  baseURL: resolveApiBaseUrl(),
  withCredentials: true,
  timeout: 15000
})

let refreshingPromise = null

http.interceptors.request.use((config) => {
  const auth = useAuthStore()
  if (auth.accessToken) {
    config.headers = config.headers || {}
    config.headers.Authorization = `Bearer ${auth.accessToken}`
  }
  return config
})

http.interceptors.response.use(
  (response) => response,
  async (error) => {
    const status = error?.response?.status
    const original = error?.config || {}
    const url = original?.url || ''

    const isAuthEndpoint = url.includes('/api/auth/login') || url.includes('/api/auth/refresh')
    if (status === 401 && !original._retry && !isAuthEndpoint) {
      original._retry = true
      try {
        if (!refreshingPromise) {
          refreshingPromise = http
            .post('/api/auth/refresh')
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
        } catch {}
        return Promise.reject(e)
      }
    }

    return Promise.reject(error)
  }
)

export default http
