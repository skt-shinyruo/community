import { formatMysqlTimestamp, toIsoTimestamp } from '../db/mysql.mjs'
import { bufferToUuid, generateUuidV7, uuidToBuffer } from '../db/uuidv7.mjs'

function serializeJson(value) {
  return value == null ? null : JSON.stringify(value)
}

function parseJson(value) {
  return value == null ? null : JSON.parse(value)
}

function mapJobRow(row) {
  return {
    id: bufferToUuid(row.id),
    batchId: bufferToUuid(row.batch_id),
    jobKey: row.job_key,
    jobType: row.job_type,
    status: row.status,
    summaryJson: parseJson(row.summary_json),
    errorMessage: row.error_message,
    createdAt: toIsoTimestamp(row.created_at, 'demo_job.created_at'),
    startedAt: toIsoTimestamp(row.started_at, 'demo_job.started_at'),
    finishedAt: toIsoTimestamp(row.finished_at, 'demo_job.finished_at')
  }
}

function createMissingJobError(jobId) {
  const error = new Error(`Missing demo_job row ${jobId}`)
  error.code = 'JOB_NOT_FOUND'
  error.jobId = jobId
  return error
}

async function findJobById(db, jobId) {
  const rows = await db.query(
    `select id, batch_id, job_key, job_type, status, summary_json, error_message, created_at, started_at, finished_at
       from demo_job
      where id = ?`,
    [uuidToBuffer(jobId)]
  )

  if (rows.length === 0) {
    return null
  }

  return mapJobRow(rows[0])
}

async function requireJobById(db, jobId) {
  const job = await findJobById(db, jobId)

  if (!job) {
    throw createMissingJobError(jobId)
  }

  return job
}

async function listJobsByBatchId(db, batchId) {
  const rows = await db.query(
    `select id, batch_id, job_key, job_type, status, summary_json, error_message, created_at, started_at, finished_at
       from demo_job
      where batch_id = ?
      order by created_at desc, id desc`,
    [uuidToBuffer(batchId)]
  )

  return rows.map(mapJobRow)
}

export function createJobRepository(db, { createId = generateUuidV7 } = {}) {
  return {
    async create({ batchId, jobKey, jobType, createdAt = new Date().toISOString() }) {
      const id = createId()
      await db.execute(
        `insert into demo_job (
          id,
          batch_id,
          job_key,
          job_type,
          status,
          summary_json,
          error_message,
          created_at,
          started_at,
          finished_at
        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?)`,
        [
          uuidToBuffer(id),
          uuidToBuffer(batchId),
          jobKey,
          jobType,
          'pending',
          null,
          null,
          formatMysqlTimestamp(createdAt, 'demo_job.createdAt'),
          null,
          null
        ]
      )

      return requireJobById(db, id)
    },

    async markStarted(jobId, { startedAt = new Date().toISOString(), status = 'running' } = {}) {
      const result = await db.execute(
        `update demo_job
            set status = ?, started_at = ?, finished_at = null, error_message = null
          where id = ? and status = ?`,
        [status, formatMysqlTimestamp(startedAt, 'demo_job.startedAt'), uuidToBuffer(jobId), 'pending']
      )

      if (result.affectedRows !== 1) {
        throw new Error(`demo_job status transition failed for ${jobId}: expected pending`)
      }

      return requireJobById(db, jobId)
    },

    async markFinished(
      jobId,
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
        `update demo_job
            set status = ?, finished_at = ?, summary_json = ?, error_message = ?
          where id = ? and status in (${statusPlaceholders})`,
        [
          status,
          formatMysqlTimestamp(finishedAt, 'demo_job.finishedAt'),
          serializeJson(summaryJson),
          errorMessage,
          uuidToBuffer(jobId),
          ...normalizedStatuses
        ]
      )

      if (result.affectedRows !== 1) {
        throw new Error(
          `demo_job status transition failed for ${jobId}: expected ${normalizedStatuses.join(' or ')}`
        )
      }

      return requireJobById(db, jobId)
    },

    async updateSummary(jobId, { summaryJson = null, errorMessage = null } = {}) {
      const result = await db.execute(
        `update demo_job
            set summary_json = ?, error_message = ?
          where id = ? and status = ?`,
        [serializeJson(summaryJson), errorMessage, uuidToBuffer(jobId), 'running']
      )

      if (result.affectedRows !== 1) {
        throw new Error(`demo_job status transition failed for ${jobId}: expected running`)
      }

      return requireJobById(db, jobId)
    },

    async getById(jobId) {
      return requireJobById(db, jobId)
    },

    async findById(jobId) {
      return findJobById(db, jobId)
    },

    async listByBatchId(batchId) {
      return listJobsByBatchId(db, batchId)
    }
  }
}
