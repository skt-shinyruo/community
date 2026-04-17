import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'

class FakeWebSocket {
  static CONNECTING = 0
  static OPEN = 1
  static instances = []

  constructor(url) {
    this.url = url
    this.readyState = FakeWebSocket.CONNECTING
    this.onopen = null
    this.onclose = null
    this.onerror = null
    this.onmessage = null
    FakeWebSocket.instances.push(this)
  }

  send() {}

  close() {
    this.readyState = 3
  }
}

describe('imRealtimeClient URL resolution', () => {
  beforeEach(() => {
    vi.resetModules()
    FakeWebSocket.instances = []
    vi.stubGlobal('WebSocket', FakeWebSocket)
  })

  afterEach(async () => {
    const { imRealtimeClient } = await import('./imRealtimeClient')
    imRealtimeClient.disconnect()
    vi.unstubAllGlobals()
    vi.unstubAllEnvs()
    try {
      delete globalThis.__COMMUNITY_RUNTIME_CONFIG__
    } catch {}
  })

  it('should prefer runtime websocket URL when configured', async () => {
    vi.stubGlobal('location', {
      protocol: 'http:',
      hostname: 'localhost',
      host: 'localhost:12881',
      port: '12881',
      href: 'http://localhost:12881/'
    })
    globalThis.__COMMUNITY_RUNTIME_CONFIG__ = {
      imWsUrl: 'wss://edge.example.com/ws/im'
    }

    const { imRealtimeClient } = await import('./imRealtimeClient')
    imRealtimeClient.connect('token-1')

    expect(FakeWebSocket.instances).toHaveLength(1)
    expect(FakeWebSocket.instances[0].url).toBe('wss://edge.example.com/ws/im')
  })

  it('should prefer VITE websocket URL when runtime config is absent', async () => {
    vi.stubEnv('VITE_IM_WS_URL', 'wss://im.example.com/ws/im')

    const { imRealtimeClient } = await import('./imRealtimeClient')
    imRealtimeClient.connect('token-1')

    expect(FakeWebSocket.instances).toHaveLength(1)
    expect(FakeWebSocket.instances[0].url).toBe('wss://im.example.com/ws/im')
  })

  it('should infer the local gateway websocket URL from localhost preview ports', async () => {
    vi.stubGlobal('location', {
      protocol: 'http:',
      hostname: 'localhost',
      host: 'localhost:12881',
      port: '12881',
      href: 'http://localhost:12881/'
    })

    const { imRealtimeClient } = await import('./imRealtimeClient')
    imRealtimeClient.connect('token-1')

    expect(FakeWebSocket.instances).toHaveLength(1)
    expect(FakeWebSocket.instances[0].url).toBe('ws://localhost:12880/ws/im')
  })
})
