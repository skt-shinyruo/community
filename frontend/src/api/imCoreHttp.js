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
    const result = error?.response?.data
    const msg = typeof result?.message === 'string' ? result.message : (error?.message || '请求失败')
    const traceId = typeof result?.traceId === 'string' ? result.traceId : ''

    if (status >= 400) {
      const traceSuffix = traceId ? ` (traceId=${traceId})` : ''
      showErrorToast(error, {
        type: 'error',
        title: status === 401 ? '未登录或登录失效' : '请求失败',
        text: `${msg}${traceSuffix}`
      })
    }
    return Promise.reject(error)
  }
)

export default imCoreHttp
