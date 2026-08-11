import { describe, expect, it, vi } from 'vitest'

import { createUploadTransport } from './uploadTransport'

describe('uploadTransport', () => {
  it('uses the long-running trusted adapter with auth and one shared recovery', async () => {
    const trustedClient = { request: vi.fn() }
    trustedClient.request
      .mockRejectedValueOnce({ response: { status: 401 } })
      .mockResolvedValueOnce({ data: { code: 0 } })
    const auth = { accessToken: 'old-token', tokenGeneration: 4 }
    const unauthorizedRecovery = vi.fn().mockResolvedValue('new-token')
    const transport = createUploadTransport({
      trustedClient,
      externalClient: { request: vi.fn() },
      authProvider: () => auth,
      unauthorizedRecovery
    })

    await transport.upload({ url: '/api/uploads/1', data: 'form' })

    expect(trustedClient.request).toHaveBeenCalledTimes(2)
    expect(trustedClient.request.mock.calls[0][0]).toMatchObject({
      timeout: 0,
      withCredentials: true,
      headers: { Authorization: 'Bearer old-token' }
    })
    expect(trustedClient.request.mock.calls[1][0].headers.Authorization).toBe('Bearer new-token')
    expect(unauthorizedRecovery).toHaveBeenCalledWith({ auth, requestGeneration: 4 })
  })

  it('preserves provider-signed headers without injecting main-site auth for an external URL', async () => {
    const externalClient = { request: vi.fn().mockResolvedValue({ data: '' }) }
    const unauthorizedRecovery = vi.fn()
    const authProvider = vi.fn(() => ({ accessToken: 'site-token', tokenGeneration: 2 }))
    const transport = createUploadTransport({
      trustedClient: { request: vi.fn() },
      externalClient,
      authProvider,
      unauthorizedRecovery,
      trustedBaseUrl: 'https://community.example.test',
      browserUrl: 'https://community.example.test/app'
    })

    await transport.upload({
      url: 'https://objects.example.test/upload',
      data: 'form',
      headers: { Authorization: 'AWS4-HMAC-SHA256 signed-value', 'X-Signed-Field': 'value' }
    })

    expect(externalClient.request).toHaveBeenCalledWith(expect.objectContaining({
      timeout: 0,
      withCredentials: false,
      headers: {
        Authorization: 'AWS4-HMAC-SHA256 signed-value',
        'X-Signed-Field': 'value'
      }
    }))
    expect(authProvider).not.toHaveBeenCalled()
    expect(unauthorizedRecovery).not.toHaveBeenCalled()
  })

  it('treats an absolute same-origin API URL as trusted', async () => {
    const trustedClient = { request: vi.fn().mockResolvedValue({ data: { code: 0 } }) }
    const externalClient = { request: vi.fn() }
    const transport = createUploadTransport({
      trustedClient,
      externalClient,
      authProvider: () => ({ accessToken: 'site-token', tokenGeneration: 2 }),
      trustedBaseUrl: 'https://community.example.test',
      browserUrl: 'https://community.example.test/app'
    })

    await transport.upload({
      url: 'https://community.example.test/api/uploads/1',
      data: 'form',
      headers: { 'X-Upload': 'value' }
    })

    expect(trustedClient.request).toHaveBeenCalledWith(expect.objectContaining({
      url: 'https://community.example.test/api/uploads/1',
      timeout: 0,
      withCredentials: true,
      headers: { 'X-Upload': 'value', Authorization: 'Bearer site-token' }
    }))
    expect(externalClient.request).not.toHaveBeenCalled()
  })

  it('normalizes progress before exposing it to callers', async () => {
    const trustedClient = {
      request: vi.fn(async (config) => {
        config.onUploadProgress({ loaded: 25, total: 100 })
        return { data: { code: 0 } }
      })
    }
    const onProgress = vi.fn()
    const transport = createUploadTransport({
      trustedClient,
      externalClient: { request: vi.fn() },
      authProvider: () => ({ accessToken: '', tokenGeneration: 0 })
    })

    await transport.upload({ url: '/api/uploads/1', data: 'form', onProgress })

    expect(onProgress).toHaveBeenCalledWith({ loaded: 25, total: 100, percent: 25 })
  })
})
