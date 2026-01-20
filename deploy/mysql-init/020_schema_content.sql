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
  create_time timestamp null default current_timestamp
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

-- Default categories for dev (idempotent)
insert ignore into category(name, description, position) values
  ('公告', '官方公告/规则', 0),
  ('技术', '技术讨论/问题求助', 10),
  ('兴趣', '兴趣分享/作品展示', 20);
