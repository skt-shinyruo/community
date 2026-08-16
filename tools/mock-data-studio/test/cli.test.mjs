import assert from 'node:assert/strict'
import test from 'node:test'

import { generateBatch, parseCommand } from '../src/cli.mjs'

test('parseCommand uses native argument parsing for generate and delete', () => {
  assert.deepEqual(
    parseCommand(['generate', '--scene', 'im-busy', '--seed', 'demo', '--users', '8', '--posts', '0']),
    {
      name: 'generate',
      scene: 'im-busy',
      seed: 'demo',
      counts: { users: 8, posts: 0 }
    }
  )
  assert.deepEqual(parseCommand(['delete', 'batch-id']), { name: 'delete', batchId: 'batch-id', force: false })
  assert.deepEqual(parseCommand(['delete', '--force', 'batch-id']), { name: 'delete', batchId: 'batch-id', force: true })
  assert.throws(() => parseCommand(['generate', '--users=-1']), /non-negative integer/u)
  assert.throws(() => parseCommand(['delete']), /requires a batch id/u)
})

test('generateBatch runs synchronously, preserves seed, and records one batch summary', async () => {
  const calls = []
  const batchRepository = {
    async create(input) {
      calls.push(['create', input])
      return { id: 'batch-1', status: 'pending' }
    },
    async markStarted(id) {
      calls.push(['started', id])
      return { id, status: 'running' }
    },
    async markFinished(id, update) {
      calls.push(['finished', id, update])
      return { id, ...update }
    }
  }
  const plan = { sceneKey: 'im-busy' }
  const result = await generateBatch({
    options: { scene: 'im-busy', seed: 'stable-seed' },
    config: { autoFill: { sceneKey: 'tech-community-hot-start' }, reindexAuth: { jwtHmacSecret: null } },
    batchRepository,
    planner: { async planDefaultBatch() { return plan } },
    communityWriter: {
      async writePhase(input) {
        assert.equal(input.seed, 'stable-seed')
        return { insertedCounts: { users: 2 }, generatedRefs: [{ entityType: 'posts' }] }
      }
    },
    imWriter: {
      async writePhase(input) {
        assert.equal(input.seed, 'stable-seed')
        return { insertedCounts: { imRooms: 1 }, generatedRefs: [] }
      }
    },
    communityApi: { async reindexSearch() { throw new Error('should not run without a secret') } },
    createBatchKey: () => 'cli-test'
  })

  assert.deepEqual(result, {
    batchId: 'batch-1',
    status: 'succeeded',
    scene: 'im-busy',
    seed: 'stable-seed',
    insertedCounts: { users: 2, imRooms: 1 },
    reindex: { attempted: false, reason: 'missing-reindex-secret' }
  })
  assert.equal(calls.filter(([name]) => name === 'finished').length, 1)
})

test('generateBatch marks a failed synchronous run', async () => {
  const updates = []
  await assert.rejects(
    () => generateBatch({
      options: {},
      config: { autoFill: { sceneKey: 'scene' }, reindexAuth: {} },
      batchRepository: {
        async create() { return { id: 'batch-1' } },
        async markStarted() { return { id: 'batch-1' } },
        async markFinished(_id, update) { updates.push(update) }
      },
      planner: { async planDefaultBatch() { throw new Error('bad plan') } },
      communityWriter: {},
      imWriter: {},
      communityApi: {}
    }),
    /bad plan/u
  )
  assert.equal(updates[0].status, 'failed')
})
