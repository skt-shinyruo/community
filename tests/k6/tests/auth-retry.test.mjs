import assert from 'node:assert/strict'
import { describe, it } from 'node:test'

const { createTokenSession, withAuthRecovery } = await import('../lib/authRetry.js')

function responseOf(status) {
  return { status }
}

describe('k6 token session', () => {
  it('logs in once and reuses the cached token', () => {
    let logins = 0
    const session = createTokenSession(() => `token-${++logins}`)

    assert.equal(session.get(), 'token-1')
    assert.equal(session.get(), 'token-1')
    assert.equal(logins, 1)
  })

  it('re-logins and replaces the cache on forced refresh', () => {
    let logins = 0
    const session = createTokenSession(() => `token-${++logins}`)

    assert.equal(session.get(), 'token-1')
    assert.equal(session.get(true), 'token-2')
    assert.equal(session.get(), 'token-2')
  })

  it('keeps retrying the login after a failed login', () => {
    const results = ['', 'token-2']
    const session = createTokenSession(() => results.shift())

    assert.equal(session.get(), '')
    assert.equal(session.get(), 'token-2')
  })
})

describe('k6 token session 401 refresh', () => {
  it('shares one re-login across requests failing with the same stale token', () => {
    let logins = 0
    const session = createTokenSession(() => `token-${++logins}`)

    assert.equal(session.get(), 'token-1')
    assert.equal(session.refresh('token-1'), 'token-2')
    assert.equal(session.refresh('token-1'), 'token-2')
    assert.equal(session.refresh('token-1'), 'token-2')
    assert.equal(logins, 2)
  })

  it('re-logins again when a newer token also goes stale', () => {
    let logins = 0
    const session = createTokenSession(() => `token-${++logins}`)

    assert.equal(session.get(), 'token-1')
    assert.equal(session.refresh('token-1'), 'token-2')
    assert.equal(session.refresh('token-2'), 'token-3')
    assert.equal(logins, 3)
  })
})

describe('k6 401 auth recovery', () => {
  it('re-logins and retries once with the fresh bearer token', () => {
    const seenHeaders = []
    const responses = [responseOf(401), responseOf(200)]
    const response = withAuthRecovery(
      (request) => {
        seenHeaders.push(request.headers)
        return responses.shift()
      },
      { headers: { Authorization: 'Bearer stale', 'Idempotency-Key': 'post-1' } },
      () => 'fresh'
    )

    assert.equal(response.status, 200)
    assert.equal(seenHeaders.length, 2)
    assert.equal(seenHeaders[1].Authorization, 'Bearer fresh')
    assert.equal(seenHeaders[1]['Idempotency-Key'], 'post-1')
  })

  it('surfaces the original 401 when re-login fails', () => {
    let sends = 0
    const response = withAuthRecovery(
      () => {
        sends += 1
        return responseOf(401)
      },
      { headers: { Authorization: 'Bearer stale' } },
      () => ''
    )

    assert.equal(response.status, 401)
    assert.equal(sends, 1)
  })

  it('does not recover a 401 on unauthenticated requests', () => {
    let sends = 0
    const response = withAuthRecovery(
      () => {
        sends += 1
        return responseOf(401)
      },
      {},
      () => 'fresh'
    )

    assert.equal(response.status, 401)
    assert.equal(sends, 1)
  })

  it('does not recover non-401 responses', () => {
    let sends = 0
    const response = withAuthRecovery(
      () => {
        sends += 1
        return responseOf(500)
      },
      { headers: { Authorization: 'Bearer stale' } },
      () => 'fresh'
    )

    assert.equal(response.status, 500)
    assert.equal(sends, 1)
  })

  it('passes through when no recovery is registered', () => {
    let sends = 0
    const response = withAuthRecovery(
      () => {
        sends += 1
        return responseOf(401)
      },
      { headers: { Authorization: 'Bearer stale' } },
      null
    )

    assert.equal(response.status, 401)
    assert.equal(sends, 1)
  })
})
