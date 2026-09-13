import axios from 'axios'
import { recoverUnauthorized } from '../auth/refreshCoordinator'
import { useAuthStore } from '../stores/auth'
import { resolveImHttpBaseUrl } from '../config/endpointResolution'
import { showErrorToast } from '../ui/toastService'
import { installAuthenticatedHttpInterceptors } from './authenticatedHttp'

const imCoreHttp = axios.create({
  baseURL: resolveImHttpBaseUrl(),
  withCredentials: false,
  timeout: 15000
})

installAuthenticatedHttpInterceptors(imCoreHttp, {
  authProvider: useAuthStore,
  unauthorizedRecovery: recoverUnauthorized
})

imCoreHttp.interceptors.response.use(
  (response) => response,
  async (error) => {
    const status = error?.response?.status
    const original = error?.config || {}
    const skipGlobalErrorToast = !!original?.skipGlobalErrorToast
    const result = error?.response?.data
    const msg = typeof result?.message === 'string' ? result.message : (error?.message || '请求失败')
    const traceId = typeof result?.traceId === 'string' ? result.traceId : ''
    const timeoutError = error?.code === 'ECONNABORTED' || error?.code === 'ETIMEDOUT'

    if (!skipGlobalErrorToast && (status >= 400 || timeoutError)) {
      const traceSuffix = traceId ? ` (traceId=${traceId})` : ''
      const title = timeoutError ? '请求超时' : status === 401 ? '未登录或登录失效' : '请求失败'
      const text = timeoutError ? '请求超时，请稍后重试。' : msg
      showErrorToast(error, {
        type: 'error',
        title,
        text: `${text}${traceSuffix}`
      })
    }
    return Promise.reject(error)
  }
)

export default imCoreHttp
