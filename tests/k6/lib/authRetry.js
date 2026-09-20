// Bearer token acquisition and 401 recovery policy for the k6 suite.
// Pure module (no k6 imports) so node --test can exercise the contract.
// Policy: one login per VU, cached; an authenticated request that answers 401
// is retried exactly once after a forced re-login, mirroring the frontend's
// 401 -> refresh -> single retry rule (docs/handbook/security.md).
export function createTokenSession(login) {
  let cachedToken

  return {
    // force: re-login and replace the cache (401 recovery; also honored by
    // loginOnEveryIteration-style callers that pass it every iteration).
    get(force = false) {
      if (force || !cachedToken) {
        cachedToken = login()
      }
      return cachedToken
    }
  }
}

// Central 401 recovery for authenticated requests: re-login, then retry the
// request exactly once with the fresh bearer token. Unauthenticated requests,
// non-401 responses, and a missing recovery callback pass the original
// response through. Caller-supplied headers other than Authorization are
// preserved (retries reuse the same Idempotency-Key, so the retry is the same
// business attempt, not a new one).
export function withAuthRecovery(send, request, recover) {
  const response = send(request)
  if (response.status !== 401 || typeof recover !== 'function' || !request || !request.headers || !request.headers.Authorization) {
    return response
  }

  const fresh = recover()
  if (!fresh) {
    return response
  }

  return send({
    ...request,
    headers: {
      ...request.headers,
      Authorization: `Bearer ${fresh}`
    }
  })
}
