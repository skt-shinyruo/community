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
  let realDocument
  let currentClient

  async function loadClient() {
    const [{ imRealtimeClient }, { default: imCoreHttp }] = await Promise.all([
      import('./imRealtimeClient'),
      import('../api/imCoreHttp')
    ])
    currentClient = imRealtimeClient
    return { imRealtimeClient, imCoreHttp }
  }

  async function flushMicrotasks() {
    await Promise.resolve()
    await Promise.resolve()
  }

  beforeEach(() => {
    vi.resetModules()
    vi.resetAllMocks()
    vi.doMock('../api/imCoreHttp', () => ({
      default: {
        post: vi.fn()
      }
    }))
    FakeWebSocket.instances = []
    currentClient = null
    vi.stubGlobal('WebSocket', FakeWebSocket)
    windowListeners = new Map()
    documentListeners = new Map()
    vi.stubGlobal('addEventListener', (type, listener) => {
      windowListeners.set(type, listener)
    })
    realDocument = globalThis.document
    Object.defineProperty(realDocument, 'visibilityState', {
      configurable: true,
      writable: true,
      value: 'visible'
    })
    vi.spyOn(realDocument, 'addEventListener').mockImplementation((type, listener) => {
      documentListeners.set(type, listener)
    })
    vi.spyOn(realDocument, 'removeEventListener').mockImplementation(() => {})
  })

  afterEach(async () => {
    currentClient?.disconnect?.()
    currentClient = null
    vi.restoreAllMocks()
    vi.unstubAllGlobals()
    vi.unstubAllEnvs()
    try {
      delete globalThis.__COMMUNITY_RUNTIME_CONFIG__
    } catch {}
  })

  it('should open a server-issued IM session before connecting the websocket', async () => {
    const { imRealtimeClient, imCoreHttp } = await loadClient()
    imCoreHttp.post.mockResolvedValue({
      data: {
        data: {
          sessionId: 'sess-1',
          workerId: 'worker-a',
          wsUrl: 'wss://edge.example.com/ws/im/workers/worker-a',
          ticket: 'ticket-1'
        }
      }
    })

    await imRealtimeClient.connect('token-1')
    await flushMicrotasks()

    expect(imCoreHttp.post).toHaveBeenCalledWith(
      '/api/im/sessions',
      null,
      expect.objectContaining({
        headers: expect.objectContaining({
          Authorization: 'Bearer token-1'
        })
      })
    )
    expect(FakeWebSocket.instances).toHaveLength(1)
    expect(FakeWebSocket.instances[0].url).toBe('wss://edge.example.com/ws/im/workers/worker-a')
  })

  it('should send connect ticket and then allow private-message sends', async () => {
    const { imRealtimeClient, imCoreHttp } = await loadClient()
    imCoreHttp.post.mockResolvedValue({
      data: {
        data: {
          sessionId: 'sess-1',
          workerId: 'worker-a',
          wsUrl: 'wss://edge.example.com/ws/im/workers/worker-a',
          ticket: 'ticket-1'
        }
      }
    })
    const sent = []
    FakeWebSocket.prototype.send = (payload) => {
      sent.push(JSON.parse(payload))
    }

    await imRealtimeClient.connect('token-1')
    await flushMicrotasks()

    const ws = FakeWebSocket.instances[0]
    ws.readyState = FakeWebSocket.OPEN
    ws.onopen?.()
    ws.onmessage?.({
      data: JSON.stringify({
        type: 'connected',
        sessionId: 'sess-1',
        workerId: 'worker-a'
      })
    })

    imRealtimeClient.sendPrivateText({
      toUserId: '22222222-2222-7222-8222-222222222222',
      content: 'hello'
    })

    expect(imCoreHttp.post).toHaveBeenCalledTimes(1)
    expect(imRealtimeClient.state.authed).toBe(true)
    expect(sent[0]).toMatchObject({ type: 'connect', ticket: 'ticket-1' })
    expect(sent[1]).toMatchObject({
      type: 'sendPrivateText',
      toUserId: '22222222-2222-7222-8222-222222222222',
      content: 'hello'
    })
  })

  it('should reopen a fresh IM session when the browser comes back online or visible', async () => {
    const { imRealtimeClient, imCoreHttp } = await loadClient()
    imCoreHttp.post
      .mockResolvedValueOnce({
        data: {
          data: {
            sessionId: 'sess-1',
            workerId: 'worker-a',
            wsUrl: 'wss://edge.example.com/ws/im/workers/worker-a',
            ticket: 'ticket-1'
          }
        }
      })
      .mockResolvedValueOnce({
        data: {
          data: {
            sessionId: 'sess-2',
            workerId: 'worker-b',
            wsUrl: 'wss://edge.example.com/ws/im/workers/worker-b',
            ticket: 'ticket-2'
          }
        }
      })
      .mockResolvedValueOnce({
        data: {
          data: {
            sessionId: 'sess-3',
            workerId: 'worker-c',
            wsUrl: 'wss://edge.example.com/ws/im/workers/worker-c',
            ticket: 'ticket-3'
          }
        }
      })

    await imRealtimeClient.connect('token-1')
    await flushMicrotasks()
    expect(FakeWebSocket.instances).toHaveLength(1)

    FakeWebSocket.instances[0].readyState = 3
    imRealtimeClient.ws = null

    windowListeners.get('online')?.()
    await flushMicrotasks()
    expect(FakeWebSocket.instances).toHaveLength(2)
    expect(FakeWebSocket.instances[1].url).toBe('wss://edge.example.com/ws/im/workers/worker-b')

    FakeWebSocket.instances[1].readyState = 3
    imRealtimeClient.ws = null
    globalThis.document.visibilityState = 'visible'

    documentListeners.get('visibilitychange')?.()
    await flushMicrotasks()
    expect(FakeWebSocket.instances).toHaveLength(3)
    expect(FakeWebSocket.instances[2].url).toBe('wss://edge.example.com/ws/im/workers/worker-c')
    expect(imCoreHttp.post).toHaveBeenCalledTimes(3)
  })
})
