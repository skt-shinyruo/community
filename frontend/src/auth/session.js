import { me, refresh } from '../api/services/authService'
import { useAppStore } from '../stores/app'
import { useAuthStore } from '../stores/auth'
import { hasSessionHint } from './sessionHint'

let pendingSessionPromise = null

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
    try {
      const { data, traceId } = await refresh({ silent: true })
      setTraceId(traceId)
      const token = data?.accessToken || ''
      if (!token) {
        auth.clear()
        return { state: 'anonymous' }
      }
      auth.setAccessToken(token)
    } catch {
      auth.clear()
      return { state: 'anonymous' }
    }
  }

  if (auth.me) {
    return { state: 'ready' }
  }

  try {
    const { data, traceId } = await me()
    setTraceId(traceId)
    if (!data) {
      return auth.accessToken ? { state: 'error' } : { state: 'anonymous' }
    }
    auth.setMe(data)
    return { state: 'ready' }
  } catch (error) {
    if (!auth.accessToken) {
      auth.clear()
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

  if (!pendingSessionPromise) {
    pendingSessionPromise = doEnsureSessionReady(authStore).finally(() => {
      pendingSessionPromise = null
    })
  }
  return pendingSessionPromise
}

export function shouldBootstrapSession({ auth } = {}) {
  const authStore = auth || useAuthStore()
  return !!authStore.accessToken || hasSessionHint()
}
