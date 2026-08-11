import { useAppStore } from '../stores/app'
import { useAuthStore } from '../stores/auth'
import { recoverUnauthorized, refreshSession } from './refreshCoordinator'
import { requestCurrentUser } from './refreshTransport'
import { hasSessionHint } from './sessionHint'

let pendingSessionPromise = null
let pendingSessionAuth = null
let pendingSessionGeneration = null

function setTraceId(traceId) {
  if (!traceId) return
  try {
    useAppStore().setTraceId(traceId)
  } catch {
    // App store may be unavailable in narrow bootstrap windows.
  }
}

async function doEnsureSessionReady(auth) {
  if (!auth.accessToken) {
    if (!hasSessionHint()) {
      return { state: 'anonymous' }
    }
    const expectedGeneration = auth.tokenGeneration
    try {
      const refreshed = await refreshSession({ auth, expectedGeneration })
      setTraceId(refreshed.traceId)
      if (!auth.accessToken) return { state: 'anonymous' }
      return refreshed.profileLoaded || auth.me ? { state: 'ready' } : { state: 'error' }
    } catch (error) {
      if (error?.sessionRefreshState === 'terminal' && !auth.accessToken) return { state: 'anonymous' }
      if (!auth.accessToken) return { state: 'error', error }
      return auth.me ? { state: 'ready' } : { state: 'error', error }
    }
  }

  if (auth.me) {
    return { state: 'ready' }
  }

  const accessToken = auth.accessToken
  const requestGeneration = auth.tokenGeneration
  try {
    const { data, traceId } = await requestCurrentUser(accessToken)
    if (auth.tokenGeneration !== requestGeneration || auth.accessToken !== accessToken) {
      if (!auth.accessToken) return { state: 'anonymous' }
      return auth.me ? { state: 'ready' } : { state: 'error' }
    }
    setTraceId(traceId)
    if (!data) {
      return auth.accessToken ? { state: 'error' } : { state: 'anonymous' }
    }
    auth.setMe(data)
    return { state: 'ready' }
  } catch (error) {
    if (auth.tokenGeneration !== requestGeneration || auth.accessToken !== accessToken) {
      if (!auth.accessToken) return { state: 'anonymous' }
      return auth.me ? { state: 'ready' } : { state: 'error', error }
    }
    if ([401, 403].includes(Number(error?.response?.status || 0))) {
      try {
        await recoverUnauthorized({ auth, requestGeneration })
        if (!auth.accessToken) return { state: 'anonymous' }
        return auth.me ? { state: 'ready' } : { state: 'error' }
      } catch (refreshError) {
        if (!auth.accessToken) return { state: 'anonymous' }
        return auth.me ? { state: 'ready' } : { state: 'error', error: refreshError }
      }
    }
    if (!auth.accessToken) {
      return { state: 'anonymous' }
    }
    return { state: 'error', error }
  }
}

export async function ensureSessionReady({ auth } = {}) {
  const authStore = auth || useAuthStore()
  if (authStore.accessToken && authStore.me) {
    return { state: 'ready' }
  }

  const generation = authStore.tokenGeneration
  const canShare = pendingSessionPromise
    && pendingSessionAuth === authStore
    && pendingSessionGeneration === generation
  if (!canShare) {
    const sessionPromise = doEnsureSessionReady(authStore).finally(() => {
      if (pendingSessionPromise === sessionPromise) {
        pendingSessionPromise = null
        pendingSessionAuth = null
        pendingSessionGeneration = null
      }
    })
    pendingSessionPromise = sessionPromise
    pendingSessionAuth = authStore
    pendingSessionGeneration = generation
  }
  return pendingSessionPromise
}

export function shouldBootstrapSession({ auth } = {}) {
  const authStore = auth || useAuthStore()
  return !!authStore.accessToken || hasSessionHint()
}
