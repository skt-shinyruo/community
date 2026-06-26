-- Source: 015_schema_growth.sql (wallet split)
-- --------------------------------------------------------------------
-- Wallet schema (community): balance accounts, transactions, and wallet orders.

use community;

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

set @has_recharge_order_global_request_uk := (
  select count(*)
  from information_schema.statistics
  where table_schema = database()
    and table_name = 'recharge_order'
    and index_name = 'uk_recharge_order_request'
);
set @sql := if(@has_recharge_order_global_request_uk > 0, 'alter table recharge_order drop index uk_recharge_order_request', 'select 1');
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @has_recharge_order_user_request_uk := (
  select count(*)
  from information_schema.statistics
  where table_schema = database()
    and table_name = 'recharge_order'
    and index_name = 'uk_recharge_order_user_request'
);
set @sql := if(@has_recharge_order_user_request_uk = 0, 'alter table recharge_order add unique key uk_recharge_order_user_request (user_id, request_id)', 'select 1');
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @has_withdraw_order_global_request_uk := (
  select count(*)
  from information_schema.statistics
  where table_schema = database()
    and table_name = 'withdraw_order'
    and index_name = 'uk_withdraw_order_request'
);
set @sql := if(@has_withdraw_order_global_request_uk > 0, 'alter table withdraw_order drop index uk_withdraw_order_request', 'select 1');
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @has_withdraw_order_user_request_uk := (
  select count(*)
  from information_schema.statistics
  where table_schema = database()
    and table_name = 'withdraw_order'
    and index_name = 'uk_withdraw_order_user_request'
);
set @sql := if(@has_withdraw_order_user_request_uk = 0, 'alter table withdraw_order add unique key uk_withdraw_order_user_request (user_id, request_id)', 'select 1');
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @has_transfer_order_global_request_uk := (
  select count(*)
  from information_schema.statistics
  where table_schema = database()
    and table_name = 'transfer_order'
    and index_name = 'uk_transfer_order_request'
);
set @sql := if(@has_transfer_order_global_request_uk > 0, 'alter table transfer_order drop index uk_transfer_order_request', 'select 1');
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @has_transfer_order_from_request_uk := (
  select count(*)
  from information_schema.statistics
  where table_schema = database()
    and table_name = 'transfer_order'
    and index_name = 'uk_transfer_order_from_request'
);
set @sql := if(@has_transfer_order_from_request_uk = 0, 'alter table transfer_order add unique key uk_transfer_order_from_request (from_user_id, request_id)', 'select 1');
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;
