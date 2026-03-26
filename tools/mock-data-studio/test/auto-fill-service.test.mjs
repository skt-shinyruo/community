import assert from 'node:assert/strict'
import test from 'node:test'

import { createAutoFillService } from '../src/jobs/autoFillService.mjs'

function createDuplicateKeyError(message = 'Duplicate entry') {
  const error = new Error(message)
  error.code = 'ER_DUP_ENTRY'
  error.errno = 1062
  return error
}

function createService({
  batchRepository,
  planner,
  communityApi = {
    async reindexSearch() {}
  },
  now = () => '2026-03-25T00:00:00.000Z'
} = {}) {
  const calls = []
  const service = createAutoFillService({
    batchRepository,
    planner,
    communityApi: {
      async reindexSearch() {
        calls.push('reindex')
        return communityApi.reindexSearch()
      }
    },
    now
  })

  return {
    calls,
    service
  }
}

test('auto-fill completion hook skips search reindex when no content-like entities were generated', async () => {
  const { calls, service } = createService()

  const result = await service.runCompletionHooks({
    generatedRefs: [
      { entityType: 'users', entityKey: 'user-1' },
      { entityType: 'users', entityKey: 'user-2' }
    ]
  })

  assert.deepEqual(calls, [])
  assert.deepEqual(result, {
    reindexAttempted: false,
    reindexed: false,
    skipped: true,
    warnings: []
  })
})

test('auto-fill completion hook reindexes search when posts or comments were generated', async () => {
  const { calls, service } = createService()

  const result = await service.runCompletionHooks({
    generatedRefs: [
      { entityType: 'users', entityKey: 'user-1' },
      { entityType: 'posts', entityKey: 'post-1' }
    ]
  })

  assert.deepEqual(calls, ['reindex'])
  assert.deepEqual(result, {
    reindexAttempted: true,
    reindexed: true,
    skipped: false,
    warnings: []
  })
})

test('auto-fill completion hook treats reindex failure as best-effort warning', async () => {
  const { calls, service } = createService({
    communityApi: {
      async reindexSearch() {
        throw new Error('community-app search reindex failed with status 401')
      }
    }
  })

  const result = await service.runCompletionHooks({
    generatedRefs: [{ entityType: 'posts', entityKey: 'post-1' }]
  })

  assert.deepEqual(calls, ['reindex'])
  assert.deepEqual(result, {
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

test('ensureDefaultBatch re-fetches by key after duplicate-key race on create', async () => {
  const storedBatch = {
    id: 42,
    batchKey: 'startup-auto-fill-default',
    batchType: 'startup-auto-fill',
    requestedBy: 'other-runner',
    status: 'pending',
    summaryJson: null,
    errorMessage: null,
    createdAt: '2026-03-25T00:00:00.000Z',
    startedAt: null,
    finishedAt: null
  }
  let findCount = 0
  const batchRepository = {
    async findByBatchKey() {
      findCount += 1
      return findCount === 1 ? null : structuredClone(storedBatch)
    },
    async create() {
      throw createDuplicateKeyError()
    }
  }
  const { service } = createService({
    batchRepository
  })

  const batch = await service.ensureDefaultBatch({
    requestedBy: 'startup-auto-fill'
  })

  assert.deepEqual(batch, storedBatch)
  assert.equal(findCount, 2)
})

test('planDefaultBatch uses the stable default batch id for planning', async () => {
  const storedBatch = {
    id: 42,
    batchKey: 'startup-auto-fill-default',
    batchType: 'startup-auto-fill',
    requestedBy: 'startup-auto-fill',
    status: 'pending',
    summaryJson: null,
    errorMessage: null,
    createdAt: '2026-03-25T00:00:00.000Z',
    startedAt: null,
    finishedAt: null
  }
  const plannerCalls = []
  const { service } = createService({
    batchRepository: {
      async findByBatchKey(batchKey) {
        assert.equal(batchKey, 'startup-auto-fill-default')
        return structuredClone(storedBatch)
      },
      async create() {
        throw new Error('create should not be called when default batch already exists')
      }
    },
    planner: {
      async planDefaultBatch({ batchId, sceneKey }) {
        plannerCalls.push({ batchId, sceneKey })
        return {
          batchId,
          sceneKey: sceneKey ?? 'tech-community-hot-start',
          needsWork: false,
          phases: []
        }
      }
    }
  })

  const result = await service.planDefaultBatch({
    requestedBy: 'startup-auto-fill',
    sceneKey: 'tech-community-hot-start'
  })

  assert.equal(result.batch.id, 42)
  assert.equal(result.plan.batchId, 42)
  assert.deepEqual(plannerCalls, [
    {
      batchId: 42,
      sceneKey: 'tech-community-hot-start'
    }
  ])
})

test('planBatch uses the current run batch id without resolving the stable default batch', async () => {
  const plannerCalls = []
  const { service } = createService({
    batchRepository: {
      async findByBatchKey() {
        throw new Error('findByBatchKey should not be called for current-run planning')
      },
      async create() {
        throw new Error('create should not be called for current-run planning')
      }
    },
    planner: {
      async planDefaultBatch({ batchId, sceneKey }) {
        plannerCalls.push({ batchId, sceneKey })
        return {
          batchId,
          sceneKey,
          needsWork: true,
          phases: [
            {
              name: 'community',
              needsWork: true
            }
          ]
        }
      }
    }
  })

  const result = await service.planBatch({
    batch: {
      id: 77,
      batchKey: 'manual-batch-77'
    },
    batchId: 77,
    sceneKey: 'tech-community-hot-start'
  })

  assert.equal(result.batch.id, 77)
  assert.equal(result.plan.batchId, 77)
  assert.deepEqual(plannerCalls, [
    {
      batchId: 77,
      sceneKey: 'tech-community-hot-start'
    }
  ])
})

test('write phase helpers forward run options to writers', async () => {
  let communityPayload = null
  let imPayload = null
  const service = createAutoFillService({
    communityWriter: {
      async writePhase(payload) {
        communityPayload = payload
        return {
          insertedCounts: {},
          generatedRefs: []
        }
      }
    },
    imWriter: {
      async writePhase(payload) {
        imPayload = payload
        return {
          insertedCounts: {},
          generatedRefs: []
        }
      }
    },
    planner: {
      async planDefaultBatch() {
        return {
          phases: []
        }
      }
    }
  })
  const runOptions = {
    mode: 'manual-generate',
    aiEnhancement: true
  }

  await service.writeCommunityPhase({
    batchId: 7,
    plan: {
      phases: []
    },
    runOptions
  })
  await service.writeImPhase({
    batchId: 7,
    plan: {
      phases: []
    },
    runOptions
  })

  assert.deepEqual(communityPayload.runOptions, runOptions)
  assert.deepEqual(imPayload.runOptions, runOptions)
})
