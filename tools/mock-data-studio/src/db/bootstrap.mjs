import { generateUuidV7, uuidToBuffer } from './uuidv7.mjs'

export async function seedDefaultAiConfig(db) {
  const defaultRows = await db.query('select id from ai_config where name = ? limit 1', ['Default'])
  if (!defaultRows || defaultRows.length === 0) {
    await db.execute(
      `insert into ai_config (
        id,
        name,
        provider,
        base_url,
        api_key,
        model,
        enabled,
        is_active,
        timeout_ms,
        max_items_per_job
      ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
      [
        uuidToBuffer(generateUuidV7()),
        'Default',
        'openai',
        null,
        null,
        'gpt-4.1-mini',
        0,
        1,
        8000,
        20
      ]
    )
  }
}
