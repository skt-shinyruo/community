import axios from 'axios'
import { recoverUnauthorized } from '../auth/refreshCoordinator'
import { useAuthStore } from '../stores/auth'
import { resolveImHttpBaseUrl } from '../config/endpointResolution'
import { showToast } from '../ui/toastService'

const imCoreHttp = axios.create({
  baseURL: resolveImHttpBaseUrl(),
  withCredentials: false,
  timeout: 15000
})

function setAuthorization(config, accessToken) {
  config.headers = config.headers || {}
  if (typeof config.headers.set === 'function') {
    config.headers.set('Authorization', `Bearer ${accessToken}`)
  } else {
    config.headers.Authorization = `Bearer ${accessToken}`
  }
}

imCoreHttp.interceptors.request.use((config) => {
  const auth = useAuthStore()
  if (auth.accessToken) {
    setAuthorization(config, auth.accessToken)
    config._authTokenGeneration = auth.tokenGeneration
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

    if (status === 401 && !original._retry) {
      original._retry = true
      const auth = useAuthStore()
      try {
        const accessToken = await recoverUnauthorized({
          auth,
          requestGeneration: original._authTokenGeneration
        })
        setAuthorization(original, accessToken)
        return imCoreHttp(original)
      } catch (e) {
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
