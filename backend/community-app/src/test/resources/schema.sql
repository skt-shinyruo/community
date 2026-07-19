create table if not exists user (
  id binary(16) primary key,
  username varchar(255) not null,
  password varchar(255),
  salt varchar(255),
  email varchar(255) not null,
  type int not null default 0,
  status int default 0,
  header_url varchar(255),
  create_time timestamp default current_timestamp,
  mute_until timestamp,
  ban_until timestamp,
  policy_version bigint not null default 0,
  security_version bigint not null default 0,
  constraint uk_user_username unique (username),
  constraint uk_user_email unique (email),
  constraint ck_user_type check (type in (0, 1, 2))
);

create table if not exists user_policy_version_counter (
  id int primary key,
  current_version bigint not null default 0
);

merge into user_policy_version_counter(id, current_version)
key(id)
values (1, 0);

create table if not exists user_security_version_counter (
  id int primary key,
  current_version bigint not null default 0
);

merge into user_security_version_counter(id, current_version)
key(id)
values (1, 0);

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
  constraint uk_wallet_account_owner unique (owner_type, owner_id, account_type)
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
  constraint uk_wallet_txn_request unique (request_id)
);

create table if not exists wallet_entry (
  entry_id binary(16) primary key,
  txn_id binary(16) not null,
  account_id binary(16) not null,
  direction varchar(8) not null,
  amount bigint not null,
  balance_after bigint not null,
  create_time timestamp null default current_timestamp
);

create index if not exists idx_wallet_entry_txn on wallet_entry(txn_id);
create index if not exists idx_wallet_entry_account_time on wallet_entry(account_id, create_time);

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
  constraint uk_recharge_order_user_request unique (user_id, request_id)
);

create index if not exists idx_recharge_order_user_time on recharge_order(user_id, create_time);

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
  constraint uk_withdraw_order_user_request unique (user_id, request_id)
);

create index if not exists idx_withdraw_order_user_time on withdraw_order(user_id, create_time);

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
  constraint uk_transfer_order_from_request unique (from_user_id, request_id)
);

create index if not exists idx_transfer_order_from_user_time on transfer_order(from_user_id, create_time);
create index if not exists idx_transfer_order_to_user_time on transfer_order(to_user_id, create_time);

create table if not exists wallet_admin_action (
  action_id binary(16) primary key,
  request_id varchar(128) not null,
  actor_user_id binary(16) not null,
  target_account_id binary(16) not null,
  action_type varchar(32) not null,
  amount bigint not null,
  remark varchar(255) default null,
  create_time timestamp null default current_timestamp,
  constraint uk_wallet_admin_action_request unique (request_id)
);

create index if not exists idx_wallet_admin_action_target_time on wallet_admin_action(target_account_id, create_time);

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
  update_time timestamp null default current_timestamp on update current_timestamp
);

create index if not exists idx_market_listing_seller_time on market_listing(seller_user_id, create_time);

create table if not exists market_inventory_unit (
  inventory_unit_id binary(16) primary key,
  listing_id binary(16) not null,
  seller_user_id binary(16) not null,
  payload_type varchar(16) not null,
  payload_content varchar(4000) not null,
  status varchar(16) not null,
  reserved_order_id binary(16) default null,
  delivered_at timestamp null default null,
  create_time timestamp null default current_timestamp
);

create index if not exists idx_market_inventory_listing_status on market_inventory_unit(listing_id, status, inventory_unit_id);

create table if not exists market_delivery (
  delivery_id binary(16) primary key,
  order_id binary(16) not null,
  seller_user_id binary(16) not null,
  delivery_type varchar(32) not null,
  delivery_content varchar(8000) not null,
  status varchar(16) not null,
  delivered_at timestamp null default null,
  create_time timestamp null default current_timestamp
);

create index if not exists idx_market_delivery_order on market_delivery(order_id, delivery_id);

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
  constraint uk_market_order_buyer_request unique (buyer_user_id, request_id)
);

create index if not exists idx_market_order_buyer_time on market_order(buyer_user_id, create_time);
create index if not exists idx_market_order_seller_time on market_order(seller_user_id, create_time);
create index if not exists idx_market_order_listing_status on market_order(listing_id, status);
create index if not exists idx_market_order_auto_confirm on market_order(status, auto_confirm_at);

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
  constraint uk_market_wallet_action_request unique (request_id)
);

create index if not exists idx_market_wallet_action_status_next on market_wallet_action(status, next_retry_at, action_id);
create index if not exists idx_market_wallet_action_order_type on market_wallet_action(order_id, action_type);

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
  update_time timestamp null default current_timestamp on update current_timestamp
);

create index if not exists idx_market_dispute_order_status on market_dispute(order_id, status);

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
  update_time timestamp null default current_timestamp on update current_timestamp
);

create index if not exists idx_market_address_user_status on market_address(user_id, status, is_default, address_id);

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
  constraint uk_market_shipment_order unique (order_id)
);

create table if not exists user_level_rule_config (
  id binary(16) primary key,
  config_key varchar(32) not null,
  window_days int not null,
  lv2_sign_in_days int not null,
  lv3_sign_in_days int not null,
  enabled boolean not null default true,
  updated_by binary(16),
  update_time timestamp default current_timestamp,
  unique (config_key)
);
create table if not exists task_template (
  task_code varchar(64) primary key,
  task_type varchar(32) not null,
  period_type varchar(16) not null,
  trigger_event_type varchar(64) not null,
  target_value int not null,
  reward_growth_delta int not null default 0,
  reward_balance_delta int not null default 0,
  claim_required boolean not null default false,
  display_order int not null default 0,
  status varchar(16) not null,
  create_time timestamp default current_timestamp,
  update_time timestamp default current_timestamp
);

create table if not exists user_task_progress (
  id binary(16) primary key,
  user_id binary(16) not null,
  task_code varchar(64) not null,
  period_key varchar(32) not null,
  current_value int not null,
  target_value int not null,
  status varchar(16) not null,
  reached_at timestamp null,
  claimed_at timestamp null,
  reward_grant_id varchar(191),
  last_source_event_id varchar(128),
  update_time timestamp default current_timestamp,
  constraint uk_user_task_period unique (user_id, task_code, period_key)
);

create table if not exists user_task_event_log (
  id binary(16) primary key,
  user_id binary(16) not null,
  task_code varchar(64) not null,
  period_key varchar(32) not null,
  source_event_id varchar(128) not null,
  create_time timestamp default current_timestamp,
  constraint uk_user_task_event unique (user_id, task_code, period_key, source_event_id)
);

create table if not exists user_consumed_event (
  id binary(16) primary key,
  event_id varchar(64) not null,
  consumed_at timestamp default current_timestamp,
  constraint uk_user_consumed_event_id unique (event_id)
);

create table if not exists auth_refresh_token (
  token_hash char(64) primary key,
  user_id binary(16) not null,
  family_id varchar(64) not null,
  security_version bigint not null,
  expires_at timestamp not null,
  state varchar(32) not null default 'ACTIVE',
  pending_expires_at timestamp,
  revoked_at timestamp,
  created_at timestamp default current_timestamp,
  constraint ck_auth_refresh_token_state check (state in ('ACTIVE', 'PENDING_ROTATION', 'CONSUMED', 'REVOKED'))
);

create table if not exists auth_refresh_token_family_revocation (
  family_id varchar(64) primary key,
  revoked_at timestamp not null default current_timestamp
);

create table if not exists discuss_post (
  id binary(16) primary key,
  user_id binary(16),
  category_id binary(16),
  title varchar(255),
  type int,
  status int,
  create_time timestamp,
  update_time timestamp,
  edit_count int default 0,
  deleted_by binary(16),
  deleted_reason varchar(255),
  deleted_time timestamp,
  comment_count int,
  score double
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
  upload_status varchar(32) not null default 'PREPARED',
  upload_operation_version bigint not null default 0,
  upload_updated_at timestamp,
  reference_status varchar(32) not null default 'UNBOUND',
  reference_operation_version bigint not null default 0,
  reference_updated_at timestamp,
  video_state varchar(32) not null default 'NONE',
  public_url varchar(1024) default '',
  failure_reason varchar(512) default '',
  create_time timestamp null default current_timestamp,
  update_time timestamp null default null
);

create index if not exists idx_post_media_asset_owner_lifecycle on post_media_asset(owner_user_id, lifecycle);
create index if not exists idx_post_media_asset_post on post_media_asset(post_id);
create index if not exists idx_post_media_asset_video_state on post_media_asset(video_state);
create index if not exists idx_post_media_upload_recovery on post_media_asset(upload_status, upload_updated_at, id);
create index if not exists idx_post_media_reference_pending on post_media_asset(reference_status, reference_updated_at);

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
  unique key uk_post_block_index (post_id, block_index)
);

create index if not exists idx_post_content_block_post on post_content_block(post_id);
create index if not exists idx_post_content_block_media on post_content_block(media_asset_id);

create table if not exists post_bookmark (
  user_id binary(16) not null,
  post_id binary(16) not null,
  create_time timestamp default current_timestamp,
  primary key (user_id, post_id)
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
  create_time timestamp default current_timestamp,
  update_time timestamp default null,
  edit_count int default 0,
  deleted_by binary(16) default null,
  deleted_reason varchar(255) default '',
  deleted_time timestamp default null,
  version bigint not null default 0
);

create index if not exists idx_comment_post_root on comment(post_id, parent_comment_id, create_time, id);
create index if not exists idx_comment_root_reply on comment(root_comment_id, parent_comment_id, create_time, id);

create table if not exists category (
  id binary(16) primary key,
  name varchar(64),
  description varchar(255),
  position int,
  create_time timestamp
);

create table if not exists user_subscription_category (
  user_id binary(16) not null,
  category_id binary(16) not null,
  create_time timestamp default current_timestamp,
  primary key (user_id, category_id)
);

create table if not exists tag (
  id binary(16) primary key,
  name varchar(64),
  create_time timestamp
);

create table if not exists post_tag (
  post_id binary(16),
  tag_id binary(16),
  create_time timestamp,
  primary key (post_id, tag_id)
);

create table if not exists report (
  id binary(16) primary key,
  reporter_id binary(16) not null,
  target_type int not null,
  target_id binary(16) not null,
  reason varchar(64) not null,
  detail varchar(512),
  status int default 0,
  create_time timestamp,
  unique(reporter_id, target_type, target_id)
);

create table if not exists moderation_action (
  id binary(16) primary key,
  report_id binary(16),
  actor_id binary(16) not null,
  action varchar(32) not null,
  reason varchar(255),
  duration_seconds int default 0,
  create_time timestamp
);

create table if not exists social_like (
  user_id binary(16) not null,
  entity_type int not null,
  entity_id binary(16) not null,
  entity_user_id binary(16),
  created_at timestamp null default current_timestamp,
  primary key (user_id, entity_type, entity_id)
);

create index if not exists idx_like_entity_user on social_like(entity_type, entity_id, user_id);

create table if not exists social_like_target_state (
  entity_type int not null,
  entity_id binary(16) not null,
  status varchar(16) not null default 'ACTIVE',
  source_event_id varchar(128),
  source_version bigint not null default 0,
  deleted_at timestamp,
  updated_at timestamp not null default current_timestamp,
  primary key (entity_type, entity_id),
  constraint ck_social_like_target_state_status check (status in ('ACTIVE', 'DELETED'))
);

create index if not exists idx_social_like_target_state_status_updated
  on social_like_target_state(status, updated_at);

create table if not exists social_user_like_count (
  user_id binary(16) not null primary key,
  like_count bigint not null default 0,
  updated_at timestamp null default current_timestamp
);

create table if not exists social_follow (
  user_id binary(16) not null,
  entity_type int not null,
  entity_id binary(16) not null,
  created_at timestamp null default current_timestamp,
  primary key (user_id, entity_type, entity_id)
);

create table if not exists social_block (
  user_id binary(16) not null,
  target_user_id binary(16) not null,
  created_at timestamp null default current_timestamp,
  version bigint not null default 0,
  primary key (user_id, target_user_id)
);

create table if not exists social_block_version_counter (
  id int primary key,
  current_version bigint not null default 0
);

merge into social_block_version_counter(id, current_version)
key(id)
values (1, 0);

create table if not exists social_block_version_log (
  version bigint primary key,
  user_id binary(16) not null,
  target_user_id binary(16) not null,
  active boolean not null,
  occurred_at timestamp null default current_timestamp
);

create table if not exists notice_record (
  id binary(16) primary key,
  sender_user_id binary(16),
  recipient_user_id binary(16) not null,
  topic varchar(64) not null,
  content varchar(4000),
  source_event_type varchar(64),
  source_relation_key varchar(255),
  status int not null default 0,
  create_time timestamp
);

create table if not exists notice_projection_event_log (
  source_event_id varchar(128) not null primary key,
  create_time datetime not null default current_timestamp
);

create table if not exists http_idempotency (
  id binary(16) primary key,
  operation varchar(64) not null,
  user_id binary(16) not null,
  idem_key varchar(128) not null,
  request_hash varchar(64) not null,
  status varchar(16) not null,
  response_json mediumtext,
  processing_expires_at timestamp,
  success_expires_at timestamp,
  created_at timestamp default current_timestamp,
  updated_at timestamp default current_timestamp,
  unique (operation, user_id, idem_key)
);

create table if not exists outbox_event (
  id binary(16) primary key,
  event_id varchar(128) not null,
  topic varchar(255) not null,
  event_key varchar(255) not null,
  payload clob not null,
  status varchar(32) not null,
  lease_token binary(16) null,
  processing_lease_until timestamp null,
  retry_count int not null default 0,
  next_retry_at timestamp,
  last_error varchar(512),
  trace_id varchar(32) null,
  traceparent varchar(128) null,
  created_at timestamp default current_timestamp,
  updated_at timestamp default current_timestamp,
  constraint uk_outbox_event_id unique (event_id)
);

create index if not exists idx_outbox_status_next on outbox_event(status, next_retry_at, id);
create index if not exists idx_outbox_processing_lease on outbox_event(status, processing_lease_until, id);
create index if not exists idx_outbox_status_updated on outbox_event(status, updated_at, id);
create index if not exists idx_outbox_status_created on outbox_event(status, created_at, id);

create table if not exists ops_governance_audit (
  id binary(16) primary key,
  action varchar(64) not null,
  actor_user_id binary(16) not null,
  target_type varchar(64) not null,
  target_id varchar(255),
  scope varchar(255),
  reason varchar(512),
  request_json clob,
  result varchar(64) not null,
  summary_json clob,
  trace_id varchar(32),
  created_at timestamp default current_timestamp
);

create index if not exists idx_ops_governance_action_created on ops_governance_audit(action, created_at, id);
create index if not exists idx_ops_governance_actor_created on ops_governance_audit(actor_user_id, created_at, id);
create index if not exists idx_ops_governance_target_created on ops_governance_audit(target_type, target_id, created_at);
create index if not exists idx_ops_governance_result_created on ops_governance_audit(result, created_at, id);

create table if not exists drive_space (
  space_id binary(16) primary key,
  user_id binary(16) not null,
  quota_bytes bigint not null default 10737418240,
  used_bytes bigint not null default 0,
  reserved_bytes bigint not null default 0,
  created_at timestamp default current_timestamp,
  updated_at timestamp default current_timestamp,
  constraint uk_drive_space_user unique (user_id)
);

create table if not exists drive_entry (
  entry_id binary(16) primary key,
  space_id binary(16) not null,
  parent_id binary(16),
  parent_key varchar(32) not null default '',
  active_name varchar(255),
  type varchar(16) not null,
  name varchar(255) not null,
  object_id binary(16),
  version_id binary(16),
  size_bytes bigint not null default 0,
  mime_type varchar(128) not null default '',
  status varchar(16) not null,
  trashed_at timestamp,
  delete_after timestamp,
  trash_root_id binary(16),
  created_at timestamp default current_timestamp,
  updated_at timestamp default current_timestamp,
  constraint uk_drive_entry_active_name unique (space_id, parent_key, active_name)
);

create index if not exists idx_drive_entry_parent_status on drive_entry(space_id, parent_id, status, name);
create index if not exists idx_drive_entry_trash on drive_entry(space_id, status, trashed_at);

create table if not exists drive_upload (
  upload_id binary(16) primary key,
  space_id binary(16) not null,
  parent_id binary(16),
  name varchar(255) not null,
  size_bytes bigint not null,
  mime_type varchar(128) not null,
  object_id binary(16) not null,
  version_id binary(16) not null,
  oss_session_id binary(16) not null,
  status varchar(16) not null,
  created_by binary(16) not null,
  created_at timestamp default current_timestamp,
  updated_at timestamp default current_timestamp,
  expires_at timestamp not null,
  completed_at timestamp,
  completed_entry_id binary(16)
);

create index if not exists idx_drive_upload_space_status on drive_upload(space_id, status, expires_at);
create index if not exists idx_drive_upload_recovery on drive_upload(status, updated_at, upload_id);

create table if not exists drive_share (
  share_id binary(16) primary key,
  entry_id binary(16) not null,
  share_token varchar(96) not null,
  password_hash varchar(255) not null,
  expires_at timestamp not null,
  status varchar(16) not null,
  created_by binary(16) not null,
  created_at timestamp default current_timestamp,
  updated_at timestamp default current_timestamp,
  constraint uk_drive_share_token unique (share_token)
);

create index if not exists idx_drive_share_entry_status on drive_share(entry_id, status);
create index if not exists idx_drive_share_expiry on drive_share(status, expires_at);

create table if not exists drive_share_access (
  access_id binary(16) primary key,
  share_id binary(16) not null,
  visitor_fingerprint varchar(128) not null default '',
  success boolean not null default false,
  accessed_at timestamp default current_timestamp
);

create index if not exists idx_drive_share_access_share_time on drive_share_access(share_id, accessed_at);

delete from user_task_progress;
delete from user_consumed_event;
delete from auth_refresh_token_family_revocation;
delete from auth_refresh_token;
delete from comment;
delete from post_tag;
delete from tag;
delete from category;
delete from social_like;
delete from social_like_target_state;
delete from social_user_like_count;
delete from social_follow;
delete from social_block;
delete from notice_projection_event_log;
delete from notice_record;
delete from http_idempotency;
delete from outbox_event;
delete from post_content_block;
delete from post_media_asset;
delete from discuss_post;
delete from drive_share_access;
delete from drive_share;
delete from drive_upload;
delete from drive_entry;
delete from drive_space;
delete from user;

merge into user (id, username, password, salt, email, type, status, header_url, create_time, policy_version)
key(id) values
  (X'00000000000070008000000000000001', 'u1', 'p', 's', 'u1@example.com', 0, 1, 'http://old.local/a.png', CURRENT_TIMESTAMP(), 1),
  (X'00000000000070008000000000000002', 'u2', 'p', 's', 'u2@example.com', 0, 1, 'http://old.local/b.png', CURRENT_TIMESTAMP(), 2);

update user_policy_version_counter set current_version = 2 where id = 1;

merge into category (id, name, description, position, create_time)
key(id) values
  (X'00000000000070008000000000000001', '技术', '技术讨论', 10, CURRENT_TIMESTAMP()),
  (X'00000000000070008000000000000002', '兴趣', '兴趣分享', 20, CURRENT_TIMESTAMP());

insert into discuss_post(id, user_id, category_id, title, type, status, create_time, update_time, edit_count, comment_count, score)
values (
  X'00000000000070008000000000000064',
  X'00000000000070008000000000000001',
  X'00000000000070008000000000000001',
  'hello world',
  0,
  0,
  CURRENT_TIMESTAMP(),
  CURRENT_TIMESTAMP(),
  0,
  0,
  0.0
);

merge into task_template(task_code, task_type, period_type, trigger_event_type, target_value, reward_growth_delta, reward_balance_delta, claim_required, display_order, status)
key(task_code) values
  ('DAILY_CHECK_IN', 'CHECK_IN', 'DAILY', 'DailyCheckIn', 1, 2, 1, false, 10, 'ACTIVE'),
  ('DAILY_POST', 'CONTENT', 'DAILY', 'PostPublished', 1, 3, 1, false, 20, 'ACTIVE'),
  ('WEEKLY_COMMENTER', 'CONTENT', 'WEEKLY', 'CommentCreated', 2, 4, 1, false, 30, 'ACTIVE'),
  ('LIFETIME_RECEIVE_LIKE', 'SOCIAL', 'LIFETIME', 'LikeCreated', 3, 6, 2, false, 40, 'ACTIVE');
