import assert from 'node:assert/strict'
import test from 'node:test'

import { createBatchRepository } from '../src/batches/batchRepository.mjs'
import { createEntityRefRepository } from '../src/batches/entityRefRepository.mjs'
import { createTargetRepository } from '../src/batches/targetRepository.mjs'
import { bufferToUuid, isUuidV7 } from '../src/db/uuidv7.mjs'
import { FakeMysqlDb, normalizeFakeSql } from './support/fakeMysql.mjs'

function metadataId(sequence) {
  return `01965429-b34a-7000-8000-${String(sequence).padStart(12, '0')}`
}

function normalizeDbId(value) {
  return bufferToUuid(value)
}

function normalizeMetadataValue(value) {
  if (value == null) return value
  try {
    return normalizeDbId(value)
  } catch {
    return String(value)
  }
}

class FakeMetadataDb extends FakeMysqlDb {
  constructor() {
    super({
      state: { demo_batch: [], demo_batch_target: [], demo_entity_ref: [] },
      normalize: normalizeMetadataValue,
      normalizeRow: (_table, row) => Object.fromEntries(
        Object.entries(row).map(([key, value]) => [
          key,
          key === 'id' || key === 'batch_id' ? normalizeDbId(value) : value
        ])
      )
    })
    this.failOnTargetKey = null
  }

  async execute(sql, params = []) {
    const normalized = normalizeFakeSql(sql)
    if (normalized.startsWith('insert into demo_batch_target ')
      && this.state.demo_batch_target.some((row) => row.batch_id === normalizeDbId(params[1]))) {
      this.state.demo_batch_target = this.state.demo_batch_target.filter(
        (row) => !(row.batch_id === normalizeDbId(params[1]) && row.entity_type === params[2] && row.target_key === params[3])
      )
    }
    if (normalized.startsWith('insert into demo_entity_ref ')) {
      this.state.demo_entity_ref = this.state.demo_entity_ref.filter(
        (row) => !(row.batch_id === normalizeDbId(params[1]) && row.entity_type === params[2] && row.entity_key === params[3])
      )
    }
    if (normalized.startsWith('insert into demo_batch_target ') && this.failOnTargetKey === params[3]) {
      throw new Error(`Injected target insert failure for ${params[3]}`)
    }
    return super.execute(sql, params)
  }

  async query(sql, params = []) {
    const rows = await super.query(sql, params)
    return rows.map((row) => hydrateTimestamps(normalizeFakeSql(sql), row))
  }
}

function hydrateTimestamps(tableName, row) {
  const timestampColumns = tableName.includes('demo_batch_target')
    ? ['created_at']
    : tableName.includes('demo_entity_ref')
      ? ['created_at']
      : ['created_at', 'started_at', 'finished_at']

  for (const column of timestampColumns) {
    row[column] = hydrateTimestamp(row[column])
  }

  return row
}

function hydrateTimestamp(value) {
  if (value == null) {
    return null
  }

  if (value instanceof Date) {
    return value
  }

  return new Date(String(value).replace(' ', 'T').replace(/$/u, 'Z'))
}

test('batch repository returns stable ISO timestamps and enforces valid state transitions', async () => {
  const db = new FakeMetadataDb()
  const batchRepository = createBatchRepository(db)
  const targetRepository = createTargetRepository(db)

  const createdAt = new Date('2026-03-24T10:00:00.000Z')
  const startedAt = new Date('2026-03-24T10:01:00.000Z')
  const finishedAt = new Date('2026-03-24T10:02:00.000Z')

  const batch = await batchRepository.create({
    batchKey: 'seed-local-demo',
    batchType: 'demo-seed',
    requestedBy: 'test-runner',
    createdAt
  })

  assert.ok(isUuidV7(batch.id))
  assert.equal(batch.status, 'pending')
  assert.equal(batch.createdAt, '2026-03-24T10:00:00.000Z')
  assert.equal(batch.startedAt, null)
  assert.equal(batch.finishedAt, null)

  await assert.rejects(
    () =>
      batchRepository.markFinished(batch.id, {
        finishedAt,
        status: 'succeeded'
      }),
    /status transition/i
  )

  const startedBatch = await batchRepository.markStarted(batch.id, {
    startedAt
  })

  assert.equal(startedBatch.status, 'running')
  assert.equal(startedBatch.startedAt, '2026-03-24T10:01:00.000Z')

  await assert.rejects(
    () => batchRepository.markStarted(batch.id, { startedAt }),
    /status transition/i
  )

  const replacedTargets = await targetRepository.replaceForBatch(batch.id, [
    {
      entityType: 'user',
      targetKey: 'users',
      targetCount: 2,
      payloadJson: { source: 'smoke' },
      createdAt
    }
  ])

  assert.equal(replacedTargets.length, 1)
  assert.ok(isUuidV7(replacedTargets[0].id))
  assert.equal(replacedTargets[0].batchId, batch.id)
  assert.equal(replacedTargets[0].entityType, 'user')
  assert.equal(replacedTargets[0].targetKey, 'users')
  assert.equal(replacedTargets[0].targetCount, 2)
  assert.deepEqual(replacedTargets[0].payloadJson, { source: 'smoke' })
  assert.equal(replacedTargets[0].createdAt, '2026-03-24T10:00:00.000Z')

  const finishedBatch = await batchRepository.markFinished(batch.id, {
    finishedAt,
    status: 'succeeded',
    summaryJson: { created: 3 }
  })

  assert.equal(finishedBatch.status, 'succeeded')
  assert.equal(finishedBatch.finishedAt, '2026-03-24T10:02:00.000Z')
  assert.deepEqual(finishedBatch.summaryJson, { created: 3 })
})

test('targetRepository replaceForBatch is atomic', async () => {
  const db = new FakeMetadataDb()
  const targetRepository = createTargetRepository(db)
  const batchId = metadataId(42)

  await targetRepository.replaceForBatch(batchId, [
    {
      entityType: 'user',
      targetKey: 'existing',
      targetCount: 1,
      payloadJson: { source: 'seed' },
      createdAt: '2026-03-24T11:00:00.000Z'
    }
  ])

  db.failOnTargetKey = 'rooms'

  await assert.rejects(
    () =>
      targetRepository.replaceForBatch(batchId, [
        {
          entityType: 'user',
          targetKey: 'users',
          targetCount: 2,
          payloadJson: { source: 'smoke' },
          createdAt: '2026-03-24T11:00:01.000Z'
        },
        {
          entityType: 'im_room',
          targetKey: 'rooms',
          targetCount: 1,
          payloadJson: { source: 'smoke' },
          createdAt: '2026-03-24T11:00:02.000Z'
        }
      ]),
    /Injected target insert failure/
  )

  const targets = await targetRepository.listByBatchId(batchId)
  assert.equal(targets.length, 1)
  assert.ok(isUuidV7(targets[0].id))
  assert.equal(targets[0].batchId, batchId)
  assert.equal(targets[0].entityType, 'user')
  assert.equal(targets[0].targetKey, 'existing')
  assert.equal(targets[0].targetCount, 1)
  assert.deepEqual(targets[0].payloadJson, { source: 'seed' })
  assert.equal(targets[0].createdAt, '2026-03-24T11:00:00.000Z')
})

test('entity refs append composite keys and reject mismatched batches', async () => {
  const db = new FakeMetadataDb()
  const entityRefRepository = createEntityRefRepository(db)
  const batchId = metadataId(42)

  await entityRefRepository.appendForBatch(batchId, [
    {
      entityType: 'user',
      entityKey: '9001',
      createdAt: '2026-03-24T11:00:00.000Z'
    }
  ])

  await assert.rejects(
    () =>
      entityRefRepository.appendForBatch(batchId, [
        {
          batchId: metadataId(7),
          entityType: 'user',
          entityKey: '9001',
          createdAt: '2026-03-24T11:00:01.000Z'
        }
      ]),
    /must match batchId/
  )

  const refs = await entityRefRepository.appendForBatch(batchId, [
    {
      entityType: 'im_room_member',
      entityKey: '9001:17',
      createdAt: '2026-03-24T11:00:01.000Z'
    }
  ])

  assert.equal(refs.length, 2)
  assert.ok(isUuidV7(refs[0].id))
  assert.ok(isUuidV7(refs[1].id))
  assert.equal(refs[0].batchId, batchId)
  assert.equal(refs[1].batchId, batchId)
  assert.equal(refs[0].entityType, 'user')
  assert.equal(refs[1].entityType, 'im_room_member')
  assert.equal(refs[0].entityKey, '9001')
  assert.equal(refs[1].entityKey, '9001:17')
  assert.equal(refs[0].createdAt, '2026-03-24T11:00:00.000Z')
  assert.equal(refs[1].createdAt, '2026-03-24T11:00:01.000Z')
})
