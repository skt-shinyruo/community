import { bufferToUuid, generateUuidV7, uuidToBuffer } from '../db/uuidv7.mjs'

function mapAiConfigRow(row) {
  return {
    id: bufferToUuid(row.id),
    name: row.name,
    provider: row.provider,
    baseUrl: row.base_url,
    apiKey: row.api_key,
    model: row.model,
    enabled: Boolean(row.enabled),
    isActive: Boolean(row.is_active),
    timeoutMs: Number(row.timeout_ms),
    maxItemsPerJob: Number(row.max_items_per_job),
    createdAt: row.created_at,
    updatedAt: row.updated_at
  }
}

export function createAiConfigRepository(db, { createId = generateUuidV7 } = {}) {
  return {
    async list() {
      const rows = await db.query('select * from ai_config order by created_at desc')
      return (rows || []).map(mapAiConfigRow)
    },

    async getById(id) {
      const rows = await db.query('select * from ai_config where id = ? limit 1', [uuidToBuffer(id)])
      if (!rows || rows.length === 0) return null
      return mapAiConfigRow(rows[0])
    },

    async getActive() {
      const rows = await db.query('select * from ai_config where is_active = 1 limit 1')
      if (!rows || rows.length === 0) return null
      return mapAiConfigRow(rows[0])
    },

    async create({ name, provider, baseUrl, apiKey, model, enabled, timeoutMs, maxItemsPerJob }) {
      const id = createId()
      await db.execute(
        `insert into ai_config (id, name, provider, base_url, api_key, model, enabled, is_active, timeout_ms, max_items_per_job)
         values (?, ?, ?, ?, ?, ?, ?, 0, ?, ?)`,
        [uuidToBuffer(id), name, provider, baseUrl || null, apiKey || null, model, enabled ? 1 : 0, timeoutMs, maxItemsPerJob]
      )
      return this.getById(id)
    },

    async update(id, { name, provider, baseUrl, apiKey, model, enabled, timeoutMs, maxItemsPerJob }) {
      await db.execute(
        `update ai_config set
           name = ?, provider = ?, base_url = ?, api_key = ?, model = ?,
           enabled = ?, timeout_ms = ?, max_items_per_job = ?
         where id = ?`,
        [name, provider, baseUrl || null, apiKey || null, model, enabled ? 1 : 0, timeoutMs, maxItemsPerJob, uuidToBuffer(id)]
      )
      return this.getById(id)
    },

    async setActive(id) {
      await db.execute('update ai_config set is_active = 0')
      await db.execute('update ai_config set is_active = 1 where id = ?', [uuidToBuffer(id)])
      return this.getById(id)
    },

    async delete(id) {
      await db.execute('delete from ai_config where id = ?', [uuidToBuffer(id)])
    },

    async testConnection({ provider, baseUrl, apiKey, model, timeoutMs }) {
      const OpenAI = (await import('openai')).default
      const client = new OpenAI({
        apiKey: apiKey || 'ollama',
        baseURL: baseUrl || undefined,
        timeout: timeoutMs || 8000
      })

      try {
        const response = await client.responses.create({
          model: model || 'gpt-4.1-mini',
          input: 'Hi'
        })

        return {
          success: true,
          message: `连接成功，模型: ${response.model ?? model}`
        }
      } catch (error) {
        return {
          success: false,
          message: error.message || String(error)
        }
      }
    }
  }
}
