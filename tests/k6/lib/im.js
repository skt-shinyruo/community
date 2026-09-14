import ws from 'k6/ws'
import { check } from 'k6'
import { config } from './config.js'
import { authenticatedParams } from './auth.js'
import { postJson, resultData } from './http.js'
import {
  imConnected,
  imPong,
  imRejected,
  imSendAcked,
  imSendCommitted,
  imSendRejected
} from './metrics.js'
import {
  buildConnectFrame,
  buildPingFrame,
  buildSendRoomTextFrame,
  createSendTracker,
  hasCurrentSchemaVersion,
  parseFrame,
  resolveWsUrl
} from './imProtocol.js'

function openImSession(accessToken) {
  const response = postJson('/api/im/sessions', {}, authenticatedParams(accessToken), 200)
  const data = resultData(response, {})
  const wsUrl = resolveWsUrl(config.wsUrl, data.wsUrl)
  check(response, {
    'IM session returns ticket': () => typeof data.ticket === 'string' && data.ticket.length > 20,
    'IM session returns wsUrl': () => typeof data.wsUrl === 'string' && data.wsUrl.startsWith('ws'),
    'IM WebSocket URL resolved': () => wsUrl.startsWith('ws')
  })
  return {
    ticket: data.ticket,
    wsUrl
  }
}

function handleImFrame(raw, state, sends) {
  const frame = parseFrame(raw)
  // Fail closed on protocol drift: frames without the current schemaVersion
  // never count towards connected/pong/send correlation.
  if (!frame || !hasCurrentSchemaVersion(frame)) {
    return
  }
  if (frame.type === 'connected') {
    state.connected = true
    imConnected.add(1)
  } else if (frame.type === 'pong') {
    state.pong = true
    imPong.add(1)
  } else if (frame.type === 'reject') {
    if (frame.cmd === 'connect') {
      state.connectRejected = true
      imRejected.add(1)
    } else if (sends.settle(frame.clientMsgId)) {
      imSendRejected.add(1)
    }
  } else if (frame.type === 'ack') {
    if (sends.match(frame.clientMsgId)) {
      imSendAcked.add(1)
    }
  } else if (frame.type === 'committed') {
    if (sends.settle(frame.clientMsgId)) {
      imSendCommitted.add(1)
    }
  }
}

export function runImWebSocket(accessToken) {
  const session = openImSession(accessToken)
  if (!session.ticket || !session.wsUrl) {
    imRejected.add(1)
    return
  }

  const state = { connected: false, pong: false, connectRejected: false }
  const sends = createSendTracker()
  const sendEnabled = Boolean(config.imSendMessages && config.imRoomId)

  const response = ws.connect(session.wsUrl, {
    tags: { type: 'ws', endpoint: '/ws/im' }
  }, (socket) => {
    socket.on('open', () => {
      socket.send(JSON.stringify(buildConnectFrame(session.ticket)))
    })

    socket.on('message', (raw) => handleImFrame(raw, state, sends))

    socket.setInterval(() => {
      socket.send(JSON.stringify(buildPingFrame()))
    }, Math.max(1, config.imPingIntervalSeconds) * 1000)

    if (sendEnabled) {
      socket.setInterval(() => {
        // Sends before the connected frame are rejected with connect_required.
        if (!state.connected) {
          return
        }
        const clientMsgId = `k6-${__VU}-${__ITER}-${Date.now()}`
        sends.record(clientMsgId)
        socket.send(JSON.stringify(buildSendRoomTextFrame({
          clientMsgId,
          roomId: config.imRoomId,
          content: `k6 room message ${Date.now()}`
        })))
      }, Math.max(2, config.imPingIntervalSeconds) * 1000)
    }

    socket.setTimeout(() => {
      socket.close()
    }, Math.max(1, config.imHoldSeconds) * 1000)
  })

  check(response, {
    'WebSocket handshake status is 101': (res) => res && res.status === 101,
    'IM connect accepted': () => state.connected,
    'IM pong observed': () => state.pong,
    'IM connect not rejected': () => !state.connectRejected
  })
}
