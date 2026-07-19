import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import MockAdapter from 'axios-mock-adapter'

import http from './http'
import { useAuthStore } from '../stores/auth'
import { setToastHandler } from '../ui/toastService'

const IDEMPOTENCY_HEADER = 'Idempotency-Key'

describe('http', () => {
  let toast
  let mock

  beforeEach(() => {
    setActivePinia(createPinia())
    useAuthStore().clear()
    vi.stubGlobal('location', { href: '' })
    vi.stubGlobal('window', {})
    toast = vi.fn()
    setToastHandler(toast)
    mock = new MockAdapter(http)
  })

  afterEach(() => {
    mock.restore()
  })

  it('should attach Authorization header when accessToken exists', async () => {
    const auth = useAuthStore()
    auth.setAccessToken('token-1')

    mock.onGet('/api/ping').reply((config) => {
      return [200, { auth: config.headers?.Authorization || '' }]
    })

    const resp = await http.get('/api/ping')
    expect(resp.data.auth).toBe('Bearer token-1')
  })

  it('should refresh token once and retry request on 401', async () => {
    const auth = useAuthStore()
    auth.setAccessToken('old-token')

    mock.onGet('/api/protected').replyOnce(401)
    mock.onPost('/api/auth/refresh').replyOnce(200, { code: 0, data: { accessToken: 'new-token' } })
    mock.onGet('/api/protected').replyOnce(200, { ok: true })

    const resp = await http.get('/api/protected')
    expect(resp.data.ok).toBe(true)
    expect(useAuthStore().accessToken).toBe('new-token')
    expect(globalThis.location.href).toBe('')
  })

  it('should redirect to login when refresh fails', async () => {
    const auth = useAuthStore()
    auth.setAccessToken('old-token')

    mock.onGet('/api/protected').replyOnce(401)
    mock.onPost('/api/auth/refresh').replyOnce(401)

    await expect(http.get('/api/protected')).rejects.toBeTruthy()
    expect(useAuthStore().accessToken).toBe('')
    expect(globalThis.location.href).toBe('/#/auth/login')
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

  it('should preserve an explicitly provided Idempotency-Key', async () => {
    const url = '/api/wallet/withdrawals'
    mock.onPost(url).replyOnce((config) => {
      return [200, { idem: config.headers?.[IDEMPOTENCY_HEADER] || '' }]
    })

    const response = await http.post(url, { amount: 10 }, {
      headers: { [IDEMPOTENCY_HEADER]: 'caller-provided-key' }
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
