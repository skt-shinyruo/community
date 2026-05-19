const webBaseUrl = (process.env.SINGLE_WEB_BASE_URL || 'http://localhost:12881').replace(/\/$/, '')
const apiBaseUrl = (process.env.SINGLE_API_BASE_URL || 'http://localhost:12880').replace(/\/$/, '')

async function assertOk(url, description) {
  const response = await fetch(url)
  if (!response.ok) {
    throw new Error(`${description} returned HTTP ${response.status}: ${url}`)
  }
  console.log(`${description}: HTTP ${response.status}`)
}

await assertOk(webBaseUrl, 'frontend')

const healthResponse = await fetch(`${apiBaseUrl}/actuator/health`)
if (!healthResponse.ok) {
  throw new Error(`gateway health returned HTTP ${healthResponse.status}`)
}
const body = await healthResponse.json()
if (body.status !== 'UP') {
  throw new Error(`gateway health status is ${JSON.stringify(body)}`)
}
console.log('gateway health: UP')
