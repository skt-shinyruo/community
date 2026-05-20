import assert from 'node:assert/strict'
import { describe, it } from 'node:test'
import { compactK6Params } from '../lib/request-params.js'

describe('k6 request parameter helpers', () => {
  it('drops undefined optional request params before calling k6/http', () => {
    assert.deepEqual(
      compactK6Params({
        headers: undefined,
        tags: { type: 'api', endpoint: '/actuator/health' },
        timeout: undefined,
        redirects: 0,
        cookies: undefined
      }),
      {
        tags: { type: 'api', endpoint: '/actuator/health' },
        redirects: 0
      }
    )
  })
})
