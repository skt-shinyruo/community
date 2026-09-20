import http from 'k6/http'
import { check, sleep } from 'k6'
import { config } from './config.js'
import { recordUnexpected } from './metrics.js'
import { withAuthRecovery } from './authRetry.js'

// Registered by lib/auth.js at import time: re-login callback used by
// withAuthRecovery when an authenticated request answers 401.
let authRecovery

export function setAuthRecovery(recover) {
  authRecovery = recover
}
function jsonHeaders(extra = {}) {
  const headers = {
    'Content-Type': 'application/json',
    Accept: 'application/json'
  }
  Object.keys(extra || {}).forEach((key) => {
    headers[key] = extra[key]
  })
  return headers
}

export function authHeaders(token, extra = {}) {
  const headers = { Authorization: `Bearer ${token}` }
  Object.keys(extra || {}).forEach((key) => {
    headers[key] = extra[key]
  })
  return jsonHeaders(headers)
}

export function idempotencyKey(prefix = 'k6') {
  return `${prefix}-${__VU}-${__ITER}-${Date.now()}-${Math.random().toString(16).slice(2)}`
}

export function url(path) {
  if (path.startsWith('http://') || path.startsWith('https://')) {
    return path
  }
  return `${config.baseUrl}${path.startsWith('/') ? path : `/${path}`}`
}

function parseJson(response, fallback = null) {
  try {
    return response.json()
  } catch (_) {
    return fallback
  }
}

/**
 * Extracts the `data` member of a Result envelope.
 * @param {*} response
 * @param {*} [fallback=null]
 * @returns {*}
 */
export function resultData(response, fallback = null) {
  const body = parseJson(response)
  if (body && Object.prototype.hasOwnProperty.call(body, 'data')) {
    return body.data
  }
  return body === null ? fallback : body
}

function expectStatus(response, expected, name) {
  const expectedSet = Array.isArray(expected) ? expected : [expected]
  const ok = expectedSet.includes(response.status)
  recordUnexpected(ok)
  check(response, {
    [name || `status is ${expectedSet.join(' or ')}`]: () => ok
  })
  return ok
}

/**
 * @param {string} path
 * @param {*} [params={}]
 * @param {Record<string, string>} [headers]
 */
function requestOptions(path, params = {}, headers = undefined) {
  const requestParams = params || {}
  const merged = {
    headers: headers || requestParams.headers,
    tags: requestParams.tags || { type: 'api', endpoint: path }
  }
  return Object.fromEntries(Object.entries(merged).filter(([, value]) => value != null))
}

export function get(path, params = {}, expected = 200) {
  const response = withAuthRecovery((request) => http.get(url(path), request), requestOptions(path, params), authRecovery)
  expectStatus(response, expected, `GET ${path} returned ${Array.isArray(expected) ? expected.join('/') : expected}`)
  return response
}
/**
 * @param {string} path
 * @param {*} body
 * @param {*} [params={}]
 * @param {number|number[]} [expected=200]
 * @returns {import('../types/k6/http').Response}
 */
export function postJson(path, body, params = {}, expected = 200) {
  const requestParams = params || {}
  const response = withAuthRecovery(
    (request) => http.post(url(path), JSON.stringify(body || {}), request),
    { ...requestOptions(path, requestParams), headers: jsonHeaders(requestParams.headers || {}) },
    authRecovery
  )
  expectStatus(response, expected, `POST ${path} returned ${Array.isArray(expected) ? expected.join('/') : expected}`)
  return response
}
/**
 * @param {string} path
 * @param {*} body
 * @param {*} [params={}]
 * @param {number|number[]} [expected=200]
 * @returns {import('../types/k6/http').Response}
 */
export function putJson(path, body, params = {}, expected = 200) {
  const requestParams = params || {}
  const response = withAuthRecovery(
    (request) => http.put(url(path), JSON.stringify(body || {}), request),
    { ...requestOptions(path, requestParams), headers: jsonHeaders(requestParams.headers || {}) },
    authRecovery
  )
  expectStatus(response, expected, `PUT ${path} returned ${Array.isArray(expected) ? expected.join('/') : expected}`)
  return response
}

export function randomThinkTime(minMs = config.thinkMinMs, maxMs = config.thinkMaxMs) {
  const min = Math.max(0, minMs)
  const max = Math.max(min, maxMs)
  sleep((min + Math.random() * (max - min)) / 1000)
}
