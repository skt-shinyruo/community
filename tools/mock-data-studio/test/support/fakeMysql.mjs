function normalizeSql(sql) {
  return sql.replace(/;+\s*$/u, '').trim().replace(/\s+/gu, ' ').toLowerCase()
}

function tableName(value) {
  return value.replaceAll('`', '').toLowerCase()
}

function splitList(value) {
  return value.split(',').map((part) => part.trim())
}

function equal(left, right, normalize) {
  return normalize(left) === normalize(right)
}

function parseWhere(where, params, normalize) {
  const conditions = where.split(/\s+and\s+/u)
  return (row) => {
    let parameterIndex = 0
    return conditions.every((condition) => {
      const inMatch = condition.match(/^\(?\s*([\w.]+)\s+in\s*\(([^)]+)\)\s*\)?$/u)
      if (inMatch) {
        const column = inMatch[1].split('.').pop()
        const values = splitList(inMatch[2]).map((token) => token === '?' ? params[parameterIndex++] : token)
        return values.some((value) => equal(row[column], value, normalize))
      }
      const match = condition.match(/^\(?\s*([\w.]+)\s*=\s*(\?|[-\d]+|'[^']*'|null)\s*\)?$/u)
      if (!match) throw new Error(`Unsupported fake WHERE condition: ${condition}`)
      const column = match[1].split('.').pop()
      const expected = match[2] === '?' ? params[parameterIndex++] :
        match[2] === 'null' ? null : match[2].startsWith("'") ? match[2].slice(1, -1) : Number(match[2])
      return equal(row[column], expected, normalize)
    })
  }
}

function selectColumns(columns, row) {
  if (columns === '*') return structuredClone(row)
  return Object.fromEntries(splitList(columns).map((part) => {
    const match = part.match(/^(?:[\w.]+\.)?([\w]+)(?:\s+as\s+([\w]+))?$/u)
    if (!match) throw new Error(`Unsupported fake SELECT column: ${part}`)
    const key = match[1]
    return [match[2] ?? key, row[key]]
  }))
}

export class FakeMysqlDb {
  constructor({
    state = {},
    nextIds = {},
    aliases = {},
    autoIds = {},
    uniqueKeys = {},
    normalize = (value) => value,
    normalizeRow = (_table, row) => row,
    queryHandler = null,
    beforeExecute = null,
    recordSnapshot = null
  } = {}) {
    this.state = structuredClone(state)
    this.nextIds = { ...nextIds }
    this.aliases = Object.fromEntries(Object.entries(aliases).map(([key, value]) => [tableName(key), value]))
    this.autoIds = Object.fromEntries(Object.entries(autoIds).map(([key, value]) => [tableName(key), value]))
    this.uniqueKeys = Object.fromEntries(Object.entries(uniqueKeys).map(([key, value]) => [tableName(key), value]))
    this.normalize = normalize
    this.normalizeRow = normalizeRow
    this.queryHandler = queryHandler
    this.beforeExecute = beforeExecute
    this.recordSnapshot = recordSnapshot
  }

  resolveStateKey(table) {
    const normalized = tableName(table)
    return this.aliases[normalized] ?? normalized.split('.').pop()
  }

  async withTransaction(work) {
    const state = structuredClone(this.state)
    const nextIds = structuredClone(this.nextIds)
    try {
      return await work(this)
    } catch (error) {
      this.state = state
      this.nextIds = nextIds
      throw error
    }
  }

  async query(sql, params = []) {
    const normalized = normalizeSql(sql)
    if (this.queryHandler) {
      const custom = await this.queryHandler({ db: this, sql: normalized, params })
      if (custom !== undefined) return custom
    }

    const withoutOrder = normalized.replace(/\s+order by .+$/u, '')
    const match = withoutOrder.match(/^select (.+?) from ([\w.`]+)(?: where (.+))?$/u)
    if (!match) throw new Error(`Unsupported fake query: ${sql}`)
    const rows = this.state[this.resolveStateKey(match[2])] ?? []
    const predicate = match[3] ? parseWhere(match[3], params, this.normalize) : () => true
    return rows.filter(predicate).map((row) => selectColumns(match[1], row))
  }

  async execute(sql, params = []) {
    const normalized = normalizeSql(sql)
    this.beforeExecute?.({ db: this, sql: normalized, params })
    this.recordSnapshot?.({ db: this, sql: normalized, params })

    const insert = normalized.match(/^insert into ([\w.`]+) \(([^)]+)\) values /u)
    if (insert) {
      const table = tableName(insert[1])
      const stateKey = this.resolveStateKey(table)
      const columns = splitList(insert[2])
      const rows = this.state[stateKey] ?? (this.state[stateKey] = [])
      const autoId = this.autoIds[table]
      let insertId = 0
      for (let offset = 0; offset < params.length; offset += columns.length) {
        const row = Object.fromEntries(columns.map((column, index) => [column, params[offset + index]]))
        if (autoId && row[autoId.field] == null) {
          row[autoId.field] = this.nextIds[stateKey] ?? 1
          this.nextIds[stateKey] = row[autoId.field] + 1
          if (insertId === 0) insertId = row[autoId.field]
        }
        const normalizedRow = this.normalizeRow(table, row)
        for (const keys of this.uniqueKeys[table] ?? []) {
          if (rows.some((candidate) => keys.every((key) => equal(candidate[key], normalizedRow[key], this.normalize)))) {
            throw new Error(`Duplicate ${table} ${keys.map((key) => normalizedRow[key]).join(':')}`)
          }
        }
        rows.push(normalizedRow)
      }
      return { affectedRows: params.length === 0 ? 0 : Math.ceil(params.length / columns.length), insertId }
    }

    const update = normalized.match(/^update ([\w.`]+) set (.+?) where (.+)$/u)
    if (update) {
      const stateKey = this.resolveStateKey(update[1])
      const rows = this.state[stateKey] ?? []
      const assignments = splitList(update[2]).map((part) => {
        const match = part.match(/^([\w.]+) = (\?|null|[-\d]+|'[^']*')$/u)
        if (!match) throw new Error(`Unsupported fake UPDATE assignment: ${part}`)
        return { column: match[1].split('.').pop(), value: match[2] }
      })
      const assignmentParameterCount = assignments.filter((assignment) => assignment.value === '?').length
      const predicate = parseWhere(update[3], params.slice(assignmentParameterCount), this.normalize)
      let affectedRows = 0
      for (const row of rows) {
        if (!predicate(row)) continue
        let parameterIndex = 0
        assignments.forEach((assignment) => {
          row[assignment.column] = assignment.value === '?' ? params[parameterIndex++]
            : assignment.value === 'null' ? null
              : assignment.value.startsWith("'") ? assignment.value.slice(1, -1) : Number(assignment.value)
        })
        affectedRows++
      }
      return { affectedRows }
    }

    const deletion = normalized.match(/^delete from ([\w.`]+) where (.+)$/u)
    if (deletion) {
      const stateKey = this.resolveStateKey(deletion[1])
      const rows = this.state[stateKey] ?? []
      const predicate = parseWhere(deletion[2], params, this.normalize)
      const kept = rows.filter((row) => !predicate(row))
      this.state[stateKey] = kept
      return { affectedRows: rows.length - kept.length }
    }

    throw new Error(`Unsupported fake execute: ${sql}`)
  }
}

export function normalizeFakeSql(sql) {
  return normalizeSql(sql)
}
