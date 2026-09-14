import assert from 'node:assert/strict'
import { describe, it } from 'node:test'

const {
  IM_SCHEMA_VERSION,
  buildConnectFrame,
  buildPingFrame,
  buildSendRoomTextFrame,
  createSendTracker,
  hasCurrentSchemaVersion,
  parseFrame,
  resolveWsUrl
} = await import('../lib/imProtocol.js')

describe('im protocol frame builders', () => {
  it('stamps the current schema version on connect frames', () => {
    const frame = buildConnectFrame('ticket-abc')

    assert.equal(frame.type, 'connect')
    assert.equal(frame.ticket, 'ticket-abc')
    assert.equal(frame.schemaVersion, IM_SCHEMA_VERSION)
  })

  it('stamps the current schema version on ping frames', () => {
    const frame = buildPingFrame(1720000000000)

    assert.equal(frame.type, 'ping')
    assert.equal(frame.sentAtEpochMillis, 1720000000000)
    assert.equal(frame.schemaVersion, IM_SCHEMA_VERSION)
  })

  it('stamps the current schema version on room send frames', () => {
    const frame = buildSendRoomTextFrame({
      clientMsgId: 'k6-1-2-3',
      roomId: 'room-uuid',
      content: 'hello'
    })

    assert.equal(frame.type, 'sendRoomText')
    assert.equal(frame.clientMsgId, 'k6-1-2-3')
    assert.equal(frame.roomId, 'room-uuid')
    assert.equal(frame.content, 'hello')
    assert.equal(frame.schemaVersion, IM_SCHEMA_VERSION)
  })
})

describe('im protocol inbound frames', () => {
  it('parses JSON object frames and rejects non-object payloads', () => {
    assert.deepEqual(parseFrame('{"type":"pong"}'), { type: 'pong' })
    assert.equal(parseFrame('not-json'), null)
    assert.equal(parseFrame('[1,2]'), null)
    assert.equal(parseFrame('42'), null)
  })

  it('only accepts frames carrying the current schema version', () => {
    assert.equal(hasCurrentSchemaVersion({ type: 'pong', schemaVersion: IM_SCHEMA_VERSION }), true)
    assert.equal(hasCurrentSchemaVersion({ type: 'pong' }), false)
    assert.equal(hasCurrentSchemaVersion({ type: 'pong', schemaVersion: IM_SCHEMA_VERSION + 1 }), false)
    assert.equal(hasCurrentSchemaVersion({ type: 'pong', schemaVersion: String(IM_SCHEMA_VERSION) }), false)
    assert.equal(hasCurrentSchemaVersion(null), false)
  })
})

describe('im websocket url resolution', () => {
  it('defaults to the session bootstrap url', () => {
    assert.equal(resolveWsUrl('', 'ws://gateway/ws/im?worker=a'), 'ws://gateway/ws/im?worker=a')
    assert.equal(resolveWsUrl(null, 'ws://gateway/ws/im'), 'ws://gateway/ws/im')
    assert.equal(resolveWsUrl(undefined, 'ws://gateway/ws/im'), 'ws://gateway/ws/im')
    assert.equal(resolveWsUrl('   ', 'ws://gateway/ws/im'), 'ws://gateway/ws/im')
  })

  it('honours an explicit override over the bootstrap url', () => {
    assert.equal(resolveWsUrl('ws://custom/ws/im', 'ws://gateway/ws/im'), 'ws://custom/ws/im')
  })

  it('returns an empty url when neither source provides one', () => {
    assert.equal(resolveWsUrl('', ''), '')
    assert.equal(resolveWsUrl(null, null), '')
  })
})

describe('im send tracker', () => {
  it('correlates ack frames with recorded sends only', () => {
    const tracker = createSendTracker()
    tracker.record('cmid-1')

    assert.equal(tracker.match('cmid-1'), true)
    assert.equal(tracker.match('cmid-unknown'), false)
  })

  it('keeps sends in flight after ack and settles them once', () => {
    const tracker = createSendTracker()
    tracker.record('cmid-1')

    assert.equal(tracker.match('cmid-1'), true, 'ack must not settle the send')
    assert.equal(tracker.inFlight, 1)
    assert.equal(tracker.settle('cmid-1'), true)
    assert.equal(tracker.inFlight, 0)
    assert.equal(tracker.settle('cmid-1'), false, 'second terminal frame must not double-count')
    assert.equal(tracker.settle('cmid-unknown'), false)
  })
})
