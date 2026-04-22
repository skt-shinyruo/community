-- Source: 015_schema_growth.sql (reward split)
-- --------------------------------------------------------------------
-- Growth reward schema (community): score logs, reward balances, and grants.

use community;

create table if not exists user_score_log (
  id binary(16) primary key,
  user_id binary(16) not null,
  event_id varchar(64) not null,
  event_type varchar(64) not null,
  delta int not null,
  create_time timestamp null default current_timestamp,
  unique key uk_event_id (event_id),
  key idx_user_time (user_id, create_time)
);

create table if not exists reward_account (
  user_id binary(16) not null primary key,
  available_balance int not null default 0,
  frozen_balance int not null default 0,
  version int not null default 0,
  update_time timestamp null default current_timestamp
);

create table if not exists reward_ledger (
  id binary(16) primary key,
  user_id binary(16) not null,
  event_id varchar(64) not null,
  event_type varchar(64) not null,
  delta int not null,
  balance_after int not null,
  frozen_balance_after int not null default 0,
  biz_key varchar(128) default null,
  source_module varchar(64) default null,
  remark varchar(255) default null,
  create_time timestamp null default current_timestamp,
  unique key uk_reward_ledger_event_id (event_id),
  key idx_reward_ledger_user_time (user_id, create_time)
);

create table if not exists reward_grant_record (
  id binary(16) primary key,
  grant_id varchar(64) not null,
  user_id binary(16) not null,
  grant_type varchar(64) not null,
  source_event_id varchar(64) not null,
  source_event_type varchar(64) not null,
  growth_delta int not null default 0,
  reward_delta int not null default 0,
  status varchar(32) not null,
  create_time timestamp null default current_timestamp,
  unique key uk_reward_grant_id (grant_id),
  key idx_reward_grant_user_time (user_id, create_time)
);
