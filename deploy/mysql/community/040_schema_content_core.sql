-- Source: 020_schema_content.sql
-- --------------------------------------------------------------------
-- Content schema (community): posts + comments.

use community;

create table if not exists discuss_post (
  id binary(16) primary key,
  user_id binary(16),
  category_id binary(16) default null,
  title varchar(255),
  content text,
  type int default 0,
  status int default 0,
  create_time timestamp null default current_timestamp,
  update_time timestamp null default null,
  edit_count int default 0,
  deleted_by binary(16) default null,
  deleted_reason varchar(255) default '',
  deleted_time timestamp null default null,
  comment_count int default 0,
  score double default 0
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
  deleted_time timestamp null default null
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
  primary key (post_id, tag_id)
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
  unique key uk_report_dedupe (reporter_id, target_type, target_id)
);

create table if not exists moderation_action (
  id binary(16) primary key,
  report_id binary(16) default null,
  actor_id binary(16) not null,
  action varchar(32) not null,
  reason varchar(255) default '',
  duration_seconds int default 0,
  create_time timestamp null default current_timestamp
);

-- bookmarks/subscriptions (MVP)
create table if not exists post_bookmark (
  user_id binary(16) not null,
  post_id binary(16) not null,
  create_time timestamp null default current_timestamp,
  primary key (user_id, post_id)
);

create table if not exists user_subscription_category (
  user_id binary(16) not null,
  category_id binary(16) not null,
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
set @sql := if(@has_category_id = 0, 'alter table discuss_post add column category_id binary(16) default null', 'select 1');
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
set @sql := if(@has_deleted_by = 0, 'alter table discuss_post add column deleted_by binary(16) default null', 'select 1');
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
set @sql := if(@has_comment_deleted_by = 0, 'alter table comment add column deleted_by binary(16) default null', 'select 1');
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
