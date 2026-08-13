import { requestCurrentUser, requestRefreshToken } from './refreshTransport'

let inFlightRefresh = null
let inFlightAuth = null
let inFlightGeneration = null

/** @typedef {{ tokenGeneration: number, accessToken: string, me: any, clear: () => void, installSession: (session: any) => void, setMe: (me: any) => void }} AuthSessionStore */

function refreshError(cause, state, fallbackMessage) {
  const error = /** @type {Error & { sessionRefreshState?: string }} */ (
    cause instanceof Error ? cause : new Error(fallbackMessage, { cause })
  )
  error.sessionRefreshState = state
  return error
}

function currentSessionIfChanged(auth, startGeneration, cause) {
  if (auth.tokenGeneration === startGeneration) {
    return null
  }
  if (auth.accessToken) {
    return {
      accessToken: auth.accessToken,
      profileLoaded: !!auth.me,
      traceId: ''
    }
  }
  throw refreshError(cause, 'session-changed', 'Session changed while refreshing')
}

function terminalFailure(auth, expectedGeneration, cause) {
  const currentSession = currentSessionIfChanged(auth, expectedGeneration, cause)
  if (currentSession) {
    return currentSession
  }
  auth.clear()
  throw refreshError(cause, 'terminal', 'Session refresh failed')
}

function retryableFailure(auth, expectedGeneration, cause) {
  const currentSession = currentSessionIfChanged(auth, expectedGeneration, cause)
  if (currentSession) {
    return currentSession
  }
  throw refreshError(cause, 'retryable', 'Session refresh is temporarily unavailable')
}

function isAuthoritativeAuthenticationFailure(error) {
  const status = Number(error?.response?.status || 0)
  return status === 401 || status === 403
}

async function performRefresh(auth, startGeneration, requireProfile) {
  let refreshResponse
  try {
    refreshResponse = await requestRefreshToken()
  } catch (error) {
    return isAuthoritativeAuthenticationFailure(error)
      ? terminalFailure(auth, startGeneration, error)
      : retryableFailure(auth, startGeneration, error)
  }

  const afterRefresh = currentSessionIfChanged(auth, startGeneration)
  if (afterRefresh) {
    return afterRefresh
  }

  const refreshData = refreshResponse?.data && typeof refreshResponse.data === 'object'
    ? /** @type {Record<string, any>} */ (refreshResponse.data)
    : {}
  const accessToken = String(refreshData.accessToken || '')
  if (!accessToken) {
    return retryableFailure(auth, startGeneration, new Error('Refresh response did not include an access token'))
  }

  auth.installSession({ accessToken, me: null })
  const installedGeneration = auth.tokenGeneration
  let traceId = refreshResponse?.traceId || ''
  if (!requireProfile) {
    return { accessToken, profileLoaded: false, traceId }
  }

  try {
    const profileResponse = await requestCurrentUser(accessToken)
    const afterProfile = currentSessionIfChanged(auth, installedGeneration)
    if (afterProfile) return afterProfile
    if (profileResponse?.data == null) {
      return retryableFailure(auth, installedGeneration, new Error('Profile response did not include an identity'))
    }
    traceId = profileResponse?.traceId || traceId
    auth.setMe(profileResponse.data)
    return { accessToken, profileLoaded: true, traceId }
  } catch (error) {
    const afterProfileFailure = currentSessionIfChanged(auth, installedGeneration, error)
    if (afterProfileFailure) return afterProfileFailure
    return isAuthoritativeAuthenticationFailure(error)
      ? terminalFailure(auth, installedGeneration, error)
      : retryableFailure(auth, installedGeneration, error)
  }
}

/**
 * @param {{ auth?: AuthSessionStore, expectedGeneration?: number, requireProfile?: boolean }} [options]
 */
export function refreshSession({ auth, expectedGeneration, requireProfile = true } = {}) {
  if (!auth) {
    return Promise.reject(new TypeError('auth store is required'))
  }
  const startGeneration = expectedGeneration ?? auth.tokenGeneration
  try {
    const currentSession = currentSessionIfChanged(auth, startGeneration)
    if (currentSession) {
      return Promise.resolve(currentSession)
    }
  } catch (error) {
    return Promise.reject(error)
  }

  const canShare = inFlightRefresh
    && inFlightAuth === auth
    && inFlightGeneration === startGeneration
  if (!canShare) {
    const sharedRefresh = performRefresh(auth, startGeneration, requireProfile).finally(() => {
      if (inFlightRefresh === sharedRefresh) {
        inFlightRefresh = null
        inFlightAuth = null
        inFlightGeneration = null
      }
    })
    inFlightRefresh = sharedRefresh
    inFlightAuth = auth
    inFlightGeneration = startGeneration
  }
  return inFlightRefresh
}

/** @param {{ auth?: AuthSessionStore, requestGeneration?: number }} [options] */
export function recoverUnauthorized({ auth, requestGeneration } = {}) {
  if (!auth) {
    return Promise.reject(new TypeError('auth store is required'))
  }
  const expectedGeneration = requestGeneration ?? auth.tokenGeneration
  try {
    const currentSession = currentSessionIfChanged(auth, expectedGeneration)
    if (currentSession) {
      return Promise.resolve(currentSession.accessToken)
    }
  } catch (error) {
    return Promise.reject(error)
  }

  return refreshSession({ auth, expectedGeneration }).then((result) => result.accessToken)
}
