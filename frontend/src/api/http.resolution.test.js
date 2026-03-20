import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'

describe('http base URL resolution', () => {
  beforeEach(() => {
    vi.resetModules()
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('should prefer community-gateway for local frontend origins', async () => {
    vi.stubGlobal('location', {
      protocol: 'http:',
      hostname: 'localhost',
      host: 'localhost:12881',
      port: '12881',
      href: 'http://localhost:12881/'
    })

    const { default: http } = await import('./http')

    expect(http.defaults.baseURL).toBe('http://localhost:12880')
  })

  it('should keep same-origin behavior for non-local hosts', async () => {
    vi.stubGlobal('location', {
      protocol: 'https:',
      hostname: 'community.example.com',
      host: 'community.example.com',
      port: '',
      href: 'https://community.example.com/'
    })

    const { default: http } = await import('./http')

    expect(http.defaults.baseURL).toBe('')
  })
})
