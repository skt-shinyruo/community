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
  })

  it('should prefer project-gateway websocket URL for local origins', async () => {
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

  it('should keep same-origin websocket URL outside localhost', async () => {
    vi.stubGlobal('location', {
      protocol: 'https:',
      hostname: 'community.example.com',
      host: 'community.example.com',
      port: '',
      href: 'https://community.example.com/'
    })

    const { imRealtimeClient } = await import('./imRealtimeClient')
    imRealtimeClient.connect('token-1')

    expect(FakeWebSocket.instances).toHaveLength(1)
    expect(FakeWebSocket.instances[0].url).toBe('wss://community.example.com/ws/im')
  })
})
