export const defaultBaseUrl = 'http://localhost:12880'

function env(name, fallback = '') {
  const value = __ENV[name]
  return value === undefined || value === '' ? fallback : value
}

function intEnv(name, fallback) {
  const raw = env(name, String(fallback))
  const parsed = Number.parseInt(raw, 10)
  return Number.isFinite(parsed) ? parsed : fallback
}

function boolEnv(name, fallback = false) {
  const raw = env(name, fallback ? 'true' : 'false').toLowerCase()
  return raw === '1' || raw === 'true' || raw === 'yes'
}

export function loadConfig() {
  const baseUrl = env('K6_BASE_URL', defaultBaseUrl).replace(/\/+$/, '')
  const derivedWsUrl = baseUrl.replace(/^http:/, 'ws:').replace(/^https:/, 'wss:') + '/ws/im'

  return {
    baseUrl,
    wsUrl: env('K6_WS_URL', derivedWsUrl),
    username: env('K6_USERNAME', 'aaa'),
    password: env('K6_PASSWORD', 'aaa'),
    secondaryUsername: env('K6_SECONDARY_USERNAME', 'bbb'),
    secondaryPassword: env('K6_SECONDARY_PASSWORD', 'aaa'),
    adminUsername: env('K6_ADMIN_USERNAME', 'admin'),
    adminPassword: env('K6_ADMIN_PASSWORD', 'aaa'),
    loginOnEveryIteration: boolEnv('K6_LOGIN_EACH_ITERATION', false),
    writeRatio: intEnv('K6_WRITE_RATIO', 10),
    readSize: intEnv('K6_READ_SIZE', 20),
    thinkMinMs: intEnv('K6_THINK_MIN_MS', 100),
    thinkMaxMs: intEnv('K6_THINK_MAX_MS', 500),
    imHoldSeconds: intEnv('K6_IM_HOLD_SECONDS', 20),
    imPingIntervalSeconds: intEnv('K6_IM_PING_INTERVAL_SECONDS', 5),
    imSendMessages: boolEnv('K6_IM_SEND_MESSAGES', false),
    imRoomId: env('K6_IM_ROOM_ID', ''),
    postTag: env('K6_POST_TAG', 'k6'),
    postCategoryId: env('K6_POST_CATEGORY_ID', ''),
    allowWrites: boolEnv('K6_ALLOW_WRITES', true),
    allowDestructiveWrites: boolEnv('K6_ALLOW_DESTRUCTIVE_WRITES', false)
  }
}

export const config = loadConfig()
