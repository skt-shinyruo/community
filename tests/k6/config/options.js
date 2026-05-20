import { profileFor } from './profiles.js'

function numberFromEnv(name, fallback) {
  const raw = __ENV[name]
  if (raw === undefined || raw === '') {
    return fallback
  }
  const parsed = Number(raw)
  return Number.isFinite(parsed) ? parsed : fallback
}

function mergeObjects(base, overrides) {
  const result = {}
  const baseValue = base || {}
  const overrideValue = overrides || {}
  Object.keys(baseValue).forEach((key) => {
    result[key] = baseValue[key]
  })
  Object.keys(overrideValue).forEach((key) => {
    result[key] = overrideValue[key]
  })
  return result
}

function removeScenario(profile) {
  const result = {}
  Object.keys(profile || {}).forEach((key) => {
    if (key !== 'scenario') {
      result[key] = profile[key]
    }
  })
  return result
}

function execName(scenarioName) {
  return String(scenarioName).split('-').join('')
}

function baseThresholds() {
  return {
    checks: [`rate>${numberFromEnv('K6_CHECK_RATE', 0.98)}`],
    http_req_failed: [`rate<${numberFromEnv('K6_HTTP_FAILED_RATE', 0.01)}`],
    'http_req_duration{type:api}': [
      `p(95)<${numberFromEnv('K6_HTTP_P95_MS', 800)}`,
      `p(99)<${numberFromEnv('K6_HTTP_P99_MS', 1500)}`
    ],
    community_login_failures: ['count<1'],
    community_unexpected_status: [`rate<${numberFromEnv('K6_UNEXPECTED_STATUS_RATE', 0.01)}`]
  }
}

function thresholdsFor(profileName) {
  const thresholds = baseThresholds()
  if (profileName === 'im-ws') {
    thresholds.ws_connecting = [`p(95)<${numberFromEnv('K6_WS_CONNECT_P95_MS', 1000)}`]
    thresholds.ws_session_duration = [`p(95)>${numberFromEnv('K6_WS_SESSION_P95_MIN_MS', 5000)}`]
  }
  return thresholds
}

export function buildOptions(profileName, overrides = {}) {
  const profile = mergeObjects(profileFor(profileName), overrides)
  const scenarioName = profile.scenario || profileName
  const scenarioOptions = removeScenario(profile)
  scenarioOptions.exec = overrides.exec || execName(scenarioName)

  return {
    scenarios: {
      [scenarioName]: scenarioOptions
    },
    thresholds: thresholdsFor(profileName),
    summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
    noConnectionReuse: __ENV.K6_NO_CONNECTION_REUSE === 'true',
    userAgent: __ENV.K6_USER_AGENT || 'community-k6-load-tests/0.0.0'
  }
}
