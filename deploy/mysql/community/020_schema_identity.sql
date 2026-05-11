-- Source: 010_schema_identity.sql
-- --------------------------------------------------------------------
-- Identity schema (keep in MYSQL_DATABASE=community for P0).
-- NOTE:
-- - 身份域数据所有权已收敛到 user 模块；
-- - auth 模块已不再直连 MySQL（通过调用 user 模块内部 API 完成鉴权/注册/邮箱验证/重置密码等）。

use community;

create table if not exists user (
  id binary(16) primary key,
  username varchar(255) not null,
  password varchar(255),
  salt varchar(255),
  email varchar(255),
  type int default 0,
  status int default 0,
  header_url varchar(255),
  create_time timestamp null default current_timestamp,
  score int not null default 0,
  mute_until timestamp null default null,
  ban_until timestamp null default null,
  unique key uk_user_username (username),
  unique key uk_user_email (email)
);

-- refresh token（SSOT=DB）：auth 模块不直连 MySQL，改由 user 模块托管会话状态
-- 注意：只存 token_hash（SHA-256 hex），避免明文凭据落库
create table if not exists auth_refresh_token (
  token_hash char(64) primary key,
  user_id binary(16) not null,
  family_id varchar(64) not null,
  expires_at timestamp not null,
  revoked_at timestamp null default null,
  created_at timestamp null default current_timestamp,
  key idx_refresh_family (family_id, expires_at),
  key idx_refresh_user (user_id, expires_at)
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


-- --------------------------------------------------------------------
