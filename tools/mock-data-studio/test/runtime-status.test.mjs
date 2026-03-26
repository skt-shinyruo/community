import assert from 'node:assert/strict'
import test from 'node:test'

import request from 'supertest'

import { buildApp } from '../src/server/app.mjs'
import { createRuntimeStatusService } from '../src/server/routes/runtimeStatus.mjs'

function fakeConfig(overrides = {}) {
  return {
    serviceName: 'mock-data-studio',
    upstreams: {
      communityAppBaseUrl: 'http://community-app:8080',
      imCoreBaseUrl: 'http://im-core:18082'
    },
    ai: {
      provider: 'openai',
      model: 'gpt-4.1-mini',
      enabled: false,
      missingConfig: [],
      ready: false
    },
    ...overrides
  }
}

test('runtime status reports db and API readiness separately from AI env readiness', async () => {
  const config = fakeConfig()
  const probeCalls = []
  const db = {
    async query(sql) {
      assert.match(sql, /select 1/i)
      return [{ ok: 1 }]
    }
  }
  const runtimeStatusService = createRuntimeStatusService({
    config,
    db,
    probeUrl: async (url) => {
      probeCalls.push(url)

      if (url.includes('community-app')) {
        return { ready: true, status: 200 }
      }

      return { ready: false, status: 503, error: 'upstream unavailable' }
    }
  })
  const app = buildApp({ config, runtimeStatusService })

  const response = await request(app).get('/api/runtime-status')

  assert.equal(response.status, 200)
  assert.deepEqual(response.body.readiness, {
    db: {
      ready: true,
      required: true
    },
    communityApp: {
      required: true,
      ready: true,
      status: 200,
      url: 'http://community-app:8080/actuator/health'
    },
    imCore: {
      required: true,
      ready: false,
      error: 'upstream unavailable',
      status: 503,
      url: 'http://im-core:18082/actuator/health'
    },
    ai: {
      provider: 'openai',
      model: 'gpt-4.1-mini',
      enabled: false,
      missingConfig: [],
      required: false,
      ready: false
    }
  })
  assert.deepEqual(response.body.summary, {
    status: 'attention',
    readyCount: 2,
    totalCount: 4,
    requiredReadyCount: 2,
    requiredCount: 3
  })
  assert.deepEqual(response.body.cards, [
    {
      key: 'db',
      label: '数据库',
      ready: true,
      required: true,
      detail: null
    },
    {
      key: 'communityApp',
      label: 'community-app',
      ready: true,
      required: true,
      detail: '200 · http://community-app:8080/actuator/health'
    },
    {
      key: 'imCore',
      label: 'im-core',
      ready: false,
      required: true,
      detail: 'upstream unavailable'
    },
    {
      key: 'ai',
      label: 'AI 环境',
      ready: false,
      required: false,
      detail: 'openai · gpt-4.1-mini · disabled'
    }
  ])
  assert.deepEqual(
    {
      ok: response.body.ok,
      service: response.body.service
    },
    {
    ok: false,
      service: 'mock-data-studio'
    }
  )
  assert.deepEqual(probeCalls, [
    'http://community-app:8080/actuator/health',
    'http://im-core:18082/actuator/health'
  ])
})

test('runtime status ok ignores optional AI readiness when required checks are ready', async () => {
  const config = fakeConfig()
  const runtimeStatusService = createRuntimeStatusService({
    config,
    db: {
      async query() {
        return [{ ok: 1 }]
      }
    },
    probeUrl: async () => ({ ready: true, status: 200 })
  })

  const status = await runtimeStatusService.getStatus()

  assert.equal(status.ok, true)
  assert.deepEqual(status.readiness.ai, {
    provider: 'openai',
    model: 'gpt-4.1-mini',
    enabled: false,
    missingConfig: [],
    required: false,
    ready: false
  })
})

test('runtime status runs DB and upstream probes in parallel', async () => {
  const started = []
  let releaseChecks
  const checkGate = new Promise((resolve) => {
    releaseChecks = resolve
  })
  const runtimeStatusService = createRuntimeStatusService({
    config: fakeConfig({
      ai: {
        provider: 'openai',
        model: 'gpt-4.1-mini',
        enabled: true,
        missingConfig: [],
        ready: true
      }
    }),
    db: {
      async query() {
        started.push('db')
        await checkGate
        return [{ ok: 1 }]
      }
    },
    probeUrl: async (url) => {
      started.push(url.includes('community-app') ? 'community-app' : 'im-core')
      await checkGate
      return { ready: true, status: 200 }
    }
  })

  const statusPromise = runtimeStatusService.getStatus()
  await new Promise((resolve) => setImmediate(resolve))

  assert.deepEqual(new Set(started), new Set(['db', 'community-app', 'im-core']))

  releaseChecks()
  const status = await statusPromise

  assert.equal(status.ok, true)
})

test('runtime status route returns a JSON 500 when the status service throws', async () => {
  const app = buildApp({
    config: fakeConfig(),
    runtimeStatusService: {
      async getStatus() {
        throw new Error('probe exploded')
      }
    }
  })

  const response = await request(app).get('/api/runtime-status')

  assert.equal(response.status, 500)
  assert.deepEqual(response.body, {
    ok: false,
    error: 'internal_error',
    message: 'probe exploded'
  })
})
