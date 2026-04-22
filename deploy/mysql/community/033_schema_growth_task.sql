-- Source: 015_schema_growth.sql (task split)
-- --------------------------------------------------------------------
-- Task schema (community): task config, progress, event log, and seed rows.

use community;

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
  last_source_event_id varchar(64) default null,
  update_time timestamp null default current_timestamp on update current_timestamp,
  unique key uk_user_task_period (user_id, task_code, period_key),
  key idx_user_task_lookup (user_id, status, update_time)
);

create table if not exists user_task_event_log (
  id binary(16) primary key,
  user_id binary(16) not null,
  task_code varchar(64) not null,
  period_key varchar(32) not null,
  source_event_id varchar(64) not null,
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
