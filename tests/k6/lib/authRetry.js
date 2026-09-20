// Bearer token acquisition and 401 recovery policy for the k6 suite.
// Pure module (no k6 imports) so node --test can exercise the contract.
// Policy: one login per VU, cached; an authenticated request that answers 401
// is retried exactly once after re-login, mirroring the frontend's
// 401 -> refresh -> single retry rule (docs/handbook/security.md).
export function createTokenSession(login) {
  let cachedToken

  return {
    // force: re-login and replace the cache unconditionally
    // (loginOnEveryIteration-style callers).
    get(force = false) {
      if (force || !cachedToken) {
        cachedToken = login()
      }
      return cachedToken
    },
    // Re-login only while the cache still holds the failed token: requests
    // 2..N hitting 401 inside the same expiry window share one fresh login.
    refresh(staleToken) {
      if (cachedToken && staleToken && cachedToken !== staleToken) {
        return cachedToken
      }
      cachedToken = login()
      return cachedToken
    }
  }
}

// Central 401 recovery for authenticated requests: ask recover(staleToken) for
// a usable token, then retry the request exactly once with the fresh bearer
// token. Unauthenticated requests, non-401 responses and a missing recovery
// callback pass the original response through. Caller-supplied headers other
// than Authorization are preserved, so retries reuse the same Idempotency-Key —
// the retry is the same business attempt, not a new one.
export function withAuthRecovery(send, request, recover) {
  const response = send(request)
  const bearer = request && request.headers && request.headers.Authorization
  if (response.status !== 401 || typeof recover !== 'function' || !bearer) {
    return response
  }

  const fresh = recover(String(bearer).replace(/^Bearer\s+/, ''))
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
