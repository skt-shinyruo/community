import { check } from 'k6'
import { config } from './config.js'
import { authHeaders, postJson, resultData } from './http.js'
import { loginFailures } from './metrics.js'

let cachedToken
let cachedSecondaryToken

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

export function token() {
  if (config.loginOnEveryIteration || !cachedToken) {
    cachedToken = login()
  }
  return cachedToken
}

export function secondaryToken() {
  if (config.loginOnEveryIteration || !cachedSecondaryToken) {
    cachedSecondaryToken = login(config.secondaryUsername, config.secondaryPassword)
  }
  return cachedSecondaryToken
}

export function authenticatedParams(accessToken, extraHeaders = {}) {
  return {
    headers: authHeaders(accessToken, extraHeaders)
  }
}
