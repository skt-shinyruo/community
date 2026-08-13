import axios from 'axios'

import { recoverUnauthorized } from '../auth/refreshCoordinator'
import { resolveApiBaseUrl } from '../config/endpointResolution'
import { useAuthStore } from '../stores/auth'
import { authenticatedRequestConfig, authenticatedRetryConfig } from './authenticatedHttp'

/** @typedef {import('axios').AxiosInstance} AxiosInstance */
/**
 * @typedef {Object} UploadRequest
 * @property {unknown} [url]
 * @property {unknown} [method]
 * @property {any} [data]
 * @property {Record<string, any>} [headers]
 * @property {AbortSignal} [signal]
 * @property {(progress: { loaded: number, total: number, percent: number | null }) => void} [onProgress]
 */

const trustedUploadClient = axios.create({
  baseURL: resolveApiBaseUrl(),
  withCredentials: true,
  timeout: 0
})

const externalUploadClient = axios.create({
  withCredentials: false,
  timeout: 0
})

export function isAbsoluteUploadUrl(url) {
  return /^(?:[a-z][a-z\d+.-]*:)?\/\//i.test(String(url || '').trim())
}

function browserLocationUrl() {
  return typeof window !== 'undefined' && window.location?.href
    ? window.location.href
    : ''
}

function originOf(value, baseUrl) {
  if (!value) return ''
  try {
    return new URL(value, baseUrl || undefined).origin
  } catch {
    return ''
  }
}

/**
 * @param {unknown} url
 * @param {{ apiBaseUrl?: string, browserUrl?: string }} [options]
 */
export function isExternalUploadUrl(url, {
  apiBaseUrl = resolveApiBaseUrl(),
  browserUrl = browserLocationUrl()
} = {}) {
  const requestUrl = String(url || '').trim()
  if (!isAbsoluteUploadUrl(requestUrl)) return false

  const absoluteApiBase = isAbsoluteUploadUrl(apiBaseUrl) ? apiBaseUrl : ''
  const resolutionBase = browserUrl || absoluteApiBase
  const requestOrigin = originOf(requestUrl, resolutionBase)
  if (!requestOrigin) return true

  const trustedOrigins = new Set([
    originOf(browserUrl),
    originOf(apiBaseUrl, browserUrl || absoluteApiBase)
  ].filter(Boolean))
  return !trustedOrigins.has(requestOrigin)
}

function progressAdapter(onProgress) {
  if (typeof onProgress !== 'function') return undefined
  return (event = {}) => {
    const loaded = Math.max(0, Number(event.loaded || 0))
    const total = Math.max(0, Number(event.total || 0))
    const percent = total > 0 ? Math.min(100, Math.round((loaded / total) * 100)) : null
    onProgress({ loaded, total, percent })
  }
}

/**
 * @param {{
 *   trustedClient?: AxiosInstance | { request: (config: any) => Promise<any> },
 *   externalClient?: AxiosInstance | { request: (config: any) => Promise<any> },
 *   authProvider?: () => any,
 *   unauthorizedRecovery?: (options: { auth: any, requestGeneration: number }) => Promise<string>,
 *   trustedBaseUrl?: string,
 *   browserUrl?: string
 * }} [options]
 */
export function createUploadTransport({
  trustedClient = trustedUploadClient,
  externalClient = externalUploadClient,
  authProvider = useAuthStore,
  unauthorizedRecovery = recoverUnauthorized,
  trustedBaseUrl = resolveApiBaseUrl(),
  browserUrl
} = {}) {
  return {
    /** @param {UploadRequest} [request] */
    async upload({ url, method = 'POST', data, headers = {}, signal, onProgress } = {}) {
      const requestUrl = String(url || '').trim()
      const requestMethod = String(method || 'POST').toUpperCase()
      const onUploadProgress = progressAdapter(onProgress)

      if (isExternalUploadUrl(requestUrl, { apiBaseUrl: trustedBaseUrl, browserUrl })) {
        return externalClient.request({
          url: requestUrl,
          method: requestMethod,
          data,
          headers: { ...(headers || {}) },
          withCredentials: false,
          timeout: 0,
          signal,
          onUploadProgress
        })
      }

      const auth = authProvider()
      const generation = auth.tokenGeneration
      const config = /** @type {import('axios').AxiosRequestConfig & { _authTokenGeneration: number, _retry?: boolean }} */ (authenticatedRequestConfig({
        url: requestUrl,
        method: requestMethod,
        data,
        headers,
        withCredentials: true,
        timeout: 0,
        signal,
        onUploadProgress
      }, auth))

      try {
        return await trustedClient.request(config)
      } catch (cause) {
        const error = /** @type {any} */ (cause)
        if (Number(error?.response?.status || 0) !== 401 || config._retry) throw error
        const accessToken = await unauthorizedRecovery({ auth, requestGeneration: generation })
        return trustedClient.request(authenticatedRetryConfig(config, accessToken))
      }
    }
  }
}

export const uploadTransport = createUploadTransport()
