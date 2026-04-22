import { formatMysqlTimestamp, toIsoTimestamp } from '../db/mysql.mjs'
import { bufferToUuid, generateUuidV7, uuidToBuffer } from '../db/uuidv7.mjs'

function mapEntityRefRow(row) {
  return {
    id: bufferToUuid(row.id),
    batchId: bufferToUuid(row.batch_id),
    entityType: row.entity_type,
    entityKey: row.entity_key,
    createdAt: toIsoTimestamp(row.created_at, 'demo_entity_ref.created_at')
  }
}

async function listByBatchId(db, batchId) {
  const rows = await db.query(
    `select id, batch_id, entity_type, entity_key, created_at
       from demo_entity_ref
      where batch_id = ?
      order by id asc`,
    [uuidToBuffer(batchId)]
  )

  return rows.map(mapEntityRefRow)
}

function validateRefs(batchId, refs) {
  for (const ref of refs) {
    if (ref.batchId != null && ref.batchId !== batchId) {
      throw new Error(
        `demo_entity_ref batchId must match replaceForBatch batchId: expected ${batchId}, received ${ref.batchId}`
      )
    }
  }
}

async function insertRefs(db, batchId, refs, createId) {
  for (const ref of refs) {
    await db.execute(
      `insert into demo_entity_ref (
        id,
        batch_id,
        entity_type,
        entity_key,
        created_at
      ) values (?, ?, ?, ?, ?)`,
      [
        uuidToBuffer(createId()),
        uuidToBuffer(batchId),
        ref.entityType,
        ref.entityKey,
        formatMysqlTimestamp(ref.createdAt ?? new Date().toISOString(), 'demo_entity_ref.createdAt')
      ]
    )
  }
}

export function createEntityRefRepository(db, { createId = generateUuidV7 } = {}) {
  return {
    async replaceForBatch(batchId, refs) {
      validateRefs(batchId, refs)

      const runInTransaction = db.withTransaction
        ? (work) => db.withTransaction(work)
        : (work) => work(db)

      await runInTransaction(async (txDb) => {
        await txDb.execute(`delete from demo_entity_ref where batch_id = ?`, [uuidToBuffer(batchId)])
        await insertRefs(txDb, batchId, refs, createId)
      })

      return listByBatchId(db, batchId)
    },

    async appendForBatch(batchId, refs, { txDb = null } = {}) {
      validateRefs(batchId, refs)

      const runDb = txDb ?? db
      await insertRefs(runDb, batchId, refs, createId)
      return listByBatchId(runDb, batchId)
    },

    async listByBatchId(batchId) {
      return listByBatchId(db, batchId)
    }
  }
}
