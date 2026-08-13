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
    const [{ ImRealtimeClient }, { default: imCoreHttp }] = await Promise.all([
      import('./imRealtimeClient'),
      import('../api/imCoreHttp')
    ])
    const imRealtimeClient = new ImRealtimeClient(imCoreHttp)
    currentClient = imRealtimeClient
    return { imRealtimeClient, imCoreHttp }
  }

  async function flushMicrotasks() {
    for (let index = 0; index < 8; index += 1) {
      await Promise.resolve()
    }
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
    FakeWebSocket.prototype.send = function () {}
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
          wsUrl: 'wss://edge.example.com/ws/im',
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
    expect(FakeWebSocket.instances[0].url).toBe('wss://edge.example.com/ws/im')
  })

  it('should use returned websocket URL directly and send connect ticket after open', async () => {
    const { imRealtimeClient, imCoreHttp } = await loadClient()
    imCoreHttp.post.mockResolvedValue({
      data: {
        data: {
          sessionId: 'sess-1',
          wsUrl: 'wss://edge.example.com/ws/im',
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
    expect(ws.url).toBe('wss://edge.example.com/ws/im')
    expect(sent).toEqual([])

    ws.readyState = FakeWebSocket.OPEN
    ws.onopen?.()
    ws.onmessage?.({
      data: JSON.stringify({
        type: 'connected',
        sessionId: 'sess-1',
        schemaVersion: 1
      })
    })

    imRealtimeClient.sendPrivateText({
      toUserId: '22222222-2222-7222-8222-222222222222',
      content: 'hello',
      clientMsgId: 'private-1'
    })
    imRealtimeClient.sendRoomText({
      roomId: '33333333-3333-7333-8333-333333333333',
      content: 'hello room',
      clientMsgId: 'room-1'
    })

    expect(imCoreHttp.post).toHaveBeenCalledTimes(1)
    expect(imRealtimeClient.state.authed).toBe(true)
    expect(sent[0]).toEqual(expect.objectContaining({ schemaVersion: 1 }))
    expect(sent[1]).toEqual(expect.objectContaining({ schemaVersion: 1 }))
    expect(sent[2]).toEqual(expect.objectContaining({ schemaVersion: 1 }))
    expect(sent[0]).toMatchObject({ type: 'connect', ticket: 'ticket-1' })
    expect(sent[1]).toMatchObject({
      type: 'sendPrivateText',
      toUserId: '22222222-2222-7222-8222-222222222222',
      content: 'hello',
      clientMsgId: 'private-1'
    })
    expect(sent[2]).toMatchObject({
      type: 'sendRoomText',
      roomId: '33333333-3333-7333-8333-333333333333',
      content: 'hello room',
      clientMsgId: 'room-1'
    })
  })

  it('rejects a non-UUID room id before writing a frame', async () => {
    const { imRealtimeClient, imCoreHttp } = await loadClient()
    imCoreHttp.post.mockResolvedValue({
      data: { data: { wsUrl: 'wss://edge.example.com/ws/im', ticket: 'ticket-1' } }
    })
    const sent = []
    FakeWebSocket.prototype.send = (payload) => sent.push(JSON.parse(payload))
    await imRealtimeClient.connect('token-1')
    await flushMicrotasks()
    const ws = FakeWebSocket.instances[0]
    ws.readyState = FakeWebSocket.OPEN
    ws.onopen?.()
    ws.onmessage?.({ data: JSON.stringify({ type: 'connected', sessionId: 'sess-1', schemaVersion: 1 }) })

    expect(() => imRealtimeClient.sendRoomText({ roomId: 42, content: 'invalid room' }))
      .toThrow('roomId 非法')
    expect(sent).toHaveLength(1)
  })

  it.each([
    ['missing', undefined],
    ['null', null],
    ['zero', 0],
    ['negative', -1],
    ['future', 2],
    ['string', '1']
  ])('should reject %s inbound schema before business callbacks', async (_label, schemaVersion) => {
    const { imRealtimeClient, imCoreHttp } = await loadClient()
    imCoreHttp.post.mockResolvedValue({
      data: {
        data: {
          sessionId: 'sess-1',
          wsUrl: 'wss://edge.example.com/ws/im',
          ticket: 'ticket-1'
        }
      }
    })
    const protocolErrors = []
    const businessEvents = []
    imRealtimeClient.on('protocolError', (error) => protocolErrors.push(error))
    imRealtimeClient.on('connected', (message) => businessEvents.push(message))

    await imRealtimeClient.connect('token-1')
    await flushMicrotasks()

    const ws = FakeWebSocket.instances[0]
    ws.readyState = FakeWebSocket.OPEN
    ws.close = vi.fn()
    ws.onopen?.()
    ws.onmessage?.({
      data: JSON.stringify({
        type: 'connected',
        sessionId: 'sess-1',
        schemaVersion
      })
    })

    expect(protocolErrors).toHaveLength(1)
    expect(protocolErrors[0]).toEqual({ reasonCode: 'unsupported_schema_version' })
    expect(businessEvents).toHaveLength(0)
    expect(imRealtimeClient.state.authed).toBe(false)
    expect(ws.close).toHaveBeenCalledWith(1002, 'unsupported_schema_version')
  })

  it('should reject command sends while websocket is open but not authenticated', async () => {
    const { imRealtimeClient, imCoreHttp } = await loadClient()
    imCoreHttp.post.mockResolvedValue({
      data: {
        data: {
          sessionId: 'sess-1',
          wsUrl: 'wss://edge.example.com/ws/im',
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

    expect(imRealtimeClient.state.connected).toBe(true)
    expect(imRealtimeClient.state.authed).toBe(false)
    expect(() => imRealtimeClient.sendPrivateText({
      toUserId: '22222222-2222-7222-8222-222222222222',
      content: 'hello'
    })).toThrow('IM 正在认证，请稍后重试')
    expect(sent).toHaveLength(1)
    expect(sent[0]).toMatchObject({ type: 'connect', ticket: 'ticket-1' })
  })

  it('should reject command sends when websocket is not open', async () => {
    const { imRealtimeClient } = await loadClient()

    expect(() => imRealtimeClient.sendPrivateText({
      toUserId: '22222222-2222-7222-8222-222222222222',
      content: 'hello'
    })).toThrow('IM 未连接')
  })

  it('should emit sendRejected for command reject frames', async () => {
    const { imRealtimeClient, imCoreHttp } = await loadClient()
    imCoreHttp.post.mockResolvedValue({
      data: {
        data: {
          sessionId: 'sess-1',
          wsUrl: 'wss://edge.example.com/ws/im',
          ticket: 'ticket-1'
        }
      }
    })
    const rejected = []
    imRealtimeClient.on('sendRejected', (msg) => rejected.push(msg))

    await imRealtimeClient.connect('token-1')
    await flushMicrotasks()

    const ws = FakeWebSocket.instances[0]
    ws.readyState = FakeWebSocket.OPEN
    ws.onopen?.()
    ws.onmessage?.({
      data: JSON.stringify({
        type: 'connected',
        sessionId: 'sess-1',
        schemaVersion: 1
      })
    })
    ws.onmessage?.({
      data: JSON.stringify({
        type: 'reject',
        cmd: 'sendPrivateText',
        clientMsgId: 'client-msg-1',
        message: 'connect required',
        schemaVersion: 1
      })
    })

    expect(rejected).toHaveLength(1)
    expect(rejected[0]).toMatchObject({
      cmd: 'sendPrivateText',
      clientMsgId: 'client-msg-1',
      message: 'connect required'
    })
  })

  it('should emit sendCommitted for persisted command results', async () => {
    const { imRealtimeClient, imCoreHttp } = await loadClient()
    imCoreHttp.post.mockResolvedValue({
      data: { data: { wsUrl: 'wss://edge.example.com/ws/im', ticket: 'ticket-1' } }
    })
    const committed = []
    imRealtimeClient.on('sendCommitted', (msg) => committed.push(msg))

    await imRealtimeClient.connect('token-1')
    await flushMicrotasks()
    const ws = FakeWebSocket.instances[0]
    ws.readyState = FakeWebSocket.OPEN
    ws.onopen?.()
    ws.onmessage?.({ data: JSON.stringify({
      type: 'committed',
      cmd: 'sendPrivateText',
      clientMsgId: 'client-msg-1',
      messageId: 'aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa',
      seq: 9,
      schemaVersion: 1
    }) })

    expect(committed).toHaveLength(1)
    expect(committed[0]).toMatchObject({ clientMsgId: 'client-msg-1', seq: 9 })
  })

  it('should emit stateChanged when websocket auth state changes', async () => {
    const { imRealtimeClient, imCoreHttp } = await loadClient()
    imCoreHttp.post.mockResolvedValue({
      data: {
        data: {
          sessionId: 'sess-1',
          wsUrl: 'wss://edge.example.com/ws/im',
          ticket: 'ticket-1'
        }
      }
    })
    const states = []
    imRealtimeClient.on('stateChanged', (state) => states.push(state))

    await imRealtimeClient.connect('token-1')
    await flushMicrotasks()

    const ws = FakeWebSocket.instances[0]
    ws.readyState = FakeWebSocket.OPEN
    ws.onopen?.()
    ws.onmessage?.({
      data: JSON.stringify({
        type: 'connected',
        sessionId: 'sess-1',
        schemaVersion: 1
      })
    })
    ws.onclose?.()

    expect(states).toEqual([
      expect.objectContaining({ connected: true, authed: false, sessionId: '' }),
      expect.objectContaining({ connected: true, authed: true, sessionId: 'sess-1' }),
      expect.objectContaining({ connected: false, authed: false, sessionId: '' })
    ])
  })

  it('should ignore delayed events from a websocket replaced during token rotation', async () => {
    const { imRealtimeClient, imCoreHttp } = await loadClient()
    imCoreHttp.post
      .mockResolvedValueOnce({
        data: { data: { wsUrl: 'wss://edge.example.com/ws/im', ticket: 'ticket-1' } }
      })
      .mockResolvedValueOnce({
        data: { data: { wsUrl: 'wss://edge.example.com/ws/im', ticket: 'ticket-2' } }
      })
    const privateMessages = []
    imRealtimeClient.on('privateMessage', (message) => privateMessages.push(message))

    await imRealtimeClient.connect('token-1')
    await flushMicrotasks()
    const replacedSocket = FakeWebSocket.instances[0]

    imRealtimeClient.disconnect()
    await imRealtimeClient.connect('token-2')
    await flushMicrotasks()
    const currentSocket = FakeWebSocket.instances[1]
    currentSocket.readyState = FakeWebSocket.OPEN
    currentSocket.onopen?.()
    currentSocket.onmessage?.({ data: JSON.stringify({
      type: 'connected',
      sessionId: 'sess-2',
      schemaVersion: 1
    }) })

    replacedSocket.onopen?.()
    replacedSocket.onmessage?.({ data: JSON.stringify({
      type: 'privateMessage',
      conversationId: 'conversation-old',
      schemaVersion: 1
    }) })
    replacedSocket.onclose?.()

    expect(privateMessages).toEqual([])
    expect(imRealtimeClient.ws).toBe(currentSocket)
    expect(imRealtimeClient.state).toMatchObject({
      connected: true,
      authed: true,
      sessionId: 'sess-2'
    })
  })

  it('should isolate every handler when a live websocket is overlapped by a replacement', async () => {
    const { imRealtimeClient, imCoreHttp } = await loadClient()
    imCoreHttp.post.mockResolvedValue({
      data: { data: { wsUrl: 'wss://edge.example.com/ws/im', ticket: 'ticket-old' } }
    })
    const sent = []
    FakeWebSocket.prototype.send = function (payload) {
      sent.push({ socket: this, message: JSON.parse(payload) })
    }
    const protocolErrors = []
    const privateMessages = []
    let currentSocket = null
    imRealtimeClient.on('protocolError', (error) => protocolErrors.push(error))
    imRealtimeClient.on('privateMessage', (message) => privateMessages.push(message))
    imRealtimeClient.on('stateChanged', (state) => {
      if (!currentSocket && state.connected && !state.authed) {
        imRealtimeClient._open('wss://edge.example.com/ws/im', 'ticket-current')
        currentSocket = FakeWebSocket.instances[1]
        currentSocket.readyState = FakeWebSocket.OPEN
      }
    })

    await imRealtimeClient.connect('token-1')
    await flushMicrotasks()
    const overlappedSocket = FakeWebSocket.instances[0]

    overlappedSocket.readyState = FakeWebSocket.OPEN
    overlappedSocket.onopen?.()
    expect(currentSocket).toBe(FakeWebSocket.instances[1])
    currentSocket.onopen?.()
    currentSocket.onmessage?.({ data: JSON.stringify({
      type: 'connected',
      sessionId: 'sess-current',
      schemaVersion: 1
    }) })

    overlappedSocket.onmessage?.({ data: JSON.stringify({
      type: 'privateMessage',
      conversationId: 'conversation-old',
      schemaVersion: 1
    }) })
    overlappedSocket.onmessage?.({ data: 'not-json' })
    overlappedSocket.onerror?.()
    overlappedSocket.onclose?.()

    expect(sent).toEqual([{
      socket: currentSocket,
      message: { type: 'connect', ticket: 'ticket-current', schemaVersion: 1 }
    }])
    expect(protocolErrors).toEqual([])
    expect(privateMessages).toEqual([])
    expect(imRealtimeClient.ws).toBe(currentSocket)
    expect(imRealtimeClient.state).toMatchObject({
      connected: true,
      authed: true,
      sessionId: 'sess-current'
    })
  })

  it('should reopen a fresh IM session when the browser comes back online or visible', async () => {
    const { imRealtimeClient, imCoreHttp } = await loadClient()
    imCoreHttp.post
      .mockResolvedValueOnce({
        data: {
          data: {
            sessionId: 'sess-1',
            wsUrl: 'wss://edge.example.com/ws/im',
            ticket: 'ticket-1'
          }
        }
      })
      .mockResolvedValueOnce({
        data: {
          data: {
            sessionId: 'sess-2',
            wsUrl: 'wss://edge.example.com/ws/im',
            ticket: 'ticket-2'
          }
        }
      })
      .mockResolvedValueOnce({
        data: {
          data: {
            sessionId: 'sess-3',
            wsUrl: 'wss://edge.example.com/ws/im',
            ticket: 'ticket-3'
          }
        }
      })
    const sent = []
    FakeWebSocket.prototype.send = (payload) => {
      sent.push(JSON.parse(payload))
    }

    await imRealtimeClient.connect('token-1')
    await flushMicrotasks()
    expect(FakeWebSocket.instances).toHaveLength(1)
    expect(FakeWebSocket.instances[0].url).toBe('wss://edge.example.com/ws/im')
    FakeWebSocket.instances[0].readyState = FakeWebSocket.OPEN
    FakeWebSocket.instances[0].onopen?.()
    expect(sent[0]).toMatchObject({ type: 'connect', ticket: 'ticket-1' })

    FakeWebSocket.instances[0].readyState = 3
    imRealtimeClient.ws = null

    windowListeners.get('online')?.()
    await flushMicrotasks()
    expect(FakeWebSocket.instances).toHaveLength(2)
    expect(FakeWebSocket.instances[1].url).toBe('wss://edge.example.com/ws/im')
    FakeWebSocket.instances[1].readyState = FakeWebSocket.OPEN
    FakeWebSocket.instances[1].onopen?.()
    expect(sent[1]).toMatchObject({ type: 'connect', ticket: 'ticket-2' })

    FakeWebSocket.instances[1].readyState = 3
    imRealtimeClient.ws = null
    globalThis.document.visibilityState = 'visible'

    documentListeners.get('visibilitychange')?.()
    await flushMicrotasks()
    expect(FakeWebSocket.instances).toHaveLength(3)
    expect(FakeWebSocket.instances[2].url).toBe('wss://edge.example.com/ws/im')
    FakeWebSocket.instances[2].readyState = FakeWebSocket.OPEN
    FakeWebSocket.instances[2].onopen?.()
    expect(sent[2]).toMatchObject({ type: 'connect', ticket: 'ticket-3' })
    expect(imCoreHttp.post).toHaveBeenCalledTimes(3)
  })
})
