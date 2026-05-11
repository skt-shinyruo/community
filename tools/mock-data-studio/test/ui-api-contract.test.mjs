import assert from 'node:assert/strict'
import test from 'node:test'

import request from 'supertest'

import { defaultAutoFillMetadataBatchKey } from '../src/jobs/autoFillService.mjs'
import { buildApp } from '../src/server/app.mjs'

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

function metadataId(sequence) {
  return `01965429-b34a-7000-8000-${String(sequence).padStart(12, '0')}`
}

function createBatch(id, overrides = {}) {
  const sequence = Number(id)
  const batchId = metadataId(Number.isFinite(sequence) ? sequence : 1)
  return {
    id: batchId,
    batchKey: `batch-${sequence}`,
    batchType: 'demo-seed',
    requestedBy: 'test-runner',
    status: 'succeeded',
    summaryJson: null,
    errorMessage: null,
    createdAt: `2026-03-2${sequence}T10:00:00.000Z`,
    startedAt: `2026-03-2${sequence}T10:01:00.000Z`,
    finishedAt: `2026-03-2${sequence}T10:02:00.000Z`,
    ...overrides
  }
}

function createJob(id, batchId, overrides = {}) {
  const sequence = Number(id)
  const jobId = metadataId(100000 + (Number.isFinite(sequence) ? sequence : 1))
  const normalizedBatchId =
    typeof batchId === 'string' ? batchId : metadataId(Number.isFinite(Number(batchId)) ? Number(batchId) : 1)
  return {
    id: jobId,
    batchId: normalizedBatchId,
    jobKey: `job-${sequence}`,
    jobType: 'demo-seed',
    status: 'succeeded',
    summaryJson: null,
    errorMessage: null,
    createdAt: `2026-03-2${sequence}T10:00:00.000Z`,
    startedAt: `2026-03-2${sequence}T10:01:00.000Z`,
    finishedAt: `2026-03-2${sequence}T10:02:00.000Z`,
    ...overrides
  }
}

function createAppHarness({
  config = fakeConfig(),
  batches = [],
  runtimeStatus = null,
  jobsByBatchId = new Map(),
  jobsById = new Map(),
  startJob = async (payload = {}) => ({
    batch: createBatch(501, {
      requestedBy: payload.requestedBy ?? 'local-dev',
      batchType: payload.batchType ?? 'demo-seed',
      status: 'pending',
      startedAt: null,
      finishedAt: null
    }),
    job: createJob(601, 501, {
      jobType: payload.jobType ?? 'demo-seed',
      status: 'pending',
      startedAt: null,
      finishedAt: null
    })
  }),
  targetsByBatchId = new Map(),
  refsByBatchId = new Map(),
  deleteBatch = async (batchId) => ({
    batchId,
    deleted: {
      business: {},
      metadata: {}
    }
  })
} = {}) {
  const batchesById = new Map(batches.map((batch) => [batch.id, structuredClone(batch)]))
  const resolvedRuntimeStatus =
    runtimeStatus ??
    {
      ok: false,
      service: 'mock-data-studio',
      readiness: {
        db: {
          ready: true,
          required: true
        },
        communityApp: {
          ready: true,
          required: true,
          status: 200,
          url: 'http://community-app:8080/actuator/health'
        },
        imCore: {
          ready: false,
          required: true,
          status: 503,
          url: 'http://im-core:18082/actuator/health',
          error: 'connect ECONNREFUSED'
        },
        ai: {
          provider: 'openai',
          model: 'gpt-4.1-mini',
          enabled: false,
          missingConfig: [],
          required: false,
          ready: false
        }
      }
    }
  const resolvedJobsById = new Map(
    Array.from(jobsById.entries(), ([jobId, job]) => [jobId, structuredClone(job)])
  )

  for (const jobs of jobsByBatchId.values()) {
    for (const job of jobs) {
      resolvedJobsById.set(job.id, structuredClone(job))
    }
  }

  return buildApp({
    config,
    batchRepository: {
      async listAll() {
        return structuredClone(batches)
      },
      async findById(batchId) {
        return structuredClone(batchesById.get(batchId) ?? null)
      },
      async getById(batchId) {
        const batch = batchesById.get(batchId)
        if (!batch) {
          throw new Error(`Missing demo_batch row ${batchId}`)
        }

        return structuredClone(batch)
      }
    },
    jobRepository: {
      async listByBatchId(batchId) {
        return structuredClone(jobsByBatchId.get(batchId) ?? [])
      },
      async findById(jobId) {
        return structuredClone(resolvedJobsById.get(jobId) ?? null)
      },
      async getById(jobId) {
        return structuredClone(
          resolvedJobsById.get(jobId) ??
            createJob(999999, metadataId(999998))
        )
      }
    },
    targetRepository: {
      async listByBatchId(batchId) {
        return structuredClone(targetsByBatchId.get(batchId) ?? [])
      }
    },
    entityRefRepository: {
      async listByBatchId(batchId) {
        return structuredClone(refsByBatchId.get(batchId) ?? [])
      }
    },
    deleteBatchService: {
      async deleteBatch(batchId) {
        return deleteBatch(batchId)
      }
    },
    jobRunner: {
      async start(payload) {
        return startJob(payload)
      }
    },
    runtimeStatusService: {
      async getStatus() {
        return structuredClone(resolvedRuntimeStatus)
      }
    }
  })
}

test('root serves the minimal studio shell assets', async () => {
  const app = createAppHarness()

  const response = await request(app).get('/')

  assert.equal(response.status, 200)
  assert.match(response.headers['content-type'], /^text\/html/u)
  assert.match(response.text, /mock-data-studio/ui)
  assert.match(response.text, /runtime-status-grid/u)
  assert.match(response.text, /batch-history-table/u)
  assert.match(response.text, /href="\/styles\.css"/u)
  assert.match(response.text, /src="\/app\.js"/u)
})

test('health exposes generate-form metadata for the UI shell preview', async () => {
  const app = createAppHarness()

  const response = await request(app).get('/health')

  assert.equal(response.status, 200)
  assert.equal(response.body.ok, true)
  assert.equal(response.body.service, 'mock-data-studio')
  assert.deepEqual(response.body.ui.generateForm.modes, [
    {
      id: 'auto-fill',
      label: '自动补数'
    },
    {
      id: 'manual-generate',
      label: '手动生成'
    }
  ])
  assert.deepEqual(response.body.ui.generateForm.defaultDraft, {
    mode: 'manual-generate',
    scenePresetId: 'community-seed',
    requestedBy: 'local-dev',
    counts: {
      users: 6,
      posts: 12,
      comments: 24,
      socialFollows: 16,
      socialLikes: 24
    },
    aiEnhancement: false,
    jobRequest: {
      requestedBy: 'local-dev',
      batchType: 'demo-seed',
      jobType: 'demo-seed',
      mode: 'manual-generate',
      aiEnhancement: false
    }
  })
  assert.deepEqual(response.body.ui.generateForm.scenePresets, [
    {
      id: 'default-deficit',
      label: '默认批次缺口',
      mode: 'auto-fill',
      counts: {
        users: 0,
        posts: 0,
        comments: 0,
        socialFollows: 0,
        socialLikes: 0
      },
      aiEnhancement: false,
      jobRequest: {
        requestedBy: 'startup-auto-fill',
        batchType: 'startup-auto-fill',
        jobType: 'startup-auto-fill',
        mode: 'auto-fill',
        aiEnhancement: false
      }
    },
    {
      id: 'community-seed',
      label: '社区热启动',
      mode: 'manual-generate',
      counts: {
        users: 6,
        posts: 12,
        comments: 24,
        socialFollows: 16,
        socialLikes: 24
      },
      aiEnhancement: false,
      jobRequest: {
        requestedBy: 'local-dev',
        batchType: 'demo-seed',
        jobType: 'demo-seed',
        mode: 'manual-generate',
        aiEnhancement: false
      }
    }
  ])
})

test('runtime status returns card-friendly summary data for the status panel', async () => {
  const app = createAppHarness()

  const response = await request(app).get('/api/runtime-status')

  assert.equal(response.status, 200)
  assert.equal(response.body.ok, false)
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
      detail: 'connect ECONNREFUSED'
    },
    {
      key: 'ai',
      label: 'AI 环境',
      ready: false,
      required: false,
      detail: 'openai · gpt-4.1-mini · disabled'
    }
  ])
})

test('job start and polling responses expose generate-flow helpers for the UI shell', async () => {
  const startedBatch = createBatch(7, {
    requestedBy: 'alice',
    status: 'running',
    batchType: 'demo-seed'
  })
  const startedJob = createJob(71, 7, {
    status: 'running',
    jobType: 'demo-seed'
  })
  const app = createAppHarness({
    config: fakeConfig({
      ai: {
        provider: 'openai',
        model: 'gpt-4.1-mini',
        enabled: true,
        missingConfig: [],
        ready: true
      }
    }),
    jobsById: new Map([[startedJob.id, startedJob]]),
    startJob: async () => ({
      batch: startedBatch,
      job: startedJob
    })
  })

  const createResponse = await request(app).post('/api/jobs').send({
    requestedBy: 'alice',
    batchType: 'demo-seed',
    jobType: 'demo-seed',
    mode: 'manual-generate',
    aiEnhancement: true
  })

  assert.equal(createResponse.status, 202)
  assert.deepEqual(createResponse.body.request, {
    requestedBy: 'alice',
    batchType: 'demo-seed',
    jobType: 'demo-seed',
    mode: 'manual-generate',
    aiEnhancement: true
  })
  assert.deepEqual(createResponse.body.polling, {
    jobId: startedJob.id,
    batchId: startedBatch.id,
    status: 'running',
    isTerminal: false,
    pollPath: `/api/jobs/${startedJob.id}`,
    batchPath: `/api/batches/${startedBatch.id}`
  })

  const pollResponse = await request(app).get(`/api/jobs/${startedJob.id}`)

  assert.equal(pollResponse.status, 200)
  assert.deepEqual(pollResponse.body.polling, {
    jobId: startedJob.id,
    batchId: startedBatch.id,
    status: 'running',
    isTerminal: false,
    pollPath: `/api/jobs/${startedJob.id}`,
    batchPath: `/api/batches/${startedBatch.id}`
  })
})

test('job start rejects manual AI enhancement when AI config is not ready', async () => {
  const app = createAppHarness({
    config: fakeConfig({
      ai: {
        provider: 'openai',
        model: 'gpt-4.1-mini',
        enabled: true,
        missingConfig: ['apiKey'],
        ready: false
      }
    })
  })

  const response = await request(app).post('/api/jobs').send({
    requestedBy: 'alice',
    batchType: 'demo-seed',
    jobType: 'demo-seed',
    mode: 'manual-generate',
    aiEnhancement: true
  })

  assert.equal(response.status, 400)
  assert.deepEqual(response.body, {
    ok: false,
    error: 'ai_not_ready',
    message: 'AI enhancement is not ready: missing apiKey'
  })
})

test('batch history groups the default batch separately from manual batches', async () => {
  const defaultBatch = createBatch(1, {
    batchKey: defaultAutoFillMetadataBatchKey,
    batchType: 'startup-auto-fill'
  })
  const manualOlder = createBatch(2, {
    batchKey: 'manual-seed-older',
    requestedBy: 'alice'
  })
  const manualNewest = createBatch(3, {
    batchKey: 'manual-seed-newest',
    requestedBy: 'bob'
  })
  const app = createAppHarness({
    batches: [manualNewest, defaultBatch, manualOlder],
    jobsByBatchId: new Map([
      [defaultBatch.id, [createJob(11, defaultBatch.id)]],
      [manualOlder.id, [createJob(22, manualOlder.id)]],
      [manualNewest.id, [createJob(33, manualNewest.id)]]
    ]),
    targetsByBatchId: new Map([
      [defaultBatch.id, [{ entityType: 'users', targetCount: 2 }]],
      [manualOlder.id, [{ entityType: 'posts', targetCount: 3 }]],
      [manualNewest.id, [{ entityType: 'comments', targetCount: 5 }]]
    ]),
    refsByBatchId: new Map([
      [defaultBatch.id, [{ entityType: 'users' }, { entityType: 'users' }]],
      [manualOlder.id, [{ entityType: 'posts' }]],
      [manualNewest.id, [{ entityType: 'comments' }, { entityType: 'comments' }]]
    ])
  })

  const response = await request(app).get('/api/batches')

  assert.equal(response.status, 200)
  assert.equal(response.body.ok, true)
  assert.equal(response.body.defaultBatch.batch.id, defaultBatch.id)
  assert.deepEqual(response.body.history, {
    totalBatchCount: 3,
    defaultBatchId: defaultBatch.id,
    manualBatchCount: 2,
    hasManualBatches: true
  })
  assert.deepEqual(
    response.body.manualBatches.map((entry) => entry.batch.id),
    [manualNewest.id, manualOlder.id]
  )
})

test('batch history does not misclassify unrelated startup-auto-fill rows as the default batch', async () => {
  const defaultBatch = createBatch(1, {
    batchKey: defaultAutoFillMetadataBatchKey,
    batchType: 'startup-auto-fill'
  })
  const unrelatedStartupRow = createBatch(2, {
    batchKey: 'batch-2',
    batchType: 'startup-auto-fill'
  })
  const app = createAppHarness({
    batches: [unrelatedStartupRow, defaultBatch],
    jobsByBatchId: new Map([
      [defaultBatch.id, [createJob(11, defaultBatch.id)]],
      [unrelatedStartupRow.id, [createJob(22, unrelatedStartupRow.id)]]
    ]),
    targetsByBatchId: new Map([
      [defaultBatch.id, [{ entityType: 'users', targetCount: 2 }]],
      [unrelatedStartupRow.id, [{ entityType: 'users', targetCount: 1 }]]
    ]),
    refsByBatchId: new Map([
      [defaultBatch.id, [{ entityType: 'users' }]],
      [unrelatedStartupRow.id, [{ entityType: 'users' }]]
    ])
  })

  const response = await request(app).get('/api/batches')

  assert.equal(response.status, 200)
  assert.equal(response.body.defaultBatch.batch.id, defaultBatch.id)
  assert.deepEqual(response.body.history, {
    totalBatchCount: 2,
    defaultBatchId: defaultBatch.id,
    manualBatchCount: 1,
    hasManualBatches: true
  })
  assert.deepEqual(
    response.body.manualBatches.map((entry) => entry.batch.id),
    [unrelatedStartupRow.id]
  )
})

test('batch detail returns target, actual, and failure summaries', async () => {
  const batch = createBatch(42, {
    batchKey: 'manual-seed-42',
    status: 'failed',
    errorMessage: 'write-community exploded'
  })
  const latestFailedJob = createJob(142, batch.id, {
    status: 'failed',
    errorMessage: 'write-community exploded'
  })
  const priorSucceededJob = createJob(141, batch.id)
  const app = createAppHarness({
    batches: [batch],
    jobsByBatchId: new Map([[batch.id, [latestFailedJob, priorSucceededJob]]]),
    targetsByBatchId: new Map([
      [
        batch.id,
        [
          { entityType: 'users', targetCount: 2 },
          { entityType: 'posts', targetCount: 3 },
          { entityType: 'comments', targetCount: 5 }
        ]
      ]
    ]),
    refsByBatchId: new Map([
      [
        batch.id,
        [
          { entityType: 'users' },
          { entityType: 'users' },
          { entityType: 'posts' },
          { entityType: 'posts' },
          { entityType: 'comments' },
          { entityType: 'comments' },
          { entityType: 'comments' }
        ]
      ]
    ])
  })

  const response = await request(app).get(`/api/batches/${batch.id}`)

  assert.equal(response.status, 200)
  assert.deepEqual(response.body, {
    ok: true,
    batch,
    latestJob: latestFailedJob,
    jobs: [latestFailedJob, priorSucceededJob],
    targetSummary: {
      totalCount: 10,
      byEntityType: {
        users: 2,
        posts: 3,
        comments: 5
      }
    },
    actualSummary: {
      totalCount: 7,
      byEntityType: {
        users: 2,
        posts: 2,
        comments: 3
      }
    },
    failureSummary: {
      totalCount: 3,
      byEntityType: {
        posts: 1,
        comments: 2
      },
      failedJobCount: 1,
      hasFailures: true,
      lastErrorMessage: 'write-community exploded'
    },
    detail: {
      batchId: batch.id,
      isDefaultBatch: false,
      jobCount: 2,
      latestJobId: latestFailedJob.id,
      canDelete: true,
      lastErrorMessage: 'write-community exploded'
    }
  })
})

test('batch detail summaries include current phase 2 moderation, growth, and im entity types', async () => {
  const batch = createBatch(52, {
    batchKey: 'manual-seed-52',
    status: 'succeeded'
  })
  const latestJob = createJob(152, batch.id)
  const app = createAppHarness({
    batches: [batch],
    jobsByBatchId: new Map([[batch.id, [latestJob]]]),
    targetsByBatchId: new Map([
      [
        batch.id,
        [
          { entityType: 'reports', targetCount: 2 },
          { entityType: 'user_task_progress', targetCount: 5 },
          { entityType: 'im_private_messages', targetCount: 6 }
        ]
      ]
    ]),
    refsByBatchId: new Map([
      [
        batch.id,
        [
          { entityType: 'reports' },
          { entityType: 'user_task_progress' },
          { entityType: 'user_task_progress' },
          { entityType: 'user_task_progress' },
          { entityType: 'im_private_messages' },
          { entityType: 'im_private_messages' },
          { entityType: 'im_private_messages' },
          { entityType: 'im_private_messages' }
        ]
      ]
    ])
  })

  const response = await request(app).get(`/api/batches/${batch.id}`)

  assert.equal(response.status, 200)
  assert.deepEqual(response.body.targetSummary, {
    totalCount: 13,
    byEntityType: {
      reports: 2,
      user_task_progress: 5,
      im_private_messages: 6
    }
  })
  assert.deepEqual(response.body.actualSummary, {
    totalCount: 8,
    byEntityType: {
      reports: 1,
      user_task_progress: 3,
      im_private_messages: 4
    }
  })
  assert.deepEqual(response.body.failureSummary.byEntityType, {
    reports: 1,
    user_task_progress: 2,
    im_private_messages: 2
  })
})

test('batch delete stays blocked while a nonterminal job exists', async () => {
  const batch = createBatch(7, {
    status: 'pending'
  })
  const app = createAppHarness({
    batches: [batch],
    deleteBatch: async () => {
      const error = new Error('Batch 7 still has a running job')
      error.code = 'BATCH_JOB_RUNNING'
      error.status = 409
      error.runningJob = createJob(207, batch.id, {
        status: 'pending'
      })
      throw error
    }
  })

  const response = await request(app).delete(`/api/batches/${batch.id}`)

  assert.equal(response.status, 409)
  assert.deepEqual(response.body, {
    ok: false,
    error: 'batch_job_running',
    runningJob: createJob(207, batch.id, {
      status: 'pending'
    })
  })
})

test('batch delete returns deleted counts when the batch is removed', async () => {
  const batch = createBatch(8)
  const app = createAppHarness({
    batches: [batch],
    deleteBatch: async (batchId) => ({
      batchId,
      deleted: {
        business: {
          socialLikes: 4,
          socialFollows: 2,
          comments: 9,
          posts: 3,
          users: 1
        },
        metadata: {
          entityRefs: 19,
          targets: 3,
          jobs: 1,
          batches: 1
        }
      }
    })
  })

  const response = await request(app).delete(`/api/batches/${batch.id}`)

  assert.equal(response.status, 200)
  assert.deepEqual(response.body, {
    ok: true,
    batchId: batch.id,
    deleted: {
      business: {
        socialLikes: 4,
        socialFollows: 2,
        comments: 9,
        posts: 3,
        users: 1
      },
      metadata: {
        entityRefs: 19,
        targets: 3,
        jobs: 1,
        batches: 1
      }
    }
  })
})
