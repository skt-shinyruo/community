import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

import { useAuthStore } from '../stores/auth'
import { useAppStore } from '../stores/app'
import { ensureSessionReady, shouldBootstrapSession } from './session'

const refreshTransport = vi.hoisted(() => ({
  requestRefreshToken: vi.fn(),
  requestCurrentUser: vi.fn()
}))

vi.mock('./refreshTransport', () => refreshTransport)
vi.mock('../api/services/authService', () => ({ me: vi.fn(), refresh: vi.fn() }))

describe('session bootstrap', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.stubGlobal('localStorage', createStorage())
    useAuthStore().clear()
    useAppStore().setTraceId('')
    refreshTransport.requestRefreshToken.mockReset()
    refreshTransport.requestCurrentUser.mockReset()
  })

  it('does not bootstrap anonymously when there is no token and no session hint', () => {
    expect(shouldBootstrapSession()).toBe(false)
    expect(refreshTransport.requestRefreshToken).not.toHaveBeenCalled()
  })

  it('returns anonymous without a refresh request when there is no session hint', async () => {
    await expect(ensureSessionReady()).resolves.toEqual({ state: 'anonymous' })
    expect(refreshTransport.requestRefreshToken).not.toHaveBeenCalled()
    expect(refreshTransport.requestCurrentUser).not.toHaveBeenCalled()
  })

  it('bootstraps when a previous session hint exists', () => {
    globalThis.localStorage.setItem('community.session.hint', '1')

    expect(shouldBootstrapSession()).toBe(true)
  })

  it('restores session from refresh cookie and loads profile when access token is missing', async () => {
    globalThis.localStorage.setItem('community.session.hint', '1')
    refreshTransport.requestRefreshToken.mockResolvedValue({
      data: { accessToken: 'new-token' },
      traceId: 'trace-refresh'
    })
    refreshTransport.requestCurrentUser.mockResolvedValue({
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
    expect(refreshTransport.requestRefreshToken).toHaveBeenCalledTimes(1)
    expect(refreshTransport.requestCurrentUser).toHaveBeenCalledTimes(1)
  })

  it('clears auth state and returns anonymous when refresh fails', async () => {
    refreshTransport.requestRefreshToken.mockRejectedValue(new Error('refresh failed'))
    globalThis.localStorage.setItem('community.session.hint', '1')

    const result = await ensureSessionReady()

    expect(result).toEqual({ state: 'anonymous' })
    expect(useAuthStore().accessToken).toBe('')
    expect(useAuthStore().me).toBeNull()
    expect(refreshTransport.requestCurrentUser).not.toHaveBeenCalled()
  })

  it('returns error instead of anonymous when profile loading fails but token still exists', async () => {
    const auth = useAuthStore()
    auth.setAccessToken('token-1')
    refreshTransport.requestCurrentUser.mockRejectedValue(new Error('profile temporarily unavailable'))

    const result = await ensureSessionReady()

    expect(result.state).toBe('error')
    expect(useAuthStore().accessToken).toBe('token-1')
    expect(useAuthStore().me).toBeNull()
  })

  it('loads a missing profile directly without refreshing a valid token', async () => {
    const auth = useAuthStore()
    auth.setAccessToken('token-1')
    refreshTransport.requestCurrentUser.mockResolvedValue({
      data: { userId: 7, username: 'alice', authorities: ['ROLE_USER'] },
      traceId: 'trace-me'
    })

    await expect(ensureSessionReady()).resolves.toEqual({ state: 'ready' })

    expect(refreshTransport.requestRefreshToken).not.toHaveBeenCalled()
    expect(refreshTransport.requestCurrentUser).toHaveBeenCalledWith('token-1')
    expect(auth.me).toEqual({ userId: 7, username: 'alice', authorities: ['ROLE_USER'] })
  })

  it('refreshes once after the existing token profile request returns 401', async () => {
    const auth = useAuthStore()
    auth.setAccessToken('old-token')
    refreshTransport.requestCurrentUser
      .mockRejectedValueOnce({ response: { status: 401 } })
      .mockResolvedValueOnce({
        data: { userId: 8, username: 'bob', authorities: ['ROLE_USER'] },
        traceId: 'trace-profile'
      })
    refreshTransport.requestRefreshToken.mockResolvedValueOnce({
      data: { accessToken: 'new-token' },
      traceId: 'trace-refresh'
    })

    await expect(ensureSessionReady()).resolves.toEqual({ state: 'ready' })

    expect(refreshTransport.requestRefreshToken).toHaveBeenCalledTimes(1)
    expect(refreshTransport.requestCurrentUser).toHaveBeenCalledTimes(2)
    expect(refreshTransport.requestCurrentUser).toHaveBeenNthCalledWith(1, 'old-token')
    expect(refreshTransport.requestCurrentUser).toHaveBeenNthCalledWith(2, 'new-token')
    expect(auth.accessToken).toBe('new-token')
    expect(auth.me).toEqual({ userId: 8, username: 'bob', authorities: ['ROLE_USER'] })
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
