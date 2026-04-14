-- Source: 020_schema_content.sql (compat split)
-- --------------------------------------------------------------------
-- Content compatibility upgrades and idempotent indexes (community).

use community;

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

-- Default categories for dev (idempotent)
insert ignore into category(name, description, position) values
  ('公告', '官方公告/规则', 0),
  ('技术', '技术讨论/问题求助', 10),
  ('兴趣', '兴趣分享/作品展示', 20);


-- --------------------------------------------------------------------
