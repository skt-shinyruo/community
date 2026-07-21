import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const transport = vi.hoisted(() => ({
  requestRefreshToken: vi.fn(),
  requestCurrentUser: vi.fn()
}))

vi.mock('./refreshTransport', () => transport)

import { useAuthStore } from '../stores/auth'
import { recoverUnauthorized, refreshSession } from './refreshCoordinator'

describe('refreshCoordinator', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    window.localStorage.clear()
    transport.requestRefreshToken.mockReset()
    transport.requestCurrentUser.mockReset()
  })

  it('shares one refresh and profile load across HTTP, IM, and bootstrap callers', async () => {
    const auth = useAuthStore()
    auth.installSession({
      accessToken: 'old-token',
      me: { userId: 7, username: 'alice' }
    })
    const generation = auth.tokenGeneration
    const refresh = deferred()
    const profile = deferred()
    transport.requestRefreshToken.mockReturnValueOnce(refresh.promise)
    transport.requestCurrentUser.mockReturnValueOnce(profile.promise)

    const httpRecovery = recoverUnauthorized({ auth, requestGeneration: generation })
    const imRecovery = recoverUnauthorized({ auth, requestGeneration: generation })
    const bootstrap = refreshSession({ auth, expectedGeneration: generation })

    expect(transport.requestRefreshToken).toHaveBeenCalledTimes(1)
    refresh.resolve({ data: { accessToken: 'new-token' }, traceId: 'trace-refresh' })
    await vi.waitFor(() => expect(transport.requestCurrentUser).toHaveBeenCalledTimes(1))
    expect(transport.requestCurrentUser).toHaveBeenCalledWith('new-token')
    expect(auth.accessToken).toBe('old-token')
    expect(auth.me).toEqual({ userId: 7, username: 'alice' })

    profile.resolve({
      data: { userId: 8, username: 'bob' },
      traceId: 'trace-profile'
    })

    const [httpToken, imToken, bootstrapResult] = await Promise.all([
      httpRecovery,
      imRecovery,
      bootstrap
    ])
    expect(httpToken).toBe('new-token')
    expect(imToken).toBe('new-token')
    expect(bootstrapResult).toEqual({
      accessToken: 'new-token',
      profileLoaded: true,
      traceId: 'trace-profile'
    })
    expect(auth.accessToken).toBe('new-token')
    expect(auth.me).toEqual({ userId: 8, username: 'bob' })
  })

  it('reuses a newer token for a stale 401 without starting refresh', async () => {
    const auth = useAuthStore()
    auth.setAccessToken('old-token')
    const requestGeneration = auth.tokenGeneration
    auth.installSession({
      accessToken: 'new-token',
      me: { userId: 8, username: 'bob' }
    })

    await expect(recoverUnauthorized({ auth, requestGeneration })).resolves.toBe('new-token')
    expect(transport.requestRefreshToken).not.toHaveBeenCalled()
    expect(transport.requestCurrentUser).not.toHaveBeenCalled()
  })

  it('rejects stale work after logout without starting refresh', async () => {
    const auth = useAuthStore()
    auth.setAccessToken('old-token')
    const requestGeneration = auth.tokenGeneration
    auth.clear()

    await expect(recoverUnauthorized({ auth, requestGeneration })).rejects.toMatchObject({
      sessionRefreshState: 'session-changed'
    })
    expect(transport.requestRefreshToken).not.toHaveBeenCalled()
  })

  it('ignores an old refresh failure after a newer login succeeds', async () => {
    const auth = useAuthStore()
    auth.setAccessToken('old-token')
    const generation = auth.tokenGeneration
    const refresh = deferred()
    transport.requestRefreshToken.mockReturnValueOnce(refresh.promise)
    const clear = vi.spyOn(auth, 'clear')

    const recovery = recoverUnauthorized({ auth, requestGeneration: generation })
    auth.installSession({
      accessToken: 'login-token',
      me: { userId: 9, username: 'carol' }
    })
    refresh.reject(new Error('old refresh failed'))

    await expect(recovery).resolves.toBe('login-token')
    expect(clear).not.toHaveBeenCalled()
    expect(auth.me).toEqual({ userId: 9, username: 'carol' })
  })

  it('discards an old refresh success after a newer login succeeds', async () => {
    const auth = useAuthStore()
    auth.setAccessToken('old-token')
    const generation = auth.tokenGeneration
    const refresh = deferred()
    transport.requestRefreshToken.mockReturnValueOnce(refresh.promise)
    const recovery = recoverUnauthorized({ auth, requestGeneration: generation })

    auth.installSession({
      accessToken: 'login-token',
      me: { userId: 9, username: 'carol' }
    })
    const installSession = vi.spyOn(auth, 'installSession')
    refresh.resolve({ data: { accessToken: 'stale-token' }, traceId: 'trace-stale' })

    await expect(recovery).resolves.toBe('login-token')
    expect(transport.requestCurrentUser).not.toHaveBeenCalled()
    expect(installSession).not.toHaveBeenCalled()
    expect(auth.me).toEqual({ userId: 9, username: 'carol' })
  })

  it('does not restore a session when an old refresh succeeds after logout', async () => {
    const auth = useAuthStore()
    auth.setAccessToken('old-token')
    const generation = auth.tokenGeneration
    const refresh = deferred()
    transport.requestRefreshToken.mockReturnValueOnce(refresh.promise)
    const recovery = recoverUnauthorized({ auth, requestGeneration: generation })

    auth.clear()
    refresh.resolve({ data: { accessToken: 'stale-token' }, traceId: 'trace-stale' })

    await expect(recovery).rejects.toMatchObject({ sessionRefreshState: 'session-changed' })
    expect(auth.accessToken).toBe('')
    expect(transport.requestCurrentUser).not.toHaveBeenCalled()
  })

  it('clears once and shares one terminal failure across all joiners', async () => {
    const auth = useAuthStore()
    auth.setAccessToken('old-token')
    const generation = auth.tokenGeneration
    const failure = new Error('refresh rejected')
    transport.requestRefreshToken.mockRejectedValueOnce(failure)
    const clear = vi.spyOn(auth, 'clear')

    const settled = await Promise.allSettled([
      recoverUnauthorized({ auth, requestGeneration: generation }),
      recoverUnauthorized({ auth, requestGeneration: generation }),
      refreshSession({ auth, expectedGeneration: generation })
    ])

    expect(settled.every((result) => result.status === 'rejected')).toBe(true)
    expect(settled[0].reason).toBe(settled[1].reason)
    expect(settled[1].reason).toBe(settled[2].reason)
    expect(settled[0].reason).toMatchObject({ sessionRefreshState: 'terminal' })
    expect(clear).toHaveBeenCalledTimes(1)
    expect(auth.accessToken).toBe('')
  })

  it('installs a valid token and preserves the old profile after a temporary profile failure', async () => {
    const auth = useAuthStore()
    const oldProfile = { userId: 7, username: 'alice' }
    auth.installSession({ accessToken: 'old-token', me: oldProfile })
    const generation = auth.tokenGeneration
    transport.requestRefreshToken.mockResolvedValueOnce({
      data: { accessToken: 'new-token' },
      traceId: 'trace-refresh'
    })
    transport.requestCurrentUser.mockRejectedValueOnce(new Error('profile unavailable'))

    await expect(refreshSession({ auth, expectedGeneration: generation })).resolves.toEqual({
      accessToken: 'new-token',
      profileLoaded: false,
      traceId: 'trace-refresh'
    })
    expect(auth.accessToken).toBe('new-token')
    expect(auth.me).toEqual(oldProfile)
  })

  it('treats a profile 401 as a terminal authentication failure', async () => {
    const auth = useAuthStore()
    auth.installSession({
      accessToken: 'old-token',
      me: { userId: 7, username: 'alice' }
    })
    const generation = auth.tokenGeneration
    transport.requestRefreshToken.mockResolvedValueOnce({
      data: { accessToken: 'new-token' },
      traceId: 'trace-refresh'
    })
    transport.requestCurrentUser.mockRejectedValueOnce({
      response: { status: 401 }
    })
    const clear = vi.spyOn(auth, 'clear')

    await expect(refreshSession({ auth, expectedGeneration: generation })).rejects.toMatchObject({
      sessionRefreshState: 'terminal'
    })
    expect(clear).toHaveBeenCalledTimes(1)
    expect(auth.accessToken).toBe('')
    expect(auth.me).toBeNull()
  })

  it('discards an old profile response when the session changes while it is loading', async () => {
    const auth = useAuthStore()
    auth.installSession({
      accessToken: 'old-token',
      me: { userId: 7, username: 'alice' }
    })
    const generation = auth.tokenGeneration
    const profile = deferred()
    transport.requestRefreshToken.mockResolvedValueOnce({
      data: { accessToken: 'refresh-token' },
      traceId: 'trace-refresh'
    })
    transport.requestCurrentUser.mockReturnValueOnce(profile.promise)

    const refreshing = refreshSession({ auth, expectedGeneration: generation })
    await vi.waitFor(() => expect(transport.requestCurrentUser).toHaveBeenCalledTimes(1))
    auth.installSession({
      accessToken: 'login-token',
      me: { userId: 9, username: 'carol' }
    })
    const installSession = vi.spyOn(auth, 'installSession')
    profile.resolve({
      data: { userId: 8, username: 'stale-profile' },
      traceId: 'trace-profile'
    })

    await expect(refreshing).resolves.toMatchObject({ accessToken: 'login-token' })
    expect(installSession).not.toHaveBeenCalled()
    expect(auth.me).toEqual({ userId: 9, username: 'carol' })
  })
})

function deferred() {
  let resolve
  let reject
  const promise = new Promise((resolvePromise, rejectPromise) => {
    resolve = resolvePromise
    reject = rejectPromise
  })
  return { promise, resolve, reject }
}
