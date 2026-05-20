import http from 'k6/http'
import { check, sleep } from 'k6'
import { config } from './config.js'
import { recordUnexpected } from './metrics.js'
import { compactK6Params } from './request-params.js'

export function jsonHeaders(extra = {}) {
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

export function parseJson(response, fallback = null) {
  try {
    return response.json()
  } catch (_) {
    return fallback
  }
}

export function resultData(response, fallback = null) {
  const body = parseJson(response)
  if (body && Object.prototype.hasOwnProperty.call(body, 'data')) {
    return body.data
  }
  return body === null ? fallback : body
}

export function expectStatus(response, expected, name) {
  const expectedSet = Array.isArray(expected) ? expected : [expected]
  const ok = expectedSet.includes(response.status)
  recordUnexpected(ok)
  check(response, {
    [name || `status is ${expectedSet.join(' or ')}`]: () => ok
  })
  return ok
}

function requestOptions(path, params = {}, headers = undefined) {
  const requestParams = params || {}
  return compactK6Params({
    headers: headers || requestParams.headers,
    tags: requestParams.tags || { type: 'api', endpoint: path },
    timeout: requestParams.timeout,
    redirects: requestParams.redirects,
    cookies: requestParams.cookies,
    jar: requestParams.jar,
    compression: requestParams.compression,
    responseType: requestParams.responseType
  })
}

export function get(path, params = {}, expected = 200) {
  const response = http.get(url(path), requestOptions(path, params))
  expectStatus(response, expected, `GET ${path} returned ${Array.isArray(expected) ? expected.join('/') : expected}`)
  return response
}

export function postJson(path, body, params = {}, expected = 200) {
  const requestParams = params || {}
  const response = http.post(url(path), JSON.stringify(body || {}), requestOptions(path, params, jsonHeaders(requestParams.headers || {})))
  expectStatus(response, expected, `POST ${path} returned ${Array.isArray(expected) ? expected.join('/') : expected}`)
  return response
}

export function putJson(path, body, params = {}, expected = 200) {
  const requestParams = params || {}
  const response = http.put(url(path), JSON.stringify(body || {}), requestOptions(path, params, jsonHeaders(requestParams.headers || {})))
  expectStatus(response, expected, `PUT ${path} returned ${Array.isArray(expected) ? expected.join('/') : expected}`)
  return response
}

export function deletePath(path, params = {}, expected = 200) {
  const response = http.del(url(path), null, requestOptions(path, params))
  expectStatus(response, expected, `DELETE ${path} returned ${Array.isArray(expected) ? expected.join('/') : expected}`)
  return response
}

export function randomThinkTime(minMs = config.thinkMinMs, maxMs = config.thinkMaxMs) {
  const min = Math.max(0, minMs)
  const max = Math.max(min, maxMs)
  sleep((min + Math.random() * (max - min)) / 1000)
}
