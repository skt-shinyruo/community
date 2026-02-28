import http from 'k6/http'
import { check, sleep } from 'k6'

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080'
const USERNAME = __ENV.USERNAME || 'aaa'
const PASSWORD = __ENV.PASSWORD || 'aaa'
const TO_NAME = __ENV.TO_NAME || 'bbb'
const DURATION = __ENV.DURATION || '30s'
const VUS = parseInt(__ENV.VUS || '5', 10)

function randomText(prefix) {
  const n = Math.floor(Math.random() * 1_000_000)
  return `${prefix}-${Date.now()}-${n}`
}

function jsonHeaders(token) {
  const headers = { 'Content-Type': 'application/json' }
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }
  return { headers }
}

function login() {
  const resp = http.post(
    `${BASE_URL}/api/auth/login`,
    JSON.stringify({ username: USERNAME, password: PASSWORD }),
    jsonHeaders()
  )
  check(resp, { 'login status=200': (r) => r.status === 200 })
  const body = resp.json()
  const token = body?.data?.accessToken || ''
  check(token, { 'login has accessToken': (t) => !!t })
  return token
}

function createPost(token) {
  const title = randomText('k6-title')
  const content = randomText('k6-content')
  const resp = http.post(
    `${BASE_URL}/api/posts`,
    JSON.stringify({ title, content }),
    jsonHeaders(token)
  )
  check(resp, { 'create post status=200': (r) => r.status === 200 })
  const body = resp.json()
  return body?.data?.postId || 0
}

export const options = {
  scenarios: {
    login: { executor: 'constant-vus', vus: VUS, duration: DURATION, exec: 'scenarioLogin' },
    post: { executor: 'constant-vus', vus: VUS, duration: DURATION, exec: 'scenarioPost' },
    like: { executor: 'constant-vus', vus: VUS, duration: DURATION, exec: 'scenarioLike' },
    search: { executor: 'constant-vus', vus: VUS, duration: DURATION, exec: 'scenarioSearch' },
    message: { executor: 'constant-vus', vus: VUS, duration: DURATION, exec: 'scenarioMessage' }
  },
  thresholds: {
    http_req_failed: ['rate<0.01']
  }
}

export function setup() {
  const token = login()
  const postId = createPost(token)
  return { token, postId }
}

export function scenarioLogin() {
  login()
  sleep(0.2)
}

export function scenarioPost(data) {
  const token = data?.token || login()

  const listResp = http.get(`${BASE_URL}/api/posts?page=0&size=10`)
  check(listResp, { 'list posts status=200': (r) => r.status === 200 })

  const postId = createPost(token)
  check(postId, { 'postId > 0': (v) => v > 0 })
  sleep(0.2)
}

export function scenarioLike(data) {
  const token = data?.token || login()
  const postId = data?.postId || 1

  const req = {
    entityType: 1,
    entityId: postId,
    entityUserId: 1,
    postId,
    liked: null
  }

  const resp = http.post(`${BASE_URL}/api/likes`, JSON.stringify(req), jsonHeaders(token))
  check(resp, { 'like status=200': (r) => r.status === 200 })
  sleep(0.2)
}

export function scenarioSearch() {
  const resp = http.get(`${BASE_URL}/api/search/posts?keyword=k6&page=0&size=10`)
  check(resp, { 'search status=200': (r) => r.status === 200 })
  sleep(0.2)
}

export function scenarioMessage(data) {
  const token = data?.token || login()
  const payload = { toName: TO_NAME, content: randomText('k6-msg') }
  const resp = http.post(`${BASE_URL}/api/messages`, JSON.stringify(payload), jsonHeaders(token))
  check(resp, { 'send message status=200': (r) => r.status === 200 })
  sleep(0.2)
}

