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
    this.sent = []
    this.closedWith = null
    FakeWebSocket.instances.push(this)
  }

  send(payload) {
    this.sent.push(JSON.parse(payload))
  }

  close(code, reason) {
    this.readyState = 3
    this.closedWith = code === undefined ? null : { code, reason }
  }

  open() {
    this.readyState = FakeWebSocket.OPEN
    this.onopen?.()
  }

  receive(frame) {
    this.onmessage?.({ data: typeof frame === 'string' ? frame : JSON.stringify(frame) })
  }

  drop() {
    this.readyState = 3
    this.onclose?.()
  }
}

const CONVERSATION_ID = '11111111-1111-7111-8111-111111111111_22222222-2222-7222-8222-222222222222'
const ME_ID = '11111111-1111-7111-8111-111111111111'
const PEER_ID = '22222222-2222-7222-8222-222222222222'
const ROOM_ID = '33333333-3333-7333-8333-333333333333'
const MESSAGE_ID = '99999999-9999-7999-8999-999999999999'

const VALID_PRIVATE_MESSAGE = {
  type: 'privateMessage',
  conversationId: CONVERSATION_ID,
  seq: 9,
  messageId: MESSAGE_ID,
  fromUserId: PEER_ID,
  toUserId: ME_ID,
  content: 'hello',
  createdAtEpochMillis: 1774060187920
}

const VALID_COMMITTED = {
  type: 'committed',
  cmd: 'sendPrivateText',
  clientMsgId: 'client-msg-1',
  requestId: 'req-1',
  conversationId: CONVERSATION_ID,
  roomId: null,
  messageId: MESSAGE_ID,
  seq: 9
}

const VALID_REJECT = {
  type: 'reject',
  cmd: 'sendPrivateText',
  clientMsgId: 'client-msg-1',
  requestId: 'req-1',
  code: 429,
  reasonCode: 'rate_limited',
  message: '发送频率过高'
}

describe('imRealtimeClient', () => {
  let windowListeners
  let documentListeners
  let realDocument
  let currentClient

  /**
   * @param {{ onListenerError?: (type: string, error: unknown) => void }} [options]
   */
  async function loadClient({ onListenerError } = {}) {
    const [{ ImRealtimeClient }, { default: imCoreHttp }] = await Promise.all([
      import('./imRealtimeClient'),
      import('../api/imCoreHttp')
    ])
    /** @type {(url: string) => WebSocket} */
    const webSocketFactory = (url) => /** @type {WebSocket} */ (/** @type {unknown} */ (new FakeWebSocket(url)))
    const imRealtimeClient = new ImRealtimeClient(imCoreHttp, { webSocketFactory, onListenerError })
    currentClient = imRealtimeClient
    return { imRealtimeClient, imCoreHttp }
  }

  async function flushMicrotasks() {
    for (let index = 0; index < 8; index += 1) {
      await Promise.resolve()
    }
  }

  function mockSession(imCoreHttp, ticket = 'ticket-1') {
    vi.mocked(imCoreHttp.post).mockResolvedValue({
      data: { data: { wsUrl: 'wss://edge.example.com/ws/im', ticket } }
    })
  }

  async function connectAndAuth(imRealtimeClient, imCoreHttp, { token = 'token-1', sessionId = 'sess-1' } = {}) {
    mockSession(imCoreHttp)
    await imRealtimeClient.connect(token)
    await flushMicrotasks()
    const ws = FakeWebSocket.instances[FakeWebSocket.instances.length - 1]
    ws.open()
    ws.receive({ type: 'connected', sessionId, schemaVersion: 1 })
    return ws
  }

  function openThenClose(socket) {
    socket.open()
    socket.drop()
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

  describe('session bootstrap', () => {
    it('should open a server-issued IM session before connecting the websocket', async () => {
      const { imRealtimeClient, imCoreHttp } = await loadClient()
      vi.mocked(imCoreHttp.post).mockResolvedValue({
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
      vi.mocked(imCoreHttp.post).mockResolvedValue({
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

      const ws = FakeWebSocket.instances[0]
      expect(ws.url).toBe('wss://edge.example.com/ws/im')
      expect(ws.sent).toEqual([])

      ws.open()
      ws.receive({ type: 'connected', sessionId: 'sess-1', schemaVersion: 1 })

      imRealtimeClient.sendPrivateText({
        toUserId: PEER_ID,
        content: 'hello',
        clientMsgId: 'private-1'
      })
      imRealtimeClient.sendRoomText({
        roomId: ROOM_ID,
        content: 'hello room',
        clientMsgId: 'room-1'
      })

      expect(imCoreHttp.post).toHaveBeenCalledTimes(1)
      expect(imRealtimeClient.state.authed).toBe(true)
      expect(ws.sent[0]).toMatchObject({ type: 'connect', ticket: 'ticket-1', schemaVersion: 1 })
      expect(ws.sent[1]).toMatchObject({
        type: 'sendPrivateText',
        toUserId: PEER_ID,
        content: 'hello',
        clientMsgId: 'private-1',
        schemaVersion: 1
      })
      expect(ws.sent[2]).toMatchObject({
        type: 'sendRoomText',
        roomId: ROOM_ID,
        content: 'hello room',
        clientMsgId: 'room-1',
        schemaVersion: 1
      })
    })

    it('rejects a non-UUID room id before writing a frame', async () => {
      const { imRealtimeClient, imCoreHttp } = await loadClient()
      const ws = await connectAndAuth(imRealtimeClient, imCoreHttp)

      expect(() => imRealtimeClient.sendRoomText({ roomId: 42, content: 'invalid room' }))
        .toThrow('roomId 非法')
      expect(ws.sent).toHaveLength(1)
      expect(ws.sent[0]).toMatchObject({ type: 'connect' })
    })
  })

  describe('send validation', () => {
    it('should reject command sends while websocket is open but not authenticated', async () => {
      const { imRealtimeClient, imCoreHttp } = await loadClient()
      mockSession(imCoreHttp)

      await imRealtimeClient.connect('token-1')
      await flushMicrotasks()

      const ws = FakeWebSocket.instances[0]
      ws.open()

      expect(imRealtimeClient.state.connected).toBe(true)
      expect(imRealtimeClient.state.authed).toBe(false)
      expect(() => imRealtimeClient.sendPrivateText({
        toUserId: PEER_ID,
        content: 'hello'
      })).toThrow('IM 正在认证，请稍后重试')
      expect(ws.sent).toHaveLength(1)
      expect(ws.sent[0]).toMatchObject({ type: 'connect', ticket: 'ticket-1' })
    })

    it('should reject command sends when websocket is not open', async () => {
      const { imRealtimeClient } = await loadClient()

      expect(() => imRealtimeClient.sendPrivateText({
        toUserId: PEER_ID,
        content: 'hello'
      })).toThrow('IM 未连接')
    })
  })

  describe('inbound frame normalization', () => {
    it('emits normalized privateMessage events with the history-aligned timestamp field', async () => {
      const { imRealtimeClient, imCoreHttp } = await loadClient()
      const messages = []
      imRealtimeClient.on('privateMessage', (message) => messages.push(message))
      const ws = await connectAndAuth(imRealtimeClient, imCoreHttp)

      ws.receive({ ...VALID_PRIVATE_MESSAGE, schemaVersion: 1 })

      expect(messages).toEqual([{
        type: 'privateMessage',
        conversationId: CONVERSATION_ID,
        seq: 9,
        messageId: MESSAGE_ID,
        fromUserId: PEER_ID,
        toUserId: ME_ID,
        content: 'hello',
        createdAtEpochMs: 1774060187920
      }])
    })

    it('emits sendCommitted for valid committed frames', async () => {
      const { imRealtimeClient, imCoreHttp } = await loadClient()
      const committed = []
      imRealtimeClient.on('sendCommitted', (message) => committed.push(message))
      const ws = await connectAndAuth(imRealtimeClient, imCoreHttp)

      ws.receive({ ...VALID_COMMITTED, schemaVersion: 1 })

      expect(committed).toEqual([{
        type: 'sendCommitted',
        cmd: 'sendPrivateText',
        clientMsgId: 'client-msg-1',
        requestId: 'req-1',
        conversationId: CONVERSATION_ID,
        roomId: '',
        messageId: MESSAGE_ID,
        seq: 9
      }])
    })

    it('emits sendRejected for valid send reject frames', async () => {
      const { imRealtimeClient, imCoreHttp } = await loadClient()
      const rejected = []
      imRealtimeClient.on('sendRejected', (message) => rejected.push(message))
      const ws = await connectAndAuth(imRealtimeClient, imCoreHttp)

      ws.receive({ ...VALID_REJECT, traceId: 'trace-1', schemaVersion: 1 })
      ws.receive({ ...VALID_REJECT, clientMsgId: 'client-msg-2', schemaVersion: 1 })

      expect(rejected).toEqual([
        {
          type: 'sendRejected',
          cmd: 'sendPrivateText',
          clientMsgId: 'client-msg-1',
          requestId: 'req-1',
          code: 429,
          reasonCode: 'rate_limited',
          message: '发送频率过高',
          traceId: 'trace-1'
        },
        {
          type: 'sendRejected',
          cmd: 'sendPrivateText',
          clientMsgId: 'client-msg-2',
          requestId: 'req-1',
          code: 429,
          reasonCode: 'rate_limited',
          message: '发送频率过高',
          traceId: ''
        }
      ])
    })

    it('maps connect reject frames to auth state only', async () => {
      const { imRealtimeClient, imCoreHttp } = await loadClient()
      const states = []
      const rejected = []
      imRealtimeClient.on('stateChanged', (state) => states.push(state))
      imRealtimeClient.on('sendRejected', (message) => rejected.push(message))
      const ws = await connectAndAuth(imRealtimeClient, imCoreHttp)
      expect(imRealtimeClient.state.authed).toBe(true)

      ws.receive({
        type: 'reject',
        cmd: 'connect',
        clientMsgId: '',
        requestId: '',
        code: 401,
        reasonCode: 'invalid_ticket',
        message: 'invalid ticket',
        schemaVersion: 1
      })

      expect(rejected).toEqual([])
      expect(states.at(-1)).toMatchObject({ connected: true, authed: false, sessionId: '' })
    })

    it('emits roomUpdatedBatch for room update frames without a schema version', async () => {
      const { imRealtimeClient, imCoreHttp } = await loadClient()
      const batches = []
      imRealtimeClient.on('roomUpdatedBatch', (message) => batches.push(message))
      const ws = await connectAndAuth(imRealtimeClient, imCoreHttp)

      ws.receive({
        type: 'roomUpdatedBatch',
        items: [{ roomId: ROOM_ID, lastSeq: 12 }]
      })

      expect(batches).toEqual([{
        type: 'roomUpdatedBatch',
        items: [{ roomId: ROOM_ID, lastSeq: 12 }]
      }])
    })

    it('accepts room update frames carrying schema version 1', async () => {
      const { imRealtimeClient, imCoreHttp } = await loadClient()
      const batches = []
      imRealtimeClient.on('roomUpdatedBatch', (message) => batches.push(message))
      const ws = await connectAndAuth(imRealtimeClient, imCoreHttp)

      ws.receive({
        type: 'roomUpdatedBatch',
        items: [{ roomId: ROOM_ID, lastSeq: 7 }],
        schemaVersion: 1
      })

      expect(batches).toHaveLength(1)
    })

    it('ignores frames of unknown or unconsumed types without protocol errors', async () => {
      const { imRealtimeClient, imCoreHttp } = await loadClient()
      const protocolErrors = []
      imRealtimeClient.on('protocolError', (error) => protocolErrors.push(error))
      const ws = await connectAndAuth(imRealtimeClient, imCoreHttp)

      ws.receive({ type: 'ack', cmd: 'sendPrivateText', clientMsgId: 'c-1', requestId: 'r-1', schemaVersion: 1 })
      ws.receive({ type: 'pong', sentAtEpochMillis: 1774060187920, schemaVersion: 1 })
      ws.receive({ type: 'futureFrame', schemaVersion: 1 })
      ws.receive({ schemaVersion: 1 })

      expect(protocolErrors).toEqual([])
      expect(ws.closedWith).toBeNull()
      expect(imRealtimeClient.state.authed).toBe(true)
    })
  })

  describe('protocol errors', () => {
    it.each([
      ['missing', undefined],
      ['null', null],
      ['zero', 0],
      ['negative', -1],
      ['future', 2],
      ['string', '1']
    ])('should reject %s inbound schema before business callbacks', async (_label, schemaVersion) => {
      const { imRealtimeClient, imCoreHttp } = await loadClient()
      mockSession(imCoreHttp)
      const protocolErrors = []
      const states = []
      imRealtimeClient.on('protocolError', (error) => protocolErrors.push(error))
      imRealtimeClient.on('stateChanged', (state) => states.push(state))

      await imRealtimeClient.connect('token-1')
      await flushMicrotasks()

      const ws = FakeWebSocket.instances[0]
      ws.open()
      states.length = 0
      ws.receive({ type: 'connected', sessionId: 'sess-1', schemaVersion })

      expect(protocolErrors).toEqual([{ reasonCode: 'unsupported_schema_version', frameType: 'connected' }])
      expect(states).toEqual([])
      expect(imRealtimeClient.state.authed).toBe(false)
      expect(ws.closedWith).toEqual({ code: 1002, reason: 'unsupported_schema_version' })
    })

    it('rejects a room update batch carrying an unsupported schema version', async () => {
      const { imRealtimeClient, imCoreHttp } = await loadClient()
      const protocolErrors = []
      const batches = []
      imRealtimeClient.on('protocolError', (error) => protocolErrors.push(error))
      imRealtimeClient.on('roomUpdatedBatch', (message) => batches.push(message))
      const ws = await connectAndAuth(imRealtimeClient, imCoreHttp)

      ws.receive({ type: 'roomUpdatedBatch', items: [], schemaVersion: 2 })

      expect(protocolErrors).toEqual([{ reasonCode: 'unsupported_schema_version', frameType: 'roomUpdatedBatch' }])
      expect(batches).toEqual([])
      expect(ws.closedWith).toEqual({ code: 1002, reason: 'unsupported_schema_version' })
    })

    it.each([
      ['unparseable text', 'not-json'],
      ['a JSON array', '[1,2]'],
      ['a JSON number', '42']
    ])('routes %s frames to protocolError without closing the socket', async (_label, payload) => {
      const { imRealtimeClient, imCoreHttp } = await loadClient()
      const protocolErrors = []
      const messages = []
      imRealtimeClient.on('protocolError', (error) => protocolErrors.push(error))
      imRealtimeClient.on('privateMessage', (message) => messages.push(message))
      const ws = await connectAndAuth(imRealtimeClient, imCoreHttp)

      ws.receive(payload)

      expect(protocolErrors).toEqual([{ reasonCode: 'invalid_frame', frameType: '' }])
      expect(messages).toEqual([])
      expect(ws.closedWith).toBeNull()
    })

    it.each([
      ['missing seq', { seq: undefined }, 'seq'],
      ['non-integer seq', { seq: 1.5 }, 'seq'],
      ['non-UUID messageId', { messageId: 'not-a-uuid' }, 'messageId'],
      ['non-UUID fromUserId', { fromUserId: 42 }, 'fromUserId'],
      ['missing content', { content: undefined }, 'content'],
      ['missing createdAtEpochMillis', { createdAtEpochMillis: undefined }, 'createdAtEpochMillis']
    ])('routes privateMessage with %s to protocolError', async (_label, overrides, field) => {
      const { imRealtimeClient, imCoreHttp } = await loadClient()
      const protocolErrors = []
      const messages = []
      imRealtimeClient.on('protocolError', (error) => protocolErrors.push(error))
      imRealtimeClient.on('privateMessage', (message) => messages.push(message))
      const ws = await connectAndAuth(imRealtimeClient, imCoreHttp)

      ws.receive({ ...VALID_PRIVATE_MESSAGE, ...overrides, schemaVersion: 1 })

      expect(protocolErrors).toEqual([{ reasonCode: 'invalid_frame', frameType: 'privateMessage', field }])
      expect(messages).toEqual([])
      expect(ws.closedWith).toBeNull()
    })

    it.each([
      ['missing clientMsgId', { clientMsgId: undefined }, 'clientMsgId'],
      ['empty clientMsgId', { clientMsgId: '  ' }, 'clientMsgId'],
      ['zero seq', { seq: 0 }, 'seq'],
      ['non-UUID messageId', { messageId: 'nope' }, 'messageId'],
      ['an unknown cmd', { cmd: 'sendImage' }, 'cmd']
    ])('routes committed with %s to protocolError', async (_label, overrides, field) => {
      const { imRealtimeClient, imCoreHttp } = await loadClient()
      const protocolErrors = []
      const committed = []
      imRealtimeClient.on('protocolError', (error) => protocolErrors.push(error))
      imRealtimeClient.on('sendCommitted', (message) => committed.push(message))
      const ws = await connectAndAuth(imRealtimeClient, imCoreHttp)

      ws.receive({ ...VALID_COMMITTED, ...overrides, schemaVersion: 1 })

      expect(protocolErrors).toEqual([{ reasonCode: 'invalid_frame', frameType: 'committed', field }])
      expect(committed).toEqual([])
      expect(ws.closedWith).toBeNull()
    })

    it.each([
      ['missing code', { code: undefined }, 'code'],
      ['non-integer code', { code: '429' }, 'code'],
      ['missing message', { message: undefined }, 'message'],
      ['non-string cmd', { cmd: 42 }, 'cmd']
    ])('routes reject with %s to protocolError', async (_label, overrides, field) => {
      const { imRealtimeClient, imCoreHttp } = await loadClient()
      const protocolErrors = []
      const rejected = []
      imRealtimeClient.on('protocolError', (error) => protocolErrors.push(error))
      imRealtimeClient.on('sendRejected', (message) => rejected.push(message))
      const ws = await connectAndAuth(imRealtimeClient, imCoreHttp)

      ws.receive({ ...VALID_REJECT, ...overrides, schemaVersion: 1 })

      expect(protocolErrors).toEqual([{ reasonCode: 'invalid_frame', frameType: 'reject', field }])
      expect(rejected).toEqual([])
      expect(ws.closedWith).toBeNull()
    })

    it.each([
      ['missing items', { items: undefined }],
      ['non-array items', { items: {} }],
      ['an item with a bad roomId', { items: [{ roomId: 'nope', lastSeq: 3 }] }],
      ['an item with a bad lastSeq', { items: [{ roomId: ROOM_ID, lastSeq: -1 }] }]
    ])('routes roomUpdatedBatch with %s to protocolError', async (_label, overrides) => {
      const { imRealtimeClient, imCoreHttp } = await loadClient()
      const protocolErrors = []
      const batches = []
      imRealtimeClient.on('protocolError', (error) => protocolErrors.push(error))
      imRealtimeClient.on('roomUpdatedBatch', (message) => batches.push(message))
      const ws = await connectAndAuth(imRealtimeClient, imCoreHttp)

      ws.receive({ type: 'roomUpdatedBatch', ...overrides })

      expect(protocolErrors).toEqual([{ reasonCode: 'invalid_frame', frameType: 'roomUpdatedBatch', field: 'items' }])
      expect(batches).toEqual([])
      expect(ws.closedWith).toBeNull()
    })

    it('closes the socket when the connected handshake frame is malformed', async () => {
      const { imRealtimeClient, imCoreHttp } = await loadClient()
      mockSession(imCoreHttp)
      const protocolErrors = []
      imRealtimeClient.on('protocolError', (error) => protocolErrors.push(error))

      await imRealtimeClient.connect('token-1')
      await flushMicrotasks()
      const ws = FakeWebSocket.instances[0]
      ws.open()

      ws.receive({ type: 'connected', schemaVersion: 1 })

      // 握手帧 malformed 时必须关闭连接：开着 socket 不发认证，重连只由 onclose 触发，
      // 不关连接客户端会永远卡在「认证中」。
      expect(protocolErrors).toEqual([{ reasonCode: 'invalid_frame', frameType: 'connected', field: 'sessionId' }])
      expect(imRealtimeClient.state.authed).toBe(false)
      expect(ws.closedWith).toEqual({ code: 1002, reason: 'invalid_frame' })
    })

    it('closes the socket when a connect reject frame is malformed', async () => {
      const { imRealtimeClient, imCoreHttp } = await loadClient()
      mockSession(imCoreHttp)
      const protocolErrors = []
      imRealtimeClient.on('protocolError', (error) => protocolErrors.push(error))

      await imRealtimeClient.connect('token-1')
      await flushMicrotasks()
      const ws = FakeWebSocket.instances[0]
      ws.open()

      ws.receive({
        type: 'reject',
        cmd: 'connect',
        clientMsgId: '',
        requestId: '',
        reasonCode: 'invalid_ticket',
        schemaVersion: 1
      })

      expect(protocolErrors).toEqual([{ reasonCode: 'invalid_frame', frameType: 'reject', field: 'code' }])
      expect(ws.closedWith).toEqual({ code: 1002, reason: 'invalid_frame' })
    })

    it('keeps the session usable after an invalid frame', async () => {
      const { imRealtimeClient, imCoreHttp } = await loadClient()
      const protocolErrors = []
      const messages = []
      imRealtimeClient.on('protocolError', (error) => protocolErrors.push(error))
      imRealtimeClient.on('privateMessage', (message) => messages.push(message))
      const ws = await connectAndAuth(imRealtimeClient, imCoreHttp)

      ws.receive({ ...VALID_PRIVATE_MESSAGE, seq: 0, schemaVersion: 1 })
      ws.receive({ ...VALID_PRIVATE_MESSAGE, schemaVersion: 1 })

      expect(protocolErrors).toHaveLength(1)
      expect(messages).toHaveLength(1)
      expect(imRealtimeClient.state.authed).toBe(true)
    })
  })

  describe('listener errors', () => {
    it('reports a synchronously throwing listener and still notifies the others', async () => {
      const listenerErrors = []
      const { imRealtimeClient, imCoreHttp } = await loadClient({
        onListenerError: (type, error) => listenerErrors.push({ type, error })
      })
      const received = []
      const boom = new Error('boom')
      imRealtimeClient.on('privateMessage', () => { throw boom })
      imRealtimeClient.on('privateMessage', (message) => received.push(message))
      const ws = await connectAndAuth(imRealtimeClient, imCoreHttp)

      ws.receive({ ...VALID_PRIVATE_MESSAGE, schemaVersion: 1 })

      expect(received).toHaveLength(1)
      expect(listenerErrors).toEqual([{ type: 'privateMessage', error: boom }])
    })

    it('reports async listener rejections instead of leaving them unhandled', async () => {
      const listenerErrors = []
      const { imRealtimeClient, imCoreHttp } = await loadClient({
        onListenerError: (type, error) => listenerErrors.push({ type, error })
      })
      const boom = new Error('async boom')
      imRealtimeClient.on('privateMessage', async () => { throw boom })
      const ws = await connectAndAuth(imRealtimeClient, imCoreHttp)

      ws.receive({ ...VALID_PRIVATE_MESSAGE, schemaVersion: 1 })
      await flushMicrotasks()

      expect(listenerErrors).toEqual([{ type: 'privateMessage', error: boom }])
    })
  })

  describe('connection lifecycle', () => {
    it('should emit stateChanged when websocket auth state changes', async () => {
      const { imRealtimeClient, imCoreHttp } = await loadClient()
      mockSession(imCoreHttp)
      const states = []
      imRealtimeClient.on('stateChanged', (state) => states.push(state))

      await imRealtimeClient.connect('token-1')
      await flushMicrotasks()

      const ws = FakeWebSocket.instances[0]
      ws.open()
      ws.receive({ type: 'connected', sessionId: 'sess-1', schemaVersion: 1 })
      ws.drop()

      expect(states).toEqual([
        expect.objectContaining({ connected: true, authed: false, sessionId: '' }),
        expect.objectContaining({ connected: true, authed: true, sessionId: 'sess-1' }),
        expect.objectContaining({ connected: false, authed: false, sessionId: '' })
      ])
    })

    it('should ignore delayed events from a websocket replaced during token rotation', async () => {
      const { imRealtimeClient, imCoreHttp } = await loadClient()
      vi.mocked(imCoreHttp.post)
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
      currentSocket.open()
      currentSocket.receive({ type: 'connected', sessionId: 'sess-2', schemaVersion: 1 })

      replacedSocket.open()
      replacedSocket.receive({ ...VALID_PRIVATE_MESSAGE, schemaVersion: 1 })
      replacedSocket.drop()

      expect(privateMessages).toEqual([])
      expect(imRealtimeClient.state).toMatchObject({
        connected: true,
        authed: true,
        sessionId: 'sess-2'
      })
    })

    it('should isolate every handler when a live websocket is overlapped by a replacement', async () => {
      const { imRealtimeClient, imCoreHttp } = await loadClient()
      vi.mocked(imCoreHttp.post)
        .mockResolvedValueOnce({
          data: { data: { wsUrl: 'wss://edge.example.com/ws/im', ticket: 'ticket-1' } }
        })
        .mockResolvedValueOnce({
          data: { data: { wsUrl: 'wss://edge.example.com/ws/im', ticket: 'ticket-2' } }
        })
      const protocolErrors = []
      const privateMessages = []
      imRealtimeClient.on('protocolError', (error) => protocolErrors.push(error))
      imRealtimeClient.on('privateMessage', (message) => privateMessages.push(message))

      await imRealtimeClient.connect('token-1')
      await flushMicrotasks()
      const overlappedSocket = FakeWebSocket.instances[0]
      overlappedSocket.open()
      overlappedSocket.receive({ type: 'connected', sessionId: 'sess-1', schemaVersion: 1 })

      // Token 轮换直接替换仍存活的旧 socket：disconnect 不会先等到旧 socket 的 onclose。
      imRealtimeClient.disconnect()
      await imRealtimeClient.connect('token-2')
      await flushMicrotasks()
      const currentSocket = FakeWebSocket.instances[1]
      currentSocket.open()
      currentSocket.receive({ type: 'connected', sessionId: 'sess-2', schemaVersion: 1 })

      overlappedSocket.receive({ ...VALID_PRIVATE_MESSAGE, schemaVersion: 1 })
      overlappedSocket.receive('not-json')
      overlappedSocket.onerror?.()
      overlappedSocket.drop()

      expect(protocolErrors).toEqual([])
      expect(privateMessages).toEqual([])
      expect(overlappedSocket.sent).toEqual([{ type: 'connect', ticket: 'ticket-1', schemaVersion: 1 }])
      expect(currentSocket.sent).toEqual([{ type: 'connect', ticket: 'ticket-2', schemaVersion: 1 }])
      expect(imRealtimeClient.state).toMatchObject({
        connected: true,
        authed: true,
        sessionId: 'sess-2'
      })
    })

    it('should reopen a fresh IM session when the browser comes back online or visible', async () => {
      const { imRealtimeClient, imCoreHttp } = await loadClient()
      vi.mocked(imCoreHttp.post)
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

      await imRealtimeClient.connect('token-1')
      await flushMicrotasks()
      expect(FakeWebSocket.instances).toHaveLength(1)
      FakeWebSocket.instances[0].open()
      expect(FakeWebSocket.instances[0].sent[0]).toMatchObject({ type: 'connect', ticket: 'ticket-1' })

      FakeWebSocket.instances[0].drop()

      windowListeners.get('online')?.()
      await flushMicrotasks()
      expect(FakeWebSocket.instances).toHaveLength(2)
      FakeWebSocket.instances[1].open()
      expect(FakeWebSocket.instances[1].sent[0]).toMatchObject({ type: 'connect', ticket: 'ticket-2' })

      FakeWebSocket.instances[1].drop()
      Object.defineProperty(globalThis.document, 'visibilityState', {
      configurable: true,
      writable: true,
      value: 'visible'
    })

      documentListeners.get('visibilitychange')?.()
      await flushMicrotasks()
      expect(FakeWebSocket.instances).toHaveLength(3)
      FakeWebSocket.instances[2].open()
      expect(FakeWebSocket.instances[2].sent[0]).toMatchObject({ type: 'connect', ticket: 'ticket-3' })
      expect(imCoreHttp.post).toHaveBeenCalledTimes(3)
    })

    describe('reconnect backoff', () => {
      beforeEach(() => {
        vi.useFakeTimers()
        // Pin jitter to 0 so delays are exactly 500 * 2^attempts (capped at 5000).
        vi.spyOn(Math, 'random').mockReturnValue(0)
      })

      afterEach(() => {
        vi.useRealTimers()
      })

      it('does not reset the reconnect counter on websocket open alone', async () => {
        const { imRealtimeClient, imCoreHttp } = await loadClient()
        mockSession(imCoreHttp)

        await imRealtimeClient.connect('token-1')
        await flushMicrotasks()
        expect(FakeWebSocket.instances).toHaveLength(1)

        // First unauthenticated open/close cycle schedules a ~500ms reconnect.
        openThenClose(FakeWebSocket.instances[0])
        await vi.advanceTimersByTimeAsync(499)
        expect(FakeWebSocket.instances).toHaveLength(1)
        await vi.advanceTimersByTimeAsync(1)
        await flushMicrotasks()
        expect(FakeWebSocket.instances).toHaveLength(2)

        // Second cycle: open must NOT have zeroed the counter, so the next
        // reconnect waits ~1000ms instead of dropping back to ~500ms.
        openThenClose(FakeWebSocket.instances[1])
        await vi.advanceTimersByTimeAsync(500)
        await flushMicrotasks()
        expect(FakeWebSocket.instances).toHaveLength(2)
        await vi.advanceTimersByTimeAsync(500)
        await flushMicrotasks()
        expect(FakeWebSocket.instances).toHaveLength(3)
      })

      it('keeps increasing the delay while connections close before the connected frame', async () => {
        const { imRealtimeClient, imCoreHttp } = await loadClient()
        mockSession(imCoreHttp)

        await imRealtimeClient.connect('token-1')
        await flushMicrotasks()
        expect(FakeWebSocket.instances).toHaveLength(1)

        // Backoff ladder with jitter=0: 500, 1000, 2000, 4000.
        const delays = [500, 1000, 2000, 4000]
        for (let index = 0; index < delays.length; index += 1) {
          openThenClose(FakeWebSocket.instances[index])
          await vi.advanceTimersByTimeAsync(delays[index] - 1)
          await flushMicrotasks()
          expect(FakeWebSocket.instances).toHaveLength(index + 1)
          await vi.advanceTimersByTimeAsync(1)
          await flushMicrotasks()
          expect(FakeWebSocket.instances).toHaveLength(index + 2)
        }
      })

      it('resets the reconnect counter after a valid connected frame', async () => {
        const { imRealtimeClient, imCoreHttp } = await loadClient()
        mockSession(imCoreHttp)

        await imRealtimeClient.connect('token-1')
        await flushMicrotasks()

        // Two unauthenticated cycles escalate the counter (delays 500, 1000).
        openThenClose(FakeWebSocket.instances[0])
        await vi.advanceTimersByTimeAsync(500)
        await flushMicrotasks()
        openThenClose(FakeWebSocket.instances[1])
        await vi.advanceTimersByTimeAsync(1000)
        await flushMicrotasks()
        expect(FakeWebSocket.instances).toHaveLength(3)

        // This socket authenticates, then drops: the next reconnect must be
        // back at the ~500ms base delay.
        const authedSocket = FakeWebSocket.instances[2]
        authedSocket.open()
        authedSocket.receive({ type: 'connected', sessionId: 'sess-1', schemaVersion: 1 })
        expect(imRealtimeClient.state.authed).toBe(true)
        authedSocket.drop()

        await vi.advanceTimersByTimeAsync(499)
        await flushMicrotasks()
        expect(FakeWebSocket.instances).toHaveLength(3)
        await vi.advanceTimersByTimeAsync(1)
        await flushMicrotasks()
        expect(FakeWebSocket.instances).toHaveLength(4)
      })

      it('caps the backoff delay at 5000ms', async () => {
        const { imRealtimeClient, imCoreHttp } = await loadClient()
        mockSession(imCoreHttp)

        await imRealtimeClient.connect('token-1')
        await flushMicrotasks()

        // Drive the counter past the 2^4 exponent cap: 500, 1000, 2000, 4000,
        // then every further delay must stay at 5000.
        const delays = [500, 1000, 2000, 4000, 5000]
        for (let index = 0; index < delays.length; index += 1) {
          openThenClose(FakeWebSocket.instances[index])
          await vi.advanceTimersByTimeAsync(delays[index])
          await flushMicrotasks()
          expect(FakeWebSocket.instances).toHaveLength(index + 2)
        }

        // One more failure: delay must still be capped, not 8000.
        openThenClose(FakeWebSocket.instances[5])
        await vi.advanceTimersByTimeAsync(4999)
        await flushMicrotasks()
        expect(FakeWebSocket.instances).toHaveLength(6)
        await vi.advanceTimersByTimeAsync(1)
        await flushMicrotasks()
        expect(FakeWebSocket.instances).toHaveLength(7)
      })

      it('cancels a pending reconnect on manual disconnect', async () => {
        const { imRealtimeClient, imCoreHttp } = await loadClient()
        mockSession(imCoreHttp)

        await imRealtimeClient.connect('token-1')
        await flushMicrotasks()
        openThenClose(FakeWebSocket.instances[0])

        imRealtimeClient.disconnect()
        await vi.advanceTimersByTimeAsync(10000)
        await flushMicrotasks()
        expect(FakeWebSocket.instances).toHaveLength(1)
      })

      it('starts a fresh login at the base delay after an unauthenticated streak', async () => {
        const { imRealtimeClient, imCoreHttp } = await loadClient()
        mockSession(imCoreHttp)

        await imRealtimeClient.connect('token-1')
        await flushMicrotasks()

        // Accumulate backoff while unauthenticated (delays 500, 1000).
        openThenClose(FakeWebSocket.instances[0])
        await vi.advanceTimersByTimeAsync(500)
        await flushMicrotasks()
        openThenClose(FakeWebSocket.instances[1])
        await vi.advanceTimersByTimeAsync(1000)
        await flushMicrotasks()
        expect(FakeWebSocket.instances).toHaveLength(3)

        // Logout + re-login (App.vue drives disconnect/connect on token change):
        // the first reconnect after the new session drops must be ~500ms again.
        openThenClose(FakeWebSocket.instances[2])
        imRealtimeClient.disconnect()
        await imRealtimeClient.connect('token-2')
        await flushMicrotasks()
        expect(FakeWebSocket.instances).toHaveLength(4)

        openThenClose(FakeWebSocket.instances[3])
        await vi.advanceTimersByTimeAsync(499)
        await flushMicrotasks()
        expect(FakeWebSocket.instances).toHaveLength(4)
        await vi.advanceTimersByTimeAsync(1)
        await flushMicrotasks()
        expect(FakeWebSocket.instances).toHaveLength(5)
      })
    })
  })
})
