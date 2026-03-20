import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'

describe('imCoreHttp base URL resolution', () => {
  beforeEach(() => {
    vi.resetModules()
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('should prefer project-gateway for local IM HTTP traffic', async () => {
    vi.stubGlobal('location', {
      protocol: 'http:',
      hostname: '127.0.0.1',
      host: '127.0.0.1:12881',
      port: '12881',
      href: 'http://127.0.0.1:12881/'
    })

    const { default: imCoreHttp } = await import('./imCoreHttp')

    expect(imCoreHttp.defaults.baseURL).toBe('http://127.0.0.1:12880')
  })

  it('should keep same-origin behavior for localhost edge origin', async () => {
    vi.stubGlobal('location', {
      protocol: 'http:',
      hostname: 'localhost',
      host: 'localhost:8080',
      port: '8080',
      href: 'http://localhost:8080/'
    })

    const { default: imCoreHttp } = await import('./imCoreHttp')

    expect(imCoreHttp.defaults.baseURL).toBe('')
  })

  it('should keep same-origin fallback outside localhost', async () => {
    vi.stubGlobal('location', {
      protocol: 'https:',
      hostname: 'community.example.com',
      host: 'community.example.com',
      port: '',
      href: 'https://community.example.com/'
    })

    const { default: imCoreHttp } = await import('./imCoreHttp')

    expect(imCoreHttp.defaults.baseURL).toBe('')
  })
})
