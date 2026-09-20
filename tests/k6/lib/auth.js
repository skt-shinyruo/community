import { check } from 'k6'
import { config } from './config.js'
import { authHeaders, postJson, resultData, setAuthRecovery } from './http.js'
import { loginFailures } from './metrics.js'
import { createTokenSession } from './authRetry.js'

export function login(username = config.username, password = config.password) {
  const response = postJson('/api/auth/login', {
    username,
    password,
    captchaId: null,
    captchaCode: null
  }, {}, [200, 401, 429])

  const data = resultData(response, {})
  const token = data && data.accessToken
  const ok = response.status === 200 && typeof token === 'string' && token.length > 20
  check(response, {
    'login returns access token': () => ok
  })
  if (!ok) {
    loginFailures.add(1)
    return ''
  }
  return token
}

// One login per VU, cached; the cache is force-refreshed on the first 401
// from any authenticated request (recovery wired in lib/http.js).
const session = createTokenSession(login)

setAuthRecovery(() => session.get(true))

export function token() {
  return session.get(config.loginOnEveryIteration)
}

export function authenticatedParams(accessToken, extraHeaders = {}) {
  return {
    headers: authHeaders(accessToken, extraHeaders)
  }
}
