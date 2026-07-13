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
  index idx_like_entity (entity_type, entity_id),
  index idx_like_entity_user (entity_type, entity_id, user_id)
);

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
  version bigint not null default 0,
  primary key (user_id, target_user_id),
  index idx_block_user_created (user_id, created_at)
);

create table if not exists social_block_version_counter (
  id int primary key,
  current_version bigint not null default 0
);

set @social_block_current_version := (
  select coalesce(max(version), 0) from social_block
);

insert into social_block_version_counter(id, current_version)
values (1, @social_block_current_version)
on duplicate key update current_version = greatest(current_version, values(current_version));

create table if not exists social_block_version_log (
  version bigint primary key,
  user_id binary(16) not null,
  target_user_id binary(16) not null,
  active tinyint(1) not null,
  occurred_at timestamp not null default current_timestamp,
  index idx_social_block_version_pair (user_id, target_user_id, version)
);

-- --------------------------------------------------------------------
