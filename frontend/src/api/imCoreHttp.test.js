import { createPinia, setActivePinia } from 'pinia'
import MockAdapter from 'axios-mock-adapter'
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'

const coordinator = vi.hoisted(() => ({
  recoverUnauthorized: vi.fn()
}))

vi.mock('../auth/refreshCoordinator', () => coordinator)

import behaviorImCoreHttp from './imCoreHttp'
import { useAuthStore } from '../stores/auth'

describe('imCoreHttp base URL resolution', () => {
  beforeEach(() => {
    vi.resetModules()
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    vi.unstubAllEnvs()
    try {
      delete globalThis.__COMMUNITY_RUNTIME_CONFIG__
    } catch {}
  })

  it('should prefer runtime IM HTTP base URL when configured', async () => {
    vi.stubGlobal('location', {
      protocol: 'http:',
      hostname: '127.0.0.1',
      host: '127.0.0.1:12881',
      port: '12881',
      href: 'http://127.0.0.1:12881/'
    })
    globalThis.__COMMUNITY_RUNTIME_CONFIG__ = {
      imHttpBaseUrl: 'https://edge.example.com'
    }

    const { default: imCoreHttp } = await import('./imCoreHttp')

    expect(imCoreHttp.defaults.baseURL).toBe('https://edge.example.com')
  })

  it('should prefer VITE IM HTTP base URL when runtime config is absent', async () => {
    vi.stubEnv('VITE_IM_CORE_BASE_URL', 'https://im.example.com')

    const { default: imCoreHttp } = await import('./imCoreHttp')

    expect(imCoreHttp.defaults.baseURL).toBe('https://im.example.com')
  })

  it('should fall back to same-origin relative IM HTTP base URL on localhost ports', async () => {
    vi.stubGlobal('location', {
      protocol: 'http:',
      hostname: '127.0.0.1',
      host: '127.0.0.1:5173',
      port: '5173',
      href: 'http://127.0.0.1:5173/'
    })

    const { default: imCoreHttp } = await import('./imCoreHttp')

    expect(imCoreHttp.defaults.baseURL).toBe('')
  })
})

describe('imCoreHttp authentication recovery', () => {
  let mock

  beforeEach(() => {
    setActivePinia(createPinia())
    coordinator.recoverUnauthorized.mockReset()
    mock = new MockAdapter(behaviorImCoreHttp)
  })

  afterEach(() => {
    mock.restore()
  })

  it('records the token generation on the Axios config without leaking a header', async () => {
    const auth = useAuthStore()
    auth.setAccessToken('token-1')
    mock.onGet('/api/im/ping').reply((config) => [200, {
      auth: config.headers?.Authorization || '',
      generation: config._authTokenGeneration,
      leakedGeneration: config.headers?._authTokenGeneration
    }])

    const response = await behaviorImCoreHttp.get('/api/im/ping')

    expect(response.data.auth).toBe('Bearer token-1')
    expect(response.data.generation).toBe(auth.tokenGeneration)
    expect(response.data.leakedGeneration).toBeUndefined()
  })

  it('uses the shared coordinator token for one retry', async () => {
    const auth = useAuthStore()
    auth.setAccessToken('old-token')
    const generation = auth.tokenGeneration
    coordinator.recoverUnauthorized.mockImplementationOnce(async ({ auth: currentAuth }) => {
      currentAuth.setAccessToken('new-token')
      return 'new-token'
    })
    mock.onGet('/api/im/protected')
      .replyOnce(401)
      .onGet('/api/im/protected')
      .replyOnce((config) => [200, { auth: config.headers?.Authorization || '' }])

    const response = await behaviorImCoreHttp.get('/api/im/protected')

    expect(response.data.auth).toBe('Bearer new-token')
    expect(coordinator.recoverUnauthorized).toHaveBeenCalledWith({
      auth,
      requestGeneration: generation
    })
  })

  it('does not recover twice when the retried IM request also returns 401', async () => {
    const auth = useAuthStore()
    auth.setAccessToken('old-token')
    coordinator.recoverUnauthorized.mockImplementationOnce(async ({ auth: currentAuth }) => {
      currentAuth.setAccessToken('new-token')
      return 'new-token'
    })
    mock.onGet('/api/im/still-unauthorized').reply(401)

    await expect(behaviorImCoreHttp.get('/api/im/still-unauthorized')).rejects.toBeTruthy()

    expect(coordinator.recoverUnauthorized).toHaveBeenCalledTimes(1)
    expect(mock.history.get.filter((request) => request.url === '/api/im/still-unauthorized')).toHaveLength(2)
  })
})
