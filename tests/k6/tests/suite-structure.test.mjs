import assert from 'node:assert/strict'
import { describe, it } from 'node:test'
import { readFile } from 'node:fs/promises'
import { existsSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const suiteRoot = path.resolve(__dirname, '..')

async function read(relativePath) {
  return readFile(path.join(suiteRoot, relativePath), 'utf8')
}

describe('k6 load testing suite structure', () => {
  const requiredFiles = [
    'package.json',
    'README.md',
    'config/options.js',
    'config/profiles.js',
    'lib/config.js',
    'lib/http.js',
    'lib/auth.js',
    'lib/data.js',
    'lib/metrics.js',
    'lib/im.js',
    'scenarios/smoke.js',
    'scenarios/api-mix.js',
    'scenarios/hot-path.js',
    'scenarios/write-paths.js',
    'scenarios/im-ws.js',
    'scenarios/soak.js',
    'scenarios/stress.js',
    'scenarios/spike.js',
    'scripts/run-k6.mjs'
  ]

  for (const relativePath of requiredFiles) {
    it(`includes ${relativePath}`, () => {
      assert.equal(existsSync(path.join(suiteRoot, relativePath)), true)
    })
  }

  it('defines runnable npm commands for every supported profile', async () => {
    const packageJson = JSON.parse(await read('package.json'))

    assert.equal(packageJson.type, 'module')
    assert.equal(packageJson.scripts.test, 'node --test tests/*.test.mjs')
    for (const profile of ['smoke', 'api-mix', 'hot-path', 'write-paths', 'im-ws', 'soak', 'stress', 'spike']) {
      assert.equal(packageJson.scripts[profile], `node scripts/run-k6.mjs ${profile}`)
    }
  })

  it('keeps load profiles centralized and thresholded', async () => {
    const optionsSource = await read('config/options.js')
    const profilesSource = await read('config/profiles.js')

    for (const profile of ['smoke', 'api-mix', 'hot-path', 'write-paths', 'im-ws', 'soak', 'stress', 'spike']) {
      assert.match(profilesSource, new RegExp(`['"]?${profile.replace('-', '\\-')}['"]?:`))
    }

    assert.match(optionsSource, /http_req_failed/)
    assert.match(optionsSource, /http_req_duration/)
    assert.match(optionsSource, /ws_connecting/)
    assert.match(optionsSource, /checks/)
    assert.match(optionsSource, /buildOptions/)
    assert.match(optionsSource, /removeScenario/)
    assert.doesNotMatch(optionsSource, /\.\.\./)
    assert.doesNotMatch(optionsSource, /replaceAll/)
  })

  it('uses gateway-first defaults and supports authenticated seeded users', async () => {
    const configSource = await read('lib/config.js')
    const authSource = await read('lib/auth.js')
    const httpSource = await read('lib/http.js')

    assert.match(configSource, /http:\/\/localhost:12880/)
    assert.match(configSource, /K6_USERNAME/)
    assert.match(configSource, /K6_PASSWORD/)
    assert.match(configSource, /K6_WS_URL/)
    assert.match(authSource, /\/api\/auth\/login/)
    assert.match(httpSource, /Authorization/)
  })

  it('covers public reads, authenticated writes, and IM WebSocket flow', async () => {
    const apiMix = await read('scenarios/api-mix.js')
    const writePaths = await read('scenarios/write-paths.js')
    const imWs = await read('scenarios/im-ws.js')
    const imLib = await read('lib/im.js')

    for (const endpoint of ['/actuator/health', '/api/posts', '/api/categories', '/api/tags/hot', '/api/search/posts']) {
      assert.match(apiMix, new RegExp(endpoint.replaceAll('/', '\\/')))
    }

    for (const endpoint of ['/api/auth/me', '/api/posts', '/api/notices/summary', '/api/wallet/summary']) {
      assert.match(writePaths, new RegExp(endpoint.replaceAll('/', '\\/')))
    }

    assert.match(`${imWs}\n${imLib}`, /\/api\/im\/sessions/)
    assert.match(imLib, /ws\.connect/)
    assert.match(imLib, /connect/)
    assert.match(imLib, /ticket/)
    assert.match(imLib, /socket\.setTimeout/)
    assert.match(imLib, /socket\.close/)
  })

  it('documents run commands, data assumptions, thresholds, and observability', async () => {
    const readme = await read('README.md')

    for (const text of [
      './deploy/deployment.sh up --topology cluster',
      'npm run smoke',
      'K6_BASE_URL',
      'aaa / aaa',
      '/actuator/prometheus',
      'Kibana',
      'threshold'
    ]) {
      assert.match(readme, new RegExp(text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')))
    }
  })
})
