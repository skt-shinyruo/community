import { describe, it, expect, beforeEach, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import MockAdapter from 'axios-mock-adapter'

import http from './http'
import { useAuthStore } from '../stores/auth'
import { setToastHandler } from '../ui/toastService'

describe('http', () => {
  let toast

  beforeEach(() => {
    setActivePinia(createPinia())
    useAuthStore().clear()
    vi.stubGlobal('location', { href: '' })
    vi.stubGlobal('window', {})
    toast = vi.fn()
    setToastHandler(toast)
  })

  it('should attach Authorization header when accessToken exists', async () => {
    const auth = useAuthStore()
    auth.setAccessToken('token-1')

    const mock = new MockAdapter(http)
    mock.onGet('/api/ping').reply((config) => {
      return [200, { auth: config.headers?.Authorization || '' }]
    })

    const resp = await http.get('/api/ping')
    expect(resp.data.auth).toBe('Bearer token-1')
    mock.restore()
  })

  it('should refresh token once and retry request on 401', async () => {
    const auth = useAuthStore()
    auth.setAccessToken('old-token')

    const mock = new MockAdapter(http)
    mock.onGet('/api/protected').replyOnce(401)
    mock.onPost('/api/auth/refresh').replyOnce(200, { code: 0, data: { accessToken: 'new-token' } })
    mock.onGet('/api/protected').replyOnce(200, { ok: true })

    const resp = await http.get('/api/protected')
    expect(resp.data.ok).toBe(true)
    expect(useAuthStore().accessToken).toBe('new-token')
    expect(globalThis.location.href).toBe('')
    mock.restore()
  })

  it('should redirect to login when refresh fails', async () => {
    const auth = useAuthStore()
    auth.setAccessToken('old-token')

    const mock = new MockAdapter(http)
    mock.onGet('/api/protected').replyOnce(401)
    mock.onPost('/api/auth/refresh').replyOnce(401)

    await expect(http.get('/api/protected')).rejects.toBeTruthy()
    expect(useAuthStore().accessToken).toBe('')
    expect(globalThis.location.href).toBe('/#/auth/login')
    mock.restore()
  })

  it('should suppress global error toast when request opts out', async () => {
    const mock = new MockAdapter(http)
    mock.onPost('/api/auth/refresh').replyOnce(401, { code: 10004, message: '刷新令牌无效', traceId: 'trace-1' })

    await expect(http.post('/api/auth/refresh', null, { skipGlobalErrorToast: true })).rejects.toBeTruthy()
    expect(toast).not.toHaveBeenCalled()
    mock.restore()
  })

  it('should only attach Idempotency-Key to configured write endpoints', async () => {
    const mock = new MockAdapter(http)
    mock.onPost('/api/users/batch-summary').reply((config) => {
      return [200, { idem: config.headers?.['Idempotency-Key'] || '' }]
    })

    const resp = await http.post('/api/users/batch-summary', { userIds: ['u1'] })

    expect(resp.data.idem).toBe('')
    mock.restore()
  })

  it('should not derive Idempotency-Key from requestId fields for wallet and market writes', async () => {
    const mock = new MockAdapter(http)
    mock.onPost('/api/wallet/recharges').reply((config) => {
      return [200, { idem: config.headers?.['Idempotency-Key'] || '' }]
    })
    mock.onPost('/api/market/orders').reply((config) => {
      return [200, { idem: config.headers?.['Idempotency-Key'] || '' }]
    })

    const recharge = await http.post('/api/wallet/recharges', { requestId: 'wallet:req-1', amount: 10 })
    const order = await http.post('/api/market/orders', { requestId: 'market:req-2', listingId: 1, quantity: 1 })

    expect(recharge.data.idem).toBeTruthy()
    expect(order.data.idem).toBeTruthy()
    expect(recharge.data.idem).not.toBe('wallet:req-1')
    expect(order.data.idem).not.toBe('market:req-2')
    mock.restore()
  })
})
