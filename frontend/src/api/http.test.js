import { describe, it, expect, beforeEach, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import MockAdapter from 'axios-mock-adapter'

import http from './http'
import { useAuthStore } from '../stores/auth'

describe('http', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    useAuthStore().clear()
    vi.stubGlobal('location', { href: '' })
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
})
