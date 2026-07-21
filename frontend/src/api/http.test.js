import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import MockAdapter from 'axios-mock-adapter'

const refreshTransport = vi.hoisted(() => ({
  requestRefreshToken: vi.fn(),
  requestCurrentUser: vi.fn()
}))

vi.mock('../auth/refreshTransport', () => refreshTransport)

import http from './http'
import imCoreHttp from './imCoreHttp'
import { ensureSessionReady } from '../auth/session'
import { useAuthStore } from '../stores/auth'
import { setToastHandler } from '../ui/toastService'

const IDEMPOTENCY_HEADER = 'Idempotency-Key'

describe('http', () => {
  let toast
  let mock
  let imMock

  beforeEach(() => {
    setActivePinia(createPinia())
    useAuthStore().clear()
    vi.stubGlobal('location', { href: '' })
    vi.stubGlobal('window', {})
    toast = vi.fn()
    setToastHandler(toast)
    refreshTransport.requestRefreshToken.mockReset()
    refreshTransport.requestCurrentUser.mockReset()
    mock = new MockAdapter(http)
    imMock = new MockAdapter(imCoreHttp)
  })

  afterEach(() => {
    mock.restore()
    imMock.restore()
  })

  it('should attach Authorization header when accessToken exists', async () => {
    const auth = useAuthStore()
    auth.setAccessToken('token-1')

    mock.onGet('/api/ping').reply((config) => {
      return [200, {
        auth: config.headers?.Authorization || '',
        generation: config._authTokenGeneration,
        leakedGeneration: config.headers?._authTokenGeneration
      }]
    })

    const resp = await http.get('/api/ping')
    expect(resp.data.auth).toBe('Bearer token-1')
    expect(resp.data.generation).toBe(auth.tokenGeneration)
    expect(resp.data.leakedGeneration).toBeUndefined()
  })

  it('should refresh token once and retry request on 401', async () => {
    const auth = useAuthStore()
    auth.setAccessToken('old-token')

    refreshTransport.requestRefreshToken.mockResolvedValueOnce({
      data: { accessToken: 'new-token' },
      traceId: 'trace-refresh'
    })
    refreshTransport.requestCurrentUser.mockResolvedValueOnce({
      data: { userId: 8, username: 'bob' },
      traceId: 'trace-profile'
    })
    mock.onGet('/api/protected').replyOnce(401)
    mock.onGet('/api/protected').replyOnce((config) => [200, {
      ok: true,
      auth: config.headers?.Authorization || ''
    }])

    const resp = await http.get('/api/protected')
    expect(resp.data.ok).toBe(true)
    expect(resp.data.auth).toBe('Bearer new-token')
    expect(useAuthStore().accessToken).toBe('new-token')
    expect(useAuthStore().me).toEqual({ userId: 8, username: 'bob' })
    expect(refreshTransport.requestRefreshToken).toHaveBeenCalledTimes(1)
    expect(refreshTransport.requestCurrentUser).toHaveBeenCalledTimes(1)
    expect(globalThis.location.href).toBe('')
  })

  it('should redirect to login when refresh fails', async () => {
    const auth = useAuthStore()
    auth.setAccessToken('old-token')

    refreshTransport.requestRefreshToken.mockRejectedValueOnce(new Error('refresh failed'))
    mock.onGet('/api/protected').replyOnce(401)

    await expect(http.get('/api/protected')).rejects.toBeTruthy()
    expect(useAuthStore().accessToken).toBe('')
    expect(globalThis.location.href).toBe('/#/auth/login')
  })

  it('retries a stale 401 with the newer token without refreshing', async () => {
    const auth = useAuthStore()
    auth.setAccessToken('old-token')
    let attempts = 0
    mock.onGet('/api/stale').reply((config) => {
      attempts += 1
      if (attempts === 1) {
        auth.installSession({
          accessToken: 'login-token',
          me: { userId: 9, username: 'carol' }
        })
        return [401]
      }
      return [200, { auth: config.headers?.Authorization || '' }]
    })

    const response = await http.get('/api/stale')

    expect(response.data.auth).toBe('Bearer login-token')
    expect(refreshTransport.requestRefreshToken).not.toHaveBeenCalled()
    expect(auth.me).toEqual({ userId: 9, username: 'carol' })
  })

  it('does not redirect when an old refresh failure loses a race to a newer login', async () => {
    const auth = useAuthStore()
    auth.setAccessToken('old-token')
    const refresh = deferred()
    refreshTransport.requestRefreshToken.mockReturnValueOnce(refresh.promise)
    let attempts = 0
    mock.onGet('/api/race').reply((config) => {
      attempts += 1
      return attempts === 1
        ? [401]
        : [200, { auth: config.headers?.Authorization || '' }]
    })

    const request = http.get('/api/race')
    await vi.waitFor(() => expect(refreshTransport.requestRefreshToken).toHaveBeenCalledTimes(1))
    auth.installSession({
      accessToken: 'login-token',
      me: { userId: 9, username: 'carol' }
    })
    refresh.reject(new Error('old refresh failed'))

    await expect(request).resolves.toMatchObject({
      data: { auth: 'Bearer login-token' }
    })
    expect(globalThis.location.href).toBe('')
    expect(auth.me).toEqual({ userId: 9, username: 'carol' })
  })

  it('shares one refresh between concurrent community and IM 401 responses', async () => {
    const auth = useAuthStore()
    auth.setAccessToken('old-token')
    refreshTransport.requestRefreshToken.mockResolvedValueOnce({
      data: { accessToken: 'new-token' },
      traceId: 'trace-refresh'
    })
    refreshTransport.requestCurrentUser.mockResolvedValueOnce({
      data: { userId: 8, username: 'bob' },
      traceId: 'trace-profile'
    })
    mock.onGet('/api/community-protected')
      .replyOnce(401)
      .onGet('/api/community-protected')
      .replyOnce((config) => [200, { auth: config.headers?.Authorization || '' }])
    imMock.onGet('/api/im/protected')
      .replyOnce(401)
      .onGet('/api/im/protected')
      .replyOnce((config) => [200, { auth: config.headers?.Authorization || '' }])

    const [communityResponse, imResponse] = await Promise.all([
      http.get('/api/community-protected'),
      imCoreHttp.get('/api/im/protected')
    ])

    expect(communityResponse.data.auth).toBe('Bearer new-token')
    expect(imResponse.data.auth).toBe('Bearer new-token')
    expect(refreshTransport.requestRefreshToken).toHaveBeenCalledTimes(1)
    expect(refreshTransport.requestCurrentUser).toHaveBeenCalledTimes(1)
  })

  it('shares one refresh between HTTP recovery and session bootstrap', async () => {
    const auth = useAuthStore()
    globalThis.localStorage.setItem('community.session.hint', '1')
    const refresh = deferred()
    refreshTransport.requestRefreshToken.mockReturnValueOnce(refresh.promise)
    refreshTransport.requestCurrentUser.mockResolvedValueOnce({
      data: { userId: 8, username: 'bob' },
      traceId: 'trace-profile'
    })
    mock.onGet('/api/bootstrap-protected')
      .replyOnce(401)
      .onGet('/api/bootstrap-protected')
      .replyOnce((config) => [200, { auth: config.headers?.Authorization || '' }])

    const httpRequest = http.get('/api/bootstrap-protected')
    const bootstrap = ensureSessionReady({ auth })
    await vi.waitFor(() => expect(refreshTransport.requestRefreshToken).toHaveBeenCalledTimes(1))
    refresh.resolve({ data: { accessToken: 'new-token' }, traceId: 'trace-refresh' })

    const [httpResponse, session] = await Promise.all([httpRequest, bootstrap])

    expect(httpResponse.data.auth).toBe('Bearer new-token')
    expect(session).toEqual({ state: 'ready' })
    expect(refreshTransport.requestRefreshToken).toHaveBeenCalledTimes(1)
    expect(refreshTransport.requestCurrentUser).toHaveBeenCalledTimes(1)
  })

  it('does not enter another refresh loop when the retried request also returns 401', async () => {
    const auth = useAuthStore()
    auth.setAccessToken('old-token')
    refreshTransport.requestRefreshToken.mockResolvedValueOnce({
      data: { accessToken: 'new-token' },
      traceId: 'trace-refresh'
    })
    refreshTransport.requestCurrentUser.mockResolvedValueOnce({
      data: { userId: 8, username: 'bob' },
      traceId: 'trace-profile'
    })
    mock.onGet('/api/still-unauthorized').reply(401)

    await expect(http.get('/api/still-unauthorized')).rejects.toBeTruthy()

    expect(refreshTransport.requestRefreshToken).toHaveBeenCalledTimes(1)
    expect(refreshTransport.requestCurrentUser).toHaveBeenCalledTimes(1)
    expect(mock.history.get.filter((request) => request.url === '/api/still-unauthorized')).toHaveLength(2)
  })

  it('should suppress global error toast when request opts out', async () => {
    mock.onPost('/api/auth/refresh').replyOnce(401, { code: 10004, message: '刷新令牌无效', traceId: 'trace-1' })

    await expect(http.post('/api/auth/refresh', null, { skipGlobalErrorToast: true })).rejects.toBeTruthy()
    expect(toast).not.toHaveBeenCalled()
  })

  it('should not attempt refresh for any auth endpoint 401 response', async () => {
    const auth = useAuthStore()
    auth.setAccessToken('old-token')

    mock.onPost('/api/auth/password/reset/confirm').replyOnce(401, { code: 401, message: '未登录' })
    mock.onPost('/api/auth/refresh').replyOnce(200, { code: 0, data: { accessToken: 'new-token' } })

    await expect(http.post('/api/auth/password/reset/confirm', { resetToken: 'x' })).rejects.toBeTruthy()

    expect(mock.history.post.filter((req) => req.url === '/api/auth/refresh')).toHaveLength(0)
    expect(useAuthStore().accessToken).toBe('old-token')
  })

  it.each([
    ['recharge', '/api/wallet/recharges', { amount: 10 }],
    ['withdrawal', '/api/wallet/withdrawals', { amount: 10 }],
    ['transfer', '/api/wallet/transfers', { targetUserId: 2, amount: 10 }],
    ['market order', '/api/market/orders', { listingId: 1, quantity: 1 }],
    ['post', '/api/posts', { title: 'title', content: 'content' }],
    ['comment', '/api/posts/1/comments', { content: 'comment' }]
  ])('should assign a fresh Idempotency-Key to each new %s invocation', async (_name, url, body) => {
    mock.onPost(url).reply((config) => {
      return [200, { idem: config.headers?.[IDEMPOTENCY_HEADER] || '' }]
    })

    const first = await http.post(url, body)
    const second = await http.post(url, body)

    expect(first.data.idem).toBeTruthy()
    expect(second.data.idem).toBeTruthy()
    expect(second.data.idem).not.toBe(first.data.idem)
  })

  it('should preserve Idempotency-Key when retrying the same Axios config after a network failure', async () => {
    const url = '/api/wallet/recharges'
    mock.onPost(url).networkErrorOnce()

    let originalConfig
    try {
      await http.post(url, { amount: 10 })
    } catch (error) {
      originalConfig = error.config
    }

    const firstKey = originalConfig?.headers?.[IDEMPOTENCY_HEADER]
    expect(firstKey).toBeTruthy()

    mock.onPost(url).replyOnce((config) => {
      return [200, { idem: config.headers?.[IDEMPOTENCY_HEADER] || '' }]
    })

    const retried = await http(originalConfig)
    expect(retried.data.idem).toBe(firstKey)
  })

  it('should preserve an explicitly provided Idempotency-Key regardless of casing', async () => {
    const url = '/api/wallet/withdrawals'
    mock.onPost(url).replyOnce((config) => {
      return [200, { idem: config.headers?.get(IDEMPOTENCY_HEADER) || '' }]
    })

    const response = await http.post(url, { amount: 10 }, {
      headers: { 'idempotency-key': 'caller-provided-key' }
    })

    expect(response.data.idem).toBe('caller-provided-key')
  })

  it.each([
    ['unprotected POST', 'post', '/api/users/batch-summary', { userIds: ['u1'] }],
    ['GET', 'get', '/api/wallet/recharges', undefined],
    ['auth refresh', 'post', '/api/auth/refresh', null]
  ])('should not attach Idempotency-Key to %s requests', async (_name, method, url, data) => {
    mock.onAny(url).replyOnce((config) => {
      return [200, { idem: config.headers?.[IDEMPOTENCY_HEADER] || '' }]
    })

    const response = await http.request({ method, url, data })

    expect(response.data.idem).toBe('')
  })

  it('should not derive Idempotency-Key from requestId fields for wallet and market writes', async () => {
    mock.onPost('/api/wallet/recharges').reply((config) => {
      return [200, { idem: config.headers?.[IDEMPOTENCY_HEADER] || '' }]
    })
    mock.onPost('/api/market/orders').reply((config) => {
      return [200, { idem: config.headers?.[IDEMPOTENCY_HEADER] || '' }]
    })

    const recharge = await http.post('/api/wallet/recharges', { requestId: 'wallet:req-1', amount: 10 })
    const order = await http.post('/api/market/orders', { requestId: 'market:req-2', listingId: 1, quantity: 1 })

    expect(recharge.data.idem).toBeTruthy()
    expect(order.data.idem).toBeTruthy()
    expect(recharge.data.idem).not.toBe('wallet:req-1')
    expect(order.data.idem).not.toBe('market:req-2')
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
