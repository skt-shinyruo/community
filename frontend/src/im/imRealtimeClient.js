// IM realtime client: WebSocket connection + simple event emitter.
// Protocol: first message must be { type: "auth", accessToken }.
import { resolveImWsUrl } from '../config/endpointResolution'
import { normalizeOpaqueId } from '../utils/opaqueId'

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
    this.state = { connected: false, authed: false, userId: '' }
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

  connect(accessToken) {
    const token = String(accessToken || '').trim()
    this.accessToken = token
    if (!token) return

    const url = resolveImWsUrl()
    if (!url) return

    if (this.ws && (this.ws.readyState === WebSocket.OPEN || this.ws.readyState === WebSocket.CONNECTING)) {
      return
    }

    this._clearReconnect()
    this._open(url)
  }

  _resumeConnection() {
    const readyState = this.ws?.readyState
    if (!this.accessToken) return
    if (readyState === WebSocket.OPEN || readyState === WebSocket.CONNECTING) return
    this._clearReconnect()
    this.connect(this.accessToken)
  }

  disconnect() {
    this.accessToken = ''
    this._clearReconnect()
    try {
      if (this.ws) this.ws.close()
    } catch {}
    this.ws = null
    this.state = { connected: false, authed: false, userId: '' }
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

  _open(url) {
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
      this.reconnectAttempts = 0
      this._send({ type: 'auth', accessToken: this.accessToken })
    }

    this.ws.onmessage = (evt) => {
      const msg = safeJsonParse(evt?.data)
      const type = String(msg?.type || '')
      if (!type) return
      if (type === 'auth_ok') {
        this.state.authed = true
        this.state.userId = normalizeOpaqueId(msg?.userId)
      }
      if (type === 'auth_error') {
        this.state.authed = false
      }
      this.emitter.emit(type, msg)
    }

    this.ws.onclose = () => {
      this.state.connected = false
      this.state.authed = false
      this.state.userId = ''
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
