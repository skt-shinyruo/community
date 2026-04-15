import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'

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

  it('should fall back to same-origin behavior when no runtime config is provided', async () => {
    vi.stubGlobal('location', {
      protocol: 'http:',
      hostname: 'localhost',
      host: 'localhost:12881',
      port: '12881',
      href: 'http://localhost:12881/'
    })

    const { default: imCoreHttp } = await import('./imCoreHttp')

    expect(imCoreHttp.defaults.baseURL).toBe('')
  })
})
