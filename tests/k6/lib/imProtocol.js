// IM WebSocket application protocol helpers for the k6 suite.
// Pure module (no k6 imports) so node --test can exercise the contract;
// frame shapes mirror backend im-common ws records (connect/ping/sendRoomText,
// connected/pong/ack/committed/reject).
export const IM_SCHEMA_VERSION = 1

export function buildConnectFrame(ticket) {
  return {
    type: 'connect',
    ticket: String(ticket || ''),
    schemaVersion: IM_SCHEMA_VERSION
  }
}

export function buildPingFrame(sentAtEpochMillis = Date.now()) {
  return {
    type: 'ping',
    sentAtEpochMillis,
    schemaVersion: IM_SCHEMA_VERSION
  }
}

export function buildSendRoomTextFrame({ clientMsgId, roomId, content }) {
  return {
    type: 'sendRoomText',
    clientMsgId: String(clientMsgId || ''),
    roomId: String(roomId || ''),
    content: String(content || ''),
    schemaVersion: IM_SCHEMA_VERSION
  }
}

// Session bootstrap is the default route: the server issues the WebSocket URL.
// An explicit K6_WS_URL is the only override.
export function resolveWsUrl(overrideUrl, bootstrapUrl) {
  const override = String(overrideUrl || '').trim()
  return override || String(bootstrapUrl || '').trim()
}

export function parseFrame(raw) {
  try {
    const frame = JSON.parse(raw)
    if (frame && typeof frame === 'object' && !Array.isArray(frame)) {
      return frame
    }
  } catch (_) {
    // fall through
  }
  return null
}

export function hasCurrentSchemaVersion(frame) {
  return Boolean(frame) && frame.schemaVersion === IM_SCHEMA_VERSION
}

// Correlates optional send frames with their ack/committed/reject outcomes by
// clientMsgId. ack is non-terminal (committed may still follow); committed and
// reject are terminal and settle the send exactly once.
export function createSendTracker() {
  const pending = new Set()
  return {
    record(clientMsgId) {
      pending.add(String(clientMsgId))
    },
    match(clientMsgId) {
      return pending.has(String(clientMsgId))
    },
    settle(clientMsgId) {
      return pending.delete(String(clientMsgId))
    },
    get inFlight() {
      return pending.size
    }
  }
}
