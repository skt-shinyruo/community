-- Community-owned schema baseline.
--
-- This migration is immutable after release. Add V002+ for every later change.
-- It intentionally excludes im_core, community_oss, third-party schemas, and development users.
-- The runner selects the target database through its JDBC URL; do not add a USE statement.

-- Imported from deploy/mysql/community/010_schema_shared.sql; frozen into V001 on 2026-07-15.
-- Source: 005_schema_shared.sql
-- --------------------------------------------------------------------
-- Shared schema (community): outbox + HTTP idempotency.
--
-- Notes:
-- - Modular monolith uses a single schema `community`.
-- - These tables are cross-cutting and shared by multiple modules.


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

-- Imported from deploy/mysql/community/011_schema_demo_metadata.sql; frozen into V001 on 2026-07-15.
-- Source: 007_schema_demo_metadata.sql
-- --------------------------------------------------------------------
-- Mock-data metadata sidecar tables (community).
--
-- Notes:
-- - Phase 1 keeps demo tracking in sidecar metadata tables.
-- - We deliberately avoid adding demo_batch_id columns to business tables.


create table if not exists demo_batch (
  id binary(16) primary key,
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
);

create table if not exists demo_job (
  id binary(16) primary key,
  batch_id binary(16) not null,
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
);

create table if not exists demo_batch_target (
  id binary(16) primary key,
  batch_id binary(16) not null,
  entity_type varchar(64) not null,
  target_key varchar(255) not null,
  target_count int not null default 1,
  payload_json mediumtext null,
  created_at timestamp not null default current_timestamp,
  unique key uk_demo_batch_target (batch_id, entity_type, target_key),
  key idx_demo_batch_target_batch (batch_id, id)
);

create table if not exists demo_entity_ref (
  id binary(16) primary key,
  batch_id binary(16) not null,
  entity_type varchar(64) not null,
  entity_key varchar(255) not null,
  created_at timestamp not null default current_timestamp,
  unique key uk_demo_entity_ref (batch_id, entity_type, entity_key),
  key idx_demo_entity_ref_batch (batch_id, id)
);

create table if not exists ai_config (
  id binary(16) primary key,
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
);


-- --------------------------------------------------------------------

-- Imported from deploy/mysql/community/020_schema_identity.sql; frozen into V001 on 2026-07-15.
-- Source: 010_schema_identity.sql
-- --------------------------------------------------------------------
-- Identity schema (keep in MYSQL_DATABASE=community for P0).
-- NOTE:
-- - 身份域数据所有权已收敛到 user 模块；
-- - auth 模块已不再直连 MySQL（通过调用 user 模块内部 API 完成鉴权/注册/邮箱验证/重置密码等）。


create table if not exists user (
  id binary(16) primary key,
  username varchar(255) not null,
  password varchar(255),
  salt varchar(255),
  email varchar(255),
  type int not null default 0,
  status int default 0,
  header_url varchar(255),
  create_time timestamp null default current_timestamp,
  mute_until timestamp null default null,
  ban_until timestamp null default null,
  policy_version bigint not null default 0,
  security_version bigint not null default 0,
  unique key uk_user_username (username),
  unique key uk_user_email (email),
  constraint ck_user_type check (type in (0, 1, 2))
);

set @user_security_seed_version := cast(floor(unix_timestamp(current_timestamp(3)) * 1000) * 4096 as unsigned);

-- refresh token（SSOT=DB）：auth 模块不直连 MySQL，改由 user 模块托管会话状态
-- 注意：只存 token_hash（SHA-256 hex），避免明文凭据落库
create table if not exists auth_refresh_token (
  token_hash char(64) primary key,
  user_id binary(16) not null,
  family_id varchar(64) not null,
  expires_at timestamp not null,
  state varchar(32) not null default 'ACTIVE',
  pending_expires_at timestamp null default null,
  revoked_at timestamp null default null,
  created_at timestamp null default current_timestamp,
  constraint ck_auth_refresh_token_state check (state in ('ACTIVE', 'PENDING_ROTATION', 'CONSUMED', 'REVOKED')),
  key idx_refresh_family (family_id, expires_at),
  key idx_refresh_user (user_id, expires_at),
  key idx_refresh_state_pending (state, pending_expires_at)
);

create table if not exists auth_refresh_token_family_revocation (
  family_id varchar(64) primary key,
  revoked_at timestamp not null default current_timestamp
);

-- 用户处罚类事件幂等：以 event_id 唯一约束为准（insert-first）。
create table if not exists user_consumed_event (
  id binary(16) primary key,
  event_id varchar(64) not null,
  consumed_at timestamp not null default current_timestamp,
  unique key uk_user_consumed_event_id (event_id),
  index idx_user_consumed_event_at (consumed_at)
);

create table if not exists user_policy_version_counter (
  id int primary key,
  current_version bigint not null default 0
);

set @user_policy_current_version := (
  select coalesce(max(policy_version), 0) from user
);

insert into user_policy_version_counter(id, current_version)
values (1, @user_policy_current_version)
on duplicate key update current_version = greatest(current_version, values(current_version));

create table if not exists user_security_version_counter (
  id int primary key,
  current_version bigint not null default 0
);

set @user_security_current_version := greatest(
  @user_security_seed_version,
  (select coalesce(max(security_version), 0) from user)
);

insert into user_security_version_counter(id, current_version)
values (1, @user_security_current_version)
on duplicate key update current_version = greatest(current_version, values(current_version));


-- --------------------------------------------------------------------

-- Imported from deploy/mysql/community/031_schema_growth_wallet.sql; frozen into V001 on 2026-07-15.
-- Source: 015_schema_growth.sql (wallet split)
-- --------------------------------------------------------------------
-- Wallet schema (community): balance accounts, transactions, and wallet orders.


create table if not exists wallet_account (
  account_id binary(16) primary key,
  owner_type varchar(32) not null,
  owner_id binary(16) not null,
  account_type varchar(32) not null,
  balance bigint not null default 0,
  status varchar(16) not null,
  version bigint not null default 0,
  create_time timestamp null default current_timestamp,
  update_time timestamp null default current_timestamp on update current_timestamp,
  unique key uk_wallet_account_owner (owner_type, owner_id, account_type)
);

create table if not exists wallet_txn (
  txn_id binary(16) primary key,
  request_id varchar(128) not null,
  txn_type varchar(32) not null,
  biz_type varchar(32) not null,
  biz_id varchar(128) not null,
  status varchar(16) not null,
  amount bigint not null,
  remark varchar(255) default null,
  create_time timestamp null default current_timestamp,
  update_time timestamp null default current_timestamp on update current_timestamp,
  unique key uk_wallet_txn_request (request_id)
);

create table if not exists wallet_entry (
  entry_id binary(16) primary key,
  txn_id binary(16) not null,
  account_id binary(16) not null,
  direction varchar(8) not null,
  amount bigint not null,
  balance_after bigint not null,
  create_time timestamp null default current_timestamp,
  key idx_wallet_entry_txn (txn_id),
  key idx_wallet_entry_account_time (account_id, create_time)
);

create table if not exists recharge_order (
  order_id binary(16) primary key,
  request_id varchar(128) not null,
  user_id binary(16) not null,
  amount bigint not null,
  status varchar(16) not null,
  channel varchar(32) default null,
  channel_order_id varchar(96) default null,
  remark varchar(255) default null,
  create_time timestamp null default current_timestamp,
  update_time timestamp null default current_timestamp on update current_timestamp,
  unique key uk_recharge_order_user_request (user_id, request_id),
  key idx_recharge_order_user_time (user_id, create_time)
);

create table if not exists withdraw_order (
  order_id binary(16) primary key,
  request_id varchar(128) not null,
  user_id binary(16) not null,
  amount bigint not null,
  status varchar(16) not null,
  payee_account varchar(128) default null,
  failure_reason varchar(255) default null,
  create_time timestamp null default current_timestamp,
  update_time timestamp null default current_timestamp on update current_timestamp,
  unique key uk_withdraw_order_user_request (user_id, request_id),
  key idx_withdraw_order_user_time (user_id, create_time)
);

create table if not exists transfer_order (
  order_id binary(16) primary key,
  request_id varchar(128) not null,
  from_user_id binary(16) not null,
  to_user_id binary(16) not null,
  amount bigint not null,
  status varchar(16) not null,
  remark varchar(255) default null,
  create_time timestamp null default current_timestamp,
  update_time timestamp null default current_timestamp on update current_timestamp,
  unique key uk_transfer_order_from_request (from_user_id, request_id),
  key idx_transfer_order_from_user_time (from_user_id, create_time),
  key idx_transfer_order_to_user_time (to_user_id, create_time)
);

create table if not exists wallet_admin_action (
  action_id binary(16) primary key,
  request_id varchar(128) not null,
  actor_user_id binary(16) not null,
  target_account_id binary(16) not null,
  action_type varchar(32) not null,
  amount bigint not null,
  remark varchar(255) default null,
  create_time timestamp null default current_timestamp,
  unique key uk_wallet_admin_action_request (request_id),
  key idx_wallet_admin_action_target_time (target_account_id, create_time)
);

-- Imported from deploy/mysql/community/032_schema_growth_market.sql; frozen into V001 on 2026-07-15.
-- Source: 015_schema_growth.sql (market split)
-- --------------------------------------------------------------------
-- Market schema (community): listings, orders, disputes, and fulfillment.


create table if not exists market_listing (
  listing_id binary(16) primary key,
  seller_user_id binary(16) not null,
  goods_type varchar(16) not null,
  title varchar(128) not null,
  description varchar(1000) not null,
  unit_price bigint not null,
  delivery_mode varchar(16) default null,
  stock_mode varchar(16) default null,
  stock_total int not null,
  stock_available int not null,
  min_purchase_quantity int not null,
  max_purchase_quantity int not null,
  status varchar(16) not null,
  create_time timestamp null default current_timestamp,
  update_time timestamp null default current_timestamp on update current_timestamp,
  key idx_market_listing_seller_time (seller_user_id, create_time)
);

create table if not exists market_inventory_unit (
  inventory_unit_id binary(16) primary key,
  listing_id binary(16) not null,
  seller_user_id binary(16) not null,
  payload_type varchar(16) not null,
  payload_content varchar(4000) not null,
  status varchar(16) not null,
  reserved_order_id binary(16) default null,
  delivered_at timestamp null default null,
  create_time timestamp null default current_timestamp,
  key idx_market_inventory_listing_status (listing_id, status, inventory_unit_id)
);

create table if not exists market_delivery (
  delivery_id binary(16) primary key,
  order_id binary(16) not null,
  seller_user_id binary(16) not null,
  delivery_type varchar(32) not null,
  delivery_content varchar(8000) not null,
  status varchar(16) not null,
  delivered_at timestamp null default null,
  create_time timestamp null default current_timestamp,
  key idx_market_delivery_order (order_id, delivery_id)
);

create table if not exists market_order (
  order_id binary(16) primary key,
  request_id varchar(128) not null,
  listing_id binary(16) not null,
  goods_type varchar(16) not null,
  seller_user_id binary(16) not null,
  buyer_user_id binary(16) not null,
  quantity int not null,
  unit_price_snapshot bigint not null,
  total_amount bigint not null,
  delivery_mode_snapshot varchar(16) default null,
  listing_title_snapshot varchar(128) not null,
  status varchar(32) not null,
  escrow_txn_id binary(16) default null,
  release_txn_id binary(16) default null,
  refund_txn_id binary(16) default null,
  auto_confirm_at timestamp null default null,
  address_id_snapshot binary(16) default null,
  receiver_name_snapshot varchar(64) default null,
  receiver_phone_snapshot varchar(32) default null,
  province_snapshot varchar(64) default null,
  city_snapshot varchar(64) default null,
  district_snapshot varchar(64) default null,
  detail_address_snapshot varchar(255) default null,
  postal_code_snapshot varchar(16) default null,
  create_time timestamp null default current_timestamp,
  update_time timestamp null default current_timestamp on update current_timestamp,
  unique key uk_market_order_buyer_request (buyer_user_id, request_id),
  key idx_market_order_buyer_time (buyer_user_id, create_time),
  key idx_market_order_seller_time (seller_user_id, create_time),
  key idx_market_order_listing_status (listing_id, status),
  key idx_market_order_auto_confirm (status, auto_confirm_at)
);

create table if not exists market_wallet_action (
  action_id binary(16) primary key,
  order_id binary(16) not null,
  dispute_id binary(16) default null,
  action_type varchar(16) not null,
  request_id varchar(128) not null,
  wallet_biz_id varchar(128) not null,
  actor_user_id binary(16) not null,
  counterparty_user_id binary(16) default null,
  amount bigint not null,
  status varchar(16) not null,
  result_type varchar(16) default null,
  wallet_txn_id binary(16) default null,
  failure_code varchar(64) default null,
  last_error varchar(255) default null,
  retry_count int not null default 0,
  next_retry_at timestamp null default null,
  processing_lease_until timestamp null default null,
  create_time timestamp null default current_timestamp,
  update_time timestamp null default current_timestamp on update current_timestamp,
  unique key uk_market_wallet_action_request (request_id),
  key idx_market_wallet_action_status_next (status, next_retry_at, action_id),
  key idx_market_wallet_action_order_type (order_id, action_type)
);

create table if not exists market_dispute (
  dispute_id binary(16) primary key,
  order_id binary(16) not null,
  goods_type varchar(16) not null,
  buyer_user_id binary(16) not null,
  seller_user_id binary(16) not null,
  status varchar(32) not null,
  reason varchar(255) not null,
  buyer_note varchar(1000) default null,
  seller_note varchar(1000) default null,
  resolution_type varchar(16) default null,
  resolved_by binary(16) default null,
  resolved_at timestamp null default null,
  create_time timestamp null default current_timestamp,
  update_time timestamp null default current_timestamp on update current_timestamp,
  key idx_market_dispute_order_status (order_id, status)
);

create table if not exists market_address (
  address_id binary(16) primary key,
  user_id binary(16) not null,
  receiver_name varchar(64) not null,
  receiver_phone varchar(32) not null,
  province varchar(64) not null,
  city varchar(64) not null,
  district varchar(64) not null,
  detail_address varchar(255) not null,
  postal_code varchar(16) default null,
  is_default boolean not null default false,
  status varchar(16) not null,
  create_time timestamp null default current_timestamp,
  update_time timestamp null default current_timestamp on update current_timestamp,
  key idx_market_address_user_status (user_id, status, is_default, address_id)
);

create table if not exists market_shipment (
  shipment_id binary(16) primary key,
  order_id binary(16) not null,
  seller_user_id binary(16) not null,
  carrier_name varchar(64) not null,
  tracking_no varchar(128) not null,
  shipping_remark varchar(1000) default null,
  shipped_at timestamp null default current_timestamp,
  create_time timestamp null default current_timestamp,
  update_time timestamp null default current_timestamp on update current_timestamp,
  unique key uk_market_shipment_order (order_id)
);

-- Imported from deploy/mysql/community/033_schema_growth_task.sql; frozen into V001 on 2026-07-15.
-- Source: 015_schema_growth.sql (task split)
-- --------------------------------------------------------------------
-- Task schema (community): task config, progress, event log, and seed rows.


create table if not exists user_level_rule_config (
  id binary(16) primary key,
  config_key varchar(32) not null,
  window_days int not null,
  lv2_sign_in_days int not null,
  lv3_sign_in_days int not null,
  enabled tinyint(1) not null default 1,
  updated_by binary(16) default null,
  update_time timestamp null default current_timestamp on update current_timestamp,
  unique key uk_user_level_rule_config_key (config_key)
);

create table if not exists task_template (
  task_code varchar(64) primary key,
  task_type varchar(32) not null,
  period_type varchar(16) not null,
  trigger_event_type varchar(64) not null,
  target_value int not null,
  reward_growth_delta int not null default 0,
  reward_balance_delta int not null default 0,
  claim_required tinyint(1) not null default 0,
  display_order int not null default 0,
  status varchar(16) not null,
  create_time timestamp null default current_timestamp,
  update_time timestamp null default current_timestamp on update current_timestamp,
  key idx_task_template_trigger (trigger_event_type, status, display_order)
);

create table if not exists user_task_progress (
  id binary(16) primary key,
  user_id binary(16) not null,
  task_code varchar(64) not null,
  period_key varchar(32) not null,
  current_value int not null,
  target_value int not null,
  status varchar(16) not null,
  reached_at timestamp null default null,
  claimed_at timestamp null default null,
  reward_grant_id varchar(64) default null,
  last_source_event_id varchar(128) default null,
  update_time timestamp null default current_timestamp on update current_timestamp,
  unique key uk_user_task_period (user_id, task_code, period_key),
  key idx_user_task_lookup (user_id, status, update_time)
);

create table if not exists user_task_event_log (
  id binary(16) primary key,
  user_id binary(16) not null,
  task_code varchar(64) not null,
  period_key varchar(32) not null,
  source_event_id varchar(128) not null,
  create_time timestamp null default current_timestamp,
  unique key uk_user_task_event (user_id, task_code, period_key, source_event_id),
  key idx_user_task_event_lookup (user_id, task_code, period_key, create_time)
);

insert ignore into task_template(task_code, task_type, period_type, trigger_event_type, target_value, reward_growth_delta, reward_balance_delta, claim_required, display_order, status)
values
  ('DAILY_CHECK_IN', 'CHECK_IN', 'DAILY', 'DailyCheckIn', 1, 2, 1, 0, 10, 'ACTIVE'),
  ('DAILY_POST', 'CONTENT', 'DAILY', 'PostPublished', 1, 3, 1, 0, 20, 'ACTIVE'),
  ('WEEKLY_COMMENTER', 'CONTENT', 'WEEKLY', 'CommentCreated', 2, 4, 1, 0, 30, 'ACTIVE'),
  ('LIFETIME_RECEIVE_LIKE', 'SOCIAL', 'LIFETIME', 'LikeCreated', 3, 6, 2, 0, 40, 'ACTIVE');

-- Imported from deploy/mysql/community/040_schema_content_core.sql; frozen into V001 on 2026-07-15.
-- Source: 020_schema_content.sql
-- --------------------------------------------------------------------
-- Content schema (community): posts + comments.


create table if not exists discuss_post (
  id binary(16) primary key,
  user_id binary(16),
  category_id binary(16) default null,
  title varchar(255),
  type int default 0,
  status int default 0,
  create_time timestamp null default current_timestamp,
  update_time timestamp null default null,
  edit_count int default 0,
  deleted_by binary(16) default null,
  deleted_reason varchar(255) default '',
  deleted_time timestamp null default null,
  comment_count int default 0,
  score double default 0,
  key idx_discuss_post_user_id (user_id),
  key idx_discuss_post_category_id (category_id)
);

create table if not exists post_media_asset (
  id binary(16) primary key,
  owner_user_id binary(16) not null,
  post_id binary(16) default null,
  oss_object_id binary(16) not null,
  oss_version_id binary(16) default null,
  oss_reference_id binary(16) default null,
  upload_session_id binary(16) default null,
  file_name varchar(255) not null,
  content_type varchar(128) not null,
  content_length bigint not null,
  media_kind varchar(32) not null,
  lifecycle varchar(32) not null,
  video_state varchar(32) not null default 'NONE',
  public_url varchar(1024) default '',
  failure_reason varchar(512) default '',
  create_time timestamp null default current_timestamp,
  update_time timestamp null default null,
  key idx_post_media_asset_owner_lifecycle (owner_user_id, lifecycle),
  key idx_post_media_asset_post (post_id),
  key idx_post_media_asset_video_state (video_state)
);

create table if not exists post_content_block (
  id binary(16) primary key,
  post_id binary(16) not null,
  block_index int not null,
  block_type varchar(32) not null,
  text_content text null,
  language varchar(64) default '',
  media_asset_id binary(16) default null,
  caption varchar(512) default '',
  display_name varchar(255) default '',
  metadata_json text default null,
  create_time timestamp null default current_timestamp,
  update_time timestamp null default null,
  unique key uk_post_block_index (post_id, block_index),
  key idx_post_content_block_post (post_id),
  key idx_post_content_block_media (media_asset_id)
);

create table if not exists comment (
  id binary(16) primary key,
  post_id binary(16) not null,
  user_id binary(16) not null,
  root_comment_id binary(16) not null,
  parent_comment_id binary(16) default null,
  reply_to_user_id binary(16) default null,
  content text,
  status int default 0,
  create_time timestamp null default current_timestamp,
  update_time timestamp null default null,
  edit_count int default 0,
  deleted_by binary(16) default null,
  deleted_reason varchar(255) default '',
  deleted_time timestamp null default null,
  key idx_comment_post_root (post_id, parent_comment_id, create_time, id),
  key idx_comment_root_reply (root_comment_id, parent_comment_id, create_time, id)
);

-- taxonomy: categories + tags (Discourse-like)
create table if not exists category (
  id binary(16) primary key,
  name varchar(64) not null,
  description varchar(255) default '',
  position int default 0,
  create_time timestamp null default current_timestamp,
  unique key uk_category_name (name)
);

create table if not exists tag (
  id binary(16) primary key,
  name varchar(64) not null,
  create_time timestamp null default current_timestamp,
  unique key uk_tag_name (name)
);

create table if not exists post_tag (
  post_id binary(16) not null,
  tag_id binary(16) not null,
  create_time timestamp null default current_timestamp,
  primary key (post_id, tag_id),
  key idx_post_tag_post_id (post_id),
  key idx_post_tag_tag_id (tag_id)
);

-- moderation: reports + actions (MVP)
create table if not exists report (
  id binary(16) primary key,
  reporter_id binary(16) not null,
  target_type int not null,
  target_id binary(16) not null,
  reason varchar(64) not null,
  detail varchar(512) default '',
  status int default 0,
  create_time timestamp null default current_timestamp,
  unique key uk_report_dedupe (reporter_id, target_type, target_id),
  key idx_report_status (status, create_time),
  key idx_report_target (target_type, target_id, create_time)
);

create table if not exists moderation_action (
  id binary(16) primary key,
  report_id binary(16) default null,
  actor_id binary(16) not null,
  action varchar(32) not null,
  reason varchar(255) default '',
  duration_seconds int default 0,
  create_time timestamp null default current_timestamp,
  key idx_moderation_action_report (report_id, create_time),
  key idx_moderation_action_actor (actor_id, create_time)
);

-- bookmarks/subscriptions (MVP)
create table if not exists post_bookmark (
  user_id binary(16) not null,
  post_id binary(16) not null,
  create_time timestamp null default current_timestamp,
  primary key (user_id, post_id),
  key idx_post_bookmark_post (post_id, create_time)
);

create table if not exists user_subscription_category (
  user_id binary(16) not null,
  category_id binary(16) not null,
  create_time timestamp null default current_timestamp,
  primary key (user_id, category_id),
  key idx_user_sub_category_user (user_id, create_time)
);

create table if not exists post_counter_snapshot (
  post_id binary(16) primary key,
  view_count bigint not null default 0,
  like_count bigint not null default 0,
  comment_count bigint not null default 0,
  bookmark_count bigint not null default 0,
  snapshot_time timestamp null default current_timestamp on update current_timestamp
);

create table if not exists post_score_snapshot (
  post_id binary(16) primary key,
  score double not null default 0,
  rank_version varchar(64) not null,
  snapshot_time timestamp null default current_timestamp on update current_timestamp
);

insert into category(id, name, description, position) values
  (x'00000000000070008000000000000101', '公告', '官方公告/规则', 0),
  (x'00000000000070008000000000000102', '技术', '技术讨论/问题求助', 10),
  (x'00000000000070008000000000000103', '兴趣', '兴趣分享/作品展示', 20)
on duplicate key update
  description = values(description),
  position = values(position);

-- Imported from deploy/mysql/community/050_schema_social.sql; frozen into V001 on 2026-07-15.
-- Source: 025_schema_social.sql
-- --------------------------------------------------------------------
-- Social schema (community): likes + follows + blocks.


create table if not exists social_like (
  user_id binary(16) not null,
  entity_type int not null,
  entity_id binary(16) not null,
  entity_user_id binary(16) null,
  created_at timestamp not null default current_timestamp,
  primary key (user_id, entity_type, entity_id),
  index idx_like_entity (entity_type, entity_id),
  index idx_like_entity_user (entity_type, entity_id, user_id)
);

-- 用户获赞数（计数 SSOT）：由写路径在“新增点赞/取消点赞”时原子增减。
create table if not exists social_user_like_count (
  user_id binary(16) not null primary key,
  like_count bigint not null default 0,
  updated_at timestamp not null default current_timestamp on update current_timestamp
);

create table if not exists social_follow (
  user_id binary(16) not null,
  entity_type int not null,
  entity_id binary(16) not null,
  created_at timestamp not null default current_timestamp,
  primary key (user_id, entity_type, entity_id),
  index idx_follow_followee (user_id, entity_type, created_at, entity_id),
  index idx_follow_follower (entity_type, entity_id, created_at, user_id)
);

create table if not exists social_block (
  user_id binary(16) not null,
  target_user_id binary(16) not null,
  created_at timestamp not null default current_timestamp,
  version bigint not null default 0,
  primary key (user_id, target_user_id),
  index idx_block_user_created (user_id, created_at)
);

create table if not exists social_block_version_counter (
  id int primary key,
  current_version bigint not null default 0
);

set @social_block_current_version := (
  select coalesce(max(version), 0) from social_block
);

insert into social_block_version_counter(id, current_version)
values (1, @social_block_current_version)
on duplicate key update current_version = greatest(current_version, values(current_version));

create table if not exists social_block_version_log (
  version bigint primary key,
  user_id binary(16) not null,
  target_user_id binary(16) not null,
  active tinyint(1) not null,
  occurred_at timestamp not null default current_timestamp,
  index idx_social_block_version_pair (user_id, target_user_id, version)
);

-- --------------------------------------------------------------------

-- Imported from deploy/mysql/community/060_schema_notice.sql; frozen into V001 on 2026-07-15.
-- Source: 060_schema_notice.sql
-- --------------------------------------------------------------------
-- Notice schema (community): notice_record.


create table if not exists notice_record (
  id binary(16) primary key,
  sender_user_id binary(16),
  recipient_user_id binary(16) not null,
  topic varchar(64) not null,
  content varchar(4000),
  source_event_type varchar(64),
  source_relation_key varchar(255),
  status int default 0,
  create_time timestamp null default current_timestamp,
  index idx_notice_record_topic (topic),
  index idx_notice_record_recipient_status (recipient_user_id, status),
  index idx_notice_record_recipient_topic_time (recipient_user_id, topic, create_time)
);

create table if not exists notice_projection_event_log (
  source_event_id varchar(128) not null primary key,
  create_time datetime not null default current_timestamp
);


-- --------------------------------------------------------------------

-- Imported from deploy/mysql/community/080_schema_search.sql; frozen into V001 on 2026-07-15.
-- Source: 040_schema_search.sql
-- --------------------------------------------------------------------
-- Search schema (community).


-- --------------------------------------------------------------------

-- Imported from deploy/mysql/community/090_schema_drive.sql; frozen into V001 on 2026-07-15.
create table if not exists drive_space (
  space_id binary(16) primary key,
  user_id binary(16) not null,
  quota_bytes bigint not null default 10737418240,
  used_bytes bigint not null default 0,
  reserved_bytes bigint not null default 0,
  created_at timestamp not null default current_timestamp,
  updated_at timestamp not null default current_timestamp,
  unique key uk_drive_space_user (user_id),
  key idx_drive_space_updated (updated_at)
);

create table if not exists drive_entry (
  entry_id binary(16) primary key,
  space_id binary(16) not null,
  parent_id binary(16) null,
  parent_key varchar(32) not null default '',
  active_name varchar(255) null,
  type varchar(16) not null,
  name varchar(255) not null,
  object_id binary(16) null,
  version_id binary(16) null,
  size_bytes bigint not null default 0,
  mime_type varchar(128) not null default '',
  status varchar(16) not null,
  trashed_at timestamp null default null,
  delete_after timestamp null default null,
  trash_root_id binary(16) null,
  created_at timestamp not null default current_timestamp,
  updated_at timestamp not null default current_timestamp,
  unique key uk_drive_entry_active_name (space_id, parent_key, active_name),
  key idx_drive_entry_parent_status (space_id, parent_id, status, name),
  key idx_drive_entry_object (object_id, version_id),
  key idx_drive_entry_trash (space_id, status, trashed_at),
  key idx_drive_entry_search (space_id, status, name)
);

create table if not exists drive_upload (
  upload_id binary(16) primary key,
  space_id binary(16) not null,
  parent_id binary(16) null,
  name varchar(255) not null,
  size_bytes bigint not null,
  mime_type varchar(128) not null,
  object_id binary(16) not null,
  version_id binary(16) not null,
  oss_session_id binary(16) not null,
  status varchar(16) not null,
  created_by binary(16) not null,
  created_at timestamp not null default current_timestamp,
  updated_at timestamp not null default current_timestamp,
  expires_at timestamp not null,
  completed_at timestamp null default null,
  completed_entry_id binary(16) null,
  key idx_drive_upload_space_status (space_id, status, expires_at),
  key idx_drive_upload_recovery (status, updated_at, upload_id),
  key idx_drive_upload_object (object_id, version_id)
);

create table if not exists drive_share (
  share_id binary(16) primary key,
  entry_id binary(16) not null,
  share_token varchar(96) not null,
  password_hash varchar(255) not null,
  expires_at timestamp not null,
  status varchar(16) not null,
  created_by binary(16) not null,
  created_at timestamp not null default current_timestamp,
  updated_at timestamp not null default current_timestamp,
  unique key uk_drive_share_token (share_token),
  key idx_drive_share_entry_status (entry_id, status),
  key idx_drive_share_expiry (status, expires_at)
);

create table if not exists drive_share_access (
  access_id binary(16) primary key,
  share_id binary(16) not null,
  visitor_fingerprint varchar(128) not null default '',
  success tinyint(1) not null default 0,
  accessed_at timestamp not null default current_timestamp,
  key idx_drive_share_access_share_time (share_id, accessed_at),
  key idx_drive_share_access_fingerprint_time (visitor_fingerprint, accessed_at)
);
