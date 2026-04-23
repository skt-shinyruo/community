create table if not exists user (
  id binary(16) primary key,
  username varchar(255) not null,
  password varchar(255),
  salt varchar(255),
  email varchar(255) not null,
  type int default 0,
  status int default 0,
  header_url varchar(255),
  create_time timestamp default current_timestamp,
  score int default 0,
  mute_until timestamp,
  ban_until timestamp,
  constraint uk_user_username unique (username),
  constraint uk_user_email unique (email)
);

create table if not exists user_score_log (
  id binary(16) primary key,
  user_id binary(16) not null,
  event_id varchar(64) not null,
  event_type varchar(64) not null,
  delta int not null,
  create_time timestamp default current_timestamp,
  constraint uk_event_id unique (event_id)
);

create table if not exists reward_account (
  user_id binary(16) not null primary key,
  available_balance int not null default 0,
  frozen_balance int not null default 0,
  version int not null default 0,
  update_time timestamp default current_timestamp
);

create table if not exists reward_ledger (
  id binary(16) primary key,
  user_id binary(16) not null,
  event_id varchar(64) not null,
  event_type varchar(64) not null,
  delta int not null,
  balance_after int not null,
  frozen_balance_after int not null default 0,
  biz_key varchar(128),
  source_module varchar(64),
  remark varchar(255),
  create_time timestamp default current_timestamp,
  constraint uk_reward_ledger_event_id unique (event_id)
);

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
  request_id varchar(96) not null,
  txn_type varchar(32) not null,
  biz_type varchar(32) not null,
  biz_id varchar(96) not null,
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
  request_id varchar(96) not null,
  user_id binary(16) not null,
  amount bigint not null,
  status varchar(16) not null,
  channel varchar(32) default null,
  channel_order_id varchar(96) default null,
  remark varchar(255) default null,
  create_time timestamp null default current_timestamp,
  update_time timestamp null default current_timestamp on update current_timestamp,
  constraint uk_recharge_order_request unique (request_id)
);

create index if not exists idx_recharge_order_user_time on recharge_order(user_id, create_time);

create table if not exists withdraw_order (
  order_id binary(16) primary key,
  request_id varchar(96) not null,
  user_id binary(16) not null,
  amount bigint not null,
  status varchar(16) not null,
  payee_account varchar(128) default null,
  failure_reason varchar(255) default null,
  create_time timestamp null default current_timestamp,
  update_time timestamp null default current_timestamp on update current_timestamp,
  constraint uk_withdraw_order_request unique (request_id)
);

create index if not exists idx_withdraw_order_user_time on withdraw_order(user_id, create_time);

create table if not exists transfer_order (
  order_id binary(16) primary key,
  request_id varchar(96) not null,
  from_user_id binary(16) not null,
  to_user_id binary(16) not null,
  amount bigint not null,
  status varchar(16) not null,
  remark varchar(255) default null,
  create_time timestamp null default current_timestamp,
  update_time timestamp null default current_timestamp on update current_timestamp,
  constraint uk_transfer_order_request unique (request_id)
);

create index if not exists idx_transfer_order_from_user_time on transfer_order(from_user_id, create_time);
create index if not exists idx_transfer_order_to_user_time on transfer_order(to_user_id, create_time);

create table if not exists wallet_admin_action (
  action_id binary(16) primary key,
  request_id varchar(96) not null,
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
  request_id varchar(96) not null,
  listing_id binary(16) not null,
  goods_type varchar(16) not null,
  seller_user_id binary(16) not null,
  buyer_user_id binary(16) not null,
  quantity int not null,
  unit_price_snapshot bigint not null,
  total_amount bigint not null,
  delivery_mode_snapshot varchar(16) default null,
  listing_title_snapshot varchar(128) not null,
  status varchar(16) not null,
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
  constraint uk_market_order_request unique (request_id)
);

create index if not exists idx_market_order_buyer_time on market_order(buyer_user_id, create_time);
create index if not exists idx_market_order_seller_time on market_order(seller_user_id, create_time);
create index if not exists idx_market_order_listing_status on market_order(listing_id, status);
create index if not exists idx_market_order_auto_confirm on market_order(status, auto_confirm_at);

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

create table if not exists reward_grant_record (
  id binary(16) primary key,
  grant_id varchar(191) not null,
  user_id binary(16) not null,
  grant_type varchar(64) not null,
  source_event_id varchar(64) not null,
  source_event_type varchar(64) not null,
  growth_delta int not null default 0,
  reward_delta int not null default 0,
  status varchar(32) not null,
  create_time timestamp default current_timestamp,
  constraint uk_reward_grant_id unique (grant_id)
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
  last_source_event_id varchar(64),
  update_time timestamp default current_timestamp,
  constraint uk_user_task_period unique (user_id, task_code, period_key)
);

create table if not exists user_task_event_log (
  id binary(16) primary key,
  user_id binary(16) not null,
  task_code varchar(64) not null,
  period_key varchar(32) not null,
  source_event_id varchar(64) not null,
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
  expires_at timestamp not null,
  revoked_at timestamp,
  created_at timestamp default current_timestamp
);

create table if not exists discuss_post (
  id binary(16) primary key,
  user_id binary(16),
  category_id binary(16),
  title varchar(255),
  content text,
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

create table if not exists post_bookmark (
  user_id binary(16) not null,
  post_id binary(16) not null,
  create_time timestamp default current_timestamp,
  primary key (user_id, post_id)
);

create table if not exists comment (
  id binary(16) primary key,
  user_id binary(16),
  entity_type int,
  entity_id binary(16),
  target_id binary(16),
  content text,
  status int,
  create_time timestamp,
  update_time timestamp,
  edit_count int default 0,
  deleted_by binary(16),
  deleted_reason varchar(255),
  deleted_time timestamp
);

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
  created_at timestamp null default current_timestamp,
  primary key (user_id, entity_type, entity_id)
);

create index if not exists idx_like_entity_user on social_like(entity_type, entity_id, user_id);

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
  primary key (user_id, target_user_id)
);

create table if not exists message (
  id binary(16) primary key,
  from_id binary(16),
  to_id binary(16),
  conversation_id varchar(255),
  content varchar(4000),
  status int,
  create_time timestamp
);

create table if not exists http_idempotency (
  id binary(16) primary key,
  operation varchar(64) not null,
  user_id binary(16) not null,
  idem_key varchar(128) not null,
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
  event_id varchar(64) not null,
  topic varchar(255) not null,
  event_key varchar(255) not null,
  payload clob not null,
  status varchar(32) not null,
  retry_count int not null default 0,
  next_retry_at timestamp,
  last_error varchar(512),
  created_at timestamp default current_timestamp,
  updated_at timestamp default current_timestamp,
  constraint uk_outbox_event_id unique (event_id)
);

create index if not exists idx_outbox_status_next on outbox_event(status, next_retry_at, id);
create index if not exists idx_outbox_status_updated on outbox_event(status, updated_at, id);
create index if not exists idx_outbox_status_created on outbox_event(status, created_at, id);

delete from user_score_log;
delete from reward_ledger;
delete from reward_grant_record;
delete from reward_account;
delete from user_task_progress;
delete from user_consumed_event;
delete from auth_refresh_token;
delete from comment;
delete from post_tag;
delete from tag;
delete from category;
delete from social_like;
delete from social_user_like_count;
delete from social_follow;
delete from social_block;
delete from message;
delete from http_idempotency;
delete from outbox_event;
delete from discuss_post;
delete from user;

merge into user (id, username, password, salt, email, type, status, header_url, create_time, score)
key(id) values
  (X'00000000000070008000000000000001', 'u1', 'p', 's', 'u1@example.com', 0, 1, 'http://old.local/a.png', CURRENT_TIMESTAMP(), 0),
  (X'00000000000070008000000000000002', 'u2', 'p', 's', 'u2@example.com', 0, 1, 'http://old.local/b.png', CURRENT_TIMESTAMP(), 0);

merge into category (id, name, description, position, create_time)
key(id) values
  (X'00000000000070008000000000000001', '技术', '技术讨论', 10, CURRENT_TIMESTAMP()),
  (X'00000000000070008000000000000002', '兴趣', '兴趣分享', 20, CURRENT_TIMESTAMP());

insert into discuss_post(id, user_id, category_id, title, content, type, status, create_time, update_time, edit_count, comment_count, score)
values (
  X'00000000000070008000000000000064',
  X'00000000000070008000000000000001',
  X'00000000000070008000000000000001',
  'hello world',
  'hello content',
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
