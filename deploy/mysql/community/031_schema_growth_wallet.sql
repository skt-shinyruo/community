-- Source: 015_schema_growth.sql (wallet split)
-- --------------------------------------------------------------------
-- Wallet schema (community): balance accounts, transactions, and wallet orders.

use community;

create table if not exists wallet_account (
  account_id bigint auto_increment primary key,
  owner_type varchar(32) not null,
  owner_id bigint not null,
  account_type varchar(32) not null,
  balance bigint not null default 0,
  status varchar(16) not null,
  version bigint not null default 0,
  create_time timestamp null default current_timestamp,
  update_time timestamp null default current_timestamp on update current_timestamp,
  unique key uk_wallet_account_owner (owner_type, owner_id, account_type)
);

create table if not exists wallet_txn (
  txn_id bigint auto_increment primary key,
  request_id varchar(96) not null,
  txn_type varchar(32) not null,
  biz_type varchar(32) not null,
  biz_id varchar(96) not null,
  status varchar(16) not null,
  amount bigint not null,
  remark varchar(255) default null,
  create_time timestamp null default current_timestamp,
  update_time timestamp null default current_timestamp on update current_timestamp,
  unique key uk_wallet_txn_request (request_id)
);

create table if not exists wallet_entry (
  entry_id bigint auto_increment primary key,
  txn_id bigint not null,
  account_id bigint not null,
  direction varchar(8) not null,
  amount bigint not null,
  balance_after bigint not null,
  create_time timestamp null default current_timestamp,
  key idx_wallet_entry_txn (txn_id),
  key idx_wallet_entry_account_time (account_id, create_time)
);

create table if not exists recharge_order (
  order_id bigint auto_increment primary key,
  request_id varchar(96) not null,
  user_id bigint not null,
  amount bigint not null,
  status varchar(16) not null,
  channel varchar(32) default null,
  channel_order_id varchar(96) default null,
  remark varchar(255) default null,
  create_time timestamp null default current_timestamp,
  update_time timestamp null default current_timestamp on update current_timestamp,
  unique key uk_recharge_order_request (request_id),
  key idx_recharge_order_user_time (user_id, create_time)
);

create table if not exists withdraw_order (
  order_id bigint auto_increment primary key,
  request_id varchar(96) not null,
  user_id bigint not null,
  amount bigint not null,
  status varchar(16) not null,
  payee_account varchar(128) default null,
  failure_reason varchar(255) default null,
  create_time timestamp null default current_timestamp,
  update_time timestamp null default current_timestamp on update current_timestamp,
  unique key uk_withdraw_order_request (request_id),
  key idx_withdraw_order_user_time (user_id, create_time)
);

create table if not exists transfer_order (
  order_id bigint auto_increment primary key,
  request_id varchar(96) not null,
  from_user_id bigint not null,
  to_user_id bigint not null,
  amount bigint not null,
  status varchar(16) not null,
  remark varchar(255) default null,
  create_time timestamp null default current_timestamp,
  update_time timestamp null default current_timestamp on update current_timestamp,
  unique key uk_transfer_order_request (request_id),
  key idx_transfer_order_from_user_time (from_user_id, create_time),
  key idx_transfer_order_to_user_time (to_user_id, create_time)
);

create table if not exists wallet_admin_action (
  action_id bigint auto_increment primary key,
  request_id varchar(96) not null,
  actor_user_id bigint not null,
  target_account_id bigint not null,
  action_type varchar(32) not null,
  amount bigint not null,
  remark varchar(255) default null,
  create_time timestamp null default current_timestamp,
  unique key uk_wallet_admin_action_request (request_id),
  key idx_wallet_admin_action_target_time (target_account_id, create_time)
);
