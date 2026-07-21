import { requestCurrentUser, requestRefreshToken } from './refreshTransport'

let inFlightRefresh = null

function refreshError(cause, state, fallbackMessage) {
  const error = cause instanceof Error ? cause : new Error(fallbackMessage, { cause })
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

function terminalFailure(auth, startGeneration, cause) {
  const currentSession = currentSessionIfChanged(auth, startGeneration, cause)
  if (currentSession) {
    return currentSession
  }
  auth.clear()
  throw refreshError(cause, 'terminal', 'Session refresh failed')
}

function isUnauthorized(error) {
  return Number(error?.response?.status || 0) === 401
}

async function performRefresh(auth, startGeneration, requireProfile) {
  let refreshResponse
  try {
    refreshResponse = await requestRefreshToken()
  } catch (error) {
    return terminalFailure(auth, startGeneration, error)
  }

  const afterRefresh = currentSessionIfChanged(auth, startGeneration)
  if (afterRefresh) {
    return afterRefresh
  }

  const accessToken = refreshResponse?.data?.accessToken || ''
  if (!accessToken) {
    return terminalFailure(auth, startGeneration, new Error('Refresh response did not include an access token'))
  }

  let profile
  let profileLoaded = false
  let traceId = refreshResponse?.traceId || ''
  if (requireProfile) {
    try {
      const profileResponse = await requestCurrentUser(accessToken)
      const afterProfile = currentSessionIfChanged(auth, startGeneration)
      if (afterProfile) {
        return afterProfile
      }
      traceId = profileResponse?.traceId || traceId
      if (profileResponse?.data != null) {
        profile = profileResponse.data
        profileLoaded = true
      }
    } catch (error) {
      const afterProfileFailure = currentSessionIfChanged(auth, startGeneration, error)
      if (afterProfileFailure) {
        return afterProfileFailure
      }
      if (isUnauthorized(error)) {
        return terminalFailure(auth, startGeneration, error)
      }
    }
  }

  const beforeInstall = currentSessionIfChanged(auth, startGeneration)
  if (beforeInstall) {
    return beforeInstall
  }
  auth.installSession({
    accessToken,
    me: profileLoaded ? profile : undefined
  })
  return { accessToken, profileLoaded, traceId }
}

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

  if (!inFlightRefresh) {
    const sharedRefresh = performRefresh(auth, startGeneration, requireProfile).finally(() => {
      if (inFlightRefresh === sharedRefresh) {
        inFlightRefresh = null
      }
    })
    inFlightRefresh = sharedRefresh
  }
  return inFlightRefresh
}

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
