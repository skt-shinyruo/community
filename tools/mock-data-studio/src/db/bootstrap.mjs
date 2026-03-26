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
  )`
]

export async function bootstrapDemoSchema(db) {
  for (const statement of demoMetadataTableStatements) {
    await db.execute(statement)
  }
}
