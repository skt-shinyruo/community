-- Source: 005_schema_shared.sql
-- --------------------------------------------------------------------
-- Shared schema (community): outbox + HTTP idempotency.
--
-- Notes:
-- - Modular monolith uses a single schema `community`.
-- - These tables are cross-cutting and shared by multiple modules.

use community;

set names utf8mb4;

-- Outbox (reliable event delivery) - shared SSOT.
create table if not exists outbox_event (
  id binary(16) primary key,
  event_id varchar(64) not null,
  topic varchar(255) not null,
  event_key varchar(255) not null,
  payload mediumtext not null,
  status varchar(32) not null,
  retry_count int not null default 0,
  next_retry_at timestamp null default null,
  last_error varchar(255),
  trace_id varchar(32) null,
  traceparent varchar(128) null,
  created_at timestamp not null default current_timestamp,
  updated_at timestamp not null default current_timestamp on update current_timestamp,
  unique key uk_outbox_event_id (event_id),
  index idx_outbox_status_next (status, next_retry_at, id),
  index idx_outbox_status_updated (status, updated_at, id),
  index idx_outbox_status_created (status, created_at, id)
);

-- HTTP write idempotency（SSOT=DB）：same (operation, user_id, idem_key) executes side effects once.
create table if not exists http_idempotency (
  id binary(16) primary key,
  operation varchar(64) not null,
  user_id binary(16) not null,
  idem_key varchar(128) not null,
  request_hash varchar(64) not null,
  status varchar(16) not null,
  response_json mediumtext null,
  processing_expires_at timestamp null,
  success_expires_at timestamp null,
  created_at timestamp null default current_timestamp,
  updated_at timestamp null default current_timestamp on update current_timestamp,
  unique key uk_http_idem (operation, user_id, idem_key),
  key idx_http_idem_processing_expires (processing_expires_at, id),
  key idx_http_idem_success_expires (success_expires_at, id)
);

-- Reliability governance audit. Governance audit records operational actions only;
-- they must not store business payloads, tokens, cookies, or raw user content.
create table if not exists ops_governance_audit (
  id binary(16) primary key,
  action varchar(64) not null,
  actor_user_id binary(16) not null,
  target_type varchar(64) not null,
  target_id varchar(255),
  scope varchar(255),
  reason varchar(512),
  request_json mediumtext,
  result varchar(64) not null,
  summary_json mediumtext,
  trace_id varchar(32),
  created_at timestamp not null default current_timestamp,
  key idx_ops_governance_action_created (action, created_at, id),
  key idx_ops_governance_actor_created (actor_user_id, created_at, id),
  key idx_ops_governance_target_created (target_type, target_id, created_at),
  key idx_ops_governance_result_created (result, created_at, id)
);

-- --------------------------------------------------------------------
