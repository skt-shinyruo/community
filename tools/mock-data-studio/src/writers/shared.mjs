import { formatMysqlTimestamp } from '../db/mysql.mjs'

export function formatBulkInsert(tableName, columns, rowCount) {
  const valueGroup = `(${columns.map(() => '?').join(', ')})`
  return `insert into ${tableName} (${columns.join(', ')}) values ${Array.from({ length: rowCount }, () => valueGroup).join(', ')}`
}

export function toInsertParams(rows) {
  return rows.flatMap((row) => row)
}

export function createGeneratedRef(entityType, entityKey, createdAt) {
  return {
    entityType,
    entityKey: String(entityKey),
    createdAt
  }
}

export function buildTimestampSource(now, label) {
  return () => {
    const timestamp = now()
    return {
      iso: timestamp,
      mysql: formatMysqlTimestamp(timestamp, label)
    }
  }
}

export async function appendEntityRefs({ entityRefRepository, batchId, refs, txDb }) {
  if (refs.length === 0) {
    return []
  }

  await entityRefRepository.appendForBatch(batchId, refs, { txDb })
  return refs
}
