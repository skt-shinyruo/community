import { formatMysqlTimestamp, toIsoTimestamp } from '../db/mysql.mjs'

function mapEntityRefRow(row) {
  return {
    id: row.id,
    batchId: row.batch_id,
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
    [batchId]
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

async function insertRefs(db, batchId, refs) {
  for (const ref of refs) {
    await db.execute(
      `insert into demo_entity_ref (
        batch_id,
        entity_type,
        entity_key,
        created_at
      ) values (?, ?, ?, ?)`,
      [
        batchId,
        ref.entityType,
        ref.entityKey,
        formatMysqlTimestamp(ref.createdAt ?? new Date().toISOString(), 'demo_entity_ref.createdAt')
      ]
    )
  }
}

export function createEntityRefRepository(db) {
  return {
    async replaceForBatch(batchId, refs) {
      validateRefs(batchId, refs)

      const runInTransaction = db.withTransaction
        ? (work) => db.withTransaction(work)
        : (work) => work(db)

      await runInTransaction(async (txDb) => {
        await txDb.execute(`delete from demo_entity_ref where batch_id = ?`, [batchId])
        await insertRefs(txDb, batchId, refs)
      })

      return listByBatchId(db, batchId)
    },

    async appendForBatch(batchId, refs, { txDb = null } = {}) {
      validateRefs(batchId, refs)

      const runDb = txDb ?? db
      await insertRefs(runDb, batchId, refs)
      return listByBatchId(runDb, batchId)
    },

    async listByBatchId(batchId) {
      return listByBatchId(db, batchId)
    }
  }
}
