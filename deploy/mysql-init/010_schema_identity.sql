-- Identity schema (keep in MYSQL_DATABASE=community for P0).
-- NOTE:
-- - 身份域数据所有权已收敛到 user-service；
-- - auth-service 已不再直连 MySQL（通过调用 user-service internal API 完成鉴权/注册/激活/重置密码等）。

use community;

create table if not exists user (
  id int auto_increment primary key,
  username varchar(255) not null,
  password varchar(255),
  salt varchar(255),
  email varchar(255),
  type int default 0,
  status int default 0,
  activation_code varchar(255),
  header_url varchar(255),
  create_time timestamp null default current_timestamp,
  score int not null default 0,
  mute_until timestamp null default null,
  ban_until timestamp null default null
);

-- refresh token（SSOT=DB）：auth-service 不直连 MySQL，改由 user-service 托管会话状态
-- 注意：只存 token_hash（SHA-256 hex），避免明文凭据落库
create table if not exists auth_refresh_token (
  token_hash char(64) primary key,
  user_id int not null,
  family_id varchar(64) not null,
  expires_at timestamp not null,
  revoked_at timestamp null default null,
  created_at timestamp null default current_timestamp,
  key idx_refresh_family (family_id, expires_at),
  key idx_refresh_user (user_id, expires_at)
);

-- Compatibility upgrade: add moderation columns for existing DBs (manual re-run scenario).
set @has_mute_until := (
  select count(*)
  from information_schema.columns
  where table_schema = database()
    and table_name = 'user'
    and column_name = 'mute_until'
);
set @sql := if(@has_mute_until = 0, 'alter table user add column mute_until timestamp null default null', 'select 1');
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @has_score := (
  select count(*)
  from information_schema.columns
  where table_schema = database()
    and table_name = 'user'
    and column_name = 'score'
);
set @sql := if(@has_score = 0, 'alter table user add column score int not null default 0', 'select 1');
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @has_ban_until := (
  select count(*)
  from information_schema.columns
  where table_schema = database()
    and table_name = 'user'
    and column_name = 'ban_until'
);
set @sql := if(@has_ban_until = 0, 'alter table user add column ban_until timestamp null default null', 'select 1');
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

-- user-service Kafka 消费幂等（处罚命令等）：以 event_id 唯一约束为准（insert-first）。
create table if not exists user_consumed_event (
  id bigint auto_increment primary key,
  event_id varchar(64) not null,
  consumed_at timestamp not null default current_timestamp,
  unique key uk_user_consumed_event_id (event_id),
  index idx_user_consumed_event_at (consumed_at)
);
