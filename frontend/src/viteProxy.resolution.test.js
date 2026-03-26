// @vitest-environment node

import { afterEach, describe, expect, it, vi } from 'vitest'

import configFactory from '../vite.config.js'

function getApiProxyTarget(config) {
  return config?.server?.proxy?.['/api']?.target
}

function getPreviewApiProxyTarget(config) {
  return config?.preview?.proxy?.['/api']?.target
}

describe('vite proxy resolution', () => {
  afterEach(() => {
    vi.unstubAllEnvs()
  })

  it('should default local frontend proxy traffic to community-gateway on 12880', () => {
    const config = configFactory({ mode: 'development' })

    expect(getApiProxyTarget(config)).toBe('http://localhost:12880')
    expect(getPreviewApiProxyTarget(config)).toBe('http://localhost:12880')
  })

  it('should allow preview proxy target override for containerized frontend runtime', () => {
    vi.stubEnv('VITE_PREVIEW_PROXY_TARGET', 'http://community-gateway:8080')

    const config = configFactory({ mode: 'development' })

    expect(getPreviewApiProxyTarget(config)).toBe('http://community-gateway:8080')
  })
})
