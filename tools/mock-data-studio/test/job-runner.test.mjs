import assert from 'node:assert/strict'
import test from 'node:test'

import request from 'supertest'

import { createAutoFillJobPhases } from '../src/jobs/autoFillService.mjs'
import { createJobRunner } from '../src/jobs/jobRunner.mjs'
import { buildApp } from '../src/server/app.mjs'

const expectedPhaseNames = [
  'bootstrap',
  'plan',
  'write-community',
  'write-im',
  'reindex',
  'finalize'
]

function metadataId(sequence) {
  return `01965429-b34a-7000-8000-${String(sequence).padStart(12, '0')}`
}

function createClock() {
  let index = 0
  const baseTime = Date.parse('2026-03-24T10:00:00.000Z')

  return () => new Date(baseTime + index++ * 1000).toISOString()
}

function fakeConfig() {
  return {
    serviceName: 'mock-data-studio'
  }
}

function createMissingJobError(jobId) {
  const error = new Error(`Missing demo_job row ${jobId}`)
  error.code = 'JOB_NOT_FOUND'
  error.jobId = jobId
  return error
}

function cloneStore(store) {
  return {
    batchCreateGate: store.batchCreateGate,
    failBatchMarkStartedCount: store.failBatchMarkStartedCount,
    failBatchMarkFinishedCount: store.failBatchMarkFinishedCount,
    failJobMarkStartedCount: store.failJobMarkStartedCount,
    failJobMarkFinishedCount: store.failJobMarkFinishedCount,
    terminalTransactionFailureCount: store.terminalTransactionFailureCount,
    nextBatchId: store.nextBatchId,
    nextJobId: store.nextJobId,
    summarySnapshots: structuredClone(store.summarySnapshots),
    batches: structuredClone(store.batches),
    jobs: structuredClone(store.jobs),
    terminalWriteCount: 0
  }
}

function createStore(overrides = {}) {
  return {
    batchCreateGate: null,
    failBatchMarkStartedCount: 0,
    failBatchMarkFinishedCount: 0,
    failJobMarkStartedCount: 0,
    failJobMarkFinishedCount: 0,
    terminalTransactionFailureCount: 0,
    nextBatchId: 1,
    nextJobId: 1,
    summarySnapshots: [],
    batches: new Map(),
    jobs: new Map(),
    terminalWriteCount: 0,
    ...overrides
  }
}

function createBatchRepository(store) {
  return {
    async create({ batchKey, batchType, requestedBy, createdAt }) {
      if (store.batchCreateGate) {
        await store.batchCreateGate
      }

      const batch = {
        id: metadataId(store.nextBatchId++),
        batchKey,
        batchType,
        requestedBy,
        status: 'pending',
        summaryJson: null,
        errorMessage: null,
        createdAt,
        startedAt: null,
        finishedAt: null
      }
      store.batches.set(batch.id, batch)
      return structuredClone(batch)
    },

    async listAll() {
      return [...store.batches.values()]
        .sort((left, right) => {
          const createdAtComparison = String(right.createdAt).localeCompare(String(left.createdAt))
          return createdAtComparison !== 0 ? createdAtComparison : String(right.id).localeCompare(String(left.id))
        })
        .map((batch) => structuredClone(batch))
    },

    async markStarted(batchId, { startedAt, status = 'running' } = {}) {
      const batch = store.batches.get(batchId)
      if (!batch || batch.status !== 'pending') {
        throw new Error(`demo_batch status transition failed for ${batchId}: expected pending`)
      }

      if (store.failBatchMarkStartedCount > 0) {
        store.failBatchMarkStartedCount -= 1
        throw new Error(`Injected batch start failure for ${batchId}`)
      }

      batch.status = status
      batch.startedAt = startedAt
      batch.finishedAt = null
      batch.errorMessage = null
      return structuredClone(batch)
    },

    async markPrepared(batchId) {
      const batch = store.batches.get(batchId)
      if (!batch) {
        throw new Error(`Missing demo_batch row ${batchId}`)
      }

      batch.status = 'pending'
      batch.summaryJson = null
      batch.errorMessage = null
      batch.startedAt = null
      batch.finishedAt = null
      return structuredClone(batch)
    },

    async markFinished(
      batchId,
      { finishedAt, status, summaryJson = null, errorMessage = null, fromStatuses = ['running'] } = {}
    ) {
      const batch = store.batches.get(batchId)
      if (!batch || !fromStatuses.includes(batch.status)) {
        throw new Error(
          `demo_batch status transition failed for ${batchId}: expected ${fromStatuses.join(' or ')}`
        )
      }

      if (store.failBatchMarkFinishedCount > 0) {
        store.failBatchMarkFinishedCount -= 1
        throw new Error(`Injected batch terminal write failure for ${batchId}`)
      }

      store.terminalWriteCount += 1
      batch.status = status
      batch.finishedAt = finishedAt
      batch.summaryJson = structuredClone(summaryJson)
      batch.errorMessage = errorMessage
      return structuredClone(batch)
    },

    async findById(batchId) {
      const batch = store.batches.get(batchId)
      return batch ? structuredClone(batch) : null
    },

    async findByBatchKey(batchKey) {
      for (const batch of store.batches.values()) {
        if (batch.batchKey === batchKey) {
          return structuredClone(batch)
        }
      }

      return null
    },

    async getById(batchId) {
      const batch = store.batches.get(batchId)
      if (!batch) {
        throw new Error(`Missing demo_batch row ${batchId}`)
      }

      return structuredClone(batch)
    }
  }
}

function createJobRepository(store) {
  return {
    async create({ batchId, jobKey, jobType, createdAt }) {
      const job = {
        id: metadataId(100000 + store.nextJobId++),
        batchId,
        jobKey,
        jobType,
        status: 'pending',
        summaryJson: null,
        errorMessage: null,
        createdAt,
        startedAt: null,
        finishedAt: null
      }
      store.jobs.set(job.id, job)
      return structuredClone(job)
    },

    async markStarted(jobId, { startedAt, status = 'running' } = {}) {
      const job = store.jobs.get(jobId)
      if (!job || job.status !== 'pending') {
        throw new Error(`demo_job status transition failed for ${jobId}: expected pending`)
      }

      if (store.failJobMarkStartedCount > 0) {
        store.failJobMarkStartedCount -= 1
        throw new Error(`Injected job start failure for ${jobId}`)
      }

      job.status = status
      job.startedAt = startedAt
      job.finishedAt = null
      job.errorMessage = null
      return structuredClone(job)
    },

    async updateSummary(jobId, { summaryJson, errorMessage = null } = {}) {
      const job = store.jobs.get(jobId)
      if (!job || job.status !== 'running') {
        throw new Error(`demo_job status transition failed for ${jobId}: expected running`)
      }

      job.summaryJson = structuredClone(summaryJson)
      job.errorMessage = errorMessage
      store.summarySnapshots.push(structuredClone(summaryJson))
      return structuredClone(job)
    },

    async markFinished(
      jobId,
      { finishedAt, status, summaryJson = null, errorMessage = null, fromStatuses = ['running'] } = {}
    ) {
      const job = store.jobs.get(jobId)
      if (!job || !fromStatuses.includes(job.status)) {
        throw new Error(
          `demo_job status transition failed for ${jobId}: expected ${fromStatuses.join(' or ')}`
        )
      }

      if (store.failJobMarkFinishedCount > 0) {
        store.failJobMarkFinishedCount -= 1
        throw new Error(`Injected job terminal write failure for ${jobId}`)
      }

      store.terminalWriteCount += 1
      job.status = status
      job.finishedAt = finishedAt
      job.summaryJson = structuredClone(summaryJson)
      job.errorMessage = errorMessage
      return structuredClone(job)
    },

    async findById(jobId) {
      const job = store.jobs.get(jobId)
      return job ? structuredClone(job) : null
    },

    async listByBatchId(batchId) {
      return [...store.jobs.values()]
        .filter((job) => job.batchId === batchId)
        .sort((left, right) => {
          const createdAtComparison = String(right.createdAt).localeCompare(String(left.createdAt))
          return createdAtComparison !== 0 ? createdAtComparison : String(right.id).localeCompare(String(left.id))
        })
        .map((job) => structuredClone(job))
    },

    async getById(jobId) {
      const job = store.jobs.get(jobId)
      if (!job) {
        throw createMissingJobError(jobId)
      }

      return structuredClone(job)
    }
  }
}

function createRepositories({ transactional = false, ...storeOverrides } = {}) {
  const store = createStore(storeOverrides)
  const repositories = {
    store,
    batchRepository: createBatchRepository(store),
    jobRepository: createJobRepository(store)
  }

  if (transactional) {
    repositories.withRepositoriesTransaction = async (work) => {
      const txStore = cloneStore(store)
      const result = await work({
        batchRepository: createBatchRepository(txStore),
        jobRepository: createJobRepository(txStore)
      })

      if (txStore.terminalWriteCount > 0 && store.terminalTransactionFailureCount > 0) {
        store.terminalTransactionFailureCount -= 1
        throw new Error('Injected terminal transaction failure')
      }

      store.nextBatchId = txStore.nextBatchId
      store.nextJobId = txStore.nextJobId
      store.summarySnapshots = txStore.summarySnapshots
      store.batches = txStore.batches
      store.jobs = txStore.jobs
      return result
    }
  }

  return repositories
}

test('job runner persists phase transitions in order', async () => {
  const { batchRepository, jobRepository, store } = createRepositories()
  const runner = createJobRunner({
    batchRepository,
    jobRepository,
    now: createClock(),
    createBatchKey: () => 'batch-1',
    createJobKey: () => 'job-1'
  })

  const startedJob = await runner.start({
    requestedBy: 'test-runner',
    batchType: 'demo-seed',
    jobType: 'demo-seed'
  })

  await runner.waitForIdle()

  const storedJob = await jobRepository.getById(startedJob.job.id)

  assert.equal(storedJob.status, 'succeeded')
  assert.deepEqual(
    storedJob.summaryJson.phases.map((phase) => phase.name),
    expectedPhaseNames
  )
  assert.deepEqual(
    store.summarySnapshots.map((summaryJson) => summaryJson.phases.map((phase) => phase.name)),
    [
      ['bootstrap'],
      ['bootstrap', 'plan'],
      ['bootstrap', 'plan', 'write-community'],
      ['bootstrap', 'plan', 'write-community', 'write-im'],
      ['bootstrap', 'plan', 'write-community', 'write-im', 'reindex'],
      ['bootstrap', 'plan', 'write-community', 'write-im', 'reindex', 'finalize']
    ]
  )
})

test('job runner forwards manual run options to phases', async () => {
  const { batchRepository, jobRepository } = createRepositories()
  const observedRunOptions = []
  const runner = createJobRunner({
    batchRepository,
    jobRepository,
    now: createClock(),
    createBatchKey: () => 'batch-manual-options',
    createJobKey: () => 'job-manual-options',
    phases: expectedPhaseNames.map((name) => ({
      name,
      run: async ({ runOptions }) => {
        observedRunOptions.push({ ...runOptions })
      }
    }))
  })

  await runner.start({
    requestedBy: 'test-runner',
    batchType: 'demo-seed',
    jobType: 'demo-seed',
    mode: 'manual-generate',
    aiEnhancement: true
  })

  await runner.waitForIdle()

  assert.equal(observedRunOptions.length, expectedPhaseNames.length)
  assert.ok(observedRunOptions.every((options) => options.mode === 'manual-generate'))
  assert.ok(observedRunOptions.every((options) => options.aiEnhancement === true))
})

test('prepared startup runs keep AI enhancement disabled', async () => {
  const { batchRepository, jobRepository } = createRepositories()
  const observedRunOptions = []
  const runner = createJobRunner({
    batchRepository,
    jobRepository,
    now: createClock(),
    phases: expectedPhaseNames.map((name) => ({
      name,
      run: async ({ runOptions }) => {
        observedRunOptions.push({ ...runOptions })
      }
    })),
    createJobKey: () => 'job-startup-options'
  })
  const stableBatch = await batchRepository.create({
    batchKey: 'startup-auto-fill-default',
    batchType: 'startup-auto-fill',
    requestedBy: 'startup-auto-fill',
    createdAt: '2026-03-25T00:00:00.000Z'
  })

  const prepared = await runner.prepare({
    batch: stableBatch,
    requestedBy: 'startup-auto-fill',
    batchType: 'startup-auto-fill',
    jobType: 'startup-auto-fill'
  })
  await runner.startPrepared(prepared)
  await runner.waitForIdle()

  assert.equal(observedRunOptions.length, expectedPhaseNames.length)
  assert.ok(observedRunOptions.every((options) => options.mode === 'auto-fill'))
  assert.ok(observedRunOptions.every((options) => options.aiEnhancement === false))
})

test('second job submission returns 409 while a job is already running', async () => {
  const { batchRepository, jobRepository } = createRepositories()
  let releaseBootstrapPhase
  const bootstrapGate = new Promise((resolve) => {
    releaseBootstrapPhase = resolve
  })
  const runner = createJobRunner({
    batchRepository,
    jobRepository,
    now: createClock(),
    createBatchKey: (() => {
      let index = 0
      return () => `batch-${++index}`
    })(),
    createJobKey: (() => {
      let index = 0
      return () => `job-${++index}`
    })(),
    phases: [
      {
        name: 'bootstrap',
        run: async () => {
          await bootstrapGate
        }
      },
      ...expectedPhaseNames.slice(1).map((name) => ({
        name,
        run: async () => {}
      }))
    ]
  })
  const app = buildApp({
    config: fakeConfig(),
    batchRepository,
    jobRepository,
    jobRunner: runner
  })

  const firstResponse = await request(app).post('/api/jobs').send({
    requestedBy: 'alice',
    batchType: 'demo-seed',
    jobType: 'demo-seed'
  })

  assert.equal(firstResponse.status, 202)

  const secondResponse = await request(app).post('/api/jobs').send({
    requestedBy: 'bob',
    batchType: 'demo-seed',
    jobType: 'demo-seed'
  })

  assert.equal(secondResponse.status, 409)
  assert.deepEqual(secondResponse.body, {
    ok: false,
    activeRun: {
      batchId: firstResponse.body.batch.id,
      jobId: firstResponse.body.job.id,
      state: 'running'
    },
    error: 'job_already_running'
  })

  releaseBootstrapPhase()
  await runner.waitForIdle()

  const jobResponse = await request(app).get(`/api/jobs/${firstResponse.body.job.id}`)

  assert.equal(jobResponse.status, 200)
  assert.equal(jobResponse.body.job.status, 'succeeded')
  assert.deepEqual(
    jobResponse.body.job.summaryJson.phases.map((phase) => phase.name),
    expectedPhaseNames
  )
})

test('second job submission during startup returns a stable conflict payload', async () => {
  let releaseBatchCreate
  const batchCreateGate = new Promise((resolve) => {
    releaseBatchCreate = resolve
  })
  const { batchRepository, jobRepository } = createRepositories({
    batchCreateGate
  })
  const runner = createJobRunner({
    batchRepository,
    jobRepository,
    now: createClock()
  })
  const app = buildApp({
    config: fakeConfig(),
    batchRepository,
    jobRepository,
    jobRunner: runner
  })

  const firstRequest = new Promise((resolve, reject) => {
    request(app)
      .post('/api/jobs')
      .send({
        requestedBy: 'alice',
        batchType: 'demo-seed',
        jobType: 'demo-seed'
      })
      .end((error, response) => {
        if (error) {
          reject(error)
          return
        }

        resolve(response)
      })
  })

  await new Promise((resolve) => setImmediate(resolve))

  const secondResponse = await request(app).post('/api/jobs').send({
    requestedBy: 'bob',
    batchType: 'demo-seed',
    jobType: 'demo-seed'
  })

  assert.equal(secondResponse.status, 409)
  assert.deepEqual(secondResponse.body, {
    ok: false,
    activeRun: {
      batchId: null,
      jobId: null,
      state: 'starting'
    },
    error: 'job_already_running'
  })

  releaseBatchCreate()
  const firstResponse = await firstRequest
  assert.equal(firstResponse.status, 202)
  await runner.waitForIdle()
})

test('missing job lookup returns 404 without string matching heuristics', async () => {
  const { batchRepository, jobRepository } = createRepositories()
  const app = buildApp({
    config: fakeConfig(),
    batchRepository,
    jobRepository,
    jobRunner: {
      async start() {
        throw new Error('not used')
      }
    }
  })

  const response = await request(app).get(`/api/jobs/${metadataId(999)}`)

  assert.equal(response.status, 404)
  assert.deepEqual(response.body, {
    ok: false,
    error: 'job_not_found'
  })
})

test('background lifecycle promise resolves even when failure persistence throws', async () => {
  const { batchRepository, jobRepository, store } = createRepositories({
    failBatchMarkFinishedCount: 1,
    failJobMarkFinishedCount: 1
  })
  const runner = createJobRunner({
    batchRepository,
    jobRepository,
    now: createClock(),
    phases: [
      {
        name: 'bootstrap',
        run: async () => {
          throw new Error('phase exploded')
        }
      }
    ]
  })

  await runner.start({
    requestedBy: 'test-runner',
    batchType: 'demo-seed',
    jobType: 'demo-seed'
  })

  await runner.waitForIdle()

  assert.equal(runner.getActiveRun(), null)
  assert.equal(store.jobs.size, 1)
})

test('reindex phase warnings are persisted in summary metadata without failing the job', async () => {
  const { batchRepository, jobRepository } = createRepositories()
  const autoFillService = {
    async planBatch({ batch, batchId }) {
      return {
        batch: batch ?? { id: batchId },
        plan: {
          needsWork: true,
          phases: [
            {
              name: 'community',
              needsWork: false
            }
          ]
        }
      }
    },
    async runCompletionHooks() {
      return {
        reindexAttempted: true,
        reindexed: false,
        skipped: false,
        warnings: [
          {
            code: 'search_reindex_failed',
            message: 'community-app search reindex failed with status 401'
          }
        ]
      }
    }
  }
  const phases = createAutoFillJobPhases({ autoFillService })
  phases[2] = {
    name: 'write-community',
    run: async ({ context }) => {
      context.generatedRefs = [{ entityType: 'posts', entityKey: 'post-1' }]
    }
  }
  const runner = createJobRunner({
    batchRepository,
    jobRepository,
    now: createClock(),
    phases
  })

  const started = await runner.start({
    requestedBy: 'startup-auto-fill',
    batchType: 'startup-auto-fill',
    jobType: 'startup-auto-fill'
  })

  await runner.waitForIdle()

  const storedBatch = await batchRepository.getById(started.batch.id)
  const storedJob = await jobRepository.getById(started.job.id)
  const reindexPhase = storedJob.summaryJson.phases.find((phase) => phase.name === 'reindex')

  assert.equal(storedBatch.status, 'succeeded')
  assert.equal(storedJob.status, 'succeeded')
  assert.deepEqual(reindexPhase.result, {
    reindexAttempted: true,
    reindexed: false,
    skipped: false,
    warnings: [
      {
        code: 'search_reindex_failed',
        message: 'community-app search reindex failed with status 401'
      }
    ]
  })
})

test('manual auto-fill run keeps UI batch detail coherent with the current run batch', async () => {
  const { batchRepository, jobRepository } = createRepositories()
  const targetsByBatchId = new Map()
  const refsByBatchId = new Map()
  const plannerBatchIds = []
  const writeBatchIds = []
  const autoFillService = {
    async planBatch({ batch, batchId }) {
      plannerBatchIds.push(batchId)
      targetsByBatchId.set(batchId, [
        {
          id: metadataId(900001),
          batchId,
          entityType: 'users',
          targetKey: 'users',
          targetCount: 2,
          payloadJson: null,
          createdAt: '2026-03-24T10:00:00.000Z'
        }
      ])

      return {
        batch: batch ?? { id: batchId },
        plan: {
          batchId,
          phases: [
            {
              name: 'community',
              needsWork: true,
              deficits: {
                users: 2,
                posts: 0,
                comments: 0
              }
            }
          ]
        }
      }
    },
    async writeCommunityPhase({ batchId }) {
      writeBatchIds.push(batchId)
      refsByBatchId.set(batchId, [
        { batchId, entityType: 'users', entityKey: 'user-1', createdAt: '2026-03-24T10:00:00.000Z' },
        { batchId, entityType: 'users', entityKey: 'user-2', createdAt: '2026-03-24T10:00:01.000Z' }
      ])

      return {
        insertedCounts: {
          users: 2,
          posts: 0,
          comments: 0,
          socialFollows: 0,
          socialLikes: 0
        },
        generatedRefs: [
          { entityType: 'users', entityKey: 'user-1' },
          { entityType: 'users', entityKey: 'user-2' }
        ]
      }
    },
    async runCompletionHooks() {
      return {
        reindexAttempted: false,
        reindexed: false,
        skipped: true,
        warnings: []
      }
    }
  }
  const phases = createAutoFillJobPhases({ autoFillService })
  const runner = createJobRunner({
    batchRepository,
    jobRepository,
    now: createClock(),
    phases,
    createBatchKey: () => 'manual-batch-1',
    createJobKey: () => 'manual-job-1'
  })
  const app = buildApp({
    config: fakeConfig(),
    batchRepository,
    jobRepository,
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
      async deleteBatch() {
        throw new Error('not used')
      }
    },
    jobRunner: runner
  })

  const createResponse = await request(app).post('/api/jobs').send({
    requestedBy: 'alice',
    batchType: 'demo-seed',
    jobType: 'demo-seed'
  })

  assert.equal(createResponse.status, 202)

  await runner.waitForIdle()

  const detailResponse = await request(app).get(createResponse.body.polling.batchPath)

  assert.equal(detailResponse.status, 200)
  assert.deepEqual(plannerBatchIds, [createResponse.body.batch.id])
  assert.deepEqual(writeBatchIds, [createResponse.body.batch.id])
  assert.equal(detailResponse.body.batch.id, createResponse.body.batch.id)
  assert.equal(detailResponse.body.latestJob.id, createResponse.body.job.id)
  assert.equal(detailResponse.body.targetSummary.totalCount, 2)
  assert.equal(detailResponse.body.actualSummary.totalCount, 2)
})

test('startup auto-fill run keeps using the prepared stable default batch during phases', async () => {
  const { batchRepository, jobRepository, store } = createRepositories()
  const stableBatch = await batchRepository.create({
    batchKey: 'startup-auto-fill-default',
    batchType: 'startup-auto-fill',
    requestedBy: 'startup-auto-fill',
    createdAt: '2026-03-25T00:00:00.000Z'
  })
  const plannerBatchIds = []
  const writeBatchIds = []
  const autoFillService = {
    async planBatch({ batch, batchId }) {
      plannerBatchIds.push(batchId)
      return {
        batch: batch ?? { id: batchId },
        plan: {
          batchId,
          phases: [
            {
              name: 'community',
              needsWork: true,
              deficits: {
                users: 1,
                posts: 0,
                comments: 0
              }
            }
          ]
        }
      }
    },
    async writeCommunityPhase({ batchId }) {
      writeBatchIds.push(batchId)
      return {
        insertedCounts: {
          users: 1,
          posts: 0,
          comments: 0,
          socialFollows: 0,
          socialLikes: 0
        },
        generatedRefs: [{ entityType: 'users', entityKey: 'user-1' }]
      }
    },
    async runCompletionHooks() {
      return {
        reindexAttempted: false,
        reindexed: false,
        skipped: true,
        warnings: []
      }
    }
  }
  const runner = createJobRunner({
    batchRepository,
    jobRepository,
    now: createClock(),
    phases: createAutoFillJobPhases({ autoFillService }),
    createJobKey: () => 'job-stable-default'
  })

  const prepared = await runner.prepare({
    batch: stableBatch,
    requestedBy: 'startup-auto-fill',
    batchType: 'startup-auto-fill',
    jobType: 'startup-auto-fill'
  })
  const started = await runner.startPrepared(prepared)

  await runner.waitForIdle()

  assert.equal(store.batches.size, 1)
  assert.equal(started.batch.id, stableBatch.id)
  assert.deepEqual(plannerBatchIds, [stableBatch.id])
  assert.deepEqual(writeBatchIds, [stableBatch.id])
})

test('runner persists failed batch and job when startup fails before reaching running state', async () => {
  const { batchRepository, jobRepository } = createRepositories({
    failJobMarkStartedCount: 1
  })
  const runner = createJobRunner({
    batchRepository,
    jobRepository,
    now: createClock(),
    createBatchKey: () => 'batch-startup',
    createJobKey: () => 'job-startup'
  })

  const started = await runner.start({
    requestedBy: 'startup-auto-fill',
    batchType: 'startup-auto-fill',
    jobType: 'startup-auto-fill'
  })

  await runner.waitForIdle()

  const storedBatch = await batchRepository.getById(started.batch.id)
  const storedJob = await jobRepository.getById(started.job.id)

  assert.equal(storedBatch.status, 'failed')
  assert.equal(storedJob.status, 'failed')
  assert.match(storedBatch.errorMessage, /Injected job start failure/)
  assert.match(storedJob.errorMessage, /Injected job start failure/)
})

test('prepared startup run can be marked failed when launch rejects after records are created', async () => {
  const { batchRepository, jobRepository } = createRepositories()
  const runner = createJobRunner({
    batchRepository,
    jobRepository,
    now: createClock(),
    createBatchKey: () => 'batch-conflict',
    createJobKey: (() => {
      let index = 0
      return () => `job-conflict-${++index}`
    })(),
    phases: [
      {
        name: 'bootstrap',
        run: async () => new Promise(() => {})
      }
    ]
  })

  await runner.start({
    requestedBy: 'active-runner',
    batchType: 'demo-seed',
    jobType: 'demo-seed'
  })

  const prepared = await runner.prepare({
    requestedBy: 'startup-auto-fill',
    batchType: 'startup-auto-fill',
    jobType: 'startup-auto-fill'
  })

  await assert.rejects(
    () => runner.startPrepared(prepared),
    /Another job is already/
  )

  await runner.failPrepared(prepared, {
    errorMessage: 'Another job is already running'
  })

  const storedBatch = await batchRepository.getById(prepared.batch.id)
  const storedJob = await jobRepository.getById(prepared.job.id)

  assert.equal(storedBatch.status, 'failed')
  assert.equal(storedJob.status, 'failed')
  assert.equal(storedBatch.errorMessage, 'Another job is already running')
  assert.equal(storedJob.errorMessage, 'Another job is already running')
})

test('prepare can attach a startup job to an existing default batch row', async () => {
  const { batchRepository, jobRepository, store } = createRepositories()
  const stableBatch = await batchRepository.create({
    batchKey: 'startup-auto-fill-default',
    batchType: 'startup-auto-fill',
    requestedBy: 'startup-auto-fill',
    createdAt: '2026-03-25T00:00:00.000Z'
  })
  await batchRepository.markFinished(stableBatch.id, {
    finishedAt: '2026-03-25T00:05:00.000Z',
    status: 'succeeded',
    summaryJson: { phases: [] },
    fromStatuses: ['pending']
  })

  const runner = createJobRunner({
    batchRepository,
    jobRepository,
    now: createClock(),
    createJobKey: () => 'job-stable-default'
  })

  const prepared = await runner.prepare({
    batch: stableBatch,
    requestedBy: 'startup-auto-fill',
    batchType: 'startup-auto-fill',
    jobType: 'startup-auto-fill'
  })

  assert.equal(prepared.batch.id, stableBatch.id)
  assert.equal(prepared.job.batchId, stableBatch.id)
  assert.equal(store.batches.size, 1)
  assert.equal(store.jobs.size, 1)
  assert.equal((await batchRepository.getById(stableBatch.id)).status, 'pending')

  const started = await runner.startPrepared(prepared)
  await runner.waitForIdle()

  assert.equal(started.batch.id, stableBatch.id)
  assert.equal((await batchRepository.getById(stableBatch.id)).status, 'succeeded')
  assert.equal((await jobRepository.getById(prepared.job.id)).batchId, stableBatch.id)
})

test('transactional finalization avoids split terminal state when the first terminal commit fails', async () => {
  const { batchRepository, jobRepository, store, withRepositoriesTransaction } = createRepositories({
    transactional: true,
    terminalTransactionFailureCount: 1
  })
  const runner = createJobRunner({
    batchRepository,
    jobRepository,
    now: createClock(),
    withRepositoriesTransaction,
    createBatchKey: () => 'batch-1',
    createJobKey: () => 'job-1'
  })

  const started = await runner.start({
    requestedBy: 'test-runner',
    batchType: 'demo-seed',
    jobType: 'demo-seed'
  })

  await runner.waitForIdle()

  const storedBatch = await batchRepository.getById(started.batch.id)
  const storedJob = await jobRepository.getById(started.job.id)

  assert.equal(storedBatch.status, 'failed')
  assert.equal(storedJob.status, 'failed')
  assert.notEqual(store.terminalTransactionFailureCount, 1)
})
