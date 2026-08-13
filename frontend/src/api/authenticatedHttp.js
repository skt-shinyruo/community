function cloneHeaders(headers) {
  if (typeof headers?.toJSON === 'function') return { ...headers.toJSON() }
  return { ...(headers || {}) }
}

function authorizationHeaders(headers, accessToken) {
  const next = cloneHeaders(headers)
  if (accessToken) next.Authorization = `Bearer ${accessToken}`
  return next
}

/** @param {Record<string, any>} config @param {AuthSnapshot} auth */
export function authenticatedRequestConfig(config = {}, auth = {}) {
  return {
    ...config,
    headers: authorizationHeaders(config.headers, auth.accessToken),
    _authTokenGeneration: auth.tokenGeneration
  }
}

/** @param {Record<string, any>} config @param {string} accessToken */
export function authenticatedRetryConfig(config = {}, accessToken) {
  return {
    ...config,
    headers: authorizationHeaders(config.headers, accessToken),
    _retry: true,
    _authRecoveryRetry: true
  }
}

/**
 * @param {import('axios').AxiosInstance} client
 * @param {AuthenticatedHttpOptions} options
 */
export function installAuthenticatedHttpInterceptors(client, {
  authProvider,
  unauthorizedRecovery,
  shouldRecoverUnauthorized = () => true,
  onRecoveryFailure = () => {}
}) {
  client.interceptors.request.use((config) => {
    const requestConfig = /** @type {typeof config & { _authRecoveryRetry?: boolean }} */ (config)
    if (requestConfig._authRecoveryRetry === true) return requestConfig
    return /** @type {any} */ (authenticatedRequestConfig(requestConfig, authProvider()))
  })

  client.interceptors.response.use(
    (response) => response,
    async (error) => {
      const status = Number(error?.response?.status || 0)
      const original = error?.config || {}
      if (
        status !== 401 ||
        original._retry === true ||
        !shouldRecoverUnauthorized({ error, status, config: original })
      ) {
        return Promise.reject(error)
      }

      const auth = authProvider()
      try {
        const accessToken = await unauthorizedRecovery({
          auth,
          requestGeneration: original._authTokenGeneration
        })
        return client(authenticatedRetryConfig(original, accessToken))
      } catch (recoveryError) {
        onRecoveryFailure({
          error: recoveryError,
          auth,
          originalError: error,
          config: original
        })
        return Promise.reject(recoveryError)
      }
    }
  )

  return client
}
/** @typedef {{ accessToken?: string, tokenGeneration?: number }} AuthSnapshot */
/**
 * @typedef {Object} AuthenticatedHttpOptions
 * @property {() => any} authProvider
 * @property {(options: { auth: any, requestGeneration: number }) => Promise<string>} unauthorizedRecovery
 * @property {(context: { error: any, status: number, config: any }) => boolean} [shouldRecoverUnauthorized]
 * @property {(context: { error: any, auth: any, originalError: any, config: any }) => void} [onRecoveryFailure]
 */
