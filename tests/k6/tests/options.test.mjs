import assert from 'node:assert/strict'
import { describe, it } from 'node:test'

globalThis.__ENV = {}

const { buildOptions } = await import('../config/options.js')

describe('k6 option builder', () => {
  it('does not apply WebSocket session thresholds to non-WebSocket profiles', () => {
    const options = buildOptions('smoke')

    assert.equal(Object.prototype.hasOwnProperty.call(options.thresholds, 'ws_connecting'), false)
    assert.equal(Object.prototype.hasOwnProperty.call(options.thresholds, 'ws_session_duration'), false)
  })

  it('applies WebSocket thresholds to the IM WebSocket profile', () => {
    const options = buildOptions('im-ws')

    assert.ok(options.thresholds.ws_connecting)
    assert.ok(options.thresholds.ws_session_duration)
  })

  it('requires application-layer IM frames in the im-ws profile', () => {
    const options = buildOptions('im-ws')

    assert.deepEqual(options.thresholds.community_im_connected, ['count>0'])
    assert.deepEqual(options.thresholds.community_im_pong, ['count>0'])
    assert.deepEqual(options.thresholds.community_im_rejected, ['count<1'])
  })

  it('omits send-correlation thresholds unless send mode is enabled', () => {
    globalThis.__ENV = {}

    const options = buildOptions('im-ws')

    assert.equal(Object.prototype.hasOwnProperty.call(options.thresholds, 'community_im_send_committed'), false)
    assert.equal(Object.prototype.hasOwnProperty.call(options.thresholds, 'community_im_send_rejected'), false)
  })

  it('omits send-correlation thresholds when no room id is configured', () => {
    globalThis.__ENV = { K6_IM_SEND_MESSAGES: 'true' }

    const options = buildOptions('im-ws')

    assert.equal(Object.prototype.hasOwnProperty.call(options.thresholds, 'community_im_send_committed'), false)
    assert.equal(Object.prototype.hasOwnProperty.call(options.thresholds, 'community_im_send_rejected'), false)

    globalThis.__ENV = {}
  })

  it('requires send correlation when send mode is enabled', () => {
    globalThis.__ENV = { K6_IM_SEND_MESSAGES: 'true', K6_IM_ROOM_ID: 'room-uuid' }

    const options = buildOptions('im-ws')

    assert.deepEqual(options.thresholds.community_im_send_committed, ['count>0'])
    assert.deepEqual(options.thresholds.community_im_send_rejected, ['count<1'])

    globalThis.__ENV = {}
  })
})
