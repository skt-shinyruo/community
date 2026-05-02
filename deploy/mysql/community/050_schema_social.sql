-- Source: 025_schema_social.sql
-- --------------------------------------------------------------------
-- Social schema (community): likes + follows + blocks.

use community;

create table if not exists social_like (
  user_id binary(16) not null,
  entity_type int not null,
  entity_id binary(16) not null,
  entity_user_id binary(16) null,
  created_at timestamp not null default current_timestamp,
  primary key (user_id, entity_type, entity_id),
  index idx_like_entity (entity_type, entity_id)
);

set @col_like_entity_user_id := (
  select count(*)
  from information_schema.columns
  where table_schema = database()
    and table_name = 'social_like'
    and column_name = 'entity_user_id'
);
set @sql := if(@col_like_entity_user_id = 0, 'alter table social_like add column entity_user_id binary(16) null after entity_id', 'select 1');
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

-- social_like 扫描索引（idempotent）：用于按 (entity_type, entity_id, user_id) keyset 分页（运维排查/历史遗留 scan 接口）
set @idx_like_entity_user := (
  select count(*)
  from information_schema.statistics
  where table_schema = database()
    and table_name = 'social_like'
    and index_name = 'idx_like_entity_user'
);
set @sql := if(@idx_like_entity_user = 0, 'create index idx_like_entity_user on social_like(entity_type, entity_id, user_id)', 'select 1');
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

-- 用户获赞数（计数 SSOT）：由写路径在“新增点赞/取消点赞”时原子增减。
create table if not exists social_user_like_count (
  user_id binary(16) not null primary key,
  like_count bigint not null default 0,
  updated_at timestamp not null default current_timestamp on update current_timestamp
);

create table if not exists social_follow (
  user_id binary(16) not null,
  entity_type int not null,
  entity_id binary(16) not null,
  created_at timestamp not null default current_timestamp,
  primary key (user_id, entity_type, entity_id),
  index idx_follow_followee (user_id, entity_type, created_at, entity_id),
  index idx_follow_follower (entity_type, entity_id, created_at, user_id)
);

create table if not exists social_block (
  user_id binary(16) not null,
  target_user_id binary(16) not null,
  created_at timestamp not null default current_timestamp,
  primary key (user_id, target_user_id),
  index idx_block_user_created (user_id, created_at)
);


-- --------------------------------------------------------------------
