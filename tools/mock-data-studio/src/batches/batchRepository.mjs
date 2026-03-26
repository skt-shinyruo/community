import { formatMysqlTimestamp, toIsoTimestamp } from '../db/mysql.mjs'

function serializeJson(value) {
  return value == null ? null : JSON.stringify(value)
}

function parseJson(value) {
  return value == null ? null : JSON.parse(value)
}

function mapBatchRow(row) {
  return {
    id: row.id,
    batchKey: row.batch_key,
    batchType: row.batch_type,
    requestedBy: row.requested_by,
    status: row.status,
    summaryJson: parseJson(row.summary_json),
    errorMessage: row.error_message,
    createdAt: toIsoTimestamp(row.created_at, 'demo_batch.created_at'),
    startedAt: toIsoTimestamp(row.started_at, 'demo_batch.started_at'),
    finishedAt: toIsoTimestamp(row.finished_at, 'demo_batch.finished_at')
  }
}

function createMissingBatchError(batchId) {
  const error = new Error(`Missing demo_batch row ${batchId}`)
  error.code = 'BATCH_NOT_FOUND'
  error.batchId = batchId
  return error
}

async function findBatchById(db, batchId) {
  const rows = await db.query(
    `select id, batch_key, batch_type, requested_by, status, summary_json, error_message, created_at, started_at, finished_at
       from demo_batch
      where id = ?`,
    [batchId]
  )

  if (rows.length === 0) {
    return null
  }

  return mapBatchRow(rows[0])
}

async function requireBatchById(db, batchId) {
  const batch = await findBatchById(db, batchId)

  if (!batch) {
    throw createMissingBatchError(batchId)
  }

  return batch
}

async function findBatchByKey(db, batchKey) {
  const rows = await db.query(
    `select id, batch_key, batch_type, requested_by, status, summary_json, error_message, created_at, started_at, finished_at
       from demo_batch
      where batch_key = ?
      limit 1`,
    [batchKey]
  )

  if (rows.length === 0) {
    return null
  }

  return mapBatchRow(rows[0])
}

async function listAllBatches(db) {
  const rows = await db.query(
    `select id, batch_key, batch_type, requested_by, status, summary_json, error_message, created_at, started_at, finished_at
       from demo_batch
      order by created_at desc, id desc`
  )

  return rows.map(mapBatchRow)
}

export function createBatchRepository(db) {
  return {
    async create({ batchKey, batchType, requestedBy, createdAt = new Date().toISOString() }) {
      const result = await db.execute(
        `insert into demo_batch (
          batch_key,
          batch_type,
          requested_by,
          status,
          summary_json,
          error_message,
          created_at,
          started_at,
          finished_at
        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?)`,
        [
          batchKey,
          batchType,
          requestedBy,
          'pending',
          null,
          null,
          formatMysqlTimestamp(createdAt, 'demo_batch.createdAt'),
          null,
          null
        ]
      )

      return requireBatchById(db, result.insertId)
    },

    async markStarted(batchId, { startedAt = new Date().toISOString(), status = 'running' } = {}) {
      const result = await db.execute(
        `update demo_batch
            set status = ?, started_at = ?, finished_at = null, error_message = null
          where id = ? and status = ?`,
        [status, formatMysqlTimestamp(startedAt, 'demo_batch.startedAt'), batchId, 'pending']
      )

      if (result.affectedRows !== 1) {
        throw new Error(`demo_batch status transition failed for ${batchId}: expected pending`)
      }

      return requireBatchById(db, batchId)
    },

    async markPrepared(batchId) {
      const result = await db.execute(
        `update demo_batch
            set status = ?, summary_json = null, error_message = null, started_at = null, finished_at = null
          where id = ?`,
        ['pending', batchId]
      )

      if (result.affectedRows !== 1) {
        throw createMissingBatchError(batchId)
      }

      return requireBatchById(db, batchId)
    },

    async markFinished(
      batchId,
      {
        finishedAt = new Date().toISOString(),
        status,
        summaryJson = null,
        errorMessage = null,
        fromStatuses = ['running']
      } = {}
    ) {
      const normalizedStatuses = [...new Set(fromStatuses)]
      const statusPlaceholders = normalizedStatuses.map(() => '?').join(', ')
      const result = await db.execute(
        `update demo_batch
            set status = ?, finished_at = ?, summary_json = ?, error_message = ?
          where id = ? and status in (${statusPlaceholders})`,
        [
          status,
          formatMysqlTimestamp(finishedAt, 'demo_batch.finishedAt'),
          serializeJson(summaryJson),
          errorMessage,
          batchId,
          ...normalizedStatuses
        ]
      )

      if (result.affectedRows !== 1) {
        throw new Error(
          `demo_batch status transition failed for ${batchId}: expected ${normalizedStatuses.join(' or ')}`
        )
      }

      return requireBatchById(db, batchId)
    },

    async getById(batchId) {
      return requireBatchById(db, batchId)
    },

    async findById(batchId) {
      return findBatchById(db, batchId)
    },

    async findByBatchKey(batchKey) {
      return findBatchByKey(db, batchKey)
    },

    async listAll() {
      return listAllBatches(db)
    }
  }
}
