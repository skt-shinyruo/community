import { useAuthStore } from '../stores/auth'
import { recoverUnauthorized, refreshSession } from './refreshCoordinator'
import { requestCurrentUser } from './refreshTransport'
import { hasSessionHint } from './sessionHint'

let pendingSessionPromise = null
let pendingSessionAuth = null
let pendingSessionGeneration = null

/** @typedef {ReturnType<typeof useAuthStore>} AuthStore */

async function doEnsureSessionReady(auth) {
  if (!auth.accessToken) {
    if (!hasSessionHint()) {
      return { state: 'anonymous' }
    }
    const expectedGeneration = auth.tokenGeneration
    try {
      const refreshed = await refreshSession({ auth, expectedGeneration })
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
    const { data } = await requestCurrentUser(accessToken)
    if (auth.tokenGeneration !== requestGeneration || auth.accessToken !== accessToken) {
      if (!auth.accessToken) return { state: 'anonymous' }
      return auth.me ? { state: 'ready' } : { state: 'error' }
    }
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

/** @param {{ auth?: AuthStore }} [options] */
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

/** @param {{ auth?: AuthStore }} [options] */
export function shouldBootstrapSession({ auth } = {}) {
  const authStore = auth || useAuthStore()
  return !!authStore.accessToken || hasSessionHint()
}
