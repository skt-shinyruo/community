// IM realtime client: open a server-side session, then connect to the assigned worker.
import { normalizeOpaqueId } from '../utils/opaqueId'
import imCoreHttp from '../api/imCoreHttp'

function safeJsonParse(s) {
  try {
    return JSON.parse(s)
  } catch {
    return null
  }
}

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

class ImRealtimeClient {
  constructor() {
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
    try {
      if (this.ws) this.ws.close()
    } catch {}
    this.ws = null
    this.state = createInitialState()
  }

  sendPrivateText({ toUserId, content, clientMsgId } = {}) {
    const toId = normalizeOpaqueId(toUserId)
    const c = String(content || '')
    if (!toId) return ''
    const cmid = String(clientMsgId || '').trim() || randomId()
    this._send({
      type: 'sendPrivateText',
      toUserId: toId,
      content: c,
      clientMsgId: cmid
    })
    return cmid
  }

  sendRoomText({ roomId, content, clientMsgId } = {}) {
    const rid = Number(roomId || 0)
    const c = String(content || '')
    const cmid = String(clientMsgId || '').trim() || randomId()
    this._send({
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
      const response = await imCoreHttp.post('/api/im/sessions', null, {
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
    try {
      this.ws = new WebSocket(url)
    } catch {
      this._scheduleReconnect()
      return
    }

    this.ws.onopen = () => {
      this.state.connected = true
      this.state.authed = false
      this.state.userId = ''
      this.state.sessionId = ''
      this.reconnectAttempts = 0
      this._send({ type: 'connect', ticket })
    }

    this.ws.onmessage = (evt) => {
      const msg = safeJsonParse(evt?.data)
      const type = String(msg?.type || '')
      if (!type) return
      if (type === 'connected') {
        this.state.authed = true
        this.state.sessionId = String(msg?.sessionId || '').trim()
      } else if (type === 'reject' && String(msg?.cmd || '') === 'connect') {
        this.state.authed = false
        this.state.sessionId = ''
      }
      this.emitter.emit(type, msg)
    }

    this.ws.onclose = () => {
      this.state.connected = false
      this.state.authed = false
      this.state.userId = ''
      this.state.sessionId = ''
      this.ws = null
      if (this.accessToken) this._scheduleReconnect()
    }

    this.ws.onerror = () => {
      // Let onclose handle reconnect
    }
  }

  _send(obj) {
    try {
      if (!this.ws || this.ws.readyState !== WebSocket.OPEN) return
      this.ws.send(JSON.stringify(obj || {}))
    } catch {}
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
