export const demoMetadataTableStatements = [
  `create table if not exists demo_batch (
    id bigint auto_increment primary key,
    batch_key varchar(128) not null,
    batch_type varchar(64) not null,
    requested_by varchar(128) not null,
    status varchar(32) not null,
    summary_json mediumtext null,
    error_message varchar(255) null,
    created_at timestamp not null default current_timestamp,
    started_at timestamp null default null,
    finished_at timestamp null default null,
    unique key uk_demo_batch_key (batch_key),
    key idx_demo_batch_status_created (status, created_at, id)
  )`,
  `create table if not exists demo_job (
    id bigint auto_increment primary key,
    batch_id bigint not null,
    job_key varchar(128) not null,
    job_type varchar(64) not null,
    status varchar(32) not null,
    summary_json mediumtext null,
    error_message varchar(255) null,
    created_at timestamp not null default current_timestamp,
    started_at timestamp null default null,
    finished_at timestamp null default null,
    unique key uk_demo_job_batch_key (batch_id, job_key),
    key idx_demo_job_batch_status (batch_id, status, id)
  )`,
  `create table if not exists demo_batch_target (
    id bigint auto_increment primary key,
    batch_id bigint not null,
    entity_type varchar(64) not null,
    target_key varchar(255) not null,
    target_count int not null default 1,
    payload_json mediumtext null,
    created_at timestamp not null default current_timestamp,
    unique key uk_demo_batch_target (batch_id, entity_type, target_key),
    key idx_demo_batch_target_batch (batch_id, id)
  )`,
  `create table if not exists demo_entity_ref (
    id bigint auto_increment primary key,
    batch_id bigint not null,
    entity_type varchar(64) not null,
    entity_key varchar(255) not null,
    created_at timestamp not null default current_timestamp,
    unique key uk_demo_entity_ref (batch_id, entity_type, entity_key),
    key idx_demo_entity_ref_batch (batch_id, id)
  )`,
  `create table if not exists ai_config (
    id bigint auto_increment primary key,
    name varchar(64) not null,
    provider varchar(32) not null default 'openai',
    base_url varchar(255) null,
    api_key varchar(512) null,
    model varchar(128) not null default 'gpt-4.1-mini',
    enabled tinyint(1) not null default 0,
    is_active tinyint(1) not null default 0,
    timeout_ms int not null default 8000,
    max_items_per_job int not null default 20,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp on update current_timestamp
  )`
]

export const aiConfigDefaultStatement = `insert ignore into ai_config (name, provider, base_url, api_key, model, enabled, is_active, timeout_ms, max_items_per_job) values ('Default', 'openai', null, null, 'gpt-4.1-mini', 0, 1, 8000, 20)`

export async function bootstrapDemoSchema(db) {
  for (const statement of demoMetadataTableStatements) {
    await db.execute(statement)
  }

  const columns = await db.query('show columns from ai_config')
  const columnNames = new Set((columns || []).map((c) => c.Field || c.field))

  if (!columnNames.has('name')) {
    await db.execute('alter table ai_config add column name varchar(64) not null default \'\' after id')
  }
  if (!columnNames.has('is_active')) {
    await db.execute('alter table ai_config add column is_active tinyint(1) not null default 0 after enabled')
  }
  if (!columnNames.has('created_at')) {
    await db.execute('alter table ai_config add column created_at timestamp not null default current_timestamp after max_items_per_job')
  }

  try {
    await db.execute('alter table ai_config drop index uk_ai_config_singleton')
  } catch {
  }

  await db.execute(aiConfigDefaultStatement)

  try {
    await db.execute("update ai_config set name = 'Default' where name = ''")
  } catch {
  }
  try {
    await db.execute("update ai_config set is_active = 1 where id = 1 and is_active = 0")
  } catch {
  }
}
