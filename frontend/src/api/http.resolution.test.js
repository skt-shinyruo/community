import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'

describe('http base URL resolution', () => {
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

  it('should prefer runtime api base URL when configured', async () => {
    vi.stubGlobal('location', {
      protocol: 'http:',
      hostname: 'localhost',
      host: 'localhost:12881',
      port: '12881',
      href: 'http://localhost:12881/'
    })
    globalThis.__COMMUNITY_RUNTIME_CONFIG__ = {
      apiBaseUrl: 'https://edge.example.com'
    }

    const { default: http } = await import('./http')

    expect(http.defaults.baseURL).toBe('https://edge.example.com')
  })

  it('should prefer VITE api base URL when runtime config is absent', async () => {
    vi.stubEnv('VITE_API_BASE_URL', 'https://api.example.com')

    const { default: http } = await import('./http')

    expect(http.defaults.baseURL).toBe('https://api.example.com')
  })

  it('should fall back to same-origin behavior when no runtime config is provided', async () => {
    vi.stubGlobal('location', {
      protocol: 'http:',
      hostname: 'localhost',
      host: 'localhost:12881',
      port: '12881',
      href: 'http://localhost:12881/'
    })

    const { default: http } = await import('./http')

    expect(http.defaults.baseURL).toBe('')
  })
})
