-- Content schema (community_content): posts + comments.

use community_content;

create table if not exists discuss_post (
  id int auto_increment primary key,
  user_id int,
  category_id int default null,
  title varchar(255),
  content text,
  type int default 0,
  status int default 0,
  create_time timestamp null default current_timestamp,
  update_time timestamp null default null,
  edit_count int default 0,
  deleted_by int default 0,
  deleted_reason varchar(255) default '',
  deleted_time timestamp null default null,
  comment_count int default 0,
  score double default 0
);

create table if not exists comment (
  id int auto_increment primary key,
  user_id int,
  entity_type int,
  entity_id int,
  target_id int default 0,
  content text,
  status int default 0,
  create_time timestamp null default current_timestamp,
  update_time timestamp null default null,
  edit_count int default 0,
  deleted_by int default 0,
  deleted_reason varchar(255) default '',
  deleted_time timestamp null default null
);

-- taxonomy: categories + tags (Discourse-like)
create table if not exists category (
  id int auto_increment primary key,
  name varchar(64) not null,
  description varchar(255) default '',
  position int default 0,
  create_time timestamp null default current_timestamp,
  unique key uk_category_name (name)
);

create table if not exists tag (
  id int auto_increment primary key,
  name varchar(64) not null,
  create_time timestamp null default current_timestamp,
  unique key uk_tag_name (name)
);

create table if not exists post_tag (
  post_id int not null,
  tag_id int not null,
  create_time timestamp null default current_timestamp,
  primary key (post_id, tag_id)
);

-- HTTP 写接口幂等（SSOT=DB）：同一 user + operation + key 只执行一次副作用
create table if not exists http_idempotency (
  id bigint auto_increment primary key,
  operation varchar(64) not null,
  user_id int not null,
  idem_key varchar(128) not null,
  status varchar(16) not null,
  response_json mediumtext null,
  processing_expires_at timestamp null,
  success_expires_at timestamp null,
  created_at timestamp null default current_timestamp,
  updated_at timestamp null default current_timestamp on update current_timestamp,
  unique key uk_http_idem (operation, user_id, idem_key),
  key idx_http_idem_processing_expires (processing_expires_at, id),
  key idx_http_idem_success_expires (success_expires_at, id)
);

-- moderation: reports + actions (MVP)
create table if not exists report (
  id int auto_increment primary key,
  reporter_id int not null,
  target_type int not null,
  target_id int not null,
  reason varchar(64) not null,
  detail varchar(512) default '',
  status int default 0,
  create_time timestamp null default current_timestamp,
  unique key uk_report_dedupe (reporter_id, target_type, target_id)
);

create table if not exists moderation_action (
  id int auto_increment primary key,
  report_id int default null,
  actor_id int not null,
  action varchar(32) not null,
  reason varchar(255) default '',
  duration_seconds int default 0,
  create_time timestamp null default current_timestamp
);

-- bookmarks/subscriptions (MVP)
create table if not exists post_bookmark (
  user_id int not null,
  post_id int not null,
  create_time timestamp null default current_timestamp,
  primary key (user_id, post_id)
);

create table if not exists user_subscription_category (
  user_id int not null,
  category_id int not null,
  create_time timestamp null default current_timestamp,
  primary key (user_id, category_id)
);

-- Compatibility upgrade: add missing column for existing DBs (manual re-run scenario).
set @has_category_id := (
  select count(*)
  from information_schema.columns
  where table_schema = database()
    and table_name = 'discuss_post'
    and column_name = 'category_id'
);
set @sql := if(@has_category_id = 0, 'alter table discuss_post add column category_id int default null', 'select 1');
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

-- Compatibility upgrade: add edit/update/delete meta columns for existing DBs.
set @has_update_time := (
  select count(*)
  from information_schema.columns
  where table_schema = database()
    and table_name = 'discuss_post'
    and column_name = 'update_time'
);
set @sql := if(@has_update_time = 0, 'alter table discuss_post add column update_time timestamp null default null', 'select 1');
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @has_edit_count := (
  select count(*)
  from information_schema.columns
  where table_schema = database()
    and table_name = 'discuss_post'
    and column_name = 'edit_count'
);
set @sql := if(@has_edit_count = 0, 'alter table discuss_post add column edit_count int default 0', 'select 1');
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @has_deleted_by := (
  select count(*)
  from information_schema.columns
  where table_schema = database()
    and table_name = 'discuss_post'
    and column_name = 'deleted_by'
);
set @sql := if(@has_deleted_by = 0, 'alter table discuss_post add column deleted_by int default 0', 'select 1');
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @has_deleted_reason := (
  select count(*)
  from information_schema.columns
  where table_schema = database()
    and table_name = 'discuss_post'
    and column_name = 'deleted_reason'
);
set @sql := if(@has_deleted_reason = 0, 'alter table discuss_post add column deleted_reason varchar(255) default ''''', 'select 1');
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @has_deleted_time := (
  select count(*)
  from information_schema.columns
  where table_schema = database()
    and table_name = 'discuss_post'
    and column_name = 'deleted_time'
);
set @sql := if(@has_deleted_time = 0, 'alter table discuss_post add column deleted_time timestamp null default null', 'select 1');
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @has_comment_update_time := (
  select count(*)
  from information_schema.columns
  where table_schema = database()
    and table_name = 'comment'
    and column_name = 'update_time'
);
set @sql := if(@has_comment_update_time = 0, 'alter table comment add column update_time timestamp null default null', 'select 1');
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @has_comment_edit_count := (
  select count(*)
  from information_schema.columns
  where table_schema = database()
    and table_name = 'comment'
    and column_name = 'edit_count'
);
set @sql := if(@has_comment_edit_count = 0, 'alter table comment add column edit_count int default 0', 'select 1');
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @has_comment_deleted_by := (
  select count(*)
  from information_schema.columns
  where table_schema = database()
    and table_name = 'comment'
    and column_name = 'deleted_by'
);
set @sql := if(@has_comment_deleted_by = 0, 'alter table comment add column deleted_by int default 0', 'select 1');
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @has_comment_deleted_reason := (
  select count(*)
  from information_schema.columns
  where table_schema = database()
    and table_name = 'comment'
    and column_name = 'deleted_reason'
);
set @sql := if(@has_comment_deleted_reason = 0, 'alter table comment add column deleted_reason varchar(255) default ''''', 'select 1');
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @has_comment_deleted_time := (
  select count(*)
  from information_schema.columns
  where table_schema = database()
    and table_name = 'comment'
    and column_name = 'deleted_time'
);
set @sql := if(@has_comment_deleted_time = 0, 'alter table comment add column deleted_time timestamp null default null', 'select 1');
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

-- Indexes (idempotent)
set @idx_discuss_post_user_id := (
  select count(*)
  from information_schema.statistics
  where table_schema = database()
    and table_name = 'discuss_post'
    and index_name = 'idx_discuss_post_user_id'
);
set @sql := if(@idx_discuss_post_user_id = 0, 'create index idx_discuss_post_user_id on discuss_post(user_id)', 'select 1');
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @idx_discuss_post_category_id := (
  select count(*)
  from information_schema.statistics
  where table_schema = database()
    and table_name = 'discuss_post'
    and index_name = 'idx_discuss_post_category_id'
);
set @sql := if(@idx_discuss_post_category_id = 0, 'create index idx_discuss_post_category_id on discuss_post(category_id)', 'select 1');
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

-- report indexes (idempotent)
set @idx_report_status := (
  select count(*)
  from information_schema.statistics
  where table_schema = database()
    and table_name = 'report'
    and index_name = 'idx_report_status'
);
set @sql := if(@idx_report_status = 0, 'create index idx_report_status on report(status, create_time)', 'select 1');
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @idx_report_target := (
  select count(*)
  from information_schema.statistics
  where table_schema = database()
    and table_name = 'report'
    and index_name = 'idx_report_target'
);
set @sql := if(@idx_report_target = 0, 'create index idx_report_target on report(target_type, target_id, create_time)', 'select 1');
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

-- moderation_action indexes (idempotent)
set @idx_moderation_action_report := (
  select count(*)
  from information_schema.statistics
  where table_schema = database()
    and table_name = 'moderation_action'
    and index_name = 'idx_moderation_action_report'
);
set @sql := if(@idx_moderation_action_report = 0, 'create index idx_moderation_action_report on moderation_action(report_id, create_time)', 'select 1');
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @idx_moderation_action_actor := (
  select count(*)
  from information_schema.statistics
  where table_schema = database()
    and table_name = 'moderation_action'
    and index_name = 'idx_moderation_action_actor'
);
set @sql := if(@idx_moderation_action_actor = 0, 'create index idx_moderation_action_actor on moderation_action(actor_id, create_time)', 'select 1');
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

-- bookmark/subscription indexes (idempotent)
set @idx_post_bookmark_post := (
  select count(*)
  from information_schema.statistics
  where table_schema = database()
    and table_name = 'post_bookmark'
    and index_name = 'idx_post_bookmark_post'
);
set @sql := if(@idx_post_bookmark_post = 0, 'create index idx_post_bookmark_post on post_bookmark(post_id, create_time)', 'select 1');
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @idx_user_sub_category_user := (
  select count(*)
  from information_schema.statistics
  where table_schema = database()
    and table_name = 'user_subscription_category'
    and index_name = 'idx_user_sub_category_user'
);
set @sql := if(@idx_user_sub_category_user = 0, 'create index idx_user_sub_category_user on user_subscription_category(user_id, create_time)', 'select 1');
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @idx_comment_entity := (
  select count(*)
  from information_schema.statistics
  where table_schema = database()
    and table_name = 'comment'
    and index_name = 'idx_comment_entity'
);
set @sql := if(@idx_comment_entity = 0, 'create index idx_comment_entity on comment(entity_type, entity_id)', 'select 1');
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @idx_post_tag_post_id := (
  select count(*)
  from information_schema.statistics
  where table_schema = database()
    and table_name = 'post_tag'
    and index_name = 'idx_post_tag_post_id'
);
set @sql := if(@idx_post_tag_post_id = 0, 'create index idx_post_tag_post_id on post_tag(post_id)', 'select 1');
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @idx_post_tag_tag_id := (
  select count(*)
  from information_schema.statistics
  where table_schema = database()
    and table_name = 'post_tag'
    and index_name = 'idx_post_tag_tag_id'
);
set @sql := if(@idx_post_tag_tag_id = 0, 'create index idx_post_tag_tag_id on post_tag(tag_id)', 'select 1');
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

-- Outbox events (idempotent)
create table if not exists outbox_event (
  id bigint auto_increment primary key,
  event_id varchar(64) unique,
  topic varchar(128),
  event_key varchar(128),
  payload text,
  status varchar(16),
  retry_count int default 0,
  next_retry_at timestamp null,
  last_error varchar(255),
  created_at timestamp null default current_timestamp,
  updated_at timestamp null default current_timestamp
);

set @idx_outbox_status_next := (
  select count(*)
  from information_schema.statistics
  where table_schema = database()
    and table_name = 'outbox_event'
    and index_name = 'idx_outbox_status_next'
);
set @idx_outbox_status_next_has_id := (
  select count(*)
  from information_schema.statistics
  where table_schema = database()
    and table_name = 'outbox_event'
    and index_name = 'idx_outbox_status_next'
    and column_name = 'id'
    and seq_in_index = 3
);

-- NEW/RETRY 轮询候选集索引：应包含 (status, next_retry_at, id) 以支撑 where + order by id。
set @sql := if(@idx_outbox_status_next = 0, 'create index idx_outbox_status_next on outbox_event(status, next_retry_at, id)', 'select 1');
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

-- 兼容：历史版本 idx_outbox_status_next 仅 (status, next_retry_at)；为避免 drift，检测缺列时重建。
set @sql := if(@idx_outbox_status_next > 0 and @idx_outbox_status_next_has_id = 0, 'drop index idx_outbox_status_next on outbox_event', 'select 1');
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @sql := if(@idx_outbox_status_next > 0 and @idx_outbox_status_next_has_id = 0, 'create index idx_outbox_status_next on outbox_event(status, next_retry_at, id)', 'select 1');
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

-- Outbox lease recover / cleanup indexes
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

-- Default categories for dev (idempotent)
insert ignore into category(name, description, position) values
  ('公告', '官方公告/规则', 0),
  ('技术', '技术讨论/问题求助', 10),
  ('兴趣', '兴趣分享/作品展示', 20);
