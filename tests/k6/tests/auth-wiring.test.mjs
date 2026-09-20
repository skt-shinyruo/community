import assert from 'node:assert/strict'
import { register } from 'node:module'
import { dirname, join } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'
import { describe, it } from 'node:test'

// Exercises the real wiring: lib/auth.js registers its recovery with
// lib/http.js at import time, and the http verbs re-login + retry once on
// 401. The k6 runtime modules are stubbed via a resolve hook (node --test
// runs each file in its own child process, so the hook is registered here);
// everything else (authRetry.js, http.js request building, auth.js session +
// registration) is the production code path.
//
// The token session is module state shared across tests (as across a VU
// lifetime), so tests accept whatever token() currently holds and reason
// about login counts and response outcomes instead of token literals.
register(pathToFileURL(join(dirname(fileURLToPath(import.meta.url)), 'k6-stub-hooks.mjs')))

globalThis.__ENV = {}
globalThis.__k6StubHttp = { loginCount: 0, responses: [], putHeaders: [] }
const httpState = globalThis.__k6StubHttp

const { token, authenticatedParams } = await import('../lib/auth.js')
const { get, putJson } = await import('../lib/http.js')

describe('k6 auth/http 401 recovery wiring', () => {
  it('re-logins once and retries the authenticated request with the fresh token', () => {
    httpState.responses = [{ status: 401 }, { status: 200 }]
    const stale = token()
    const loginsAtStart = httpState.loginCount

    const response = get('/api/auth/me', authenticatedParams(stale))

    assert.equal(response.status, 200)
    // Exactly one re-login on top of whatever the session already did.
    assert.equal(httpState.loginCount, loginsAtStart + 1)
  })

  it('shares one re-login across several 401s carrying the same stale token', () => {
    httpState.responses = [{ status: 401 }, { status: 200 }, { status: 401 }, { status: 200 }]
    const stale = token()
    const loginsAtStart = httpState.loginCount

    const params = authenticatedParams(stale)
    const first = get('/api/notices/summary', params)
    const second = get('/api/wallet/summary', params)

    assert.equal(first.status, 200)
    assert.equal(second.status, 200)
    // A single shared re-login, despite two 401s on the same stale token.
    assert.equal(httpState.loginCount, loginsAtStart + 1)
  })

  it('recovers 401s on write verbs too, preserving the Idempotency-Key', () => {
    httpState.responses = [{ status: 401 }, { status: 200 }]
    httpState.putHeaders = []
    const stale = token()

    const response = putJson('/api/posts/x/bookmark', {}, authenticatedParams(stale, {
      'Idempotency-Key': 'post-1'
    }))

    assert.equal(response.status, 200)
    assert.equal(httpState.putHeaders.length, 2)
    assert.equal(httpState.putHeaders[1]['Idempotency-Key'], 'post-1')
    assert.notEqual(httpState.putHeaders[1].Authorization, httpState.putHeaders[0].Authorization)
  })

  it('does not recover a 401 on an unauthenticated request', () => {
    httpState.responses = [{ status: 401 }]
    const loginsAtStart = httpState.loginCount

    const response = get('/api/some/public/endpoint')

    assert.equal(response.status, 401)
    assert.equal(httpState.loginCount, loginsAtStart)
  })
})
