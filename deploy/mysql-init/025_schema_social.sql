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
