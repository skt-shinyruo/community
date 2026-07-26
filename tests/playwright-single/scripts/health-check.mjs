const webBaseUrl = (process.env.SINGLE_WEB_BASE_URL || 'http://localhost:12881').replace(/\/$/, '')
const apiBaseUrl = (process.env.SINGLE_API_BASE_URL || 'http://localhost:12880').replace(/\/$/, '')
const maxAttempts = 60
const attemptDelayMs = 1000
const fetchTimeoutMs = 3000

function delay(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

async function fetchWithTimeout(url) {
  const controller = new AbortController()
  const timeout = setTimeout(() => controller.abort(), fetchTimeoutMs)
  try {
    return await fetch(url, { signal: controller.signal })
  } finally {
    clearTimeout(timeout)
  }
}

async function probeFrontend() {
  const response = await fetchWithTimeout(webBaseUrl)
  const body = await response.text()
  if (!response.ok) {
    throw new Error(`${webBaseUrl} returned HTTP ${response.status}: ${body.slice(0, 240)}`)
  }
  if (!body.includes('<div id="app"></div>')) {
    throw new Error(`${webBaseUrl} returned an unexpected body: missing <div id="app"></div>`)
  }
  return `HTTP ${response.status}`
}

async function probeGateway() {
  const url = `${apiBaseUrl}/actuator/health`
  const response = await fetchWithTimeout(url)
  const body = await response.text()
  if (!response.ok) {
    throw new Error(`${url} returned HTTP ${response.status}: ${body.slice(0, 240)}`)
  }
  let parsed
  try {
    parsed = JSON.parse(body)
  } catch {
    throw new Error(`${url} returned invalid JSON: ${body.slice(0, 240)}`)
  }
  if (parsed.status !== 'UP') {
    throw new Error(`${url} returned body ${JSON.stringify(parsed)}`)
  }
  return 'UP'
}

let lastFrontendError = 'not checked'
let lastGatewayError = 'not checked'

for (let attempt = 1; attempt <= maxAttempts; attempt += 1) {
  const [frontend, gateway] = await Promise.allSettled([probeFrontend(), probeGateway()])
  if (frontend.status === 'fulfilled' && gateway.status === 'fulfilled') {
    console.log(`frontend: ${frontend.value}`)
    console.log(`gateway health: ${gateway.value}`)
    process.exit(0)
  }

  lastFrontendError = frontend.status === 'rejected' ? frontend.reason?.message || String(frontend.reason) : 'ok'
  lastGatewayError = gateway.status === 'rejected' ? gateway.reason?.message || String(gateway.reason) : 'ok'
  console.error(`health attempt ${attempt}/${maxAttempts}: frontend=${lastFrontendError}; gateway=${lastGatewayError}`)
  if (attempt < maxAttempts) await delay(attemptDelayMs)
}

throw new Error(
  `single health check failed after ${maxAttempts} attempts; `
  + `frontend ${webBaseUrl}: ${lastFrontendError}; `
  + `gateway ${apiBaseUrl}/actuator/health: ${lastGatewayError}`
)
