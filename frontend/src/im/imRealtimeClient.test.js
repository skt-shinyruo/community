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
  let windowListeners
  let documentListeners

  beforeEach(() => {
    vi.resetModules()
    FakeWebSocket.instances = []
    vi.stubGlobal('WebSocket', FakeWebSocket)
    windowListeners = new Map()
    documentListeners = new Map()
    vi.stubGlobal('addEventListener', (type, listener) => {
      windowListeners.set(type, listener)
    })
    vi.stubGlobal('document', {
      visibilityState: 'visible',
      addEventListener(type, listener) {
        documentListeners.set(type, listener)
      },
      removeEventListener() {}
    })
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

  it('should preserve UUID user ids during auth and private-message sends', async () => {
    const { imRealtimeClient } = await import('./imRealtimeClient')
    const sent = []
    FakeWebSocket.prototype.send = (payload) => {
      sent.push(JSON.parse(payload))
    }

    imRealtimeClient.connect('token-1')

    const ws = FakeWebSocket.instances[0]
    ws.readyState = FakeWebSocket.OPEN
    ws.onopen?.()
    ws.onmessage?.({
      data: JSON.stringify({
        type: 'auth_ok',
        userId: '11111111-1111-7111-8111-111111111111'
      })
    })

    imRealtimeClient.sendPrivateText({
      toUserId: '22222222-2222-7222-8222-222222222222',
      content: 'hello'
    })

    expect(imRealtimeClient.state.userId).toBe('11111111-1111-7111-8111-111111111111')
    expect(sent[0]).toMatchObject({ type: 'auth', accessToken: 'token-1' })
    expect(sent[1]).toMatchObject({
      type: 'sendPrivateText',
      toUserId: '22222222-2222-7222-8222-222222222222',
      content: 'hello'
    })
  })

  it('should retry connection when the browser comes back online or visible', async () => {
    const { imRealtimeClient } = await import('./imRealtimeClient')

    imRealtimeClient.connect('token-1')
    expect(FakeWebSocket.instances).toHaveLength(1)

    FakeWebSocket.instances[0].readyState = 3
    imRealtimeClient.ws = null

    windowListeners.get('online')?.()
    expect(FakeWebSocket.instances).toHaveLength(2)

    FakeWebSocket.instances[1].readyState = 3
    imRealtimeClient.ws = null
    globalThis.document.visibilityState = 'visible'

    documentListeners.get('visibilitychange')?.()
    expect(FakeWebSocket.instances).toHaveLength(3)
  })
})
