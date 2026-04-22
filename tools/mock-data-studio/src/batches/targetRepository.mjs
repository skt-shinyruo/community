import { formatMysqlTimestamp, toIsoTimestamp } from '../db/mysql.mjs'
import { bufferToUuid, generateUuidV7, uuidToBuffer } from '../db/uuidv7.mjs'

function serializeJson(value) {
  return value == null ? null : JSON.stringify(value)
}

function parseJson(value) {
  return value == null ? null : JSON.parse(value)
}

function mapTargetRow(row) {
  return {
    id: bufferToUuid(row.id),
    batchId: bufferToUuid(row.batch_id),
    entityType: row.entity_type,
    targetKey: row.target_key,
    targetCount: row.target_count,
    payloadJson: parseJson(row.payload_json),
    createdAt: toIsoTimestamp(row.created_at, 'demo_batch_target.created_at')
  }
}

async function listByBatchId(db, batchId) {
  const rows = await db.query(
    `select id, batch_id, entity_type, target_key, target_count, payload_json, created_at
       from demo_batch_target
      where batch_id = ?
      order by id asc`,
    [uuidToBuffer(batchId)]
  )

  return rows.map(mapTargetRow)
}

export function createTargetRepository(db, { createId = generateUuidV7 } = {}) {
  return {
    async replaceForBatch(batchId, targets) {
      const runInTransaction = db.withTransaction
        ? (work) => db.withTransaction(work)
        : (work) => work(db)

      await runInTransaction(async (txDb) => {
        await txDb.execute(`delete from demo_batch_target where batch_id = ?`, [uuidToBuffer(batchId)])

        for (const target of targets) {
          await txDb.execute(
            `insert into demo_batch_target (
              id,
              batch_id,
              entity_type,
              target_key,
              target_count,
              payload_json,
              created_at
            ) values (?, ?, ?, ?, ?, ?)`,
            [
              uuidToBuffer(createId()),
              uuidToBuffer(batchId),
              target.entityType,
              target.targetKey,
              target.targetCount,
              serializeJson(target.payloadJson),
              formatMysqlTimestamp(
                target.createdAt ?? new Date().toISOString(),
                'demo_batch_target.createdAt'
              )
            ]
          )
        }
      })

      return listByBatchId(db, batchId)
    },

    async listByBatchId(batchId) {
      return listByBatchId(db, batchId)
    }
  }
}
