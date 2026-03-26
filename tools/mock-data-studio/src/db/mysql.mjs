function formatErrorMessage(error) {
  if (error instanceof Error && error.message) {
    return error.message
  }

  return String(error)
}

function padNumber(value, length = 2) {
  return String(value).padStart(length, '0')
}

function coerceDate(value, label) {
  if (value == null) {
    return null
  }

  if (value instanceof Date) {
    if (Number.isNaN(value.getTime())) {
      throw new Error(`${label} must be a valid timestamp`)
    }

    return value
  }

  if (typeof value === 'string') {
    const normalized = value.trim()

    if (normalized === '') {
      throw new Error(`${label} must be a valid timestamp`)
    }

    const candidate = /^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}(\.\d{1,3})?$/u.test(normalized)
      ? new Date(normalized.replace(' ', 'T') + 'Z')
      : new Date(normalized)

    if (Number.isNaN(candidate.getTime())) {
      throw new Error(`${label} must be a valid timestamp`)
    }

    return candidate
  }

  throw new Error(`${label} must be a valid timestamp`)
}

export function formatMysqlTimestamp(value, label = 'timestamp') {
  const date = coerceDate(value, label)

  if (date == null) {
    return null
  }

  return [
    `${date.getUTCFullYear()}-${padNumber(date.getUTCMonth() + 1)}-${padNumber(date.getUTCDate())}`,
    `${padNumber(date.getUTCHours())}:${padNumber(date.getUTCMinutes())}:${padNumber(date.getUTCSeconds())}.${padNumber(date.getUTCMilliseconds(), 3)}`
  ].join(' ')
}

export function toIsoTimestamp(value, label = 'timestamp') {
  const date = coerceDate(value, label)

  return date == null ? null : date.toISOString()
}

function parseMysqlConnectionUrl(url) {
  const parsed = new URL(url)

  if (parsed.protocol !== 'mysql:') {
    throw new Error(`MOCK_DATA_STUDIO_DB_URL must use the mysql:// protocol`)
  }

  if (parsed.hostname.trim() === '') {
    throw new Error(`MOCK_DATA_STUDIO_DB_URL must include a host`)
  }

  if (parsed.port !== '') {
    const port = Number.parseInt(parsed.port, 10)

    if (!Number.isInteger(port) || port < 1 || port > 65535) {
      throw new Error(`MOCK_DATA_STUDIO_DB_URL must include a valid TCP port`)
    }
  }

  const database = decodeURIComponent(parsed.pathname.replace(/^\//, '').trim())

  if (database === '' || database.includes('/')) {
    throw new Error(`MOCK_DATA_STUDIO_DB_URL must include a database name`)
  }

  return {
    host: parsed.hostname,
    port: parsed.port === '' ? 3306 : Number.parseInt(parsed.port, 10),
    database
  }
}

async function loadMysqlDriver() {
  try {
    return await import('mysql2/promise')
  } catch (error) {
    throw new Error(
      `mock-data-studio requires the mysql2 package for DB bootstrap: ${formatErrorMessage(error)}`
    )
  }
}

function createQueryClient(client) {
  return {
    async execute(sql, params = []) {
      if (params.length === 0) {
        const [result] = await client.query(sql)
        return result
      }

      const [result] = await client.execute(sql, params)
      return result
    },

    async query(sql, params = []) {
      const [rows] = await client.query(sql, params)
      return rows
    }
  }
}

export async function createDb(config) {
  const connection = parseMysqlConnectionUrl(config.db.url)
  const mysql = await loadMysqlDriver()
  const pool = mysql.createPool({
    host: connection.host,
    port: connection.port,
    database: connection.database,
    user: config.db.user,
    password: config.db.password,
    waitForConnections: true,
    connectionLimit: 4,
    queueLimit: 0,
    timezone: 'Z'
  })
  const queryClient = createQueryClient(pool)

  return {
    execute(sql, params = []) {
      return queryClient.execute(sql, params)
    },

    query(sql, params = []) {
      return queryClient.query(sql, params)
    },

    async withTransaction(callback) {
      const connectionClient = await pool.getConnection()

      try {
        await connectionClient.beginTransaction()
        const txDb = createQueryClient(connectionClient)
        const result = await callback(txDb)
        await connectionClient.commit()
        return result
      } catch (error) {
        await connectionClient.rollback()
        throw error
      } finally {
        connectionClient.release()
      }
    },

    async end() {
      await pool.end()
    }
  }
}
