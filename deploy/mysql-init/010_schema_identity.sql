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
