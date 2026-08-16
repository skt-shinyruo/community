import assert from 'node:assert/strict'
import test from 'node:test'

import { createBatchRepository } from '../src/batches/batchRepository.mjs'
import { createEntityRefRepository } from '../src/batches/entityRefRepository.mjs'
import { createTargetRepository } from '../src/batches/targetRepository.mjs'
import { bufferToUuid, isUuidV7 } from '../src/db/uuidv7.mjs'

function normalizeSql(sql) {
  return sql.replace(/;+\s*$/u, '').trim().replace(/\s+/gu, ' ').toLowerCase()
}

function stringifyKey(batchId, entityType, key) {
  return `${batchId}:${entityType}:${key}`
}

function metadataId(sequence) {
  return `01965429-b34a-7000-8000-${String(sequence).padStart(12, '0')}`
}

function normalizeDbId(value) {
  return bufferToUuid(value)
}

class FakeMetadataDb {
  constructor() {
    this.tables = {
      demo_batch: [],
      demo_batch_target: [],
      demo_entity_ref: []
    }
    this.nextIds = {
      demo_batch: 1,
      demo_batch_target: 1,
      demo_entity_ref: 1
    }
    this.failOnTargetKey = null
  }

  async execute(sql, params = []) {
    return executeOnState(this, sql, params)
  }

  async query(sql, params = []) {
    return queryOnState(this, sql, params)
  }

  async withTransaction(callback) {
    const snapshot = {
      tables: structuredClone(this.tables),
      nextIds: structuredClone(this.nextIds)
    }

    const txState = {
      tables: structuredClone(this.tables),
      nextIds: structuredClone(this.nextIds),
      failOnTargetKey: this.failOnTargetKey
    }

    const txDb = {
      execute(sql, params = []) {
        return executeOnState(txState, sql, params)
      },
      query(sql, params = []) {
        return queryOnState(txState, sql, params)
      }
    }

    try {
      const result = await callback(txDb)
      this.tables = txState.tables
      this.nextIds = txState.nextIds
      return result
    } catch (error) {
      this.tables = snapshot.tables
      this.nextIds = snapshot.nextIds
      throw error
    }
  }
}

function executeOnState(state, sql, params) {
  const normalized = normalizeSql(sql)

  if (normalized.startsWith('insert into demo_batch ')) {
    const row = {
      id: normalizeDbId(params[0]),
      batch_key: params[1],
      batch_type: params[2],
      requested_by: params[3],
      status: params[4],
      summary_json: params[5],
      error_message: params[6],
      created_at: params[7],
      started_at: params[8],
      finished_at: params[9]
    }
    state.tables.demo_batch.push(row)
    return { affectedRows: 1 }
  }

  if (
    normalized.startsWith(
      'update demo_batch set status = ?, started_at = ?, finished_at = null, error_message = null where id = ? and status = ?'
    )
  ) {
    const row = state.tables.demo_batch.find(
      (candidate) => candidate.id === normalizeDbId(params[2]) && candidate.status === params[3]
    )

    if (!row) {
      return { affectedRows: 0 }
    }

    row.status = params[0]
    row.started_at = params[1]
    row.finished_at = null
    row.error_message = null
    return { affectedRows: 1 }
  }

  if (
    normalized.startsWith(
      'update demo_batch set status = ?, finished_at = ?, summary_json = ?, error_message = ? where id = ? and status in ('
    )
  ) {
    const expectedStatuses = params.slice(5)
    const row = state.tables.demo_batch.find(
      (candidate) => candidate.id === normalizeDbId(params[4]) && expectedStatuses.includes(candidate.status)
    )

    if (!row) {
      return { affectedRows: 0 }
    }

    row.status = params[0]
    row.finished_at = params[1]
    row.summary_json = params[2]
    row.error_message = params[3]
    return { affectedRows: 1 }
  }

  if (normalized.startsWith('delete from demo_batch_target where batch_id = ?')) {
    state.tables.demo_batch_target = state.tables.demo_batch_target.filter(
      (row) => row.batch_id !== normalizeDbId(params[0])
    )
    return { affectedRows: 1 }
  }

  if (normalized.startsWith('insert into demo_batch_target ')) {
    if (state.failOnTargetKey === params[3]) {
      throw new Error(`Injected target insert failure for ${params[3]}`)
    }

    const compositeKey = stringifyKey(normalizeDbId(params[1]), params[2], params[3])
    state.tables.demo_batch_target = state.tables.demo_batch_target.filter(
      (row) => stringifyKey(row.batch_id, row.entity_type, row.target_key) !== compositeKey
    )

    const row = {
      id: normalizeDbId(params[0]),
      batch_id: normalizeDbId(params[1]),
      entity_type: params[2],
      target_key: params[3],
      target_count: params[4],
      payload_json: params[5],
      created_at: params[6]
    }
    state.tables.demo_batch_target.push(row)
    return { affectedRows: 1 }
  }

  if (normalized.startsWith('insert into demo_entity_ref ')) {
    const compositeKey = stringifyKey(normalizeDbId(params[1]), params[2], params[3])
    state.tables.demo_entity_ref = state.tables.demo_entity_ref.filter(
      (row) => stringifyKey(row.batch_id, row.entity_type, row.entity_key) !== compositeKey
    )

    const row = {
      id: normalizeDbId(params[0]),
      batch_id: normalizeDbId(params[1]),
      entity_type: params[2],
      entity_key: params[3],
      created_at: params[4]
    }
    state.tables.demo_entity_ref.push(row)
    return { affectedRows: 1 }
  }

  throw new Error(`Unexpected SQL: ${sql}`)
}

function queryOnState(state, sql, params) {
  const normalized = normalizeSql(sql)

  if (
    normalized.startsWith(
      'select id, batch_key, batch_type, requested_by, status, summary_json, error_message, created_at, started_at, finished_at from demo_batch where id = ?'
    )
  ) {
    return selectRows(state.tables.demo_batch, (row) => row.id === normalizeDbId(params[0]), 'demo_batch')
  }

  if (
    normalized.startsWith(
      'select id, batch_id, entity_type, target_key, target_count, payload_json, created_at from demo_batch_target where batch_id = ? order by id asc'
    )
  ) {
    return selectRows(
      state.tables.demo_batch_target,
      (row) => row.batch_id === normalizeDbId(params[0]),
      'demo_batch_target'
    )
  }

  if (
    normalized.startsWith(
      'select id, batch_id, entity_type, entity_key, created_at from demo_entity_ref where batch_id = ? order by id asc'
    )
  ) {
    return selectRows(
      state.tables.demo_entity_ref,
      (row) => row.batch_id === normalizeDbId(params[0]),
      'demo_entity_ref'
    )
  }

  throw new Error(`Unexpected SQL: ${sql}`)
}

function selectRows(rows, predicate, tableName) {
  return rows
    .filter(predicate)
    .map((row) => structuredClone(row))
    .map((row) => hydrateTimestamps(tableName, row))
}

function hydrateTimestamps(tableName, row) {
  const timestampColumns = {
    demo_batch: ['created_at', 'started_at', 'finished_at'],
    demo_batch_target: ['created_at'],
    demo_entity_ref: ['created_at']
  }

  for (const column of timestampColumns[tableName]) {
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
