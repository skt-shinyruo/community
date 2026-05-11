import axios from 'axios'
import { useAuthStore } from '../stores/auth'
import http from './http'
import { resolveImHttpBaseUrl } from '../config/endpointResolution'
import { showToast } from '../ui/toastService'

const imCoreHttp = axios.create({
  baseURL: resolveImHttpBaseUrl(),
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
    const traceId = typeof result?.traceId === 'string' ? result.traceId : ''

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

    if (status >= 400) {
      const traceSuffix = traceId ? ` (traceId=${traceId})` : ''
      showToast({
        type: 'error',
        title: status === 401 ? '未登录或登录失效' : '请求失败',
        text: `${msg}${traceSuffix}`
      })
    }
    return Promise.reject(error)
  }
)

export default imCoreHttp
