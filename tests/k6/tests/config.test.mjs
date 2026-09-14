import assert from 'node:assert/strict'
import { describe, it } from 'node:test'

globalThis.__ENV = {}

const { loadConfig } = await import('../lib/config.js')

describe('k6 config loader', () => {
  it('leaves wsUrl empty by default so the session bootstrap url wins', () => {
    globalThis.__ENV = {}

    assert.equal(loadConfig().wsUrl, '')
  })

  it('uses K6_WS_URL only as an explicit override', () => {
    globalThis.__ENV = { K6_WS_URL: 'ws://custom:13880/ws/im' }

    assert.equal(loadConfig().wsUrl, 'ws://custom:13880/ws/im')
  })
})
