-- Source: 020_schema_content.sql
-- --------------------------------------------------------------------
-- Content schema (community): posts + comments.

use community;

create table if not exists discuss_post (
  id binary(16) primary key,
  user_id binary(16),
  category_id binary(16) default null,
  title varchar(255),
  type int default 0,
  status int default 0,
  create_time timestamp null default current_timestamp,
  update_time timestamp null default null,
  edit_count int default 0,
  deleted_by binary(16) default null,
  deleted_reason varchar(255) default '',
  deleted_time timestamp null default null,
  comment_count int default 0,
  score double default 0,
  key idx_discuss_post_user_id (user_id),
  key idx_discuss_post_category_id (category_id)
);

create table if not exists post_media_asset (
  id binary(16) primary key,
  owner_user_id binary(16) not null,
  post_id binary(16) default null,
  oss_object_id binary(16) not null,
  oss_version_id binary(16) default null,
  oss_reference_id binary(16) default null,
  upload_session_id binary(16) default null,
  file_name varchar(255) not null,
  content_type varchar(128) not null,
  content_length bigint not null,
  media_kind varchar(32) not null,
  lifecycle varchar(32) not null,
  video_state varchar(32) not null default 'NONE',
  public_url varchar(1024) default '',
  failure_reason varchar(512) default '',
  create_time timestamp null default current_timestamp,
  update_time timestamp null default null,
  key idx_post_media_asset_owner_lifecycle (owner_user_id, lifecycle),
  key idx_post_media_asset_post (post_id),
  key idx_post_media_asset_video_state (video_state)
);

create table if not exists post_content_block (
  id binary(16) primary key,
  post_id binary(16) not null,
  block_index int not null,
  block_type varchar(32) not null,
  text_content text null,
  language varchar(64) default '',
  media_asset_id binary(16) default null,
  caption varchar(512) default '',
  display_name varchar(255) default '',
  metadata_json text default null,
  create_time timestamp null default current_timestamp,
  update_time timestamp null default null,
  unique key uk_post_block_index (post_id, block_index),
  key idx_post_content_block_post (post_id),
  key idx_post_content_block_media (media_asset_id)
);

create table if not exists comment (
  id binary(16) primary key,
  user_id binary(16),
  entity_type int,
  entity_id binary(16),
  target_id binary(16) default null,
  content text,
  status int default 0,
  create_time timestamp null default current_timestamp,
  update_time timestamp null default null,
  edit_count int default 0,
  deleted_by binary(16) default null,
  deleted_reason varchar(255) default '',
  deleted_time timestamp null default null,
  key idx_comment_entity (entity_type, entity_id)
);

-- taxonomy: categories + tags (Discourse-like)
create table if not exists category (
  id binary(16) primary key,
  name varchar(64) not null,
  description varchar(255) default '',
  position int default 0,
  create_time timestamp null default current_timestamp,
  unique key uk_category_name (name)
);

create table if not exists tag (
  id binary(16) primary key,
  name varchar(64) not null,
  create_time timestamp null default current_timestamp,
  unique key uk_tag_name (name)
);

create table if not exists post_tag (
  post_id binary(16) not null,
  tag_id binary(16) not null,
  create_time timestamp null default current_timestamp,
  primary key (post_id, tag_id),
  key idx_post_tag_post_id (post_id),
  key idx_post_tag_tag_id (tag_id)
);

-- moderation: reports + actions (MVP)
create table if not exists report (
  id binary(16) primary key,
  reporter_id binary(16) not null,
  target_type int not null,
  target_id binary(16) not null,
  reason varchar(64) not null,
  detail varchar(512) default '',
  status int default 0,
  create_time timestamp null default current_timestamp,
  unique key uk_report_dedupe (reporter_id, target_type, target_id),
  key idx_report_status (status, create_time),
  key idx_report_target (target_type, target_id, create_time)
);

create table if not exists moderation_action (
  id binary(16) primary key,
  report_id binary(16) default null,
  actor_id binary(16) not null,
  action varchar(32) not null,
  reason varchar(255) default '',
  duration_seconds int default 0,
  create_time timestamp null default current_timestamp,
  key idx_moderation_action_report (report_id, create_time),
  key idx_moderation_action_actor (actor_id, create_time)
);

-- bookmarks/subscriptions (MVP)
create table if not exists post_bookmark (
  user_id binary(16) not null,
  post_id binary(16) not null,
  create_time timestamp null default current_timestamp,
  primary key (user_id, post_id),
  key idx_post_bookmark_post (post_id, create_time)
);

create table if not exists user_subscription_category (
  user_id binary(16) not null,
  category_id binary(16) not null,
  create_time timestamp null default current_timestamp,
  primary key (user_id, category_id),
  key idx_user_sub_category_user (user_id, create_time)
);

create table if not exists post_counter_snapshot (
  post_id binary(16) primary key,
  view_count bigint not null default 0,
  like_count bigint not null default 0,
  comment_count bigint not null default 0,
  bookmark_count bigint not null default 0,
  snapshot_time timestamp null default current_timestamp on update current_timestamp
);

create table if not exists post_score_snapshot (
  post_id binary(16) primary key,
  score double not null default 0,
  rank_version varchar(64) not null,
  snapshot_time timestamp null default current_timestamp on update current_timestamp
);

insert ignore into category(name, description, position) values
  ('公告', '官方公告/规则', 0),
  ('技术', '技术讨论/问题求助', 10),
  ('兴趣', '兴趣分享/作品展示', 20);
