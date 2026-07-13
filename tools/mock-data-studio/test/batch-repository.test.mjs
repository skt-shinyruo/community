import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import path from 'node:path'
import test from 'node:test'
import { fileURLToPath } from 'node:url'

import { createBatchRepository } from '../src/batches/batchRepository.mjs'
import { createEntityRefRepository } from '../src/batches/entityRefRepository.mjs'
import { createJobRepository } from '../src/jobs/jobRepository.mjs'
import {
  bootstrapDemoSchema,
  demoMetadataTableStatements
} from '../src/db/bootstrap.mjs'
import { createTargetRepository } from '../src/batches/targetRepository.mjs'
import { bufferToUuid, isUuidV7 } from '../src/db/uuidv7.mjs'

const communityDir = fileURLToPath(new URL('../../../deploy/mysql/community/', import.meta.url))
const demoMetadataSchemaPath = path.join(communityDir, '011_schema_demo_metadata.sql')
const communityBootstrapPath = path.join(communityDir, '001_bootstrap.sh')

function normalizeSql(sql) {
  return sql.replace(/;+\s*$/u, '').trim().replace(/\s+/gu, ' ').toLowerCase()
}

function extractDemoCreateTableStatements(schemaSql) {
  return [
    ...schemaSql.matchAll(/create table if not exists (?:demo_[^(]+|ai_config)\s*\([\s\S]*?\);/giu)
  ].map((match) => match[0])
}

function extractBootstrapOrder(scriptText) {
  const match = scriptText.match(/SCHEMA_FILES=\(([\s\S]*?)\)\n/u)
  assert.ok(match, 'SCHEMA_FILES array missing from community bootstrap script')
  return [...match[1].matchAll(/\s+([0-9]{3}_[A-Za-z0-9_.-]+)\n/gu)].map((entry) => entry[1])
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
    this.ddlStatements = []
    this.tables = {
      demo_batch: [],
      demo_job: [],
      demo_batch_target: [],
      demo_entity_ref: [],
      ai_config: []
    }
    this.nextIds = {
      demo_batch: 1,
      demo_job: 1,
      demo_batch_target: 1,
      demo_entity_ref: 1
    }
    this.failOnTargetKey = null
    this.failOnEntityKey = null
  }

  async execute(sql, params = []) {
    return executeOnState(this, sql, params)
  }

  async query(sql, params = []) {
    return queryOnState(this, sql, params)
  }

  async withTransaction(callback) {
    const snapshot = {
      ddlStatements: [...this.ddlStatements],
      tables: structuredClone(this.tables),
      nextIds: structuredClone(this.nextIds)
    }

    const txState = {
      ddlStatements: [...this.ddlStatements],
      tables: structuredClone(this.tables),
      nextIds: structuredClone(this.nextIds),
      failOnTargetKey: this.failOnTargetKey,
      failOnEntityKey: this.failOnEntityKey
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
      this.ddlStatements = txState.ddlStatements
      this.tables = txState.tables
      this.nextIds = txState.nextIds
      return result
    } catch (error) {
      this.ddlStatements = snapshot.ddlStatements
      this.tables = snapshot.tables
      this.nextIds = snapshot.nextIds
      throw error
    }
  }
}

function executeOnState(state, sql, params) {
  const normalized = normalizeSql(sql)

  if (normalized.startsWith('create table if not exists')) {
    state.ddlStatements.push(sql)
    return { affectedRows: 0 }
  }

  if (normalized.startsWith('insert into ai_config ')) {
    state.tables.ai_config.push({ id: normalizeDbId(params[0]), name: params[1] })
    return { affectedRows: 1 }
  }

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

  if (normalized.startsWith('insert into demo_job ')) {
    const row = {
      id: normalizeDbId(params[0]),
      batch_id: normalizeDbId(params[1]),
      job_key: params[2],
      job_type: params[3],
      status: params[4],
      summary_json: params[5],
      error_message: params[6],
      created_at: params[7],
      started_at: params[8],
      finished_at: params[9]
    }
    state.tables.demo_job.push(row)
    return { affectedRows: 1 }
  }

  if (
    normalized.startsWith(
      'update demo_job set status = ?, started_at = ?, finished_at = null, error_message = null where id = ? and status = ?'
    )
  ) {
    const row = state.tables.demo_job.find(
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
      'update demo_job set status = ?, finished_at = ?, summary_json = ?, error_message = ? where id = ? and status in ('
    )
  ) {
    const expectedStatuses = params.slice(5)
    const row = state.tables.demo_job.find(
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

  if (normalized.startsWith('delete from demo_entity_ref where batch_id = ?')) {
    state.tables.demo_entity_ref = state.tables.demo_entity_ref.filter((row) => row.batch_id !== normalizeDbId(params[0]))
    return { affectedRows: 1 }
  }

  if (normalized.startsWith('insert into demo_entity_ref ')) {
    if (state.failOnEntityKey === params[3]) {
      throw new Error(`Injected entity ref insert failure for ${params[3]}`)
    }

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

  if (normalized === 'select id from ai_config where name = ? limit 1') {
    return state.tables.ai_config
      .filter((row) => row.name === params[0])
      .slice(0, 1)
      .map((row) => ({ id: row.id }))
  }

  if (
    normalized.startsWith(
      'select id, batch_key, batch_type, requested_by, status, summary_json, error_message, created_at, started_at, finished_at from demo_batch where id = ?'
    )
  ) {
    return selectRows(state.tables.demo_batch, (row) => row.id === normalizeDbId(params[0]), 'demo_batch')
  }

  if (
    normalized.startsWith(
      'select id, batch_id, job_key, job_type, status, summary_json, error_message, created_at, started_at, finished_at from demo_job where id = ?'
    )
  ) {
    return selectRows(state.tables.demo_job, (row) => row.id === normalizeDbId(params[0]), 'demo_job')
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
    demo_job: ['created_at', 'started_at', 'finished_at'],
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

test('bootstrapDemoSchema DDL matches the deploy community metadata schema and replay order', async () => {
  const db = new FakeMetadataDb()

  await bootstrapDemoSchema(db)

  const schemaSql = readFileSync(demoMetadataSchemaPath, 'utf8')
  const schemaStatements = extractDemoCreateTableStatements(schemaSql)
  const bootstrapOrder = extractBootstrapOrder(readFileSync(communityBootstrapPath, 'utf8'))

  assert.equal(schemaStatements.length, demoMetadataTableStatements.length)
  assert.deepEqual(
    db.ddlStatements.map(normalizeSql),
    schemaStatements.map(normalizeSql)
  )
  assert.deepEqual(bootstrapOrder.slice(0, 2), ['010_schema_shared.sql', '011_schema_demo_metadata.sql'])
  assert.equal(db.tables.ai_config.length, 1)

  await bootstrapDemoSchema(db)

  assert.equal(db.tables.ai_config.length, 1)
})

test('batch and job repositories return stable ISO timestamps and enforce valid state transitions', async () => {
  const db = new FakeMetadataDb()
  const batchRepository = createBatchRepository(db)
  const jobRepository = createJobRepository(db)
  const targetRepository = createTargetRepository(db)

  const createdAt = new Date('2026-03-24T10:00:00.000Z')
  const startedAt = new Date('2026-03-24T10:01:00.000Z')
  const finishedAt = new Date('2026-03-24T10:02:00.000Z')
  const jobStartedAt = new Date('2026-03-24T10:01:30.000Z')
  const jobFinishedAt = new Date('2026-03-24T10:01:45.000Z')

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

  const job = await jobRepository.create({
    batchId: batch.id,
    jobKey: 'seed-users',
    jobType: 'seed-users',
    createdAt
  })

  assert.ok(isUuidV7(job.id))
  assert.equal(job.createdAt, '2026-03-24T10:00:00.000Z')
  await assert.rejects(
    () =>
      jobRepository.markFinished(job.id, {
        finishedAt: jobFinishedAt,
        status: 'succeeded'
      }),
    /status transition/i
  )

  const startedJob = await jobRepository.markStarted(job.id, {
    startedAt: jobStartedAt
  })

  assert.equal(startedJob.startedAt, '2026-03-24T10:01:30.000Z')

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

  const finishedJob = await jobRepository.markFinished(job.id, {
    finishedAt: jobFinishedAt,
    status: 'succeeded',
    summaryJson: { created: 2 }
  })

  assert.equal(finishedJob.status, 'succeeded')
  assert.equal(finishedJob.finishedAt, '2026-03-24T10:01:45.000Z')
  assert.deepEqual(finishedJob.summaryJson, { created: 2 })

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

test('entity refs are atomic, strict on batchId, and support composite keys', async () => {
  const db = new FakeMetadataDb()
  const entityRefRepository = createEntityRefRepository(db)
  const batchId = metadataId(42)

  await entityRefRepository.replaceForBatch(batchId, [
    {
      entityType: 'user',
      entityKey: '9001',
      createdAt: '2026-03-24T11:00:00.000Z'
    }
  ])

  await assert.rejects(
    () =>
      entityRefRepository.replaceForBatch(batchId, [
        {
          batchId: metadataId(7),
          entityType: 'user',
          entityKey: '9001',
          createdAt: '2026-03-24T11:00:01.000Z'
        }
      ]),
    /must match replaceForBatch batchId/
  )

  db.failOnEntityKey = '9001:17'
  await assert.rejects(
    () =>
      entityRefRepository.replaceForBatch(batchId, [
        {
          entityType: 'user',
          entityKey: '9001',
          createdAt: '2026-03-24T11:00:02.000Z'
        },
        {
          entityType: 'im_room_member',
          entityKey: '9001:17',
          createdAt: '2026-03-24T11:00:03.000Z'
        }
      ]),
    /Injected entity ref insert failure/
  )

  db.failOnEntityKey = null
  const refs = await entityRefRepository.replaceForBatch(batchId, [
    {
      entityType: 'user',
      entityKey: '9001',
      createdAt: '2026-03-24T11:00:04.000Z'
    },
    {
      entityType: 'im_room_member',
      entityKey: '9001:17',
      createdAt: '2026-03-24T11:00:05.000Z'
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
  assert.equal(refs[0].createdAt, '2026-03-24T11:00:04.000Z')
  assert.equal(refs[1].createdAt, '2026-03-24T11:00:05.000Z')
})
