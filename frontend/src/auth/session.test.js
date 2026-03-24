import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

import { useAuthStore } from '../stores/auth'
import { useAppStore } from '../stores/app'
import { ensureSessionReady, shouldBootstrapSession } from './session'
import { me, refresh } from '../api/services/authService'

vi.mock('../api/services/authService', () => ({
  me: vi.fn(),
  refresh: vi.fn()
}))

describe('session bootstrap', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.stubGlobal('localStorage', createStorage())
    useAuthStore().clear()
    useAppStore().setTraceId('')
    vi.clearAllMocks()
  })

  it('does not bootstrap anonymously when there is no token and no session hint', () => {
    expect(shouldBootstrapSession()).toBe(false)
    expect(refresh).not.toHaveBeenCalled()
  })

  it('bootstraps when a previous session hint exists', () => {
    globalThis.localStorage.setItem('community.session.hint', '1')

    expect(shouldBootstrapSession()).toBe(true)
  })

  it('restores session from refresh cookie and loads profile when access token is missing', async () => {
    refresh.mockResolvedValue({ data: { accessToken: 'new-token' }, traceId: 'trace-refresh' })
    me.mockResolvedValue({
      data: { userId: 7, username: 'alice', authorities: ['ROLE_USER'] },
      traceId: 'trace-me'
    })

    const result = await ensureSessionReady()

    const auth = useAuthStore()
    const app = useAppStore()
    expect(result).toEqual({ state: 'ready' })
    expect(auth.accessToken).toBe('new-token')
    expect(auth.me).toEqual({ userId: 7, username: 'alice', authorities: ['ROLE_USER'] })
    expect(app.traceId).toBe('trace-me')
    expect(refresh).toHaveBeenCalledTimes(1)
    expect(me).toHaveBeenCalledTimes(1)
  })

  it('clears auth state and returns anonymous when refresh fails', async () => {
    refresh.mockRejectedValue(new Error('refresh failed'))
    globalThis.localStorage.setItem('community.session.hint', '1')

    const result = await ensureSessionReady()

    expect(result).toEqual({ state: 'anonymous' })
    expect(useAuthStore().accessToken).toBe('')
    expect(useAuthStore().me).toBeNull()
    expect(me).not.toHaveBeenCalled()
  })

  it('returns error instead of anonymous when profile loading fails but token still exists', async () => {
    const auth = useAuthStore()
    auth.setAccessToken('token-1')
    me.mockRejectedValue(new Error('profile temporarily unavailable'))

    const result = await ensureSessionReady()

    expect(result.state).toBe('error')
    expect(useAuthStore().accessToken).toBe('token-1')
    expect(useAuthStore().me).toBeNull()
  })
})

function createStorage() {
  const values = new Map()
  return {
    getItem(key) {
      return values.has(key) ? values.get(key) : null
    },
    setItem(key, value) {
      values.set(key, String(value))
    },
    removeItem(key) {
      values.delete(key)
    },
    clear() {
      values.clear()
    }
  }
}
