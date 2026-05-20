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
})
