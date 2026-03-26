import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
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

const schemaPath = fileURLToPath(new URL('../../../deploy/mysql-init/010_schema.sql', import.meta.url))

function normalizeSql(sql) {
  return sql.replace(/;+\s*$/u, '').trim().replace(/\s+/gu, ' ').toLowerCase()
}

function extractDemoCreateTableStatements(schemaSql) {
  return [...schemaSql.matchAll(/create table if not exists demo_[^(]+\([\s\S]*?\);/giu)].map(
    (match) => match[0]
  )
}

function stringifyKey(batchId, entityType, key) {
  return `${batchId}:${entityType}:${key}`
}

class FakeMetadataDb {
  constructor() {
    this.ddlStatements = []
    this.tables = {
      demo_batch: [],
      demo_job: [],
      demo_batch_target: [],
      demo_entity_ref: []
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

  if (normalized.startsWith('insert into demo_batch ')) {
    const row = {
      id: state.nextIds.demo_batch++,
      batch_key: params[0],
      batch_type: params[1],
      requested_by: params[2],
      status: params[3],
      summary_json: params[4],
      error_message: params[5],
      created_at: params[6],
      started_at: params[7],
      finished_at: params[8]
    }
    state.tables.demo_batch.push(row)
    return { insertId: row.id, affectedRows: 1 }
  }

  if (
    normalized.startsWith(
      'update demo_batch set status = ?, started_at = ?, finished_at = null, error_message = null where id = ? and status = ?'
    )
  ) {
    const row = state.tables.demo_batch.find(
      (candidate) => candidate.id === params[2] && candidate.status === params[3]
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
      (candidate) => candidate.id === params[4] && expectedStatuses.includes(candidate.status)
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
      id: state.nextIds.demo_job++,
      batch_id: params[0],
      job_key: params[1],
      job_type: params[2],
      status: params[3],
      summary_json: params[4],
      error_message: params[5],
      created_at: params[6],
      started_at: params[7],
      finished_at: params[8]
    }
    state.tables.demo_job.push(row)
    return { insertId: row.id, affectedRows: 1 }
  }

  if (
    normalized.startsWith(
      'update demo_job set status = ?, started_at = ?, finished_at = null, error_message = null where id = ? and status = ?'
    )
  ) {
    const row = state.tables.demo_job.find(
      (candidate) => candidate.id === params[2] && candidate.status === params[3]
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
      (candidate) => candidate.id === params[4] && expectedStatuses.includes(candidate.status)
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
      (row) => row.batch_id !== params[0]
    )
    return { affectedRows: 1 }
  }

  if (normalized.startsWith('insert into demo_batch_target ')) {
    if (state.failOnTargetKey === params[2]) {
      throw new Error(`Injected target insert failure for ${params[2]}`)
    }

    const compositeKey = stringifyKey(params[0], params[1], params[2])
    state.tables.demo_batch_target = state.tables.demo_batch_target.filter(
      (row) => stringifyKey(row.batch_id, row.entity_type, row.target_key) !== compositeKey
    )

    const row = {
      id: state.nextIds.demo_batch_target++,
      batch_id: params[0],
      entity_type: params[1],
      target_key: params[2],
      target_count: params[3],
      payload_json: params[4],
      created_at: params[5]
    }
    state.tables.demo_batch_target.push(row)
    return { insertId: row.id, affectedRows: 1 }
  }

  if (normalized.startsWith('delete from demo_entity_ref where batch_id = ?')) {
    state.tables.demo_entity_ref = state.tables.demo_entity_ref.filter((row) => row.batch_id !== params[0])
    return { affectedRows: 1 }
  }

  if (normalized.startsWith('insert into demo_entity_ref ')) {
    if (state.failOnEntityKey === params[2]) {
      throw new Error(`Injected entity ref insert failure for ${params[2]}`)
    }

    const compositeKey = stringifyKey(params[0], params[1], params[2])
    state.tables.demo_entity_ref = state.tables.demo_entity_ref.filter(
      (row) => stringifyKey(row.batch_id, row.entity_type, row.entity_key) !== compositeKey
    )

    const row = {
      id: state.nextIds.demo_entity_ref++,
      batch_id: params[0],
      entity_type: params[1],
      entity_key: params[2],
      created_at: params[3]
    }
    state.tables.demo_entity_ref.push(row)
    return { insertId: row.id, affectedRows: 1 }
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
    return selectRows(state.tables.demo_batch, (row) => row.id === params[0], 'demo_batch')
  }

  if (
    normalized.startsWith(
      'select id, batch_id, job_key, job_type, status, summary_json, error_message, created_at, started_at, finished_at from demo_job where id = ?'
    )
  ) {
    return selectRows(state.tables.demo_job, (row) => row.id === params[0], 'demo_job')
  }

  if (
    normalized.startsWith(
      'select id, batch_id, entity_type, target_key, target_count, payload_json, created_at from demo_batch_target where batch_id = ? order by id asc'
    )
  ) {
    return selectRows(
      state.tables.demo_batch_target,
      (row) => row.batch_id === params[0],
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
      (row) => row.batch_id === params[0],
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

test('bootstrapDemoSchema DDL matches the deploy schema for all metadata tables', async () => {
  const db = new FakeMetadataDb()

  await bootstrapDemoSchema(db)

  const schemaSql = readFileSync(schemaPath, 'utf8')
  const schemaStatements = extractDemoCreateTableStatements(schemaSql)

  assert.equal(schemaStatements.length, demoMetadataTableStatements.length)
  assert.deepEqual(
    db.ddlStatements.map(normalizeSql),
    schemaStatements.map(normalizeSql)
  )
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

  assert.deepEqual(replacedTargets, [
    {
      id: 1,
      batchId: batch.id,
      entityType: 'user',
      targetKey: 'users',
      targetCount: 2,
      payloadJson: { source: 'smoke' },
      createdAt: '2026-03-24T10:00:00.000Z'
    }
  ])

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

  await targetRepository.replaceForBatch(42, [
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
      targetRepository.replaceForBatch(42, [
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

  assert.deepEqual(await targetRepository.listByBatchId(42), [
    {
      id: 1,
      batchId: 42,
      entityType: 'user',
      targetKey: 'existing',
      targetCount: 1,
      payloadJson: { source: 'seed' },
      createdAt: '2026-03-24T11:00:00.000Z'
    }
  ])
})

test('entity refs are atomic, strict on batchId, and support composite keys', async () => {
  const db = new FakeMetadataDb()
  const entityRefRepository = createEntityRefRepository(db)

  await entityRefRepository.replaceForBatch(42, [
    {
      entityType: 'user',
      entityKey: '9001',
      createdAt: '2026-03-24T11:00:00.000Z'
    }
  ])

  await assert.rejects(
    () =>
      entityRefRepository.replaceForBatch(42, [
        {
          batchId: 7,
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
      entityRefRepository.replaceForBatch(42, [
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
  const refs = await entityRefRepository.replaceForBatch(42, [
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

  assert.deepEqual(refs, [
    {
      id: 2,
      batchId: 42,
      entityType: 'user',
      entityKey: '9001',
      createdAt: '2026-03-24T11:00:04.000Z'
    },
    {
      id: 3,
      batchId: 42,
      entityType: 'im_room_member',
      entityKey: '9001:17',
      createdAt: '2026-03-24T11:00:05.000Z'
    }
  ])
})
