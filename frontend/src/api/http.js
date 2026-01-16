import axios from 'axios'
import { useAuthStore } from '../stores/auth'
import router from '../router'

const http = axios.create({
  baseURL: '',
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
        router.replace({ name: 'login' })
        return Promise.reject(e)
      }
    }

    return Promise.reject(error)
  }
)

export default http

