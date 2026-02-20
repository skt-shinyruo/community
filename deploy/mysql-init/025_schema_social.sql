-- Social schema (community_social): likes + follows + blocks + outbox.

use community_social;

create table if not exists social_like (
  user_id bigint not null,
  entity_type int not null,
  entity_id bigint not null,
  created_at timestamp not null default current_timestamp,
  primary key (user_id, entity_type, entity_id),
  index idx_like_entity (entity_type, entity_id)
);

-- social_like 扫描索引（idempotent）：用于按 (entity_type, entity_id, user_id) keyset 分页回填下游投影
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
  user_id bigint not null primary key,
  like_count bigint not null default 0,
  updated_at timestamp not null default current_timestamp on update current_timestamp
);

create table if not exists social_follow (
  user_id bigint not null,
  entity_type int not null,
  entity_id bigint not null,
  created_at timestamp not null default current_timestamp,
  primary key (user_id, entity_type, entity_id),
  index idx_follow_followee (user_id, entity_type, created_at, entity_id),
  index idx_follow_follower (entity_type, entity_id, created_at, user_id)
);

create table if not exists social_block (
  user_id bigint not null,
  target_user_id bigint not null,
  created_at timestamp not null default current_timestamp,
  primary key (user_id, target_user_id),
  index idx_block_user_created (user_id, created_at)
);

-- content 实体元信息投影（用于 social 写路径解析 entity -> owner/postId/status，避免跨域同步 resolve）
create table if not exists social_content_entity_projection (
  entity_type int not null,
  entity_id bigint not null,
  entity_user_id bigint not null default 0,
  post_id bigint not null default 0,
  status int not null default 0,
  updated_at timestamp not null default current_timestamp,
  primary key (entity_type, entity_id)
);

-- Outbox（可靠事件投递）
create table if not exists outbox_event (
  id bigint not null auto_increment primary key,
  event_id varchar(64) not null,
  topic varchar(128) not null,
  event_key varchar(128) not null,
  payload text not null,
  status varchar(32) not null,
  retry_count int not null default 0,
  next_retry_at timestamp null,
  last_error varchar(512) null,
  created_at timestamp not null default current_timestamp,
  updated_at timestamp not null default current_timestamp on update current_timestamp,
  unique key uk_outbox_event_id (event_id),
  index idx_outbox_status_next (status, next_retry_at, id)
);

-- Outbox lease recover / cleanup indexes（idempotent）
set @idx_outbox_status_updated := (
  select count(*)
  from information_schema.statistics
  where table_schema = database()
    and table_name = 'outbox_event'
    and index_name = 'idx_outbox_status_updated'
);
set @sql := if(@idx_outbox_status_updated = 0, 'create index idx_outbox_status_updated on outbox_event(status, updated_at, id)', 'select 1');
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @idx_outbox_status_created := (
  select count(*)
  from information_schema.statistics
  where table_schema = database()
    and table_name = 'outbox_event'
    and index_name = 'idx_outbox_status_created'
);
set @sql := if(@idx_outbox_status_created = 0, 'create index idx_outbox_status_created on outbox_event(status, created_at, id)', 'select 1');
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;
