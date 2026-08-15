// IM realtime client: open a server-side session, then connect to the assigned worker.
import { normalizeOpaqueId, requireApiOpaqueId } from '../utils/opaqueId'
import { safeJsonParse } from '../utils/safeJson'
import imCoreHttp from '../api/imCoreHttp'

const IM_SCHEMA_VERSION = 1

function randomId() {
  try {
    const c = globalThis?.crypto
    if (c?.randomUUID) return c.randomUUID()
  } catch {}
  return `c_${Date.now()}_${Math.random().toString(36).slice(2)}`
}

function createInitialState() {
  return {
    connected: false,
    authed: false,
    userId: '',
    sessionId: ''
  }
}

function readSessionBootstrap(response) {
  const data = response?.data?.data || {}
  return {
    wsUrl: String(data?.wsUrl || '').trim(),
    ticket: String(data?.ticket || '').trim()
  }
}

class Emitter {
  constructor() {
    this.listeners = new Map()
  }
  on(type, fn) {
    const t = String(type || '')
    if (!t || typeof fn !== 'function') return () => {}
    const set = this.listeners.get(t) || new Set()
    set.add(fn)
    this.listeners.set(t, set)
    return () => this.off(t, fn)
  }
  off(type, fn) {
    const t = String(type || '')
    const set = this.listeners.get(t)
    if (!set) return
    set.delete(fn)
    if (set.size === 0) this.listeners.delete(t)
  }
  emit(type, payload) {
    const t = String(type || '')
    const set = this.listeners.get(t)
    if (!set) return
    for (const fn of set) {
      try { fn(payload) } catch {}
    }
  }
}

export class ImRealtimeClient {
  constructor(sessionHttp = imCoreHttp) {
    this.sessionHttp = sessionHttp
    this.ws = null
    this.accessToken = ''
    this.connectAttempt = 0
    this.state = createInitialState()
    this.emitter = new Emitter()
    this.reconnectTimer = null
    this.reconnectAttempts = 0
    this._bindBrowserRecovery()
  }

  on(type, fn) {
    return this.emitter.on(type, fn)
  }

  _bindBrowserRecovery() {
    try {
      globalThis?.addEventListener?.('online', () => {
        this._resumeConnection()
      })
    } catch {}

    try {
      globalThis?.document?.addEventListener?.('visibilitychange', () => {
        if (globalThis?.document?.visibilityState !== 'visible') return
        this._resumeConnection()
      })
    } catch {}
  }

  async connect(accessToken) {
    const token = String(accessToken || '').trim()
    this.accessToken = token
    if (!token || this._hasActiveSocket()) return

    this._clearReconnect()
    const attempt = ++this.connectAttempt
    return this._connectWithSession(token, attempt)
  }

  _resumeConnection() {
    if (!this.accessToken || this._hasActiveSocket()) return
    this._clearReconnect()
    void this.connect(this.accessToken)
  }

  disconnect() {
    this.accessToken = ''
    this.connectAttempt += 1
    this._clearReconnect()
    const socket = this.ws
    this.ws = null
    try {
      socket?.close?.()
    } catch {}
    this.state = createInitialState()
    this._emitStateChanged()
  }

  sendPrivateText({ toUserId, content, clientMsgId } = {}) {
    const toId = normalizeOpaqueId(toUserId)
    const c = String(content || '')
    if (!toId) return ''
    const cmid = String(clientMsgId || '').trim() || randomId()
    this._sendCommand({
      type: 'sendPrivateText',
      toUserId: toId,
      content: c,
      clientMsgId: cmid
    })
    return cmid
  }

  sendRoomText({ roomId, content, clientMsgId } = {}) {
    const rid = requireApiOpaqueId(roomId, 'roomId')
    const c = String(content || '')
    const cmid = String(clientMsgId || '').trim() || randomId()
    this._sendCommand({
      type: 'sendRoomText',
      roomId: rid,
      content: c,
      clientMsgId: cmid
    })
    return cmid
  }

  _hasActiveSocket() {
    const readyState = this.ws?.readyState
    return readyState === WebSocket.OPEN || readyState === WebSocket.CONNECTING
  }

  async _connectWithSession(token, attempt) {
    try {
      const response = await this.sessionHttp.post('/api/im/sessions', null, {
        headers: {
          Authorization: `Bearer ${token}`
        }
      })
      const { wsUrl, ticket } = readSessionBootstrap(response)
      if (!wsUrl || !ticket) {
        throw new Error('missing IM session bootstrap data')
      }
      if (attempt !== this.connectAttempt || token !== this.accessToken || this._hasActiveSocket()) return
      this._open(wsUrl, ticket)
    } catch {
      if (attempt === this.connectAttempt && token === this.accessToken && !this._hasActiveSocket()) {
        this._scheduleReconnect()
      }
    }
  }

  _open(url, ticket) {
    let socket
    try {
      socket = new WebSocket(url)
    } catch {
      this._scheduleReconnect()
      return
    }
    this.ws = socket

    const isCurrentSocket = () => this.ws === socket

    socket.onopen = () => {
      if (!isCurrentSocket()) return
      this.state.connected = true
      this.state.authed = false
      this.state.userId = ''
      this.state.sessionId = ''
      this.reconnectAttempts = 0
      this._emitStateChanged()
      if (!isCurrentSocket()) return
      try {
        this._sendOnSocket(socket, { type: 'connect', ticket })
      } catch {
        try { socket.close?.() } catch {}
      }
    }

    socket.onmessage = (evt) => {
      if (!isCurrentSocket()) return
      const msg = safeJsonParse(evt?.data)
      if (!msg || typeof msg !== 'object' || Array.isArray(msg) || msg.schemaVersion !== IM_SCHEMA_VERSION) {
        this.emitter.emit('protocolError', { reasonCode: 'unsupported_schema_version' })
        socket.close?.(1002, 'unsupported_schema_version')
        return
      }
      const type = String(msg?.type || '')
      if (!type) return
      if (type === 'connected') {
        this.state.authed = true
        this.state.sessionId = String(msg?.sessionId || '').trim()
        this._emitStateChanged()
      } else if (type === 'reject' && String(msg?.cmd || '') === 'connect') {
        this.state.authed = false
        this.state.sessionId = ''
        this._emitStateChanged()
      } else if (type === 'reject' && this._isSendCommand(msg?.cmd)) {
        this.emitter.emit('sendRejected', msg)
      } else if (type === 'committed' && this._isSendCommand(msg?.cmd)) {
        this.emitter.emit('sendCommitted', msg)
      }
      if (isCurrentSocket()) this.emitter.emit(type, msg)
    }

    socket.onclose = () => {
      if (!isCurrentSocket()) return
      this.state.connected = false
      this.state.authed = false
      this.state.userId = ''
      this.state.sessionId = ''
      this.ws = null
      this._emitStateChanged()
      if (this.accessToken) this._scheduleReconnect()
    }

    socket.onerror = () => {
      if (!isCurrentSocket()) return
      // Let onclose handle reconnect
    }
  }

  _sendOnSocket(socket, obj) {
    if (this.ws !== socket || socket?.readyState !== WebSocket.OPEN) {
      throw new Error('IM 未连接')
    }
    socket.send(JSON.stringify({ ...(obj || {}), schemaVersion: IM_SCHEMA_VERSION }))
  }

  _send(obj) {
    this._sendOnSocket(this.ws, obj)
  }

  _sendCommand(obj) {
    if (!this.ws || this.ws.readyState !== WebSocket.OPEN) {
      throw new Error('IM 未连接')
    }
    if (!this.state.authed) {
      throw new Error('IM 正在认证，请稍后重试')
    }
    this._send(obj)
  }

  _isSendCommand(cmd) {
    const c = String(cmd || '')
    return c === 'sendPrivateText' || c === 'sendRoomText'
  }

  _emitStateChanged() {
    this.emitter.emit('stateChanged', { ...this.state })
  }

  _scheduleReconnect() {
    this._clearReconnect()
    const base = 500
    const max = 5000
    const jitter = Math.floor(Math.random() * 200)
    const delay = Math.min(max, base * Math.pow(2, Math.min(4, this.reconnectAttempts))) + jitter
    this.reconnectAttempts += 1
    this.reconnectTimer = setTimeout(() => {
      this.reconnectTimer = null
      if (this.accessToken) this.connect(this.accessToken)
    }, delay)
  }

  _clearReconnect() {
    if (this.reconnectTimer) {
      try { clearTimeout(this.reconnectTimer) } catch {}
      this.reconnectTimer = null
    }
  }
}

export const imRealtimeClient = new ImRealtimeClient()
