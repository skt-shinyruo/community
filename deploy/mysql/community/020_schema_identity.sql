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
  mute_until timestamp null default null,
  ban_until timestamp null default null,
  policy_version bigint not null default 0,
  security_version bigint not null default 0,
  unique key uk_user_username (username),
  unique key uk_user_email (email),
  constraint ck_user_type check (type in (0, 1, 2))
);

set @col_user_policy_version := (
  select count(*)
  from information_schema.columns
  where table_schema = database()
    and table_name = 'user'
    and column_name = 'policy_version'
);
set @sql := if(@col_user_policy_version = 0, 'alter table user add column policy_version bigint not null default 0 after ban_until', 'select 1');
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @col_user_security_version := (
  select count(*)
  from information_schema.columns
  where table_schema = database()
    and table_name = 'user'
    and column_name = 'security_version'
);
set @sql := if(@col_user_security_version = 0, 'alter table user add column security_version bigint not null default 0 after policy_version', 'select 1');
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @ck_user_type := (
  select count(*)
  from information_schema.table_constraints
  where table_schema = database()
    and table_name = 'user'
    and constraint_name = 'ck_user_type'
);
set @sql := if(@ck_user_type = 0, 'alter table user add constraint ck_user_type check (type in (0, 1, 2))', 'select 1');
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @user_policy_seed_version := cast(floor(unix_timestamp(current_timestamp(3)) * 1000) * 4096 as unsigned);
update user
set policy_version = @user_policy_seed_version
where policy_version = 0;

set @user_security_seed_version := cast(floor(unix_timestamp(current_timestamp(3)) * 1000) * 4096 as unsigned);
update user
set security_version = @user_security_seed_version
where security_version = 0;

set @has_user_score_col := (
  select count(*)
  from information_schema.columns
  where table_schema = database()
    and table_name = 'user'
    and column_name = 'score'
);
set @sql := if(@has_user_score_col > 0, 'alter table user drop column score', 'select 1');
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

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

create table if not exists user_policy_version_counter (
  id int primary key,
  current_version bigint not null default 0
);

set @user_policy_current_version := greatest(
  @user_policy_seed_version,
  (select coalesce(max(policy_version), 0) from user)
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
