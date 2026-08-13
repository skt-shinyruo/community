import axios from 'axios'
import MockAdapter from 'axios-mock-adapter'
import { describe, expect, it, vi } from 'vitest'

import {
  authenticatedRequestConfig,
  installAuthenticatedHttpInterceptors
} from './authenticatedHttp'

describe('authenticatedHttp', () => {
  it('captures the token generation and retries one 401 with the recovered token', async () => {
    const client = axios.create()
    const mock = new MockAdapter(client)
    const auth = { accessToken: 'old-token', tokenGeneration: 7 }
    const unauthorizedRecovery = vi.fn().mockResolvedValue('new-token')
    installAuthenticatedHttpInterceptors(client, {
      authProvider: () => auth,
      unauthorizedRecovery
    })
    mock.onGet('/protected').reply((config) => {
      if (config.headers?.Authorization === 'Bearer old-token') return [401]
      return [200, { generation: config._authTokenGeneration }]
    })

    const response = await client.get('/protected')

    expect(response.data).toEqual({ generation: 7 })
    expect(unauthorizedRecovery).toHaveBeenCalledWith({ auth, requestGeneration: 7 })
    expect(mock.history.get).toHaveLength(2)
    expect(mock.history.get[1].headers.Authorization).toBe('Bearer new-token')
  })

  it('leaves excluded 401 responses to the owning adapter policy', async () => {
    const client = axios.create()
    const mock = new MockAdapter(client)
    const unauthorizedRecovery = vi.fn()
    installAuthenticatedHttpInterceptors(client, {
      authProvider: () => ({ accessToken: 'token', tokenGeneration: 1 }),
      unauthorizedRecovery,
      shouldRecoverUnauthorized: ({ config }) => config.url !== '/auth/refresh'
    })
    mock.onPost('/auth/refresh').reply(401)

    await expect(client.post('/auth/refresh')).rejects.toMatchObject({ response: { status: 401 } })
    expect(unauthorizedRecovery).not.toHaveBeenCalled()
  })

  it('reports terminal recovery failure without retrying the request', async () => {
    const client = axios.create()
    const mock = new MockAdapter(client)
    const terminal = Object.assign(new Error('expired'), { sessionRefreshState: 'terminal' })
    const onRecoveryFailure = vi.fn()
    installAuthenticatedHttpInterceptors(client, {
      authProvider: () => ({ accessToken: '', tokenGeneration: 2 }),
      unauthorizedRecovery: vi.fn().mockRejectedValue(terminal),
      onRecoveryFailure
    })
    mock.onGet('/protected').reply(401)

    await expect(client.get('/protected')).rejects.toBe(terminal)
    expect(onRecoveryFailure).toHaveBeenCalledWith(expect.objectContaining({ error: terminal }))
    expect(mock.history.get).toHaveLength(1)
  })

  it('prepares a cloned trusted-upload config without mutating signed input headers', () => {
    const original = { headers: { Authorization: 'signed', 'X-Upload': 'value' } }

    const prepared = authenticatedRequestConfig(original, {
      accessToken: 'site-token',
      tokenGeneration: 3
    })

    expect(prepared).not.toBe(original)
    expect(original.headers.Authorization).toBe('signed')
    expect(prepared).toMatchObject({
      _authTokenGeneration: 3,
      headers: { Authorization: 'Bearer site-token', 'X-Upload': 'value' }
    })
  })
})
