import ws from 'k6/ws'
import { check } from 'k6'
import { config } from './config.js'
import { authenticatedParams } from './auth.js'
import { postJson, resultData } from './http.js'
import { imConnected, imRejected } from './metrics.js'

export function openImSession(accessToken) {
  const response = postJson('/api/im/sessions', {}, authenticatedParams(accessToken), 200)
  const data = resultData(response, {})
  check(response, {
    'IM session returns ticket': () => typeof data.ticket === 'string' && data.ticket.length > 20,
    'IM session returns wsUrl': () => typeof data.wsUrl === 'string' && data.wsUrl.startsWith('ws')
  })
  return {
    sessionId: data.sessionId,
    ticket: data.ticket,
    wsUrl: config.wsUrl || data.wsUrl
  }
}

export function runImWebSocket(accessToken) {
  const session = openImSession(accessToken)
  if (!session.ticket || !session.wsUrl) {
    imRejected.add(1)
    return
  }

  const response = ws.connect(session.wsUrl, {
    tags: { type: 'ws', endpoint: '/ws/im' }
  }, (socket) => {
    socket.on('open', () => {
      socket.send(JSON.stringify({ type: 'connect', ticket: session.ticket }))
    })

    socket.on('message', (raw) => {
      let frame = {}
      try {
        frame = JSON.parse(raw)
      } catch (_) {
        return
      }
      if (frame.type === 'connected') {
        imConnected.add(1)
      }
      if (frame.type === 'reject') {
        imRejected.add(1)
      }
    })

    socket.setInterval(() => {
      socket.send(JSON.stringify({ type: 'ping', sentAtEpochMillis: Date.now() }))
    }, Math.max(1, config.imPingIntervalSeconds) * 1000)

    if (config.imSendMessages && config.imRoomId) {
      socket.setInterval(() => {
        socket.send(JSON.stringify({
          type: 'sendRoomText',
          clientMsgId: `k6-${__VU}-${__ITER}-${Date.now()}`,
          roomId: config.imRoomId,
          content: `k6 room message ${Date.now()}`
        }))
      }, Math.max(2, config.imPingIntervalSeconds) * 1000)
    }

    socket.setTimeout(() => {
      socket.close()
    }, Math.max(1, config.imHoldSeconds) * 1000)
  })

  check(response, {
    'WebSocket handshake status is 101': (res) => res && res.status === 101
  })
}
