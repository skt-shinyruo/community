export function createAiConfigRepository(db) {
  return {
    async list() {
      const rows = await db.query('select * from ai_config order by created_at desc')
      return (rows || []).map((row) => ({
        id: Number(row.id),
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
      }))
    },

    async getById(id) {
      const rows = await db.query('select * from ai_config where id = ? limit 1', [id])
      if (!rows || rows.length === 0) return null
      const row = rows[0]
      return {
        id: Number(row.id),
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
    },

    async getActive() {
      const rows = await db.query('select * from ai_config where is_active = 1 limit 1')
      if (!rows || rows.length === 0) return null
      const row = rows[0]
      return {
        id: Number(row.id),
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
    },

    async create({ name, provider, baseUrl, apiKey, model, enabled, timeoutMs, maxItemsPerJob }) {
      const [result] = await db.execute(
        `insert into ai_config (name, provider, base_url, api_key, model, enabled, is_active, timeout_ms, max_items_per_job)
         values (?, ?, ?, ?, ?, ?, 0, ?, ?)`,
        [name, provider, baseUrl || null, apiKey || null, model, enabled ? 1 : 0, timeoutMs, maxItemsPerJob]
      )
      return this.getById(Number(result.insertId))
    },

    async update(id, { name, provider, baseUrl, apiKey, model, enabled, timeoutMs, maxItemsPerJob }) {
      await db.execute(
        `update ai_config set
           name = ?, provider = ?, base_url = ?, api_key = ?, model = ?,
           enabled = ?, timeout_ms = ?, max_items_per_job = ?
         where id = ?`,
        [name, provider, baseUrl || null, apiKey || null, model, enabled ? 1 : 0, timeoutMs, maxItemsPerJob, id]
      )
      return this.getById(id)
    },

    async setActive(id) {
      await db.execute('update ai_config set is_active = 0')
      await db.execute('update ai_config set is_active = 1 where id = ?', [id])
      return this.getById(id)
    },

    async delete(id) {
      await db.execute('delete from ai_config where id = ?', [id])
    },

    async testConnection({ provider, baseUrl, apiKey, model, timeoutMs }) {
      const OpenAI = (await import('openai')).default
      const client = new OpenAI({
        apiKey: apiKey || 'ollama',
        baseURL: baseUrl || undefined,
        timeout: timeoutMs || 8000
      })

      try {
        const response = await client.chat.completions.create({
          model: model || 'gpt-4.1-mini',
          messages: [{ role: 'user', content: 'Hi' }],
          max_tokens: 10
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
