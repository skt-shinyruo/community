// @vitest-environment node

import { afterEach, describe, expect, it, vi } from 'vitest'

import configFactory from '../vite.config.js'

function getApiProxyTarget(config) {
  return config?.server?.proxy?.['/api']?.target
}

function getPreviewApiProxyTarget(config) {
  return config?.preview?.proxy?.['/api']?.target
}

function getFilesProxyTarget(config) {
  return config?.server?.proxy?.['/files']?.target
}

function getPreviewFilesProxyTarget(config) {
  return config?.preview?.proxy?.['/files']?.target
}

function getWsProxyTarget(config) {
  return config?.server?.proxy?.['/ws/im']?.target
}

function getPreviewWsProxyTarget(config) {
  return config?.preview?.proxy?.['/ws/im']?.target
}

describe('vite proxy resolution', () => {
  afterEach(() => {
    vi.unstubAllEnvs()
  })

  it('should default local frontend proxy traffic to same gateway target for api, files, and websocket', () => {
    const config = configFactory({ mode: 'development' })

    expect(getApiProxyTarget(config)).toBe('http://localhost:12880')
    expect(getPreviewApiProxyTarget(config)).toBe('http://localhost:12880')
    expect(getFilesProxyTarget(config)).toBe('http://localhost:12880')
    expect(getPreviewFilesProxyTarget(config)).toBe('http://localhost:12880')
    expect(getWsProxyTarget(config)).toBe('http://localhost:12880')
    expect(getPreviewWsProxyTarget(config)).toBe('http://localhost:12880')
  })

  it('should allow preview proxy target override for containerized frontend runtime', () => {
    vi.stubEnv('VITE_PREVIEW_PROXY_TARGET', 'http://community-gateway:8080')

    const config = configFactory({ mode: 'development' })

    expect(getPreviewApiProxyTarget(config)).toBe('http://community-gateway:8080')
    expect(getPreviewFilesProxyTarget(config)).toBe('http://community-gateway:8080')
    expect(getPreviewWsProxyTarget(config)).toBe('http://community-gateway:8080')
  })
})
